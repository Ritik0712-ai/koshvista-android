package com.ritikagarwal.koshvista.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ritikagarwal.koshvista.core.Money
import com.ritikagarwal.koshvista.backup.LocalVaultBackup
import com.ritikagarwal.koshvista.core.EntryKind
import com.ritikagarwal.koshvista.imports.ImportRepository
import com.ritikagarwal.koshvista.security.DocumentStore
import java.time.LocalDate
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultIntegrationTest {
    @Test fun reconciliationRequiresExplicitAdjustmentAndRejectsStaleComparison() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = VaultFactory(context)
        val owner = "test-${UUID.randomUUID()}"
        try {
            val database = factory.open(owner)
            try {
                val repo = LedgerRepository(owner, database)
                val day = LocalDate.of(2026, 10, 1)
                repo.initialiseOwner("Tester")
                val bank = repo.addAccount("bank", "Bank", "INR", Money(10_000, "INR"), day)
                val stale = repo.observeBalance(bank, day, Money(12_000, "INR"))
                assertEquals(10_000L, repo.balances.first().single().balanceMinor)
                repo.addTransaction(bank, EntryKind.INCOME, Money(500, "INR"), day, "Late entry")
                assertTrue(runCatching { repo.postReconciliationAdjustment(stale) }.isFailure)
                val current = repo.observeBalance(bank, day, Money(12_000, "INR"))
                val observation = repo.observations(bank).first().first { it.id == current }
                assertEquals(1_500L, observation.differenceMinor)
                repo.postReconciliationAdjustment(current)
                assertEquals(12_000L, repo.balances.first().single().balanceMinor)
                assertEquals("adjusted", repo.observations(bank).first().first { it.id == current }.status)
                assertTrue(runCatching { repo.postReconciliationAdjustment(current) }.isFailure)
            } finally { database.close() }
        } finally { factory.delete(owner) }
    }

    @Test fun accountArchiveRequiresZeroBalanceAndPreservesLedger() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = VaultFactory(context)
        val owner = "test-${UUID.randomUUID()}"
        try {
            val database = factory.open(owner)
            try {
                val repo = LedgerRepository(owner, database)
                val day = LocalDate.of(2026, 10, 1)
                repo.initialiseOwner("Tester")
                val bank = repo.addAccount("bank", "Bank", "INR", Money(10_000, "INR"), day)
                val cash = repo.addAccount("cash", "Wallet", "INR", Money(0, "INR"), day)
                repo.renameAccount(cash, "Petty cash")
                assertEquals("Petty cash", repo.accounts.first().single { it.id == cash }.name)
                assertTrue(runCatching { repo.archiveAccount(bank) }.isFailure)
                repo.transfer(bank, cash, Money(10_000, "INR"), day)
                repo.archiveAccount(bank)
                assertEquals("archived", database.vaultDao().account(owner, bank)?.status)
                assertEquals(1, repo.accounts.first().size)
                assertEquals(2, database.vaultDao().transferEntries(owner,
                    repo.recentTransactions.first().first().transferGroupId!!).size)
            } finally { database.close() }
        } finally { factory.delete(owner) }
    }

    @Test fun investmentBuyAndSellKeepCostBasisAndCashReconciled() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = VaultFactory(context)
        val owner = "test-${UUID.randomUUID()}"
        try {
            val database = factory.open(owner)
            try {
                val repo = LedgerRepository(owner, database)
                val day = LocalDate.of(2026, 10, 1)
                repo.initialiseOwner("Tester")
                val broker = repo.addAccount("broker_cash", "Broker", "INR", Money(100_000, "INR"), day)
                repo.recordTrade(broker, "ABC", "ABC Ltd", "buy", BigDecimal("10"), BigDecimal("20"),
                    Money(100, "INR"), day)
                assertEquals(10, repo.positions.first().single().quantity.toInt())
                assertEquals(20_000L, repo.positions.first().single().costBasisMinor)
                assertEquals(79_900L, repo.balances.first().single { it.id == broker }.balanceMinor)
                val sellId = repo.recordTrade(broker, "ABC", "ABC Ltd", "sell", BigDecimal("4"), BigDecimal("25"),
                    Money(50, "INR"), day.plusDays(1))
                assertEquals(6, repo.positions.first().single().quantity.toInt())
                assertEquals(12_000L, repo.positions.first().single().costBasisMinor)
                assertEquals(89_850L, repo.balances.first().single { it.id == broker }.balanceMinor)
                assertEquals(101_850L, repo.balances.first().sumOf { it.balanceMinor })
                assertEquals(2_000L, repo.investmentTrades.first().single { it.id == sellId }.realisedGainMinor)
                val linked = repo.recentTransactions.first().first { it.tradeId == sellId }
                assertTrue(runCatching { repo.voidTransaction(linked.id) }.isFailure)
                assertTrue(runCatching { repo.recordTrade(broker, "ABC", "ABC Ltd", "sell", BigDecimal("7"),
                    BigDecimal("25"), Money(0, "INR"), day.plusDays(2)) }.isFailure)
            } finally { database.close() }
        } finally { factory.delete(owner) }
    }

    @Test fun encryptedLocalBackupRestoresLedgerAndSourceOnNewVaultKey() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = VaultFactory(context)
        val owner = "test-${UUID.randomUUID()}"
        val backup = LocalVaultBackup(context)
        val passphrase = "synthetic recovery words only".toCharArray()
        var oldRef: String? = null
        var newRef: String? = null
        try {
            val first = factory.open(owner)
            val archive = try {
                val repo = LedgerRepository(owner, first)
                repo.initialiseOwner("Tester")
                val day = LocalDate.of(2026, 10, 1)
                val bank = repo.addAccount("bank", "Bank", "INR", Money(50_000, "INR"), day)
                val food = repo.categories.first().first { it.name == "Food" }.id
                repo.setMonthlyBudget(food, Money(10_000, "INR"))
                val importer = ImportRepository(owner, first, context)
                val job = importer.stageCsv(bank, "sample.csv", "Date,Description,Debit,Credit\n2026-10-01,Cafe,12.50,\n".toByteArray())
                importer.commit(job)
                oldRef = first.importDao().document(owner, first.importDao().job(owner, job)!!.documentId)!!.encryptedFileRef
                backup.create(owner, first, passphrase)
            } finally { first.close() }
            oldRef?.let { DocumentStore(context).delete(owner, it) }
            factory.delete(owner)

            val restored = factory.open(owner)
            try {
                val repo = LedgerRepository(owner, restored)
                repo.initialiseOwner("Tester")
                assertTrue(runCatching { backup.restore(owner, restored, "wrong recovery words".toCharArray(), archive) }.isFailure)
                backup.restore(owner, restored, passphrase, archive)
                assertEquals(1, repo.recentTransactions.first().size)
                assertEquals(48_750L, repo.balances.first().single().balanceMinor)
                assertEquals(10_000L, repo.budgets.first().single().limitMinor)
                val job = restored.importDao().jobs(owner).first().single()
                newRef = restored.importDao().document(owner, job.documentId)!!.encryptedFileRef
                assertEquals("Date,Description,Debit,Credit\n2026-10-01,Cafe,12.50,\n",
                    String(DocumentStore(context).read(owner, newRef!!)))
                assertTrue(runCatching { backup.restore(owner, restored, passphrase, archive) }.isFailure)
            } finally { restored.close() }
        } finally {
            newRef?.let { DocumentStore(context).delete(owner, it) }
            factory.delete(owner)
            passphrase.fill('\u0000')
        }
    }

    @Test fun monthlyBudgetUsesExpenseOnlyAndCanBeUpdated() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = VaultFactory(context)
        val owner = "test-${UUID.randomUUID()}"
        try {
            val database = factory.open(owner)
            try {
                val repo = LedgerRepository(owner, database)
                val day = LocalDate.of(2026, 10, 1)
                repo.initialiseOwner("Tester")
                val bank = repo.addAccount("bank", "Bank", "INR", Money(100_000, "INR"), day)
                val cash = repo.addAccount("cash", "Cash", "INR", Money(0, "INR"), day)
                val food = repo.categories.first().first { it.name == "Food" }.id
                repo.setMonthlyBudget(food, Money(5_000, "INR"))
                repo.addTransaction(bank, EntryKind.EXPENSE, Money(-2_000, "INR"), day, "Lunch", food)
                repo.transfer(bank, cash, Money(1_000, "INR"), day)
                assertEquals(2_000L, database.vaultDao().categorySpending(owner, day.toString(), day.toString()).first()
                    .single { it.categoryId == food }.totalMinor)
                repo.setMonthlyBudget(food, Money(6_000, "INR"))
                assertEquals(1, repo.budgets.first().size)
                assertEquals(6_000L, repo.budgets.first().single().limitMinor)
            } finally { database.close() }
        } finally { factory.delete(owner) }
    }

    @Test fun reversingTransferVoidsBothSidesAndProtectsDeposits() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = VaultFactory(context)
        val owner = "test-${UUID.randomUUID()}"
        try {
            val database = factory.open(owner)
            try {
                val repo = LedgerRepository(owner, database)
                val day = LocalDate.of(2026, 1, 1)
                repo.initialiseOwner("Tester")
                val bank = repo.addAccount("bank", "Bank", "INR", Money(100_000, "INR"), day)
                val cash = repo.addAccount("cash", "Cash", "INR", Money(0, "INR"), day)
                val group = repo.transfer(bank, cash, Money(5_000, "INR"), day)
                repo.voidTransaction(database.vaultDao().transferEntries(owner, group).first().id)
                assertEquals(listOf("void", "void"), database.vaultDao().transferEntries(owner, group).map { it.status })
                assertEquals(100_000L, repo.balances.first().sumOf { it.balanceMinor })
                assertEquals(0L, repo.balances.first().single { it.id == cash }.balanceMinor)

                repo.createFixedDeposit(bank, "FD", "Bank", Money(20_000, "INR"), BigDecimal("0.05"), day, day.plusYears(1))
                val funding = repo.recentTransactions.first().first { it.description.startsWith("Fixed deposit funding") }
                assertTrue(runCatching { repo.voidTransaction(funding.id) }.isFailure)
                assertEquals(100_000L, repo.balances.first().sumOf { it.balanceMinor })
            } finally { database.close() }
        } finally { factory.delete(owner) }
    }

    @Test fun fixedDepositFundingIsAtomicAndDoesNotInflateNetWorth() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = VaultFactory(context)
        val owner = "test-${UUID.randomUUID()}"
        try {
            val database = factory.open(owner)
            try {
                val repo = LedgerRepository(owner, database)
                val start = LocalDate.of(2026, 1, 1)
                repo.initialiseOwner("Tester")
                val bank = repo.addAccount("bank", "Bank", "INR", Money(100_000, "INR"), start)
                val failed = runCatching { repo.createFixedDeposit(bank, "Invalid", "Bank", Money(20_000, "INR"),
                    BigDecimal("0.075"), start, start) }
                assertTrue(failed.isFailure)
                assertEquals(1, repo.balances.first().size)
                val id = repo.createFixedDeposit(bank, "One year FD", "Bank", Money(20_000, "INR"),
                    BigDecimal("0.075"), start, start.plusYears(1))
                val contract = repo.fixedDeposits.first().single()
                assertEquals(id, contract.id)
                assertEquals("0.075", contract.annualRateDecimal)
                val balances = repo.balances.first()
                assertEquals(100_000L, balances.sumOf { it.balanceMinor })
                assertEquals(80_000L, balances.single { it.id == bank }.balanceMinor)
                assertEquals(20_000L, balances.single { it.id == contract.assetAccountId }.balanceMinor)
                assertEquals(0L, database.vaultDao().totalForKind(owner, "expense", start.toString(), start.toString()).first())
            } finally { database.close() }
        } finally { factory.delete(owner) }
    }

    @Test fun spendingChartsReconcileWithSplitExpensesAndIgnoreTransfers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = VaultFactory(context)
        val owner = "test-${UUID.randomUUID()}"
        try {
            val database = factory.open(owner)
            try {
                val ledger = LedgerRepository(owner, database)
                val day = LocalDate.of(2026, 9, 30)
                ledger.initialiseOwner("Tester")
                val bank = ledger.addAccount("bank", "Bank", "INR", Money(10_000, "INR"), day)
                val cash = ledger.addAccount("cash", "Cash", "INR", Money(0, "INR"), day)
                val categories = ledger.categories.first().filter { it.kind == "expense" }
                val food = categories.first { it.name == "Food" }.id
                val shopping = categories.first { it.name == "Shopping" }.id
                val expense = ledger.addTransaction(bank, EntryKind.EXPENSE, Money(-2_000, "INR"), day, "Market")
                ledger.split(expense, listOf(food to Money(-1_200, "INR"), shopping to Money(-800, "INR")))
                ledger.transfer(bank, cash, Money(1_000, "INR"), day)

                val dao = database.vaultDao()
                val totals = dao.categorySpending(owner, day.toString(), day.toString()).first()
                assertEquals(2_000L, totals.sumOf { it.totalMinor })
                assertEquals(1_200L, totals.single { it.categoryId == food }.totalMinor)
                assertEquals(800L, totals.single { it.categoryId == shopping }.totalMinor)
                assertEquals(2_000L, dao.dailySpending(owner, day.toString(), day.toString()).first().single().totalMinor)
                assertEquals(3, dao.transactionsForDay(owner, day.toString()).first().size)
                assertEquals(listOf(expense), dao.transactionsForCategory(owner, food, day.toString(), day.toString()).first().map { it.id })
                assertEquals(listOf(expense), dao.searchTransactions(owner, bank, day.toString(), true,
                    food, day.toString(), day.toString(), "Market", 50, 0).first().map { it.id })
                assertEquals(1, dao.searchTransactions(owner, bank, null, false, null,
                    day.toString(), day.toString(), null, 1, 1).first().size)
                assertTrue(dao.searchTransactions(owner, null, null, false, null,
                    day.toString(), day.toString(), "not present", 50, 0).first().isEmpty())
            } finally { database.close() }
        } finally { factory.delete(owner) }
    }

    @Test fun encryptedVaultKeepsOwnersSeparateAndTransferBalanced() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = VaultFactory(context)
        val alice = "test-${UUID.randomUUID()}"
        val bob = "test-${UUID.randomUUID()}"
        try {
            val aliceDatabase = factory.open(alice)
            try {
                val database = aliceDatabase
                val repo = LedgerRepository(alice, database)
                repo.initialiseOwner("Alice")
                val bank = repo.addAccount("bank", "Bank", "INR", Money(10_000, "INR"), LocalDate.now())
                val cash = repo.addAccount("cash", "Cash", "INR", Money(0, "INR"), LocalDate.now())
                repo.transfer(bank, cash, Money(2_500, "INR"), LocalDate.now())
                val balances = repo.balances.first()
                assertEquals(7_500L, balances.single { it.id == bank }.balanceMinor)
                assertEquals(2_500L, balances.single { it.id == cash }.balanceMinor)
            } finally { aliceDatabase.close() }
            val bobDatabase = factory.open(bob)
            try {
                val database = bobDatabase
                val repo = LedgerRepository(bob, database)
                repo.initialiseOwner("Bob")
                assertTrue(repo.balances.first().isEmpty())
            } finally { bobDatabase.close() }
            val reopenedDatabase = factory.open(alice)
            try {
                val database = reopenedDatabase
                assertEquals(2, LedgerRepository(alice, database).balances.first().size)
            } finally { reopenedDatabase.close() }
        } finally {
            factory.delete(alice)
            factory.delete(bob)
        }
    }

    @Test fun csvReviewAndCommitAreAtomicAndRepeatable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = VaultFactory(context)
        val owner = "test-${UUID.randomUUID()}"
        var sourceRef: String? = null
        try {
            val database = factory.open(owner)
            try {
                val ledger = LedgerRepository(owner, database)
                ledger.initialiseOwner("Tester")
                val bank = ledger.addAccount("bank", "Bank", "INR", Money(0, "INR"), LocalDate.of(2026, 1, 1))
                val importer = ImportRepository(owner, database, context)
                val csv = "Date,Description,Debit,Credit\n2026-09-30,Cafe,12.50,\n03/04/2026,Salary,,300.00\n".toByteArray()
                val jobId = importer.stageCsv(bank, "sample.csv", csv)
                val rows = importer.candidates(jobId).first()
                assertEquals(2, rows.size)
                assertTrue(runCatching { importer.commit(jobId) }.isFailure)
                assertTrue(ledger.recentTransactions.first().isEmpty())
                importer.decide(rows.single { it.decision == "unreviewed" }.id, "accepted")
                assertEquals(2, importer.commit(jobId))
                assertEquals(2, ledger.recentTransactions.first().size)
                assertEquals(jobId, importer.stageCsv(bank, "sample.csv", csv))
                assertEquals(2, importer.commit(jobId))
                sourceRef = database.importDao().document(owner, database.importDao().job(owner, jobId)!!.documentId)!!.encryptedFileRef
                assertEquals(String(csv), String(DocumentStore(context).read(owner, sourceRef!!)))
                assertEquals(2, importer.undo(jobId))
                assertTrue(ledger.recentTransactions.first().isEmpty())
                assertTrue(runCatching { importer.undo(jobId) }.isFailure)
            } finally { database.close() }
        } finally {
            sourceRef?.let { DocumentStore(context).delete(owner, it) }
            factory.delete(owner)
        }
    }

    @Test fun repeatedCsvRowsRequireIndividualDuplicateReview() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = VaultFactory(context)
        val owner = "test-${UUID.randomUUID()}"
        var sourceRef: String? = null
        try {
            val database = factory.open(owner)
            try {
                val ledger = LedgerRepository(owner, database)
                ledger.initialiseOwner("Tester")
                val bank = ledger.addAccount("bank", "Bank", "INR", Money(0, "INR"), LocalDate.of(2026, 1, 1))
                val importer = ImportRepository(owner, database, context)
                val job = importer.stageCsv(bank, "repeated.csv",
                    "Date,Description,Debit,Credit\n2026-10-01,Cafe,12.50,\n2026-10-01,Cafe,12.50,\n".toByteArray())
                val rows = importer.candidates(job).first()
                assertEquals(listOf("accepted", "duplicate"), rows.map { it.decision })
                assertEquals(1, importer.commit(job))
                assertEquals(1, ledger.recentTransactions.first().size)
                sourceRef = database.importDao().document(owner, database.importDao().job(owner, job)!!.documentId)!!.encryptedFileRef
            } finally { database.close() }
        } finally {
            sourceRef?.let { DocumentStore(context).delete(owner, it) }
            factory.delete(owner)
        }
    }

    @Test fun uncertainImportRowCanBeCorrectedBeforePosting() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = VaultFactory(context)
        val owner = "test-${UUID.randomUUID()}"
        var sourceRef: String? = null
        try {
            val database = factory.open(owner)
            try {
                val ledger = LedgerRepository(owner, database)
                ledger.initialiseOwner("Tester")
                val bank = ledger.addAccount("bank", "Bank", "INR", Money(0, "INR"), LocalDate.of(2026, 1, 1))
                val importer = ImportRepository(owner, database, context)
                val job = importer.stageCsv(bank, "uncertain.csv",
                    "Date,Description,Amount,Type\n03/04/2026,Cafe,12.50,unknown\n".toByteArray())
                val candidate = importer.candidates(job).first().single()
                assertEquals("unreviewed", candidate.decision)
                assertTrue(runCatching { importer.editCandidate(candidate.id, "invalid", "Cafe", "-12.50") }.isFailure)
                importer.editCandidate(candidate.id, "2026-04-03", "Cafe", "-12.50")
                assertEquals("unreviewed", importer.candidates(job).first().single().decision)
                importer.decide(candidate.id, "accepted")
                assertEquals(1, importer.commit(job))
                assertEquals(-1250L, ledger.recentTransactions.first().single().amountMinor)
                sourceRef = database.importDao().document(owner, database.importDao().job(owner, job)!!.documentId)!!.encryptedFileRef
            } finally { database.close() }
        } finally {
            sourceRef?.let { DocumentStore(context).delete(owner, it) }
            factory.delete(owner)
        }
    }
}
