package com.ritikagarwal.koshvista.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class AccountBalance(
    val ownerId: String,
    val id: String,
    val name: String,
    val type: String,
    val currencyCode: String,
    val balanceMinor: Long,
)

data class CategoryTotal(val categoryId: String?, val totalMinor: Long)
data class DailySpend(val localDate: String, val totalMinor: Long)

@Dao
interface VaultDao {
    @Insert suspend fun insertOwner(owner: OwnerEntity)
    @Query("SELECT * FROM owners WHERE ownerId = :ownerId") suspend fun owner(ownerId: String): OwnerEntity?

    @Insert suspend fun insertAccount(account: AccountEntity)
    @Update suspend fun updateAccount(account: AccountEntity)
    @Query("SELECT * FROM accounts WHERE ownerId = :ownerId AND id = :id") suspend fun account(ownerId: String, id: String): AccountEntity?
    @Query("SELECT * FROM accounts WHERE ownerId = :ownerId AND status = 'active' ORDER BY createdAtMs")
    fun accounts(ownerId: String): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts WHERE ownerId = :ownerId ORDER BY createdAtMs")
    fun allAccounts(ownerId: String): Flow<List<AccountEntity>>
    @Query("""
        SELECT a.ownerId, a.id, a.name, a.type, a.currencyCode,
               a.openingBalanceMinor + COALESCE(SUM(t.amountMinor), 0) AS balanceMinor
        FROM accounts a LEFT JOIN transactions t
          ON t.ownerId = a.ownerId AND t.accountId = a.id AND t.status = 'posted'
        WHERE a.ownerId = :ownerId AND a.status = 'active'
        GROUP BY a.ownerId, a.id
        ORDER BY a.createdAtMs
    """)
    fun balances(ownerId: String): Flow<List<AccountBalance>>
    @Query("SELECT openingBalanceMinor + COALESCE((SELECT SUM(amountMinor) FROM transactions WHERE ownerId = :ownerId AND accountId = :accountId AND status = 'posted'), 0) FROM accounts WHERE ownerId = :ownerId AND id = :accountId")
    suspend fun accountBalanceOnce(ownerId: String, accountId: String): Long?
    @Query("SELECT openingBalanceMinor + COALESCE((SELECT SUM(amountMinor) FROM transactions WHERE ownerId = :ownerId AND accountId = :accountId AND status = 'posted' AND localDate <= :day), 0) FROM accounts WHERE ownerId = :ownerId AND id = :accountId")
    suspend fun accountBalanceThrough(ownerId: String, accountId: String, day: String): Long?

    @Insert suspend fun insertCategory(category: CategoryEntity)
    @Query("SELECT * FROM categories WHERE ownerId = :ownerId AND isArchived = 0 ORDER BY name")
    fun categories(ownerId: String): Flow<List<CategoryEntity>>
    @Query("SELECT COUNT(*) FROM categories WHERE ownerId = :ownerId") suspend fun categoryCount(ownerId: String): Int

    @Insert suspend fun insertTransaction(transaction: TransactionEntity)
    @Update suspend fun updateTransaction(transaction: TransactionEntity)
    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND id = :id") suspend fun transaction(ownerId: String, id: String): TransactionEntity?
    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND status = 'posted' ORDER BY localDate DESC, createdAtMs DESC LIMIT :limit OFFSET :offset")
    fun transactions(ownerId: String, limit: Int, offset: Int): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND accountId = :accountId AND status = 'posted' ORDER BY localDate DESC, createdAtMs DESC LIMIT :limit")
    fun accountTransactions(ownerId: String, accountId: String, limit: Int): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND status = 'posted' AND localDate = :day ORDER BY createdAtMs DESC")
    fun transactionsForDay(ownerId: String, day: String): Flow<List<TransactionEntity>>
    @Query("""
        SELECT DISTINCT t.* FROM transactions t
        LEFT JOIN transaction_splits s ON s.ownerId = t.ownerId AND s.transactionId = t.id
        WHERE t.ownerId = :ownerId AND t.status = 'posted' AND t.kind = 'expense'
          AND t.localDate BETWEEN :from AND :through
          AND ((:categoryId IS NULL AND COALESCE(s.categoryId, t.categoryId) IS NULL)
               OR COALESCE(s.categoryId, t.categoryId) = :categoryId)
        ORDER BY t.localDate DESC, t.createdAtMs DESC
    """)
    fun transactionsForCategory(ownerId: String, categoryId: String?, from: String, through: String): Flow<List<TransactionEntity>>
    @Query("""
        SELECT DISTINCT t.* FROM transactions t
        LEFT JOIN transaction_splits s ON s.ownerId = t.ownerId AND s.transactionId = t.id
        WHERE t.ownerId = :ownerId AND t.status = 'posted'
          AND (:accountId IS NULL OR t.accountId = :accountId)
          AND (:day IS NULL OR t.localDate = :day)
          AND (:categoryFilter = 0 OR (t.kind = 'expense' AND t.localDate BETWEEN :from AND :through
            AND ((:categoryId IS NULL AND COALESCE(s.categoryId, t.categoryId) IS NULL)
              OR COALESCE(s.categoryId, t.categoryId) = :categoryId)))
          AND (:search IS NULL OR instr(lower(t.description), lower(:search)) > 0)
        ORDER BY t.localDate DESC, t.createdAtMs DESC
        LIMIT :limit OFFSET :offset
    """)
    fun searchTransactions(ownerId: String, accountId: String?, day: String?, categoryFilter: Boolean,
        categoryId: String?, from: String, through: String, search: String?, limit: Int, offset: Int): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId AND transferGroupId = :groupId")
    suspend fun transferEntries(ownerId: String, groupId: String): List<TransactionEntity>
    @Query("SELECT COUNT(*) FROM transactions WHERE ownerId = :ownerId AND accountId = :accountId AND sourceFingerprint = :fingerprint AND status = 'posted'")
    suspend fun fingerprintCount(ownerId: String, accountId: String, fingerprint: String): Int
    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE ownerId = :ownerId AND kind = :kind AND status = 'posted' AND localDate BETWEEN :from AND :through")
    fun totalForKind(ownerId: String, kind: String, from: String, through: String): Flow<Long>
    @Query("""
        SELECT COALESCE(s.categoryId, t.categoryId) AS categoryId,
               -SUM(COALESCE(s.amountMinor, t.amountMinor)) AS totalMinor
        FROM transactions t LEFT JOIN transaction_splits s
          ON s.ownerId = t.ownerId AND s.transactionId = t.id
        WHERE t.ownerId = :ownerId AND t.kind = 'expense' AND t.status = 'posted'
          AND t.localDate BETWEEN :from AND :through
        GROUP BY COALESCE(s.categoryId, t.categoryId)
        ORDER BY totalMinor DESC
    """)
    fun categorySpending(ownerId: String, from: String, through: String): Flow<List<CategoryTotal>>
    @Query("""
        SELECT localDate, -SUM(amountMinor) AS totalMinor
        FROM transactions
        WHERE ownerId = :ownerId AND kind = 'expense' AND status = 'posted'
          AND localDate BETWEEN :from AND :through
        GROUP BY localDate ORDER BY localDate
    """)
    fun dailySpending(ownerId: String, from: String, through: String): Flow<List<DailySpend>>

