package com.ritikagarwal.koshvista.data

import androidx.room.withTransaction
import com.ritikagarwal.koshvista.core.EntryKind
import com.ritikagarwal.koshvista.core.LedgerMath
import com.ritikagarwal.koshvista.core.Money
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** The only write path for posted account and ledger records. */
class LedgerRepository(private val ownerId: String, private val database: VaultDatabase) {
    private val dao = database.vaultDao()

    val accounts: Flow<List<AccountEntity>> = dao.accounts(ownerId)
    val balances: Flow<List<AccountBalance>> = dao.balances(ownerId)
    val categories: Flow<List<CategoryEntity>> = dao.categories(ownerId)
    val recentTransactions: Flow<List<TransactionEntity>> = dao.transactions(ownerId, 100, 0)

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
        dao.insertAccount(AccountEntity(ownerId, id, type, name.trim(), institutionName?.trim(), currencyCode,
            openingBalance.minor, openingDate.toString(), isDefaultCash = isDefaultCash, createdAtMs = now, updatedAtMs = now))
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
}
