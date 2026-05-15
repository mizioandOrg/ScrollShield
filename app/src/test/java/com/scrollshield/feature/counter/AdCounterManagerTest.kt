package com.scrollshield.feature.counter

import android.app.Application
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.scrollshield.data.db.SessionDao
import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.ClassifiedItem
import com.scrollshield.data.model.FeedItem
import com.scrollshield.data.model.SkipDecision
import com.scrollshield.data.model.TopicCategory
import com.scrollshield.profile.ProfileManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdCounterManagerTest {

    @Test
    fun exportJsonContainsKeyFields() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dao = mockk<SessionDao>(relaxed = true)
        val pm = mockk<ProfileManager>(relaxed = true)
        every { pm.getActiveProfileId() } returns flowOf(null)
        val mgr = AdCounterManager(context, dao, pm)

        val state = AdCounterManager.UiState(
            sessionId = "s1",
            currentApp = "com.instagram.android",
            adsDetected = 4,
            adsSkipped = 2,
            itemsSeen = 20,
        )
        val json = mgr.exportJson(state)
        check(json.contains("\"adsDetected\":4"))
        check(json.contains("\"itemsSeen\":20"))
        check(json.contains("\"sessionId\":\"s1\""))
    }

    @Test
    fun statusColorDefaults() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dao = mockk<SessionDao>(relaxed = true)
        val pm = mockk<ProfileManager>(relaxed = true)
        every { pm.getActiveProfileId() } returns flowOf(null)
        val mgr = AdCounterManager(context, dao, pm)
        val green = mgr.statusColor(AdCounterManager.UiState(adsDetected = 0))
        val amber = mgr.statusColor(AdCounterManager.UiState(adsDetected = 5))
        val red = mgr.statusColor(AdCounterManager.UiState(adsDetected = 30))
        check(green == StatusColor.GREEN) { "green got $green" }
        check(amber == StatusColor.AMBER) { "amber got $amber" }
        check(red == StatusColor.RED) { "red got $red" }
    }
}
