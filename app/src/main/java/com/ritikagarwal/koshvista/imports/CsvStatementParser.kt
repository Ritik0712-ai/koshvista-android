package com.ritikagarwal.koshvista.imports

import com.ritikagarwal.koshvista.core.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

data class TransactionCandidate(
    val sourceRow: Int,
    val date: LocalDate?,
    val description: String,
    val amount: Money?,
    val reviewReasons: List<String>,
) {
    val ready: Boolean get() = date != null && amount != null && description.isNotBlank() && reviewReasons.isEmpty()
}

/** Parses a selected CSV export without guessing a missing debit/credit direction. */
object CsvStatementParser {
    private val datePatterns = listOf("uuuu-MM-dd", "dd/MM/uuuu", "dd-MM-uuuu", "dd MMM uuuu")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT) }

    fun parse(csv: String, currencyCode: String = "INR"): List<TransactionCandidate> {
        val rows = readRows(csv.removePrefix("\uFEFF"))
        require(rows.isNotEmpty()) { "The CSV is empty" }
        val header = rows.first().map { it.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z]"), "") }
        fun column(vararg names: String) = header.indexOfFirst { it in names }.takeIf { it >= 0 }
        val dateColumn = column("date", "transactiondate", "valuedate", "posteddate")
            ?: error("The CSV needs a date column")
        val descriptionColumn = column("description", "narration", "details", "particulars", "merchant")
            ?: error("The CSV needs a description column")
        val debitColumn = column("debit", "withdrawal", "withdrawals", "paidout")
        val creditColumn = column("credit", "deposit", "deposits", "paidin")
        val amountColumn = column("amount", "transactionamount")
        val typeColumn = column("type", "transactiontype", "drcr")
        require((debitColumn != null && creditColumn != null) || (amountColumn != null && typeColumn != null)) {
            "The CSV needs debit and credit columns, or amount and type columns"
        }
        return rows.drop(1).filter { row -> row.any(String::isNotBlank) }.mapIndexed { index, row ->
            val reasons = mutableListOf<String>()
            fun value(column: Int?) = column?.let { row.getOrNull(it)?.trim() }.orEmpty()
            val rawDate = value(dateColumn)
            val date = datePatterns.firstNotNullOfOrNull { formatter ->
                runCatching { LocalDate.parse(rawDate, formatter) }.getOrNull()
            }
            if (date == null) reasons += "Check the date"
            if (Regex("^\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}$").matches(rawDate)) {
                val parts = rawDate.split('/', '-')
                if (parts[0].toInt() <= 12 && parts[1].toInt() <= 12) reasons += "Confirm the day and month"
            }
            val description = value(descriptionColumn)
            if (description.isBlank()) reasons += "Add a description"
            val amount = try {
                val debit = value(debitColumn)
                val credit = value(creditColumn)
                when {
                    debitColumn != null && creditColumn != null && debit.isNotBlank() && credit.isBlank() ->
                        -Money.parse(debit.replace("₹", ""), currencyCode)
                    debitColumn != null && creditColumn != null && credit.isNotBlank() && debit.isBlank() ->
                        Money.parse(credit.replace("₹", ""), currencyCode)
                    amountColumn != null && typeColumn != null -> {
                        val parsed = Money.parse(value(amountColumn).replace("₹", ""), currencyCode)
                        when (value(typeColumn).lowercase(Locale.ROOT)) {
                            "dr", "debit", "withdrawal", "expense" -> -Money(kotlin.math.abs(parsed.minor), currencyCode)
                            "cr", "credit", "deposit", "income" -> Money(kotlin.math.abs(parsed.minor), currencyCode)
                            else -> { reasons += "Confirm debit or credit"; null }
                        }
                    }
                    else -> { reasons += "Confirm debit or credit"; null }
                }
            } catch (_: Exception) { reasons += "Check the amount"; null }
            if (amount?.minor == 0L) reasons += "Check the zero amount"
            TransactionCandidate(index + 2, date, description, amount, reasons.distinct())
        }
    }

    fun readRows(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < input.length) {
            when (val character = input[index]) {
                '"' -> {
                    if (quoted && input.getOrNull(index + 1) == '"') { field.append('"'); index++ }
                    else if (quoted || field.isEmpty()) quoted = !quoted
                    else error("Malformed CSV quote")
                }
                ',' -> if (quoted) field.append(character) else { row += field.toString(); field.clear() }
                '\n' -> if (quoted) field.append(character) else {
                    row += field.toString().trimEnd('\r'); field.clear()
                    rows += row.toList(); row.clear()
                }
                else -> field.append(character)
            }
            index++
        }
        require(!quoted) { "The CSV has an unclosed quote" }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row.toList() }
        return rows
    }
}
