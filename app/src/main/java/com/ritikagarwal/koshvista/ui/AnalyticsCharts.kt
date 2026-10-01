package com.ritikagarwal.koshvista.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ritikagarwal.koshvista.data.CategoryTotal
import com.ritikagarwal.koshvista.data.DailySpend
import java.time.LocalDate

@Composable
fun DailySpendingCard(spending: List<DailySpend>, onDay: (String) -> Unit) {
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()).toString() }
    val totals = days.map { date -> spending.find { it.localDate == date }?.totalMinor ?: 0L }
    val hasRefundDay = totals.any { it < 0L }
    val max = maxOf(totals.maxOfOrNull { kotlin.math.abs(it.toDouble()) } ?: 0.0, 1.0).toFloat()
    val expenseColor = MaterialTheme.colorScheme.error
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Daily net spending", style = MaterialTheme.typography.titleMedium)
            Text("Last 7 days · INR", style = MaterialTheme.typography.bodySmall)
            Canvas(Modifier.fillMaxWidth().height(112.dp)
                .pointerInput(days) { detectTapGestures { tap ->
                    val index = ((tap.x / size.width) * 7).toInt().coerceIn(0, 6)
                    onDay(days[index])
                } }
                .semantics { contentDescription = days.zip(totals).joinToString { (day, total) -> "$day: ${formatMoney(total, "INR")}" } }) {
                val slot = size.width / 7
                if (hasRefundDay) drawLine(Color.Gray.copy(alpha = 0.5f), Offset(0f, size.height * 0.5f),
                    Offset(size.width, size.height * 0.5f), 1.dp.toPx())
                totals.forEachIndexed { index, total ->
                    val availableHeight = if (hasRefundDay) size.height * 0.48f else size.height
                    val height = (kotlin.math.abs(total.toDouble()).toFloat() / max) * availableHeight
                    val baseline = if (hasRefundDay) size.height * 0.5f else size.height
                    drawRoundRect(expenseColor,
                        topLeft = Offset(index * slot + slot * 0.17f,
                            if (total < 0L) baseline else baseline - height),
                        size = Size(slot * 0.66f, height),
                        cornerRadius = CornerRadius(5.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEach { Text(it.takeLast(2), style = MaterialTheme.typography.bodySmall) }
            }
            Text("Tap a day to view its records", style = MaterialTheme.typography.bodySmall)
            if (hasRefundDay) Text("Bars below the middle line show days with net refunds.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun CategorySpendingCard(
    totals: List<CategoryTotal>, categoryNames: Map<String, String>,
    onCategory: (String?) -> Unit,
) {
    val color = MaterialTheme.colorScheme.primary
    val max = maxOf(totals.maxOfOrNull { it.totalMinor } ?: 0L, 1L).toFloat()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Spending by category", style = MaterialTheme.typography.titleMedium)
            Text("This month · INR", style = MaterialTheme.typography.bodySmall)
            if (totals.isEmpty()) Text("Categorised expenses will appear here.")
            totals.forEach { item ->
                Column(Modifier.fillMaxWidth().clickable { onCategory(item.categoryId) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.categoryId?.let { categoryNames[it] } ?: "Uncategorised")
                        Text(formatMoney(item.totalMinor, "INR"))
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).background(color.copy(alpha = 0.15f))) {
                        Box(Modifier.fillMaxWidth((item.totalMinor / max).coerceIn(0f, 1f)).height(6.dp).background(color))
                    }
                }
            }
            if (totals.isNotEmpty()) Text("Tap a category to view its records", style = MaterialTheme.typography.bodySmall)
        }
    }
}
