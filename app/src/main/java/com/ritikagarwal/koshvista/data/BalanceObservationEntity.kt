package com.ritikagarwal.koshvista.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "balance_observations", primaryKeys = ["ownerId", "id"],
    foreignKeys = [ForeignKey(AccountEntity::class, ["ownerId", "id"], ["ownerId", "accountId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["ownerId", "accountId", "observedLocalDate"])])
data class BalanceObservationEntity(
    val ownerId: String,
    val id: String,
    val accountId: String,
    val observedLocalDate: String,
    val observedBalanceMinor: Long,
    val computedBalanceMinor: Long,
    val differenceMinor: Long,
    val currencyCode: String,
    val status: String,
    val resolvedTransactionId: String? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
