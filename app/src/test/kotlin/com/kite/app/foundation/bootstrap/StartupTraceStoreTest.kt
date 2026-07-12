package com.kite.app.foundation.bootstrap

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StartupTraceStoreTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        context.getSharedPreferences("kite_startup_trace", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    @Test
    fun applicationStageFailurePersistsStageAndRedactsSensitiveValues() {
        StartupTraceStore.prepareProcess(context)

        val completed = StartupTraceStore.runApplicationStage(context, "application.webview") {
            error("provider rejected code=secret-code&state=secret-state")
        }

        assertFalse(completed)
        val failure = requireNotNull(StartupTraceStore.readFailure(context))
        assertEquals("stage_failed", failure.status)
        assertEquals("application.webview", failure.stage)
        assertEquals("java.lang.IllegalStateException", failure.exceptionClass)
        assertTrue(failure.exceptionMessage.contains("code=<redacted>"))
        assertTrue(failure.exceptionMessage.contains("state=<redacted>"))
        assertFalse(failure.exceptionMessage.contains("secret-code"))
        assertFalse(failure.stackTrace.contains("secret-state"))
    }

    @Test
    fun nextProcessRecordsLastStageWithoutBlockingWhenPreviousLaunchWasInterrupted() {
        StartupTraceStore.prepareProcess(context)
        StartupTraceStore.markStage(context, "main.webview_create")

        StartupTraceStore.prepareProcess(context)

        assertFalse(StartupTraceStore.hasFailure(context))
        val prefs = context.getSharedPreferences("kite_startup_trace", Context.MODE_PRIVATE)
        assertEquals("main.webview_create", prefs.getString("last_incomplete_stage", null))
        assertTrue(prefs.getString("last_incomplete_timeline", "").orEmpty().contains("main.webview_create"))
    }

    @Test
    fun stageIsVisibleImmediatelyWhileDiskWriteRemainsAsynchronous() {
        StartupTraceStore.prepareProcess(context)

        StartupTraceStore.markStage(context, "main.resources_and_server")

        val prefs = context.getSharedPreferences("kite_startup_trace", Context.MODE_PRIVATE)
        assertEquals("main.resources_and_server", prefs.getString("current_stage", null))
        assertTrue(prefs.getString("current_timeline", "").orEmpty().contains("main.resources_and_server"))
    }

    @Test
    fun legacyIncompleteFailureIsClearedDuringUpgrade() {
        context.getSharedPreferences("kite_startup_trace", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("failure_pending", true)
            .putString("failure_status", "previous_process_incomplete")
            .putString("failure_stage", "application.notification_channels.completed")
            .commit()

        StartupTraceStore.prepareProcess(context)

        assertFalse(StartupTraceStore.hasFailure(context))
        assertEquals(null, StartupTraceStore.readFailure(context))
    }

    @Test
    fun readyLaunchIsNotReportedAsIncompleteOnNextProcess() {
        StartupTraceStore.prepareProcess(context)
        StartupTraceStore.markStage(context, "main.content_view")
        StartupTraceStore.markReady(context)

        StartupTraceStore.prepareProcess(context)

        assertFalse(StartupTraceStore.hasFailure(context))
    }

    @Test
    fun launcherResolvesToStartupGuardInsteadOfMainActivity() {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName)

        val resolved = context.packageManager.resolveActivity(intent, 0)

        assertNotNull(resolved)
        assertEquals(StartupGuardActivity::class.java.name, resolved?.activityInfo?.name)
    }
}
