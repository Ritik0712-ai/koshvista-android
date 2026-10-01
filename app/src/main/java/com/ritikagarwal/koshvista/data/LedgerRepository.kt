package com.ritikagarwal.koshvista.data

import androidx.room.withTransaction
import com.ritikagarwal.koshvista.core.EntryKind
import com.ritikagarwal.koshvista.core.LedgerMath
import com.ritikagarwal.koshvista.core.Money
import com.ritikagarwal.koshvista.core.Position
import com.ritikagarwal.koshvista.core.PositionMath
import java.time.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class PositionSummary(val instrument: InstrumentEntity, val quantity: BigDecimal, val costBasisMinor: Long)

/** The only write path for posted account and ledger records. */
class LedgerRepository(private val ownerId: String, private val database: VaultDatabase) {
    private val dao = database.vaultDao()

    val accounts: Flow<List<AccountEntity>> = dao.accounts(ownerId)
    val allAccounts: Flow<List<AccountEntity>> = dao.allAccounts(ownerId)
    val balances: Flow<List<AccountBalance>> = dao.balances(ownerId)
    val categories: Flow<List<CategoryEntity>> = dao.categories(ownerId)
    val recentTransactions: Flow<List<TransactionEntity>> = dao.transactions(ownerId, 100, 0)
    val fixedDeposits: Flow<List<FixedDepositEntity>> = dao.fixedDeposits(ownerId)
    val budgets: Flow<List<BudgetEntity>> = dao.budgets(ownerId)
    val instruments: Flow<List<InstrumentEntity>> = dao.instruments(ownerId)
    val investmentTrades: Flow<List<InvestmentTradeEntity>> = dao.investmentTrades(ownerId)
    val positions: Flow<List<PositionSummary>> = combine(instruments, investmentTrades) { allInstruments, trades ->
        allInstruments.map { instrument ->
            val held = trades.filter { it.instrumentId == instrument.id }.fold(Position(BigDecimal.ZERO, 0L)) { position, trade ->
                val units = trade.quantityDecimal.toBigDecimal()
                if (trade.side == "buy") Position(position.quantity + units, Math.addExact(position.costBasisMinor, trade.grossMinor))
                else Position(position.quantity - units, Math.subtractExact(position.costBasisMinor, trade.costBasisMinor))
            }
            PositionSummary(instrument, held.quantity, held.costBasisMinor)
        }.filter { it.quantity > BigDecimal.ZERO }
    }
    fun observations(accountId: String): Flow<List<BalanceObservationEntity>> = dao.observations(ownerId, accountId)

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
        require(date.toString() >= account.openingLocalDate) { "Date precedes account opening" }
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

    suspend fun renameAccount(accountId: String, name: String) = database.withTransaction {
        require(name.isNotBlank())
        val account = dao.account(ownerId, accountId) ?: error("Account unavailable")
        require(account.status == "active")
        dao.updateAccount(account.copy(name = name.trim(), updatedAtMs = System.currentTimeMillis()))
    }

    suspend fun archiveAccount(accountId: String) = database.withTransaction {
        val account = dao.account(ownerId, accountId) ?: error("Account unavailable")
        require(account.status == "active")
        require(dao.accountBalanceOnce(ownerId, accountId) == 0L) { "Move the remaining balance before archiving" }
        require(database.importDao().pendingJobsForAccount(ownerId, accountId) == 0) { "Review or cancel pending imports first" }
        require(dao.activeDepositCountForAccount(ownerId, accountId) == 0) { "An active fixed deposit uses this account" }
        require(dao.instrumentCountForAccount(ownerId, accountId) == 0) { "An investment uses this account" }
        dao.updateAccount(account.copy(status = "archived", updatedAtMs = System.currentTimeMillis()))
    }

    suspend fun transfer(fromAccountId: String, toAccountId: String, amount: Money, date: LocalDate, note: String? = null): String = database.withTransaction {
        val from = dao.account(ownerId, fromAccountId) ?: error("Source account unavailable")
        val to = dao.account(ownerId, toAccountId) ?: error("Destination account unavailable")
        require(from.status == "active" && to.status == "active")
        require(date.toString() >= from.openingLocalDate && date.toString() >= to.openingLocalDate) {
            "Transfer date precedes an account opening"
        }
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
        require(start.toString() >= source.openingLocalDate)
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
        require(original.tradeId == null) { "Investment trades must be adjusted through the trade ledger" }
        if (original.kind == "expense") require(dao.refundedMinor(ownerId, original.id) == 0L) {
            "Reverse linked refunds before reversing this expense"
        }
        val entries = original.transferGroupId?.let { dao.transferEntries(ownerId, it) } ?: listOf(original)
        require(entries.isNotEmpty() && entries.all { it.status == "posted" && it.tradeId == null })
        if (original.transferGroupId != null) require(entries.size == 2 && entries.sumOf { it.amountMinor } == 0L)
        require(entries.none { dao.activeDepositCountForAccount(ownerId, it.accountId) > 0 }) {
            "An active fixed deposit uses this funding transfer"
        }
        val now = System.currentTimeMillis()
        entries.forEach { dao.updateTransaction(it.copy(status = "void", updatedAtMs = now)) }
    }

