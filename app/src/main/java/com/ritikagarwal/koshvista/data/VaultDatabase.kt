package com.ritikagarwal.koshvista.data

import androidx.room.Database
import androidx.room.AutoMigration
import androidx.room.RoomDatabase

@Database(
    entities = [
        OwnerEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionSplitEntity::class,
        SourceDocumentEntity::class,
        ImportJobEntity::class,
        ImportCandidateEntity::class,
        FixedDepositEntity::class,
        BudgetEntity::class,
        InstrumentEntity::class,
        InvestmentTradeEntity::class,
        BalanceObservationEntity::class,
    ],
    version = 6,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6)],
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun importDao(): ImportDao
}
