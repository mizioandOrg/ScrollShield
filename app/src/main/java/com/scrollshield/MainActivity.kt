package com.scrollshield

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.scrollshield.data.preferences.UserPreferencesStore
import com.scrollshield.ui.onboarding.OnboardingScreen
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
                        SettingsScreen()
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
