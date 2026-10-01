package com.ritikagarwal.koshvista.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        OwnerEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionSplitEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}
