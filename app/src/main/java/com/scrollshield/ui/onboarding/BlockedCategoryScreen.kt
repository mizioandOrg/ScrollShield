package com.scrollshield.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BlockedCategoryScreen(
    gamblingBlocked: Boolean,
    dietCultureBlocked: Boolean,
    cryptoBlocked: Boolean,
    politicalOutrageBlocked: Boolean,
    clickbaitBlocked: Boolean,
    explicitAdsBlocked: Boolean,
    onToggle: (String, Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Block Content Categories",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Content in these categories will be automatically skipped. All are off by default.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        BlockedCategoryRow(
            label = "Gambling",
            description = "Betting, casino, and gambling-related content",
            checked = gamblingBlocked,
            onCheckedChange = { onToggle("gambling", it) }
        )
        BlockedCategoryRow(
            label = "Diet Culture",
            description = "Extreme dieting, body shaming, and unhealthy weight loss content",
            checked = dietCultureBlocked,
            onCheckedChange = { onToggle("dietCulture", it) }
        )
        BlockedCategoryRow(
            label = "Cryptocurrency",
            description = "Crypto promotion, NFTs, and speculative investment content",
            checked = cryptoBlocked,
            onCheckedChange = { onToggle("crypto", it) }
        )
        BlockedCategoryRow(
            label = "Political Outrage",
            description = "Deliberately inflammatory political content designed to provoke anger",
            checked = politicalOutrageBlocked,
            onCheckedChange = { onToggle("politicalOutrage", it) }
        )
        BlockedCategoryRow(
            label = "Clickbait",
            description = "Misleading thumbnails and sensationalized headlines",
            checked = clickbaitBlocked,
            onCheckedChange = { onToggle("clickbait", it) }
        )
        BlockedCategoryRow(
            label = "Explicit Ads",
            description = "Sexually suggestive or explicit advertising content",
            checked = explicitAdsBlocked,
            onCheckedChange = { onToggle("explicitAds", it) }
        )
    }
}

@Composable
private fun BlockedCategoryRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
