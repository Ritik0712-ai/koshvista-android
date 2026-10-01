package com.ritikagarwal.koshvista.data

import androidx.room.withTransaction
import com.ritikagarwal.koshvista.core.EntryKind
import com.ritikagarwal.koshvista.core.LedgerMath
import com.ritikagarwal.koshvista.core.Money
import java.time.LocalDate
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** The only write path for posted account and ledger records. */
class LedgerRepository(private val ownerId: String, private val database: VaultDatabase) {
    private val dao = database.vaultDao()

    val accounts: Flow<List<AccountEntity>> = dao.accounts(ownerId)
    val balances: Flow<List<AccountBalance>> = dao.balances(ownerId)
    val categories: Flow<List<CategoryEntity>> = dao.categories(ownerId)
    val recentTransactions: Flow<List<TransactionEntity>> = dao.transactions(ownerId, 100, 0)
    val fixedDeposits: Flow<List<FixedDepositEntity>> = dao.fixedDeposits(ownerId)

    suspend fun initialiseOwner(displayName: String?) = database.withTransaction {
        if (dao.owner(ownerId) == null) {
            dao.insertOwner(OwnerEntity(ownerId, displayName, System.currentTimeMillis()))
        }
        if (dao.categoryCount(ownerId) == 0) {
            val now = System.currentTimeMillis()
            listOf("Food", "Groceries", "Transport", "Shopping", "Bills", "Health", "Other")
                .forEach { name -> dao.insertCategory(CategoryEntity(ownerId, UUID.randomUUID().toString(), name, "expense", true, createdAtMs = now, updatedAtMs = now)) }
            listOf("Salary", "Interest", "Gifts", "Other income")
                .forEach { name -> dao.insertCategory(CategoryEntity(ownerId, UUID.randomUUID().toString(), name, "income", true, createdAtMs = now, updatedAtMs = now)) }
        }
    }

    suspend fun addAccount(
        type: String,
        name: String,
        currencyCode: String,
        openingBalance: Money,
        openingDate: LocalDate,
        institutionName: String? = null,
        isDefaultCash: Boolean = false,
    ): String = database.withTransaction {
        require(type in setOf("bank", "cash", "credit_card", "broker_cash", "asset", "liability"))
        require(name.isNotBlank())
        require(openingBalance.currencyCode == currencyCode)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val openingMinor = if (type == "liability" || type == "credit_card") -kotlin.math.abs(openingBalance.minor)
            else openingBalance.minor
        dao.insertAccount(AccountEntity(ownerId, id, type, name.trim(), institutionName?.trim(), currencyCode,
            openingMinor, openingDate.toString(), isDefaultCash = isDefaultCash, createdAtMs = now, updatedAtMs = now))
        id
    }

    suspend fun addTransaction(
        accountId: String,
        kind: EntryKind,
        amount: Money,
        date: LocalDate,
        description: String,
        categoryId: String? = null,
        note: String? = null,
        sourceDocumentId: String? = null,
        sourceFingerprint: String? = null,
    ): String = database.withTransaction {
        val account = dao.account(ownerId, accountId) ?: error("Account unavailable")
        require(account.status == "active")
        require(account.currencyCode == amount.currencyCode)
        require(description.isNotBlank())
        require(amount.minor != 0L)
        require(kind != EntryKind.TRANSFER) { "Use transfer() for paired entries" }
        require(kind != EntryKind.EXPENSE && kind != EntryKind.FEE || amount.minor < 0)
        require(kind != EntryKind.INCOME && kind != EntryKind.REFUND || amount.minor > 0)
        if (sourceFingerprint != null) require(dao.fingerprintCount(ownerId, accountId, sourceFingerprint) == 0) { "Already recorded" }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.insertTransaction(TransactionEntity(ownerId, id, accountId, date.toString(), description.trim(), categoryId,
            amount.minor, amount.currencyCode, kind.name.lowercase(), sourceDocumentId = sourceDocumentId,
            sourceFingerprint = sourceFingerprint, note = note, createdAtMs = now, updatedAtMs = now))
        id
    }

