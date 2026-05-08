package com.scrollshield.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrollshield.data.model.MonthlyAggregate
import com.scrollshield.reports.DashboardData

@Composable
fun ReportScreen(
    viewModel: ReportViewModel = hiltViewModel()
) {
    val dashboard by viewModel.dashboard.collectAsState()
    val weekly by viewModel.weekly.collectAsState()
    val child by viewModel.child.collectAsState()
    val monthlies by viewModel.monthlies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Reports",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            item { DashboardCard(dashboard) }
            item {
                WeeklyReportCard(
                    report = weekly,
                    onExportJson = { viewModel.exportWeeklyJson() },
                    onExportCsv = { viewModel.exportWeeklyCsv() }
                )
            }
            item {
                ChildActivityReportSection(
                    report = child,
                    onGenerate = { viewModel.loadChildReport() },
                    onExportJson = { viewModel.exportChildJson() },
                    onExportCsv = { viewModel.exportChildCsv() }
                )
            }
            item {
                Text(
                    text = "Monthly Aggregates",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (monthlies.isEmpty()) {
                item {
                    Text(
                        text = "No monthly aggregates yet — they appear after the first month rolls over.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                items(monthlies, key = { it.id }) { aggregate ->
                    MonthlyAggregateRow(
                        aggregate = aggregate,
                        onExportJson = { viewModel.exportMonthlyJson(aggregate.yearMonth) }
                    )
                }
            }
        }
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun DashboardCard(dashboard: DashboardData?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Dashboard (last ${dashboard?.rangeDays ?: 90} days)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            if (dashboard == null) {
                Text("No data yet", style = MaterialTheme.typography.bodyMedium)
                return@Card
            }
            Text(
                text = "Sessions: ${dashboard.totalSessions} · Time: ${"%.1f".format(dashboard.totalDurationMinutes)} min",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Ads detected: ${dashboard.adsDetected} · Ads skipped: ${dashboard.adsSkipped}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MonthlyAggregateRow(
    aggregate: MonthlyAggregate,
    onExportJson: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = aggregate.yearMonth,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Sessions: ${aggregate.totalSessions} · Duration: ${"%.1f".format(aggregate.totalDurationMinutes)} min",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Ads detected: ${aggregate.totalAdsDetected} · skipped: ${aggregate.totalAdsSkipped}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = onExportJson) { Text("Export JSON") }
            }
        }
    }
}
