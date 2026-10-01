package com.ritikagarwal.koshvista.core

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionMathTest {
    @Test fun decimalQuantityAndFullDisposalUseExactMinorUnits() {
        assertEquals(1235L, PositionMath.grossMinor(BigDecimal("0.5"), BigDecimal("24.70"), "INR"))
        val held = Position(BigDecimal("0.75"), 1001L)
        assertEquals(334L, PositionMath.soldCostBasis(held, BigDecimal("0.25")))
        assertEquals(1001L, PositionMath.soldCostBasis(held, BigDecimal("0.75")))
        assertTrue(runCatching { PositionMath.soldCostBasis(held, BigDecimal("1")) }.isFailure)
    }
}