    suspend fun transfer(fromAccountId: String, toAccountId: String, amount: Money, date: LocalDate, note: String? = null): String = database.withTransaction {
        val from = dao.account(ownerId, fromAccountId) ?: error("Source account unavailable")
        val to = dao.account(ownerId, toAccountId) ?: error("Destination account unavailable")
        require(from.status == "active" && to.status == "active")
        require(from.currencyCode == amount.currencyCode && to.currencyCode == amount.currencyCode) { "Cross-currency transfer needs an explicit FX rate" }
        val (debit, credit) = LedgerMath.transfer(fromAccountId, toAccountId, amount, date)
        val group = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        listOf(debit, credit).forEach { entry ->
            dao.insertTransaction(TransactionEntity(ownerId, UUID.randomUUID().toString(), entry.accountId,
                entry.date.toString(), note?.ifBlank { null } ?: "Transfer", null, entry.amount.minor,
                entry.amount.currencyCode, "transfer", transferGroupId = group, createdAtMs = now, updatedAtMs = now))
        }
        check(dao.transferEntries(ownerId, group).sumOf { it.amountMinor } == 0L)
        group
    }

    suspend fun split(transactionId: String, allocations: List<Pair<String, Money>>) = database.withTransaction {
        val transaction = dao.transaction(ownerId, transactionId) ?: error("Transaction unavailable")
        require(dao.splits(ownerId, transactionId).isEmpty()) { "Splits already exist" }
        LedgerMath.checkSplits(Money(transaction.amountMinor, transaction.currencyCode), allocations.map { it.second })
        val now = System.currentTimeMillis()
        allocations.forEachIndexed { position, (categoryId, amount) ->
            dao.insertSplit(TransactionSplitEntity(ownerId, UUID.randomUUID().toString(), transactionId, categoryId,
                amount.minor, null, position, now, now))
        }
    }

    /** Moves principal into an asset account in the same transaction as the contract. */
    suspend fun createFixedDeposit(
        sourceAccountId: String,
        name: String,
        institutionName: String,
        principal: Money,
        annualRate: BigDecimal,
        start: LocalDate,
        maturity: LocalDate,
    ): String = database.withTransaction {
        require(name.isNotBlank() && institutionName.isNotBlank())
        require(principal.minor > 0)
        require(maturity > start)
        require(annualRate >= BigDecimal.ZERO && annualRate <= BigDecimal.ONE)
        val source = dao.account(ownerId, sourceAccountId) ?: error("Funding account unavailable")
        require(source.status == "active" && source.type in setOf("bank", "cash", "broker_cash"))
        require(source.currencyCode == principal.currencyCode)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val assetId = UUID.randomUUID().toString()
        dao.insertAccount(AccountEntity(ownerId, assetId, "asset", name.trim(), institutionName.trim(),
            principal.currencyCode, 0, start.toString(), createdAtMs = now, updatedAtMs = now))
        val group = UUID.randomUUID().toString()
        val (debit, credit) = LedgerMath.transfer(sourceAccountId, assetId, principal, start)
        listOf(debit, credit).forEach { entry ->
            dao.insertTransaction(TransactionEntity(ownerId, UUID.randomUUID().toString(), entry.accountId,
                start.toString(), "Fixed deposit funding: ${name.trim()}", null, entry.amount.minor,
                principal.currencyCode, "transfer", transferGroupId = group, createdAtMs = now, updatedAtMs = now))
        }
        dao.insertFixedDeposit(FixedDepositEntity(ownerId, id, name.trim(), institutionName.trim(), assetId,
            principal.minor, principal.currencyCode, start.toString(), maturity.toString(),
            annualRate.stripTrailingZeros().toPlainString(), createdAtMs = now, updatedAtMs = now))
        check(dao.transferEntries(ownerId, group).sumOf { it.amountMinor } == 0L)
        id
    }

    /** Retains the source record and atomically voids both sides of a transfer. */
    suspend fun voidTransaction(transactionId: String) = database.withTransaction {
        val original = dao.transaction(ownerId, transactionId) ?: error("Transaction unavailable")
        require(original.status == "posted") { "Transaction is already void" }
        val entries = original.transferGroupId?.let { dao.transferEntries(ownerId, it) } ?: listOf(original)
        require(entries.isNotEmpty() && entries.all { it.status == "posted" })
        if (original.transferGroupId != null) require(entries.size == 2 && entries.sumOf { it.amountMinor } == 0L)
        require(entries.none { dao.activeDepositCountForAccount(ownerId, it.accountId) > 0 }) {
            "An active fixed deposit uses this funding transfer"
        }
        val now = System.currentTimeMillis()
        entries.forEach { dao.updateTransaction(it.copy(status = "void", updatedAtMs = now)) }
    }
}
