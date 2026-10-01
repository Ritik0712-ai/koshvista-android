package com.ritikagarwal.koshvista.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/** Exact money in the currency's minor unit. No floating point is used in ledger maths. */
data class Money(val minor: Long, val currencyCode: String) {
    init { Currency.getInstance(currencyCode) }

    operator fun plus(other: Money): Money {
        require(currencyCode == other.currencyCode) { "Currencies differ" }
        return copy(minor = Math.addExact(minor, other.minor))
    }

    operator fun unaryMinus() = copy(minor = Math.negateExact(minor))

    companion object {
        fun parse(value: String, currencyCode: String): Money {
            val scale = Currency.getInstance(currencyCode).defaultFractionDigits
            require(scale >= 0) { "Unsupported currency scale" }
            val amount = value.trim().replace(",", "").toBigDecimal()
            return Money(amount.movePointRight(scale).setScale(0, RoundingMode.UNNECESSARY).longValueExact(), currencyCode)
        }
    }
}
