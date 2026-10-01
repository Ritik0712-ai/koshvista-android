package com.ritikagarwal.koshvista.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "owners")
data class OwnerEntity(
    @PrimaryKey val ownerId: String,
    val displayName: String?,
    val createdAtMs: Long,
)

@Entity(
    tableName = "accounts",
    primaryKeys = ["ownerId", "id"],
    foreignKeys = [ForeignKey(OwnerEntity::class, ["ownerId"], ["ownerId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("ownerId"), Index(value = ["ownerId", "type", "status"])],
)
data class AccountEntity(
    val ownerId: String,
    val id: String,
    val type: String,
    val name: String,
    val institutionName: String?,
    val currencyCode: String,
    val openingBalanceMinor: Long,
    val openingLocalDate: String,
    val status: String = "active",
    val isDefaultCash: Boolean = false,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "categories",
    primaryKeys = ["ownerId", "id"],
    foreignKeys = [ForeignKey(OwnerEntity::class, ["ownerId"], ["ownerId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("ownerId"), Index(value = ["ownerId", "kind", "name"], unique = true)],
)
data class CategoryEntity(
    val ownerId: String,
    val id: String,
    val name: String,
    val kind: String,
    val isSystem: Boolean,
    val isArchived: Boolean = false,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "transactions",
    primaryKeys = ["ownerId", "id"],
    foreignKeys = [
        ForeignKey(AccountEntity::class, ["ownerId", "id"], ["ownerId", "accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(CategoryEntity::class, ["ownerId", "id"], ["ownerId", "categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["ownerId", "accountId", "localDate"]),
        Index(value = ["ownerId", "categoryId", "localDate"]),
        Index(value = ["ownerId", "transferGroupId"]),
        Index(value = ["ownerId", "sourceFingerprint"]),
    ],
)
data class TransactionEntity(
    val ownerId: String,
    val id: String,
    val accountId: String,
    val localDate: String,
    val description: String,
    val categoryId: String?,
    val amountMinor: Long,
    val currencyCode: String,
    val kind: String,
    val status: String = "posted",
    val transferGroupId: String? = null,
    val sourceDocumentId: String? = null,
    val sourceFingerprint: String? = null,
    val tradeId: String? = null,
    val refundOfTransactionId: String? = null,
    val note: String? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "transaction_splits",
    primaryKeys = ["ownerId", "id"],
    foreignKeys = [
        ForeignKey(TransactionEntity::class, ["ownerId", "id"], ["ownerId", "transactionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(CategoryEntity::class, ["ownerId", "id"], ["ownerId", "categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["ownerId", "transactionId"]), Index(value = ["ownerId", "categoryId"])],
)
data class TransactionSplitEntity(
    val ownerId: String,
    val id: String,
    val transactionId: String,
    val categoryId: String,
    val amountMinor: Long,
    val memo: String?,
    val position: Int,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
