package com.universestream.app.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.universestream.app.ui.components.shell.VodHeroStrip
import com.universestream.app.ui.theme.UniverseStreamTheme
import com.universestream.data.sync.ProviderSyncWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeBannerAndSyncUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(MOBILE_LIGHTWEIGHT_WORK_NAME).result.get()
    }

    @After
    fun tearDown() {
        workManager.cancelUniqueWork(MOBILE_LIGHTWEIGHT_WORK_NAME).result.get()
    }

    @Test
    fun resumeBanner_isCompactInLandscapeOnMobile() {
        val landscapePhoneConfiguration = Configuration(
            context.resources.configuration
        ).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
            screenWidthDp = 480
            screenHeightDp = 320
        }

        composeRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides landscapePhoneConfiguration) {
                UniverseStreamTheme {
                    VodHeroStrip(
                        title = "Resume title",
                        subtitle = "Resume subtitle",
                        actionLabel = "Open",
                        onClick = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("vod_hero_strip")
            .assertHeightIsEqualTo(60.dp)
    }

    @Test
    fun appOpenSync_isEnqueuedUniquelyAndCanBeKept() {
        ProviderSyncWorker.enqueueMobileLightweightCheck(context)
        ProviderSyncWorker.enqueueMobileLightweightCheck(context)

        val workInfos = workManager
            .getWorkInfosForUniqueWork(MOBILE_LIGHTWEIGHT_WORK_NAME)
            .get()

        assertEquals("Repeated app opens must keep one lightweight sync", 1, workInfos.size)
        assertTrue(
            "The lightweight sync should be queued or running",
            workInfos.single().state == WorkInfo.State.ENQUEUED ||
                workInfos.single().state == WorkInfo.State.RUNNING
        )
    }

    private companion object {
        const val MOBILE_LIGHTWEIGHT_WORK_NAME =
            "provider-sync-mobile-lightweight-check"
    }
}
