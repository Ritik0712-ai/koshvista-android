package com.ritikagarwal.koshvista.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "fixed_deposits",
    primaryKeys = ["ownerId", "id"],
    foreignKeys = [
        ForeignKey(OwnerEntity::class, ["ownerId"], ["ownerId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AccountEntity::class, ["ownerId", "id"], ["ownerId", "assetAccountId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("ownerId"), Index(value = ["ownerId", "assetAccountId"], unique = true)],
)
data class FixedDepositEntity(
    val ownerId: String,
    val id: String,
    val name: String,
    val institutionName: String,
    val assetAccountId: String,
    val principalMinor: Long,
    val currencyCode: String,
    val startLocalDate: String,
    val maturityLocalDate: String,
    /** Canonical fractional annual rate, for example 0.075 for 7.5%. */
    val annualRateDecimal: String,
    val status: String = "active",
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
