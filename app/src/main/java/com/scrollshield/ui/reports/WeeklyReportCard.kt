package com.scrollshield.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrollshield.reports.WeeklyReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WeeklyReportCard(
    report: WeeklyReport?,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Weekly Report",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            if (report == null) {
                Text(
                    text = "No weekly report available yet",
                    style = MaterialTheme.typography.bodyMedium
                )
                return@Card
            }
            val df = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
            Text(
                text = "Period: ${df.format(Date(report.periodStartMs))} → ${df.format(Date(report.periodEndMs))}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text(text = "Total time by profile:", style = MaterialTheme.typography.bodyMedium)
            if (report.totalTimeByProfileMinutes.isEmpty()) {
                Text("  (no sessions)", style = MaterialTheme.typography.bodySmall)
            } else {
                report.totalTimeByProfileMinutes.toSortedMap().forEach { (profileId, mins) ->
                    Text(
                        text = "  $profileId: ${"%.1f".format(mins)} min",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ads detected: ${report.adsDetected} · Ads skipped: ${report.adsSkipped}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(text = "Top 5 brands:", style = MaterialTheme.typography.bodyMedium)
            if (report.topFiveBrands.isEmpty()) {
                Text("  (none)", style = MaterialTheme.typography.bodySmall)
            } else {
                report.topFiveBrands.forEach { (brand, count) ->
                    Text(
                        text = "  $brand × $count",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(text = "7-day ad-to-content ratio:", style = MaterialTheme.typography.bodyMedium)
            RatioTrendBar(report.adToContentRatioTrend)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Avg satisfaction: ${report.averageSatisfaction?.let { "%.2f".format(it) } ?: "—"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(text = "Classification tier breakdown:", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "  Tier 0 (text): ${report.tierBreakdown.tier0}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "  Tier 1 (visual): ${report.tierBreakdown.tier1}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "  Tier 2 (deep text): ${report.tierBreakdown.tier2}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExportJson) { Text("Export JSON") }
                OutlinedButton(onClick = onExportCsv) { Text("Export CSV") }
            }
        }
    }
}

@Composable
private fun RatioTrendBar(values: List<Float>) {
    val maxV = (values.maxOrNull() ?: 0f).coerceAtLeast(0.0001f)
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (v in values) {
            val frac = (v / maxV).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((40.dp.value * frac).coerceAtLeast(2f).dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
        if (values.isEmpty()) {
            Spacer(Modifier.width(0.dp))
        }
    }
}

