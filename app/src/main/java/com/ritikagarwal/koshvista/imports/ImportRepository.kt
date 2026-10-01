package com.ritikagarwal.koshvista.imports

import android.content.Context
import androidx.room.withTransaction
import com.ritikagarwal.koshvista.data.ImportCandidateEntity
import com.ritikagarwal.koshvista.data.ImportJobEntity
import com.ritikagarwal.koshvista.data.SourceDocumentEntity
import com.ritikagarwal.koshvista.data.TransactionEntity
import com.ritikagarwal.koshvista.data.VaultDatabase
import com.ritikagarwal.koshvista.security.DocumentStore
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.time.LocalDate
import com.ritikagarwal.koshvista.core.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ImportRepository(
    private val ownerId: String,
    private val database: VaultDatabase,
    context: Context,
) {
    private val dao = database.importDao()
    private val ledgerDao = database.vaultDao()
    private val documents = DocumentStore(context)
    val history: Flow<List<ImportJobEntity>> = dao.jobs(ownerId)

    fun candidates(jobId: String): Flow<List<ImportCandidateEntity>> = dao.candidates(ownerId, jobId)

    suspend fun stageCsv(accountId: String, displayName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty() && bytes.size <= 20 * 1024 * 1024) { "CSV must be between 1 byte and 20 MB" }
        val account = ledgerDao.account(ownerId, accountId) ?: error("Account unavailable")
        require(account.status == "active")
        val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val parsed = CsvStatementParser.parse(text, account.currencyCode)
        require(parsed.isNotEmpty()) { "No statement rows found" }
        val hash = sha256(bytes)
        dao.documentByHash(ownerId, hash)?.let { existing ->
            return@withContext dao.jobByDocument(ownerId, existing.id)?.id ?: error("This file was already stored")
        }
        val fileRef = documents.store(ownerId, bytes)
        try {
            database.withTransaction {
                val now = System.currentTimeMillis()
                val docId = UUID.randomUUID().toString()
                val jobId = UUID.randomUUID().toString()
                dao.insertDocument(SourceDocumentEntity(ownerId, docId, displayName.take(200), "text/csv", hash,
                    fileRef, "bank_statement", bytes.size.toLong(), now, now))
                dao.insertJob(ImportJobEntity(ownerId, jobId, docId, accountId, "review", 0, 0, now, now))
                parsed.forEach { row ->
                    val fingerprint = if (row.date != null && row.amount != null && row.description.isNotBlank())
                        sha256("$accountId|${row.date}|${row.amount.minor}|${row.description.trim().lowercase()}".toByteArray())
                    else null
                    val possibleDuplicate = fingerprint != null && ledgerDao.fingerprintCount(ownerId, accountId, fingerprint) > 0
                    val reasons = row.reviewReasons + if (possibleDuplicate) listOf("Possible duplicate") else emptyList()
                    val decision = when {
                        possibleDuplicate -> "duplicate"
                        row.ready -> "accepted"
                        else -> "unreviewed"
                    }
                    dao.insertCandidate(ImportCandidateEntity(ownerId, UUID.randomUUID().toString(), jobId,
                        row.sourceRow, row.date?.toString(), row.description, row.amount?.minor,
                        account.currencyCode, reasons.joinToString("; "), decision, fingerprint,
                        createdAtMs = now, updatedAtMs = now))
                }
                jobId
            }
        } catch (error: Exception) {
            documents.delete(ownerId, fileRef)
            throw error
        }
    }

    suspend fun decide(candidateId: String, decision: String) = database.withTransaction {
        require(decision in setOf("accepted", "rejected", "duplicate"))
        val candidate = dao.candidate(ownerId, candidateId) ?: error("Candidate unavailable")
        val job = dao.job(ownerId, candidate.jobId) ?: error("Import unavailable")
        require(job.status == "review")
        if (decision == "accepted") {
            require(candidate.localDate != null && candidate.amountMinor != null && candidate.amountMinor != 0L)
            require(candidate.description.isNotBlank())
        }
        dao.updateCandidate(candidate.copy(decision = decision, updatedAtMs = System.currentTimeMillis()))
    }

    suspend fun editCandidate(candidateId: String, date: String, description: String, signedAmount: String) = database.withTransaction {
        val candidate = dao.candidate(ownerId, candidateId) ?: error("Candidate unavailable")
        val job = dao.job(ownerId, candidate.jobId) ?: error("Import unavailable")
        require(job.status == "review")
        val parsedDate = LocalDate.parse(date.trim()).toString()
        val parsedAmount = Money.parse(signedAmount, candidate.currencyCode)
        require(parsedAmount.minor != 0L) { "Amount must not be zero" }
        require(description.isNotBlank()) { "Description is required" }
        val normalDescription = description.trim()
        val fingerprint = sha256("${job.accountId}|$parsedDate|${parsedAmount.minor}|${normalDescription.lowercase()}".toByteArray())
        val possibleDuplicate = ledgerDao.fingerprintCount(ownerId, job.accountId, fingerprint) > 0
        dao.updateCandidate(candidate.copy(localDate = parsedDate, description = normalDescription,
            amountMinor = parsedAmount.minor, fingerprint = fingerprint,
            reviewReasons = if (possibleDuplicate) "Possible duplicate; confirm before accepting" else "Edited; confirm before accepting",
            decision = "unreviewed", updatedAtMs = System.currentTimeMillis()))
    }

    suspend fun commit(jobId: String): Int = database.withTransaction {
        val job = dao.job(ownerId, jobId) ?: error("Import unavailable")
        if (job.status == "completed") return@withTransaction job.acceptedCount
        require(job.status == "review")
        val account = ledgerDao.account(ownerId, job.accountId) ?: error("Account unavailable")
        val rows = dao.candidatesOnce(ownerId, jobId)
        require(rows.none { it.decision == "unreviewed" }) { "Resolve uncertain rows before saving" }
        val now = System.currentTimeMillis()
        var count = 0
        rows.filter { it.decision == "accepted" }.forEach { row ->
            val amount = requireNotNull(row.amountMinor)
            require(amount != 0L && row.currencyCode == account.currencyCode)
            val id = UUID.randomUUID().toString()
            ledgerDao.insertTransaction(TransactionEntity(ownerId, id, job.accountId, requireNotNull(row.localDate),
                row.description, null, amount, row.currencyCode, if (amount < 0) "expense" else "income",
                sourceDocumentId = job.documentId, sourceFingerprint = row.fingerprint,
                createdAtMs = now, updatedAtMs = now))
            dao.updateCandidate(row.copy(linkedTransactionId = id, updatedAtMs = now))
            count++
        }
        dao.updateJob(job.copy(status = "completed", acceptedCount = count,
            duplicateCount = rows.count { it.decision == "duplicate" }, updatedAtMs = now))
        count
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
