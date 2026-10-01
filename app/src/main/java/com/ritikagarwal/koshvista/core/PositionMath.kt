package com.ritikagarwal.koshvista.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

data class Position(val quantity: BigDecimal, val costBasisMinor: Long)

object PositionMath {
    fun grossMinor(quantity: BigDecimal, unitPrice: BigDecimal, currencyCode: String): Long {
        require(quantity > BigDecimal.ZERO && unitPrice > BigDecimal.ZERO)
        val scale = Currency.getInstance(currencyCode).defaultFractionDigits
        require(scale >= 0)
        return quantity.multiply(unitPrice).movePointRight(scale)
            .setScale(0, RoundingMode.HALF_EVEN).longValueExact().also { require(it > 0) }
    }

    fun soldCostBasis(position: Position, quantity: BigDecimal): Long {
        require(quantity > BigDecimal.ZERO && quantity <= position.quantity) { "Not enough units to sell" }
        return if (quantity.compareTo(position.quantity) == 0) position.costBasisMinor
        else BigDecimal.valueOf(position.costBasisMinor).multiply(quantity)
            .divide(position.quantity, 0, RoundingMode.HALF_EVEN).longValueExact()
    }
}
