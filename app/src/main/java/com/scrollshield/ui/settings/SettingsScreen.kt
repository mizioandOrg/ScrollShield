package com.scrollshield.ui.settings

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollshield.data.db.ScrollShieldDatabase
import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.ScoringWeights
import com.scrollshield.data.model.TopicCategory
import com.scrollshield.data.model.UserProfile
import com.scrollshield.data.preferences.UserPreferencesStore
import com.scrollshield.profile.ProfileManager
import com.scrollshield.profile.ProfileSwitcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val profiles: List<UserProfile> = emptyList(),
    val activeProfileId: String? = null,
    val activeProfile: UserProfile? = null,
    val selectedTopics: Set<TopicCategory> = emptySet(),
    val blockedCategories: Set<TopicCategory> = emptySet(),
    val blockedClassifications: Set<Classification> = emptySet(),
    val timeBudgets: Map<String, Int> = emptyMap(),
    val counterEnabled: Boolean = false,
    val maskEnabled: Boolean = false,
    val scoringWeights: ScoringWeights = ScoringWeights(),
    val cpmTikTok: Float = 10f,
    val cpmInstagram: Float = 12f,
    val cpmYouTube: Float = 15f,
    val preScanBufferSize: Int = 10,
    val extensionSize: Int = 0,
    val statusDotAmber: Int = 3,
    val statusDotRed: Int = 11,
    val visualClassificationEnabled: Boolean = true,
    val childProfiles: List<UserProfile> = emptyList(),
    val showChildSettings: Boolean = false,
    val childAuthRequired: Boolean = false,
    val childAuthenticated: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
    private val profileSwitcher: ProfileSwitcher,
    private val preferencesStore: UserPreferencesStore,
    private val database: ScrollShieldDatabase
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            profileManager.getAllProfiles().collect { profiles ->
                val activeId = preferencesStore.activeProfileId.first()
                val activeProfile = activeId?.let { profileManager.getProfileById(it) }
                val childProfiles = profiles.filter { it.isChildProfile }

                // Load CPM overrides
                val counterPrefs = context.getSharedPreferences(
                    "scrollshield_counter_prefs", Context.MODE_PRIVATE
                )
                val cpmTikTok = counterPrefs.getFloat("cpm_TikTok", 10f)
                val cpmInstagram = counterPrefs.getFloat("cpm_Instagram", 12f)
                val cpmYouTube = counterPrefs.getFloat("cpm_YouTube", 15f)
                val extensionSize = counterPrefs.getInt("extension_size", 0)
                val dotAmber = counterPrefs.getInt("dot_amber", 3)
                val dotRed = counterPrefs.getInt("dot_red", 11)

                val preScanBuffer = preferencesStore.preScanBufferSize.first()
                val visualEnabled = preferencesStore.visualClassificationEnabled.first()

                _state.update { s ->
                    s.copy(
                        profiles = profiles,
                        activeProfileId = activeId,
                        activeProfile = activeProfile,
                        selectedTopics = activeProfile?.let { p ->
                            TopicCategory.entries.filter { t ->
                                p.interestVector.getOrNull(t.index)?.let { it > 0f } == true
                            }.toSet()
                        } ?: emptySet(),
                        blockedCategories = activeProfile?.blockedCategories ?: emptySet(),
                        blockedClassifications = activeProfile?.blockedClassifications ?: emptySet(),
                        timeBudgets = activeProfile?.timeBudgets ?: emptyMap(),
                        counterEnabled = activeProfile?.counterEnabled ?: false,
                        maskEnabled = activeProfile?.maskEnabled ?: false,
                        scoringWeights = activeProfile?.scoringWeights ?: ScoringWeights(),
                        cpmTikTok = cpmTikTok,
                        cpmInstagram = cpmInstagram,
                        cpmYouTube = cpmYouTube,
                        preScanBufferSize = preScanBuffer,
                        extensionSize = extensionSize,
                        statusDotAmber = dotAmber,
                        statusDotRed = dotRed,
                        visualClassificationEnabled = visualEnabled,
                        childProfiles = childProfiles,
                        showChildSettings = childProfiles.isNotEmpty()
                    )
                }
            }
        }
    }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            val result = profileSwitcher.switchProfile(profileId)
            when (result) {
                ProfileSwitcher.SwitchResult.APPLIED -> {
                    _state.update { it.copy(snackbarMessage = "Profile switched") }
                }
                ProfileSwitcher.SwitchResult.DEFERRED -> {
                    _state.update { it.copy(snackbarMessage = "Profile will switch after current session") }
                }
                ProfileSwitcher.SwitchResult.PIN_REQUIRED -> {
                    _state.update { it.copy(snackbarMessage = "PIN required to switch from child profile") }
                }
                ProfileSwitcher.SwitchResult.LOCKED_OUT -> {
                    _state.update { it.copy(snackbarMessage = "Too many failed attempts. Try again later.") }
                }
                ProfileSwitcher.SwitchResult.PROFILE_NOT_FOUND -> {
                    _state.update { it.copy(snackbarMessage = "Profile not found") }
                }
            }
        }
    }

    fun updateSelectedTopics(topics: Set<TopicCategory>) {
        _state.update { it.copy(selectedTopics = topics) }
    }

    fun updateBlockedCategories(categories: Set<TopicCategory>) {
        _state.update { it.copy(blockedCategories = categories) }
    }

    fun updateTimeBudgets(budgets: Map<String, Int>) {
        _state.update { it.copy(timeBudgets = budgets) }
    }

    fun updateProfileSettings() {
        viewModelScope.launch {
            val s = _state.value
            val profile = s.activeProfile ?: return@launch

            val interestVector = FloatArray(20) { 0f }
            s.selectedTopics.forEach { topic ->
                interestVector[topic.index] = 1f
            }

            val updated = profile.copy(
                interestVector = interestVector,
                blockedCategories = s.blockedCategories,
                timeBudgets = s.timeBudgets,
                scoringWeights = s.scoringWeights
            )
            profileManager.updateProfile(updated)
            _state.update { it.copy(snackbarMessage = "Settings saved. Changes take effect next session.") }
        }
    }

    fun updateFeatureToggles(counterEnabled: Boolean, maskEnabled: Boolean) {
        viewModelScope.launch {
            val profileId = _state.value.activeProfileId ?: return@launch
            val profile = _state.value.activeProfile ?: return@launch
            profileSwitcher.updateFeatureToggles(
                profileId = profileId,
                maskEnabled = maskEnabled,
                counterEnabled = counterEnabled,
                maskDismissable = profile.maskDismissable
            )
            _state.update {
                it.copy(
                    counterEnabled = counterEnabled,
                    maskEnabled = maskEnabled,
                    snackbarMessage = "Feature toggles updated immediately."
                )
            }
        }
    }

    fun updateScoringWeights(weights: ScoringWeights) {
        _state.update { it.copy(scoringWeights = weights) }
    }

    fun saveCpmOverrides() {
        val s = _state.value
        val prefs = context.getSharedPreferences("scrollshield_counter_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("cpm_TikTok", s.cpmTikTok)
            .putFloat("cpm_Instagram", s.cpmInstagram)
            .putFloat("cpm_YouTube", s.cpmYouTube)
            .apply()
        _state.update { it.copy(snackbarMessage = "CPM overrides saved") }
    }

    fun updateCpm(platform: String, value: Float) {
        _state.update { s ->
            when (platform) {
                "TikTok" -> s.copy(cpmTikTok = value)
                "Instagram" -> s.copy(cpmInstagram = value)
                "YouTube" -> s.copy(cpmYouTube = value)
                else -> s
            }
        }
    }

    fun saveExtensionSize(size: Int) {
        val prefs = context.getSharedPreferences("scrollshield_counter_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("extension_size", size).apply()
        _state.update { it.copy(extensionSize = size, snackbarMessage = "Extension size saved. Restart app to apply.") }
    }

    fun saveStatusDotThresholds(amber: Int, red: Int) {
        val prefs = context.getSharedPreferences("scrollshield_counter_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("dot_amber", amber)
            .putInt("dot_red", red)
            .apply()
        _state.update {
            it.copy(
                statusDotAmber = amber,
                statusDotRed = red,
                snackbarMessage = "Status dot thresholds saved"
            )
        }
    }

    fun savePreScanBufferSize(size: Int) {
        viewModelScope.launch {
            preferencesStore.setPreScanBufferSize(size)
            _state.update { it.copy(preScanBufferSize = size, snackbarMessage = "Pre-scan buffer size saved") }
        }
    }

    fun saveVisualClassification(enabled: Boolean) {
        viewModelScope.launch {
            preferencesStore.setVisualClassificationEnabled(enabled)
            _state.update { it.copy(visualClassificationEnabled = enabled) }
        }
    }

    fun showDeleteConfirmation() {
        _state.update { it.copy(showDeleteConfirmation = true) }
    }

    fun hideDeleteConfirmation() {
        _state.update { it.copy(showDeleteConfirmation = false) }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true) }
            try {
                // Clear database
                database.clearAllTables()

                // Reset DataStore keys individually
                preferencesStore.setOnboardingCompleted(false)
                preferencesStore.setMediaProjectionGranted(false)
                preferencesStore.setVisualClassificationEnabled(true)
                preferencesStore.setPreScanBufferSize(10)
                preferencesStore.setStatusDotThreshold(0.7f)
                preferencesStore.setCpmOverrideUsd(2.5f)

                // Clear SharedPreferences
                context.getSharedPreferences("scrollshield_sensitive_prefs", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                context.getSharedPreferences("scrollshield_counter_prefs", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                context.getSharedPreferences("scrollshield_mp_prefs", Context.MODE_PRIVATE)
                    .edit().clear().apply()

                _state.update {
                    it.copy(
                        isDeleting = false,
                        showDeleteConfirmation = false,
                        snackbarMessage = "All data deleted"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isDeleting = false,
                        showDeleteConfirmation = false,
                        snackbarMessage = "Error deleting data: ${e.message}"
                    )
                }
            }
        }
    }

    fun authenticateForChildSettings(pin: String) {
        viewModelScope.launch {
            val childProfile = _state.value.childProfiles.firstOrNull() ?: return@launch
            val verified = profileSwitcher.verifyPin(childProfile.id, pin)
            _state.update {
                it.copy(
                    childAuthenticated = verified,
                    childAuthRequired = !verified,
                    snackbarMessage = if (!verified) "Incorrect PIN" else null
                )
            }
        }
    }

    fun changeChildPin(childProfileId: String, newPin: String) {
        viewModelScope.launch {
            profileSwitcher.setPin(childProfileId, newPin)
            _state.update { it.copy(snackbarMessage = "Child PIN updated") }
        }
    }

    fun resetChildProfile(childProfileId: String) {
        viewModelScope.launch {
            val profile = profileManager.getProfileById(childProfileId) ?: return@launch
            profileManager.deleteProfile(profile)
            _state.update { it.copy(snackbarMessage = "Child profile deleted") }
        }
    }

    fun clearSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    fun exportSessionData(format: String) {
        viewModelScope.launch {
            try {
                val sessions = database.sessionDao().getSessionsSince(0)
                val data = if (format == "json") {
                    val arr = org.json.JSONArray()
                    sessions.forEach { session ->
                        val obj = org.json.JSONObject()
                        obj.put("id", session.id)
                        obj.put("profileId", session.profileId)
                        obj.put("app", session.app)
                        obj.put("startTime", session.startTime)
                        obj.put("endTime", session.endTime)
                        obj.put("durationMinutes", session.durationMinutes.toDouble())
                        obj.put("adsDetected", session.adsDetected)
                        obj.put("adsSkipped", session.adsSkipped)
                        obj.put("itemsSeen", session.itemsSeen)
                        arr.put(obj)
                    }
                    arr.toString(2)
                } else {
                    // CSV
                    val sb = StringBuilder()
                    sb.appendLine("id,profileId,app,startTime,endTime,durationMinutes,adsDetected,adsSkipped,itemsSeen")
                    sessions.forEach { session ->
                        sb.appendLine("${session.id},${session.profileId},${session.app},${session.startTime},${session.endTime},${session.durationMinutes},${session.adsDetected},${session.adsSkipped},${session.itemsSeen}")
                    }
                    sb.toString()
                }

                val fileName = "scrollshield_export.${if (format == "json") "json" else "csv"}"
                val mimeType = if (format == "json") "application/json" else "text/csv"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = android.content.ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs()
                    val file = java.io.File(dir, fileName)
                    java.io.FileOutputStream(file).use { it.write(data.toByteArray()) }
                }

                _state.update { it.copy(snackbarMessage = "Exported to Downloads/$fileName") }
            } catch (e: Exception) {
                _state.update { it.copy(snackbarMessage = "Export failed: ${e.message}") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium
            )

            // ---- Section 1: Profile Switcher ----
            SectionHeader("Profile")
            ProfileSwitcherSection(
                profiles = state.profiles,
                activeProfileId = state.activeProfileId,
                onSwitch = viewModel::switchProfile
            )

            @Suppress("DEPRECATION") Divider()

            // ---- Section 2: Per-Profile Settings ----
            if (state.activeProfile != null && !state.activeProfile!!.isChildProfile) {
                SectionHeader("Interest Topics")
                InterestTopicsGrid(
                    selectedTopics = state.selectedTopics,
                    onToggle = { topic ->
                        val current = state.selectedTopics
                        val updated = if (topic in current) current - topic
                        else if (current.size < 8) current + topic
                        else current
                        viewModel.updateSelectedTopics(updated)
                    }
                )

                SectionHeader("Blocked Categories")
                BlockedCategoriesSection(
                    blockedCategories = state.blockedCategories,
                    onToggle = { category ->
                        val current = state.blockedCategories
                        val updated = if (category in current) current - category else current + category
                        viewModel.updateBlockedCategories(updated)
                    }
                )

                SectionHeader("Time Budgets")
                TimeBudgetsSection(
                    timeBudgets = state.timeBudgets,
                    onUpdate = { viewModel.updateTimeBudgets(it) }
                )

                Button(
                    onClick = { viewModel.updateProfileSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Profile Settings")
                }

                @Suppress("DEPRECATION") Divider()

                SectionHeader("Feature Toggles")
                Text(
                    text = "Changes take effect immediately",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FeatureToggleRow(
                    label = "Ad Counter",
                    checked = state.counterEnabled,
                    onCheckedChange = { viewModel.updateFeatureToggles(it, state.maskEnabled) }
                )
                FeatureToggleRow(
                    label = "Scroll Mask",
                    checked = state.maskEnabled,
                    onCheckedChange = { viewModel.updateFeatureToggles(state.counterEnabled, it) }
                )

                @Suppress("DEPRECATION") Divider()
            }

            // ---- Section 3: Advanced ----
            SectionHeader("Advanced")

            // Scoring Weights
            Text(text = "Scoring Weights", style = MaterialTheme.typography.titleSmall)
            WeightSlider("Interest", state.scoringWeights.interest) { v ->
                viewModel.updateScoringWeights(state.scoringWeights.copy(interest = v))
            }
            WeightSlider("Well-being", state.scoringWeights.wellbeing) { v ->
                viewModel.updateScoringWeights(state.scoringWeights.copy(wellbeing = v))
            }
            WeightSlider("Novelty", state.scoringWeights.novelty) { v ->
                viewModel.updateScoringWeights(state.scoringWeights.copy(novelty = v))
            }
            WeightSlider("Manipulation", state.scoringWeights.manipulation) { v ->
                viewModel.updateScoringWeights(state.scoringWeights.copy(manipulation = v))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // CPM Sliders
            Text(text = "CPM Overrides ($/1000 impressions)", style = MaterialTheme.typography.titleSmall)
            CpmSlider("TikTok", state.cpmTikTok) { viewModel.updateCpm("TikTok", it) }
            CpmSlider("Instagram", state.cpmInstagram) { viewModel.updateCpm("Instagram", it) }
            CpmSlider("YouTube", state.cpmYouTube) { viewModel.updateCpm("YouTube", it) }
            Button(
                onClick = { viewModel.saveCpmOverrides() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save CPM Overrides")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pre-scan buffer
            Text(text = "Pre-scan Buffer Size", style = MaterialTheme.typography.titleSmall)
            var preScanInput by remember(state.preScanBufferSize) {
                mutableStateOf(state.preScanBufferSize.toString())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = preScanInput,
                    onValueChange = { preScanInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Buffer size") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        preScanInput.toIntOrNull()?.let { viewModel.savePreScanBufferSize(it) }
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Save")
                }
            }

            // Extension size
            Text(text = "Extension Size", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Changes require app restart",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            var extInput by remember(state.extensionSize) {
                mutableStateOf(state.extensionSize.toString())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = extInput,
                    onValueChange = { extInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Extension size") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        extInput.toIntOrNull()?.let { viewModel.saveExtensionSize(it) }
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Save")
                }
            }

            // Status dot thresholds
            Text(text = "Status Dot Thresholds", style = MaterialTheme.typography.titleSmall)
            var amberInput by remember(state.statusDotAmber) {
                mutableStateOf(state.statusDotAmber.toString())
            }
            var redInput by remember(state.statusDotRed) {
                mutableStateOf(state.statusDotRed.toString())
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = amberInput,
                    onValueChange = { amberInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Amber") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = redInput,
                    onValueChange = { redInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Red") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    val a = amberInput.toIntOrNull() ?: return@Button
                    val r = redInput.toIntOrNull() ?: return@Button
                    viewModel.saveStatusDotThresholds(a, r)
                }) {
                    Text("Save")
                }
            }

            // Visual classification toggle
            FeatureToggleRow(
                label = "Visual Classification",
                checked = state.visualClassificationEnabled,
                onCheckedChange = { viewModel.saveVisualClassification(it) }
            )

            // MediaProjection re-grant
            val mediaProjectionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    viewModel.saveVisualClassification(true)
                }
            }
            OutlinedButton(
                onClick = {
                    val mpManager = context.getSystemService(
                        Context.MEDIA_PROJECTION_SERVICE
                    ) as MediaProjectionManager
                    mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Re-grant Screen Capture")
            }

            @Suppress("DEPRECATION") Divider()

            // ---- Section 4: Child Profile Management ----
            if (state.showChildSettings) {
                SectionHeader("Child Profile Management")

                if (!state.childAuthenticated) {
                    var pinInput by remember { mutableStateOf("") }
                    Text(
                        text = "Enter parent PIN to manage child settings",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { value ->
                                if (value.length <= 4 && value.all { it.isDigit() }) {
                                    pinInput = value
                                }
                            },
                            label = { Text("PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { viewModel.authenticateForChildSettings(pinInput) },
                            modifier = Modifier.padding(start = 8.dp),
                            enabled = pinInput.length == 4
                        ) {
                            Text("Verify")
                        }
                    }
                } else {
                    state.childProfiles.forEach { child ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Child: ${child.name}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Time budget: ${child.timeBudgets.values.firstOrNull() ?: "N/A"} min",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Mask: ${if (child.maskEnabled) "on" else "off"}, Dismissable: ${if (child.maskDismissable) "yes" else "no"}",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Change PIN
                                var newPin by remember { mutableStateOf("") }
                                var confirmNewPin by remember { mutableStateOf("") }
                                OutlinedTextField(
                                    value = newPin,
                                    onValueChange = { v ->
                                        if (v.length <= 4 && v.all { it.isDigit() }) newPin = v
                                    },
                                    label = { Text("New PIN") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = confirmNewPin,
                                    onValueChange = { v ->
                                        if (v.length <= 4 && v.all { it.isDigit() }) confirmNewPin = v
                                    },
                                    label = { Text("Confirm New PIN") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        if (newPin.length == 4 && newPin == confirmNewPin) {
                                            viewModel.changeChildPin(child.id, newPin)
                                            newPin = ""
                                            confirmNewPin = ""
                                        }
                                    },
                                    enabled = newPin.length == 4 && newPin == confirmNewPin,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Change PIN")
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = { viewModel.resetChildProfile(child.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Delete Child Profile")
                                }
                            }
                        }
                    }
                }

                @Suppress("DEPRECATION") Divider()
            }

            // ---- Section 5: Data ----
            SectionHeader("Data")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.exportSessionData("csv") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export CSV")
                }
                OutlinedButton(
                    onClick = { viewModel.exportSessionData("json") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export JSON")
                }
            }

            Button(
                onClick = { viewModel.showDeleteConfirmation() },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete All Data")
            }
        }

        SnackbarHost(hostState = snackbarHostState) { data ->
            Snackbar(snackbarData = data)
        }
    }

    // Delete confirmation dialog
    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirmation() },
            title = { Text("Delete All Data?") },
            text = {
                Text("This will permanently delete all profiles, session history, ad signatures, and preferences. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAllData() },
                    enabled = !state.isDeleting,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(if (state.isDeleting) "Deleting..." else "Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ProfileSwitcherSection(
    profiles: List<UserProfile>,
    activeProfileId: String?,
    onSwitch: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val activeProfile = profiles.find { it.id == activeProfileId }

    Column {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(activeProfile?.name ?: "No profile selected")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${profile.name}${if (profile.isChildProfile) " (Child)" else ""}${if (profile.id == activeProfileId) " *" else ""}"
                        )
                    },
                    onClick = {
                        onSwitch(profile.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InterestTopicsGrid(
    selectedTopics: Set<TopicCategory>,
    onToggle: (TopicCategory) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(300.dp)
    ) {
        items(TopicCategory.entries.toList()) { topic ->
            FilterChip(
                selected = topic in selectedTopics,
                onClick = { onToggle(topic) },
                label = { Text(topic.label) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BlockedCategoriesSection(
    blockedCategories: Set<TopicCategory>,
    onToggle: (TopicCategory) -> Unit
) {
    val blockableCategories = listOf(
        TopicCategory.FINANCE to "Gambling/Crypto",
        TopicCategory.HEALTH to "Diet Culture",
        TopicCategory.POLITICS to "Political Outrage",
        TopicCategory.NEWS to "Clickbait",
        TopicCategory.FASHION to "Explicit Ads"
    )

    blockableCategories.forEach { (category, label) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, modifier = Modifier.weight(1f))
            Switch(
                checked = category in blockedCategories,
                onCheckedChange = { onToggle(category) }
            )
        }
    }
}

@Composable
private fun TimeBudgetsSection(
    timeBudgets: Map<String, Int>,
    onUpdate: (Map<String, Int>) -> Unit
) {
    val platforms = listOf("TikTok", "Instagram", "YouTube")
    platforms.forEach { platform ->
        val budget = timeBudgets[platform] ?: 30
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = platform, modifier = Modifier.weight(1f))
                Text(text = "$budget min", style = MaterialTheme.typography.bodySmall)
            }
            Slider(
                value = budget.toFloat(),
                onValueChange = {
                    onUpdate(timeBudgets + (platform to it.toInt()))
                },
                valueRange = 15f..120f,
                steps = ((120 - 15) / 5) - 1
            )
        }
    }
}

@Composable
private fun FeatureToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun WeightSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(text = "%.2f".format(value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f
        )
    }
}

@Composable
private fun CpmSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(text = "$%.1f".format(value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 1f..50f
        )
    }
}