    suspend fun setMonthlyBudget(categoryId: String, limit: Money) = database.withTransaction {
        require(limit.minor > 0 && limit.currencyCode == "INR")
        val category = dao.category(ownerId, categoryId) ?: error("Category unavailable")
        require(category.kind == "expense" && !category.isArchived)
        val now = System.currentTimeMillis()
        val existing = dao.budgetForCategory(ownerId, categoryId)
        if (existing == null) dao.insertBudget(BudgetEntity(ownerId, UUID.randomUUID().toString(),
            categoryId, limit.minor, limit.currencyCode, createdAtMs = now, updatedAtMs = now))
        else dao.updateBudget(existing.copy(limitMinor = limit.minor, status = "active", updatedAtMs = now))
    }

    suspend fun refund(originalExpenseId: String, amount: Money, date: LocalDate): String = database.withTransaction {
        val original = dao.transaction(ownerId, originalExpenseId) ?: error("Original expense unavailable")
        require(original.status == "posted" && original.kind == "expense")
        val account = dao.account(ownerId, original.accountId) ?: error("Account unavailable")
        require(account.status == "active" && amount.currencyCode == original.currencyCode)
        require(date.toString() >= original.localDate) { "Refund predates the expense" }
        require(amount.minor > 0)
        val remaining = BigDecimal.valueOf(original.amountMinor).negate()
            .subtract(BigDecimal.valueOf(dao.refundedMinor(ownerId, originalExpenseId))).longValueExact()
        require(amount.minor <= remaining) { "Refund exceeds the remaining expense" }
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.insertTransaction(TransactionEntity(ownerId, id, original.accountId, date.toString(),
            "Refund: ${original.description}", original.categoryId, amount.minor, amount.currencyCode,
            "refund", refundOfTransactionId = original.id, createdAtMs = now, updatedAtMs = now))
        val originalSplits = dao.splits(ownerId, original.id)
        if (originalSplits.isNotEmpty()) {
            var allocated = 0L
            originalSplits.forEachIndexed { index, split ->
                val part = if (index == originalSplits.lastIndex) Math.subtractExact(amount.minor, allocated)
                else minOf(Math.subtractExact(amount.minor, allocated),
                    BigDecimal.valueOf(amount.minor).multiply(BigDecimal.valueOf(split.amountMinor).abs())
                        .divide(BigDecimal.valueOf(original.amountMinor).abs(), 0, RoundingMode.HALF_EVEN).longValueExact())
                allocated = Math.addExact(allocated, part)
                dao.insertSplit(TransactionSplitEntity(ownerId, UUID.randomUUID().toString(), id,
                    split.categoryId, part, null, index, now, now))
            }
            check(allocated == amount.minor)
        }
        id
    }

