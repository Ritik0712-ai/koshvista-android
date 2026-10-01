package com.ritikagarwal.koshvista.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvStatementParserTest {
    @Test fun parsesQuotedNarrativeAndDebitCredit() {
        val rows = CsvStatementParser.parse("Date,Description,Debit,Credit\n2026-09-30,\"Cafe, Mumbai\",12.50,\n2026-09-30,Salary,,300.00\n")
        assertEquals(2, rows.size)
        assertEquals("Cafe, Mumbai", rows[0].description)
        assertEquals(-1250L, rows[0].amount?.minor)
        assertEquals(30000L, rows[1].amount?.minor)
        assertTrue(rows.all { it.ready })
    }

    @Test fun ambiguousDateAndDirectionNeedReview() {
        val rows = CsvStatementParser.parse("Date,Description,Amount,Type\n03/04/2026,Transfer,100.00,unknown\n")
        assertFalse(rows.single().ready)
        assertTrue(rows.single().reviewReasons.any { it.contains("day and month") })
        assertTrue(rows.single().reviewReasons.any { it.contains("debit or credit") })
    }

    @Test fun rejectsUnclosedQuote() {
        val failure = runCatching { CsvStatementParser.readRows("Date,Description\n2026-09-30,\"broken") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
