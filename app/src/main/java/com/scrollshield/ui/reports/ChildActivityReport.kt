package com.scrollshield.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrollshield.reports.ChildActivityReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChildActivityReportSection(
    report: ChildActivityReport?,
    onGenerate: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Child Activity Report",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            if (report == null) {
                Text(
                    text = "Generate an on-demand summary of child sessions over the past 7 days.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onGenerate) { Text("Generate Child Report") }
                return@Card
            }
            val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            Text(
                text = "Period: ${df.format(Date(report.periodStartMs))} → ${df.format(Date(report.periodEndMs))}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text(text = "Time per app:", style = MaterialTheme.typography.bodyMedium)
            if (report.timePerAppMinutes.isEmpty()) {
                Text("  (no sessions)", style = MaterialTheme.typography.bodySmall)
            } else {
                report.timePerAppMinutes.toSortedMap().forEach { (app, mins) ->
                    Text(
                        text = "  $app: ${"%.1f".format(mins)} min",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ads blocked: ${report.adsBlocked}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(text = "Categories encountered:", style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (report.categoriesEncountered.isEmpty()) {
                    Text("  (none)", style = MaterialTheme.typography.bodySmall)
                } else {
                    report.categoriesEncountered.forEach { c ->
                        AssistChip(
                            onClick = {},
                            label = { Text(c) },
                            colors = AssistChipDefaults.assistChipColors()
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(text = "Budget compliance:", style = MaterialTheme.typography.bodyMedium)
            if (report.budgetCompliance.isEmpty()) {
                Text("  (no budgets configured)", style = MaterialTheme.typography.bodySmall)
            } else {
                report.budgetCompliance.toSortedMap().forEach { (app, row) ->
                    val status = if (row.withinBudget) "OK" else "OVER"
                    Text(
                        text = "  $app: ${"%.1f".format(row.actualMinutes)}/${row.budgetMinutes} min — $status",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExportJson) { Text("Export JSON") }
                OutlinedButton(onClick = onExportCsv) { Text("Export CSV") }
            }
        }
    }
}