    /** Trade entries, funding transfer and fees are committed together. */
    suspend fun recordTrade(brokerAccountId: String, symbol: String, name: String, side: String,
        quantity: BigDecimal, unitPrice: BigDecimal, fees: Money, date: LocalDate): String = database.withTransaction {
        require(side in setOf("buy", "sell"))
        require(symbol.isNotBlank() && name.isNotBlank())
        require(quantity > BigDecimal.ZERO && unitPrice > BigDecimal.ZERO)
        require(fees.minor >= 0)
        val broker = dao.account(ownerId, brokerAccountId) ?: error("Broker cash account unavailable")
        require(broker.status == "active" && broker.type == "broker_cash")
        require(date.toString() >= broker.openingLocalDate)
        require(broker.currencyCode == fees.currencyCode)
        val normalizedSymbol = symbol.trim().uppercase()
        val gross = PositionMath.grossMinor(quantity, unitPrice, fees.currencyCode)
        val now = System.currentTimeMillis()
        var instrument = dao.instrumentBySymbol(ownerId, normalizedSymbol)
        if (instrument == null) {
            require(side == "buy") { "Buy an instrument before selling it" }
            val assetId = UUID.randomUUID().toString()
            dao.insertAccount(AccountEntity(ownerId, assetId, "asset", name.trim(), broker.institutionName,
                broker.currencyCode, 0, date.toString(), createdAtMs = now, updatedAtMs = now))
            instrument = InstrumentEntity(ownerId, UUID.randomUUID().toString(), name.trim(), normalizedSymbol,
                assetId, broker.currencyCode, now, now)
            dao.insertInstrument(instrument)
        }
        require(instrument.currencyCode == broker.currencyCode)
        val previous = dao.instrumentTrades(ownerId, instrument.id)
        require(previous.isEmpty() || date.toString() >= previous.last().tradeLocalDate) {
            "Add trades in date order so cost basis stays correct"
        }
        val held = previous.fold(Position(BigDecimal.ZERO, 0L)) { position, trade ->
            val units = trade.quantityDecimal.toBigDecimal()
            if (trade.side == "buy") Position(position.quantity + units, Math.addExact(position.costBasisMinor, trade.grossMinor))
            else Position(position.quantity - units, Math.subtractExact(position.costBasisMinor, trade.costBasisMinor))
        }
        val costBasis = if (side == "sell") PositionMath.soldCostBasis(held, quantity) else gross
        val realisedGain = if (side == "sell") Math.subtractExact(gross, costBasis) else 0L
        val tradeId = UUID.randomUUID().toString()
        val group = UUID.randomUUID().toString()
        val funding = if (side == "buy") LedgerMath.transfer(brokerAccountId, instrument.assetAccountId,
            Money(gross, broker.currencyCode), date)
        else LedgerMath.transfer(instrument.assetAccountId, brokerAccountId, Money(costBasis, broker.currencyCode), date)
        listOf(funding.first, funding.second).forEach { entry ->
            dao.insertTransaction(TransactionEntity(ownerId, UUID.randomUUID().toString(), entry.accountId,
                date.toString(), "${side.replaceFirstChar { it.uppercase() }} $normalizedSymbol", null,
                entry.amount.minor, broker.currencyCode, "transfer", transferGroupId = group,
                tradeId = tradeId, createdAtMs = now, updatedAtMs = now))
        }
        if (realisedGain != 0L) dao.insertTransaction(TransactionEntity(ownerId, UUID.randomUUID().toString(),
            brokerAccountId, date.toString(), "Realised gain/loss $normalizedSymbol", null, realisedGain,
            broker.currencyCode, "trade_cash", tradeId = tradeId, createdAtMs = now, updatedAtMs = now))
        if (fees.minor > 0) dao.insertTransaction(TransactionEntity(ownerId, UUID.randomUUID().toString(),
            brokerAccountId, date.toString(), "Trade fee $normalizedSymbol", null, -fees.minor,
            broker.currencyCode, "fee", tradeId = tradeId, createdAtMs = now, updatedAtMs = now))
        dao.insertTrade(InvestmentTradeEntity(ownerId, tradeId, instrument.id, brokerAccountId, side,
            date.toString(), quantity.stripTrailingZeros().toPlainString(), unitPrice.stripTrailingZeros().toPlainString(),
            fees.minor, gross, costBasis, realisedGain, broker.currencyCode, now, now))
        check(dao.transferEntries(ownerId, group).sumOf { it.amountMinor } == 0L)
        tradeId
    }

    suspend fun observeBalance(accountId: String, day: LocalDate, observed: Money): String = database.withTransaction {
        val account = dao.account(ownerId, accountId) ?: error("Account unavailable")
        require(account.status == "active" && account.currencyCode == observed.currencyCode)
        require(day.toString() >= account.openingLocalDate)
        val computed = dao.accountBalanceThrough(ownerId, accountId, day.toString()) ?: error("Account unavailable")
        val difference = Math.subtractExact(observed.minor, computed)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.insertObservation(BalanceObservationEntity(ownerId, id, accountId, day.toString(), observed.minor,
            computed, difference, observed.currencyCode, if (difference == 0L) "matched" else "unresolved",
            createdAtMs = now, updatedAtMs = now))
        id
    }

    suspend fun postReconciliationAdjustment(observationId: String): String = database.withTransaction {
        val observation = dao.observation(ownerId, observationId) ?: error("Observation unavailable")
        require(observation.status == "unresolved" && observation.differenceMinor != 0L)
        val account = dao.account(ownerId, observation.accountId) ?: error("Account unavailable")
        require(account.status == "active")
        require(dao.accountBalanceThrough(ownerId, account.id, observation.observedLocalDate) == observation.computedBalanceMinor) {
            "Ledger changed since comparison; compare the balance again"
        }
        val now = System.currentTimeMillis()
        val transactionId = UUID.randomUUID().toString()
        dao.insertTransaction(TransactionEntity(ownerId, transactionId, account.id, observation.observedLocalDate,
            "Balance reconciliation", null, observation.differenceMinor, account.currencyCode, "adjustment",
            note = "User-approved adjustment for observation ${observation.id}", createdAtMs = now, updatedAtMs = now))
        dao.updateObservation(observation.copy(status = "adjusted", resolvedTransactionId = transactionId, updatedAtMs = now))
        check(dao.accountBalanceThrough(ownerId, account.id, observation.observedLocalDate) == observation.observedBalanceMinor)
        transactionId
    }
}
