package com.scrollshield.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrollshield.data.model.TopicCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterestSelectionScreen(
    selectedTopics: Set<TopicCategory>,
    onTopicToggle: (TopicCategory) -> Unit,
    selectionCount: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Select Your Interests",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Choose 3-8 topics that interest you. This helps ScrollShield personalize your feed scoring.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "$selectionCount/8 selected",
            style = MaterialTheme.typography.labelLarge,
            color = if (selectionCount < 3) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(TopicCategory.entries.toList()) { topic ->
                val isSelected = topic in selectedTopics
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected || selectionCount < 8) {
                            onTopicToggle(topic)
                        }
                    },
                    label = { Text(topic.label) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
