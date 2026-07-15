package com.kite.app.feature.runtimemanagement

import android.app.Activity
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.kite.app.R
import com.kite.app.application.runtimemanagement.RuntimeManagedOwnerKind
import com.kite.app.application.runtimemanagement.RuntimeManagedProcess
import com.kite.app.application.runtimemanagement.RuntimeManagementSnapshot
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RuntimeManagementScreenTest {
    @Test
    fun `same topology updates existing rows without rebuilding body`() {
        val screen = RuntimeManagementScreen(
            context = themedContext(),
            initialScrollY = 0,
            onBack = {},
            onRefresh = {},
            onAction = {}
        )
        Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(screen.root)
        val initial = projected()
        screen.render(initial)
        val runRoot = screen.runRootForTesting("run-1")
        val rebuilds = screen.bodyRebuildCountForTesting()

        screen.render(
            RuntimeManagementProjector.project(
                snapshot(),
                mutations = mapOf(
                    "process:process-52" to RuntimeManagementMutation(
                        key = "process:process-52",
                        phase = RuntimeManagementMutationPhase.AwaitingConfirmation
                    )
                )
            )
        )

        assertEquals(rebuilds, screen.bodyRebuildCountForTesting())
        assertSame(runRoot, screen.runRootForTesting("run-1"))
    }

    private fun projected() = RuntimeManagementProjector.project(snapshot())

    private fun snapshot() = RuntimeManagementSnapshot(
        runs = listOf(
            CardRunState(
                instanceId = "run-1",
                recipeId = "recipe-1",
                recipeName = "OpenClaw",
                status = CardRunStatus.Running,
                surface = CardRunSurface.Report,
                runtimeRootOwnerId = "card:run-1@10",
                runtimeOwnerId = "card:run-1@10/step/0-shell/attempt/1",
                runtimeUnitId = "card:run-1@10/step/0-shell/attempt/1",
                ownedRuntimeOwnerIds = listOf("card:run-1@10/step/0-shell/attempt/1"),
                rootPid = "41",
                lastMeaningfulOutput = "running",
                createdAt = 10L,
                updatedAt = 10L
            )
        ),
        processes = listOf(
            RuntimeManagedProcess(
                id = "process-52",
                pid = 52,
                parentPid = 41,
                title = "child",
                stateLabel = "运行中",
                ownerKind = RuntimeManagedOwnerKind.Card,
                ownerId = "card:run-1@10/step/0-shell/attempt/1",
                canEndDirectly = true
            )
        )
    )

    private fun themedContext(): ContextThemeWrapper = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Kite
    )
}
