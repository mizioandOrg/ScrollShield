package com.scrollshield

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.scrollshield.data.preferences.UserPreferencesStore
import com.scrollshield.ui.onboarding.OnboardingScreen
import com.scrollshield.ui.reports.ReportScreen
import com.scrollshield.ui.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesStore: UserPreferencesStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val onboardingCompleted by preferencesStore.onboardingCompleted.collectAsState(initial = false)

                    if (onboardingCompleted) {
                        var selectedTab by remember { mutableStateOf(0) }
                        Scaffold(
                            bottomBar = {
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = selectedTab == 0,
                                        onClick = { selectedTab = 0 },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Settings"
                                            )
                                        },
                                        label = { Text("Settings") }
                                    )
                                    NavigationBarItem(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1 },
                                        icon = {
                                            // Icons.Default.Assessment isn't always part of the
                                            // core icon set; List is a stable fallback.
                                            Icon(
                                                imageVector = Icons.Default.List,
                                                contentDescription = "Reports"
                                            )
                                        },
                                        label = { Text("Reports") }
                                    )
                                }
                            }
                        ) { padding ->
                            Surface(modifier = Modifier.padding(padding)) {
                                when (selectedTab) {
                                    0 -> SettingsScreen()
                                    1 -> ReportScreen()
                                }
                            }
                        }
                    } else {
                        OnboardingScreen(
                            onComplete = { /* DataStore update triggers recomposition */ }
                        )
                    }
                }
            }
        }
    }
}