    @Insert suspend fun insertSplit(split: TransactionSplitEntity)
    @Query("SELECT * FROM transaction_splits WHERE ownerId = :ownerId AND transactionId = :transactionId ORDER BY position")
    suspend fun splits(ownerId: String, transactionId: String): List<TransactionSplitEntity>

    @Insert suspend fun insertFixedDeposit(contract: FixedDepositEntity)
    @Query("SELECT * FROM fixed_deposits WHERE ownerId = :ownerId AND status = 'active' ORDER BY maturityLocalDate")
    fun fixedDeposits(ownerId: String): Flow<List<FixedDepositEntity>>
    @Query("SELECT COUNT(*) FROM fixed_deposits WHERE ownerId = :ownerId AND assetAccountId = :accountId AND status = 'active'")
    suspend fun activeDepositCountForAccount(ownerId: String, accountId: String): Int

    @Insert suspend fun insertBudget(budget: BudgetEntity)
    @Update suspend fun updateBudget(budget: BudgetEntity)
    @Query("SELECT * FROM budgets WHERE ownerId = :ownerId AND categoryId = :categoryId")
    suspend fun budgetForCategory(ownerId: String, categoryId: String): BudgetEntity?
    @Query("SELECT * FROM budgets WHERE ownerId = :ownerId AND status = 'active' ORDER BY createdAtMs")
    fun budgets(ownerId: String): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM categories WHERE ownerId = :ownerId AND id = :id")
    suspend fun category(ownerId: String, id: String): CategoryEntity?

    @Insert suspend fun insertInstrument(instrument: InstrumentEntity)
    @Query("SELECT * FROM instruments WHERE ownerId = :ownerId ORDER BY name")
    fun instruments(ownerId: String): Flow<List<InstrumentEntity>>
    @Query("SELECT * FROM instruments WHERE ownerId = :ownerId AND id = :id")
    suspend fun instrument(ownerId: String, id: String): InstrumentEntity?
    @Query("SELECT * FROM instruments WHERE ownerId = :ownerId AND symbol = :symbol")
    suspend fun instrumentBySymbol(ownerId: String, symbol: String): InstrumentEntity?
    @Insert suspend fun insertTrade(trade: InvestmentTradeEntity)
    @Query("SELECT * FROM investment_trades WHERE ownerId = :ownerId ORDER BY tradeLocalDate, createdAtMs")
    fun investmentTrades(ownerId: String): Flow<List<InvestmentTradeEntity>>
    @Query("SELECT * FROM investment_trades WHERE ownerId = :ownerId AND instrumentId = :instrumentId ORDER BY tradeLocalDate, createdAtMs")
    suspend fun instrumentTrades(ownerId: String, instrumentId: String): List<InvestmentTradeEntity>
    @Query("SELECT COUNT(*) FROM instruments WHERE ownerId = :ownerId AND assetAccountId = :accountId")
    suspend fun instrumentCountForAccount(ownerId: String, accountId: String): Int

    @Insert suspend fun insertObservation(observation: BalanceObservationEntity)
    @Update suspend fun updateObservation(observation: BalanceObservationEntity)
    @Query("SELECT * FROM balance_observations WHERE ownerId = :ownerId AND id = :id")
    suspend fun observation(ownerId: String, id: String): BalanceObservationEntity?
    @Query("SELECT * FROM balance_observations WHERE ownerId = :ownerId AND accountId = :accountId ORDER BY observedLocalDate DESC, createdAtMs DESC")
    fun observations(ownerId: String, accountId: String): Flow<List<BalanceObservationEntity>>
}
