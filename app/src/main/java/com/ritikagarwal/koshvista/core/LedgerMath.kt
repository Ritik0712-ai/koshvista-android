package com.ritikagarwal.koshvista.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class EntryKind { EXPENSE, INCOME, TRANSFER, REFUND, ADJUSTMENT, FEE, TRADE_CASH }

data class LedgerEntry(
    val accountId: String,
    val amount: Money,
    val kind: EntryKind,
    val date: LocalDate,
    val categoryId: String? = null,
)

data class CashFlow(val income: Money, val expense: Money, val net: Money)

object LedgerMath {
    fun balance(opening: Money, entries: Iterable<LedgerEntry>, through: LocalDate): Money =
        entries.filter { it.date <= through }.fold(opening) { total, entry -> total + entry.amount }

    fun transfer(fromAccount: String, toAccount: String, amount: Money, date: LocalDate): Pair<LedgerEntry, LedgerEntry> {
        require(fromAccount != toAccount) { "Transfer accounts must differ" }
        require(amount.minor > 0) { "Transfer amount must be positive" }
        return LedgerEntry(fromAccount, -amount, EntryKind.TRANSFER, date) to
            LedgerEntry(toAccount, amount, EntryKind.TRANSFER, date)
    }

    fun checkSplits(parent: Money, splits: List<Money>) {
        require(splits.isNotEmpty()) { "At least one split is required" }
        require(splits.all { it.currencyCode == parent.currencyCode }) { "Split currency differs" }
        require(splits.fold(0L) { a, b -> Math.addExact(a, b.minor) } == parent.minor) { "Splits do not sum to transaction" }
    }

    fun cashFlow(entries: Iterable<LedgerEntry>, currencyCode: String): CashFlow {
        val own = entries.filter { it.amount.currencyCode == currencyCode }
        val income = own.filter { it.kind == EntryKind.INCOME || it.kind == EntryKind.REFUND }
            .fold(Money(0, currencyCode)) { a, b -> a + b.amount }
        val expense = own.filter { it.kind == EntryKind.EXPENSE || it.kind == EntryKind.FEE }
            .fold(Money(0, currencyCode)) { a, b -> a + b.amount }
        return CashFlow(income, expense, income + expense)
    }

    /** A simple-interest estimate, never a posted ledger entry. */
    fun simpleMaturity(principal: Money, annualRate: BigDecimal, start: LocalDate, maturity: LocalDate): Money {
        require(maturity > start)
        require(annualRate >= BigDecimal.ZERO)
        val days = ChronoUnit.DAYS.between(start, maturity)
        val interest = BigDecimal.valueOf(principal.minor).multiply(annualRate)
            .multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(365), 0, RoundingMode.HALF_EVEN)
        return principal + Money(interest.longValueExact(), principal.currencyCode)
    }
}
