package com.scrollshield.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TimeBudgetScreen(
    selectedApps: Set<String>,
    installedTargetApps: List<TargetApp>,
    timeBudgets: Map<String, Int>,
    unlimitedApps: Set<String>,
    onBudgetChange: (String, Int) -> Unit,
    onUnlimitedToggle: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Set Time Budgets",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Set a daily time budget for each app. You'll receive nudges when approaching your limit.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val appsToShow = installedTargetApps.filter { it.packageName in selectedApps }

        appsToShow.forEach { app ->
            val isUnlimited = app.packageName in unlimitedApps
            val budget = timeBudgets[app.displayName] ?: 30

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = app.displayName,
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isUnlimited,
                        onCheckedChange = { onUnlimitedToggle(app.packageName) }
                    )
                    Text(
                        text = "Unlimited",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (!isUnlimited) {
                    Text(
                        text = "$budget minutes/day",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Slider(
                        value = budget.toFloat(),
                        onValueChange = { onBudgetChange(app.displayName, it.toInt()) },
                        valueRange = 15f..120f,
                        steps = ((120 - 15) / 5) - 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
