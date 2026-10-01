package com.ritikagarwal.koshvista.core

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LedgerMathTest {
    private val date = LocalDate.parse("2026-09-30")

    @Test fun transferChangesBalancesWithoutChangingCashFlow() {
        val (bank, cash) = LedgerMath.transfer("bank", "cash", Money(2_500, "INR"), date)
        assertEquals(Money(7_500, "INR"), LedgerMath.balance(Money(10_000, "INR"), listOf(bank), date))
        assertEquals(Money(2_500, "INR"), LedgerMath.balance(Money(0, "INR"), listOf(cash), date))
        assertEquals(Money(0, "INR"), LedgerMath.cashFlow(listOf(bank, cash), "INR").net)
    }

    @Test fun splitMustReconcileExactly() {
        LedgerMath.checkSplits(Money(-101, "INR"), listOf(Money(-50, "INR"), Money(-51, "INR")))
        assertThrows(IllegalArgumentException::class.java) {
            LedgerMath.checkSplits(Money(-101, "INR"), listOf(Money(-50, "INR"), Money(-50, "INR")))
        }
    }

    @Test fun moneyParsingRejectsFractionalPaise() {
        assertEquals(Money(123_450, "INR"), Money.parse("1,234.50", "INR"))
        assertThrows(ArithmeticException::class.java) { Money.parse("1.001", "INR") }
    }

    @Test fun estimatedInterestNeverChangesCashFlow() {
        val projected = LedgerMath.simpleMaturity(Money(100_000, "INR"), BigDecimal("0.10"), date, date.plusDays(365))
        assertEquals(Money(110_000, "INR"), projected)
        assertEquals(Money(0, "INR"), LedgerMath.cashFlow(emptyList(), "INR").income)
    }
}
