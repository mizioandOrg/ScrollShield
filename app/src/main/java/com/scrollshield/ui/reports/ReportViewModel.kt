package com.scrollshield.ui.reports

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollshield.data.model.MonthlyAggregate
import com.scrollshield.reports.ChildActivityReport
import com.scrollshield.reports.DashboardData
import com.scrollshield.reports.ReportExporter
import com.scrollshield.reports.ReportRepository
import com.scrollshield.reports.WeeklyReport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reportRepository: ReportRepository
) : ViewModel() {

    private val _dashboard = MutableStateFlow<DashboardData?>(null)
    val dashboard: StateFlow<DashboardData?> = _dashboard.asStateFlow()

    private val _weekly = MutableStateFlow<WeeklyReport?>(null)
    val weekly: StateFlow<WeeklyReport?> = _weekly.asStateFlow()

    private val _child = MutableStateFlow<ChildActivityReport?>(null)
    val child: StateFlow<ChildActivityReport?> = _child.asStateFlow()

    private val _monthlies = MutableStateFlow<List<MonthlyAggregate>>(emptyList())
    val monthlies: StateFlow<List<MonthlyAggregate>> = _monthlies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    init {
        loadInitial()
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }

    private fun loadInitial() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val now = System.currentTimeMillis()
                _dashboard.value = withContext(Dispatchers.IO) {
                    runCatching { reportRepository.loadDashboard() }.getOrNull()
                }
                _weekly.value = withContext(Dispatchers.IO) {
                    runCatching { reportRepository.loadWeeklyReport(now) }.getOrNull()
                }
                _monthlies.value = withContext(Dispatchers.IO) {
                    runCatching { reportRepository.listMonthlyAggregates() }.getOrDefault(emptyList())
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadChildReport() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val now = System.currentTimeMillis()
                _child.value = withContext(Dispatchers.IO) {
                    runCatching { reportRepository.loadChildActivityReport(now) }.getOrNull()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportWeeklyJson() = viewModelScope.launch {
        val w = _weekly.value ?: return@launch
        writeToDownloads(ReportExporter.weeklyJson(w), "scrollshield_weekly.json", "application/json")
    }

    fun exportWeeklyCsv() = viewModelScope.launch {
        val w = _weekly.value ?: return@launch
        writeToDownloads(ReportExporter.weeklyCsv(w), "scrollshield_weekly.csv", "text/csv")
    }

    fun exportChildJson() = viewModelScope.launch {
        val c = _child.value ?: return@launch
        writeToDownloads(ReportExporter.childJson(c), "scrollshield_child.json", "application/json")
    }

    fun exportChildCsv() = viewModelScope.launch {
        val c = _child.value ?: return@launch
        writeToDownloads(ReportExporter.childCsv(c), "scrollshield_child.csv", "text/csv")
    }

    fun exportMonthlyJson(yearMonth: String) = viewModelScope.launch {
        val m = _monthlies.value.firstOrNull { it.yearMonth == yearMonth } ?: return@launch
        writeToDownloads(
            ReportExporter.monthlyJson(m),
            "scrollshield_monthly_${yearMonth}.json",
            "application/json"
        )
    }

    private suspend fun writeToDownloads(content: String, fileName: String, mimeType: String) {
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, fileName)
                    FileOutputStream(file).use { it.write(content.toByteArray()) }
                }
                _snackbar.value = "Exported to Downloads/$fileName"
            } catch (e: Exception) {
                _snackbar.value = "Export failed: ${e.message}"
            }
        }
    }
}
