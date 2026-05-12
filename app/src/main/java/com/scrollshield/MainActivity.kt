package com.scrollshield

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.scrollshield.data.preferences.UserPreferencesStore
import com.scrollshield.service.MediaProjectionHolder
import com.scrollshield.service.ScreenCaptureService
import com.scrollshield.ui.onboarding.OnboardingScreen
import com.scrollshield.ui.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferencesStore: UserPreferencesStore
    @Inject lateinit var mediaProjectionHolder: MediaProjectionHolder

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            mediaProjectionHolder.setMediaProjection(result.resultCode, data)
        } else {
            // User denied — stop the foreground service we pre-started
            stopService(Intent(this, ScreenCaptureService::class.java)
                .setAction(ScreenCaptureService.ACTION_STOP))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 14+ requires FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION to be active
        // BEFORE calling getMediaProjection(). Start the service first, then show the
        // consent dialog. If the user denies, the launcher callback stops the service.
        val onboardingCompleted = runBlocking { preferencesStore.onboardingCompleted.first() }
        if (onboardingCompleted) {
            val svcIntent = Intent(this, ScreenCaptureService::class.java)
                .setAction(ScreenCaptureService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svcIntent)
            } else {
                startService(svcIntent)
            }
            val mpManager = getSystemService(MediaProjectionManager::class.java)
            screenCaptureLauncher.launch(mpManager.createScreenCaptureIntent())
        }

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
