package com.ritikagarwal.koshvista.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "budgets",
    primaryKeys = ["ownerId", "id"],
    foreignKeys = [
        ForeignKey(OwnerEntity::class, ["ownerId"], ["ownerId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(CategoryEntity::class, ["ownerId", "id"], ["ownerId", "categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("ownerId"), Index(value = ["ownerId", "categoryId"], unique = true)],
)
data class BudgetEntity(
    val ownerId: String,
    val id: String,
    val categoryId: String,
    val limitMinor: Long,
    val currencyCode: String,
    val status: String = "active",
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
