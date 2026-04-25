package com.scrollshield.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollshield.data.model.TopicCategory
import com.scrollshield.data.preferences.UserPreferencesStore
import com.scrollshield.profile.ChildProfileConfig
import com.scrollshield.profile.ProfileManager
import com.scrollshield.profile.ProfileSwitcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TargetApp(
    val packageName: String,
    val displayName: String,
    val isInstalled: Boolean
)

data class OnboardingState(
    val currentStep: Int = 0,
    val installedApps: List<TargetApp> = emptyList(),
    val selectedApps: Set<String> = emptySet(),
    val selectedTopics: Set<TopicCategory> = emptySet(),
    val gamblingBlocked: Boolean = false,
    val dietCultureBlocked: Boolean = false,
    val cryptoBlocked: Boolean = false,
    val politicalOutrageBlocked: Boolean = false,
    val clickbaitBlocked: Boolean = false,
    val explicitAdsBlocked: Boolean = false,
    val timeBudgets: Map<String, Int> = emptyMap(),
    val unlimitedApps: Set<String> = emptySet(),
    val adCounterEnabled: Boolean = true,
    val scrollMaskEnabled: Boolean = false,
    val wantsChildProfile: Boolean? = null,
    val childPinDigits: String = "",
    val childPinConfirm: String = "",
    val accessibilityEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val mediaProjectionGranted: Boolean = false,
    val mediaProjectionDenied: Boolean = false,
    val isComplete: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
    private val profileSwitcher: ProfileSwitcher,
    private val preferencesStore: UserPreferencesStore
) : ViewModel() {

    companion object {
        val TARGET_PACKAGES = mapOf(
            "com.zhiliaoapp.musically" to "TikTok",
            "com.instagram.android" to "Instagram",
            "com.google.android.youtube" to "YouTube"
        )
    }

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        detectInstalledApps()
    }

    private fun detectInstalledApps() {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val installedPackages = installed.map { it.packageName }.toSet()

        val targetApps = TARGET_PACKAGES.map { (pkg, name) ->
            TargetApp(
                packageName = pkg,
                displayName = name,
                isInstalled = pkg in installedPackages
            )
        }

        _state.update { it.copy(installedApps = targetApps) }
    }

    fun toggleApp(packageName: String) {
        _state.update { state ->
            val newSelected = if (packageName in state.selectedApps) {
                state.selectedApps - packageName
            } else {
                state.selectedApps + packageName
            }
            state.copy(selectedApps = newSelected)
        }
    }

    fun toggleTopic(topic: TopicCategory) {
        _state.update { state ->
            val newTopics = if (topic in state.selectedTopics) {
                state.selectedTopics - topic
            } else {
                if (state.selectedTopics.size < 8) state.selectedTopics + topic
                else state.selectedTopics
            }
            state.copy(selectedTopics = newTopics)
        }
    }

    fun toggleBlockedCategory(category: String, enabled: Boolean) {
        _state.update { state ->
            when (category) {
                "gambling" -> state.copy(gamblingBlocked = enabled)
                "dietCulture" -> state.copy(dietCultureBlocked = enabled)
                "crypto" -> state.copy(cryptoBlocked = enabled)
                "politicalOutrage" -> state.copy(politicalOutrageBlocked = enabled)
                "clickbait" -> state.copy(clickbaitBlocked = enabled)
                "explicitAds" -> state.copy(explicitAdsBlocked = enabled)
                else -> state
            }
        }
    }

    fun updateTimeBudget(appName: String, minutes: Int) {
        _state.update { state ->
            state.copy(timeBudgets = state.timeBudgets + (appName to minutes))
        }
    }

    fun toggleUnlimited(packageName: String) {
        _state.update { state ->
            val newUnlimited = if (packageName in state.unlimitedApps) {
                state.unlimitedApps - packageName
            } else {
                state.unlimitedApps + packageName
            }
            state.copy(unlimitedApps = newUnlimited)
        }
    }

    fun setAdCounterEnabled(enabled: Boolean) {
        _state.update { it.copy(adCounterEnabled = enabled) }
    }

    fun setScrollMaskEnabled(enabled: Boolean) {
        _state.update { it.copy(scrollMaskEnabled = enabled) }
    }

    fun setWantsChildProfile(wants: Boolean) {
        _state.update { it.copy(wantsChildProfile = wants) }
    }

    fun setChildPin(pin: String) {
        _state.update { it.copy(childPinDigits = pin) }
    }

    fun setChildPinConfirm(pin: String) {
        _state.update { it.copy(childPinConfirm = pin) }
    }

    fun setAccessibilityEnabled(enabled: Boolean) {
        _state.update { it.copy(accessibilityEnabled = enabled) }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        _state.update { it.copy(overlayEnabled = enabled) }
    }

    fun setMediaProjectionGranted(granted: Boolean) {
        _state.update { it.copy(mediaProjectionGranted = granted) }
    }

    fun setMediaProjectionDenied(denied: Boolean) {
        _state.update { it.copy(mediaProjectionDenied = denied) }
    }

    fun nextStep() {
        val current = _state.value
        when (current.currentStep) {
            1 -> {
                if (current.selectedApps.isEmpty()) return
            }
            2 -> {
                if (current.selectedTopics.size < 3) return
            }
            6 -> {
                if (current.wantsChildProfile == true) {
                    if (current.childPinDigits.length != 4 ||
                        current.childPinDigits != current.childPinConfirm
                    ) return
                }
            }
        }
        _state.update { it.copy(currentStep = it.currentStep + 1) }
    }

    fun previousStep() {
        _state.update {
            if (it.currentStep > 0) it.copy(currentStep = it.currentStep - 1)
            else it
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            val s = _state.value

            // Build interest vector from selected topics
            val interestVector = FloatArray(20) { 0f }
            s.selectedTopics.forEach { topic ->
                interestVector[topic.index] = 1f
            }

            // Create the default profile
            val profile = profileManager.createDefaultProfile("Default", interestVector)

            // Determine blocked categories
            val blockedCategories = mutableSetOf<TopicCategory>()
            if (s.gamblingBlocked) blockedCategories.add(TopicCategory.FINANCE)
            if (s.dietCultureBlocked) blockedCategories.add(TopicCategory.HEALTH)
            if (s.cryptoBlocked) blockedCategories.add(TopicCategory.FINANCE)
            if (s.politicalOutrageBlocked) blockedCategories.add(TopicCategory.POLITICS)
            if (s.clickbaitBlocked) blockedCategories.add(TopicCategory.NEWS)
            if (s.explicitAdsBlocked) blockedCategories.add(TopicCategory.FASHION)

            // Build time budgets
            val timeBudgets = mutableMapOf<String, Int>()
            for ((pkg, name) in TARGET_PACKAGES) {
                if (pkg in s.selectedApps && pkg !in s.unlimitedApps) {
                    timeBudgets[name] = s.timeBudgets[name] ?: 30
                }
            }

            // Update profile with settings
            val updatedProfile = profile.copy(
                blockedCategories = blockedCategories,
                timeBudgets = timeBudgets,
                counterEnabled = s.adCounterEnabled,
                maskEnabled = s.scrollMaskEnabled
            )
            profileManager.updateProfile(updatedProfile)

            // Create child profile if requested
            if (s.wantsChildProfile == true && s.childPinDigits.length == 4) {
                val childProfile = profileManager.createChildProfile(
                    name = "Child",
                    parentPinHash = "",  // PIN hash is set via profileSwitcher.setPin
                    timeBudgets = mapOf("default" to ChildProfileConfig.DEFAULT_TIME_BUDGET_MINUTES)
                )
                profileSwitcher.setPin(childProfile.id, s.childPinDigits)
            }

            // Mark onboarding complete
            preferencesStore.setOnboardingCompleted(true)
            _state.update { it.copy(isComplete = true) }
        }
    }
}

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    if (state.isComplete) {
        onComplete()
        return
    }

    val totalSteps = 9
    val progress = (state.currentStep + 1).toFloat() / totalSteps

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        @Suppress("DEPRECATION")
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (state.currentStep) {
                0 -> WelcomeStep()
                1 -> AppSelectionStep(
                    installedApps = state.installedApps,
                    selectedApps = state.selectedApps,
                    onToggle = viewModel::toggleApp
                )
                2 -> InterestSelectionScreen(
                    selectedTopics = state.selectedTopics,
                    onTopicToggle = viewModel::toggleTopic,
                    selectionCount = state.selectedTopics.size
                )
                3 -> BlockedCategoryScreen(
                    gamblingBlocked = state.gamblingBlocked,
                    dietCultureBlocked = state.dietCultureBlocked,
                    cryptoBlocked = state.cryptoBlocked,
                    politicalOutrageBlocked = state.politicalOutrageBlocked,
                    clickbaitBlocked = state.clickbaitBlocked,
                    explicitAdsBlocked = state.explicitAdsBlocked,
                    onToggle = viewModel::toggleBlockedCategory
                )
                4 -> TimeBudgetScreen(
                    selectedApps = state.selectedApps,
                    installedTargetApps = state.installedApps,
                    timeBudgets = state.timeBudgets,
                    unlimitedApps = state.unlimitedApps,
                    onBudgetChange = viewModel::updateTimeBudget,
                    onUnlimitedToggle = viewModel::toggleUnlimited
                )
                5 -> FeatureTogglesStep(
                    adCounterEnabled = state.adCounterEnabled,
                    scrollMaskEnabled = state.scrollMaskEnabled,
                    onAdCounterChange = viewModel::setAdCounterEnabled,
                    onScrollMaskChange = viewModel::setScrollMaskEnabled
                )
                6 -> ChildProfileSetupScreen(
                    wantsChildProfile = state.wantsChildProfile,
                    childPinDigits = state.childPinDigits,
                    childPinConfirm = state.childPinConfirm,
                    onWantsChildProfile = viewModel::setWantsChildProfile,
                    onPinChange = viewModel::setChildPin,
                    onPinConfirmChange = viewModel::setChildPinConfirm
                )
                7 -> PermissionsStep(
                    accessibilityEnabled = state.accessibilityEnabled,
                    overlayEnabled = state.overlayEnabled,
                    mediaProjectionGranted = state.mediaProjectionGranted,
                    mediaProjectionDenied = state.mediaProjectionDenied,
                    onAccessibilityCheck = viewModel::setAccessibilityEnabled,
                    onOverlayCheck = viewModel::setOverlayEnabled,
                    onMediaProjectionGranted = viewModel::setMediaProjectionGranted,
                    onMediaProjectionDenied = viewModel::setMediaProjectionDenied
                )
                8 -> DoneStep()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (state.currentStep > 0) {
                OutlinedButton(onClick = { viewModel.previousStep() }) {
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (state.currentStep < 8) {
                val canProceed = when (state.currentStep) {
                    0 -> true
                    1 -> state.selectedApps.isNotEmpty()
                    2 -> state.selectedTopics.size >= 3
                    6 -> state.wantsChildProfile != true ||
                            (state.childPinDigits.length == 4 &&
                                    state.childPinDigits == state.childPinConfirm)
                    else -> true
                }
                Button(
                    onClick = { viewModel.nextStep() },
                    enabled = canProceed
                ) {
                    Text(if (state.currentStep == 0) "Get Started" else "Next")
                }
            } else {
                Button(onClick = { viewModel.completeOnboarding() }) {
                    Text("Finish")
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Welcome to ScrollShield",
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = "ScrollShield helps you take control of your social media experience by:",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(text = "\u2022 Detecting and counting ads in your feed", style = MaterialTheme.typography.bodyMedium)
        Text(text = "\u2022 Scoring content based on your interests", style = MaterialTheme.typography.bodyMedium)
        Text(text = "\u2022 Setting time budgets for each app", style = MaterialTheme.typography.bodyMedium)
        Text(text = "\u2022 Protecting children with restricted profiles", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AppSelectionStep(
    installedApps: List<TargetApp>,
    selectedApps: Set<String>,
    onToggle: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Select Apps to Monitor",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Choose at least one app for ScrollShield to monitor.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        installedApps.forEach { app ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = app.packageName in selectedApps,
                    onCheckedChange = { onToggle(app.packageName) },
                    enabled = app.isInstalled
                )
                Column {
                    Text(
                        text = app.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (!app.isInstalled) {
                        Text(
                            text = "Not installed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureTogglesStep(
    adCounterEnabled: Boolean,
    scrollMaskEnabled: Boolean,
    onAdCounterChange: (Boolean) -> Unit,
    onScrollMaskChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Feature Toggles",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Enable or disable core features. These can be changed later in Settings.",
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Ad Counter", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Track and display ad counts in your feed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = adCounterEnabled, onCheckedChange = onAdCounterChange)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Scroll Mask", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Overlay a mask on low-quality content",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = scrollMaskEnabled, onCheckedChange = onScrollMaskChange)
        }
    }
}

@Composable
private fun PermissionsStep(
    accessibilityEnabled: Boolean,
    overlayEnabled: Boolean,
    mediaProjectionGranted: Boolean,
    mediaProjectionDenied: Boolean,
    onAccessibilityCheck: (Boolean) -> Unit,
    onOverlayCheck: (Boolean) -> Unit,
    onMediaProjectionGranted: (Boolean) -> Unit,
    onMediaProjectionDenied: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onMediaProjectionGranted(true)
        } else {
            onMediaProjectionDenied(true)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Required Permissions",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "ScrollShield needs these permissions to monitor and overlay content.",
            style = MaterialTheme.typography.bodyMedium
        )

        // Accessibility Service
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (accessibilityEnabled)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Accessibility Service", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Required to detect feed content and ads in target apps.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                if (accessibilityEnabled) {
                    Text(
                        text = "Enabled",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                } else {
                    Button(onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }) {
                        Text("Open Settings")
                    }
                    TextButton(onClick = {
                        // Re-check accessibility
                        val enabled = try {
                            val settingValue = Settings.Secure.getString(
                                context.contentResolver,
                                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                            )
                            settingValue?.contains(context.packageName) == true
                        } catch (_: Exception) {
                            false
                        }
                        onAccessibilityCheck(enabled)
                    }) {
                        Text("I've enabled it")
                    }
                }
            }
        }

        // Overlay Permission
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (overlayEnabled)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Display Over Other Apps", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Required to show the ad counter overlay and scroll mask.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                if (overlayEnabled) {
                    Text(
                        text = "Enabled",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                } else {
                    Button(onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }) {
                        Text("Grant Permission")
                    }
                    TextButton(onClick = {
                        onOverlayCheck(Settings.canDrawOverlays(context))
                    }) {
                        Text("I've enabled it")
                    }
                }
            }
        }

        // MediaProjection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (mediaProjectionGranted)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Screen Capture", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Required for visual classification of feed content. ScrollShield captures screen frames locally and never transmits them.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                if (mediaProjectionGranted) {
                    Text(
                        text = "Granted",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                } else {
                    if (mediaProjectionDenied) {
                        Text(
                            text = "Screen capture was denied. Visual classification will be unavailable. You can grant this later in Settings.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    Button(onClick = {
                        val mpManager = context.getSystemService(
                            Context.MEDIA_PROJECTION_SERVICE
                        ) as MediaProjectionManager
                        mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                    }) {
                        Text("Grant Screen Capture")
                    }
                }
            }
        }
    }
}

@Composable
private fun DoneStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "You're All Set!",
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = "ScrollShield is configured and ready to protect your feed.",
            style = MaterialTheme.typography.bodyLarge
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "First Session Tip",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Open one of your selected apps and scroll normally. ScrollShield will begin classifying content and counting ads in the background. Check the notification shade for your live ad count.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
