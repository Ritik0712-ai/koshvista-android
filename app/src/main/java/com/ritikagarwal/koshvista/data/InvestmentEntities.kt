package com.ritikagarwal.koshvista.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "instruments", primaryKeys = ["ownerId", "id"],
    foreignKeys = [
        ForeignKey(OwnerEntity::class, ["ownerId"], ["ownerId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AccountEntity::class, ["ownerId", "id"], ["ownerId", "assetAccountId"], onDelete = ForeignKey.RESTRICT),
    ], indices = [Index("ownerId"), Index(value = ["ownerId", "symbol"], unique = true),
        Index(value = ["ownerId", "assetAccountId"], unique = true)])
data class InstrumentEntity(
    val ownerId: String,
    val id: String,
    val name: String,
    val symbol: String,
    val assetAccountId: String,
    val currencyCode: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(tableName = "investment_trades", primaryKeys = ["ownerId", "id"],
    foreignKeys = [
        ForeignKey(InstrumentEntity::class, ["ownerId", "id"], ["ownerId", "instrumentId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AccountEntity::class, ["ownerId", "id"], ["ownerId", "brokerAccountId"], onDelete = ForeignKey.RESTRICT),
    ], indices = [Index(value = ["ownerId", "instrumentId", "tradeLocalDate"]),
        Index(value = ["ownerId", "brokerAccountId"])])
data class InvestmentTradeEntity(
    val ownerId: String,
    val id: String,
    val instrumentId: String,
    val brokerAccountId: String,
    val side: String,
    val tradeLocalDate: String,
    val quantityDecimal: String,
    val unitPriceDecimal: String,
    val feesMinor: Long,
    val grossMinor: Long,
    val costBasisMinor: Long,
    val realisedGainMinor: Long,
    val currencyCode: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
