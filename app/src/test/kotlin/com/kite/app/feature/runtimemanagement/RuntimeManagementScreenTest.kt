package com.kite.app.feature.runtimemanagement

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.kite.app.R
import com.kite.app.application.runtimemanagement.RuntimeManagedOwnerKind
import com.kite.app.application.runtimemanagement.RuntimeManagedProcess
import com.kite.app.application.runtimemanagement.RuntimeManagementSnapshot
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog

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

        screen.openAllForTesting()
        assertEquals("all", screen.scopeKeyForTesting())
        assertTrue(screen.navigateUp())
        assertEquals("overview", screen.scopeKeyForTesting())
        assertFalse(screen.navigateUp())
    }

    @Test
    fun `process details use themed dialog with balanced safe and danger actions`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = ContextThemeWrapper(activity, R.style.Theme_Kite)
        var submitted: RuntimeManagementActionUiState? = null
        val screen = RuntimeManagementScreen(
            context = context,
            initialScrollY = 0,
            onBack = {},
            onRefresh = {},
            onAction = { submitted = it },
        )
        activity.setContentView(screen.root)
        val state = RuntimeManagementProjector.project(
            RuntimeManagementSnapshot(
                processes = listOf(
                    RuntimeManagedProcess(
                        id = "process-52",
                        pid = 52,
                        title = "child",
                        stateLabel = "运行中",
                        ownerKind = RuntimeManagedOwnerKind.Unattributed,
                        canEndDirectly = true,
                    )
                )
            )
        )
        screen.render(state)
        screen.openAllForTesting()

        assertTrue(screen.processRootForTesting("process-52")?.performClick() == true)
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        assertEquals(BottomSheetDialog::class.java, dialog.javaClass)
        assertTrue(dialog.isShowing)
        val textViews = dialog.window?.decorView?.textViews().orEmpty()
        assertTrue(textViews.any { it.text.toString() == "child" })
        assertTrue(textViews.any { it.text.toString() == "未关联卡片" })
        val labels = textViews.map { it.text.toString() }
        val closeLabel = context.getString(R.string.runtime_management_dialog_close)
        val dangerLabel = state.unassignedProcessGroups.single().processes.single().stopAction?.label
            ?: error("测试进程应允许结束")
        val close = textViews.firstOrNull { it.text.toString() == closeLabel }
            ?: error("详情弹层缺少关闭动作：$labels")
        val danger = textViews.firstOrNull { it.text.toString() == dangerLabel }
            ?: error("详情弹层缺少危险动作：$labels")
        assertEquals(1f, (close.layoutParams as LinearLayout.LayoutParams).weight)
        assertEquals(1f, (danger.layoutParams as LinearLayout.LayoutParams).weight)

        danger.performClick()

        assertNull(submitted)
        assertFalse(dialog.isShowing)
        val confirmation = ShadowDialog.getLatestDialog()
        assertTrue(confirmation.isShowing)
        confirmation.window?.decorView?.textViews().orEmpty()
            .first { it.text.toString() == dangerLabel }
            .performClick()
        assertNotNull(submitted)
        assertFalse(confirmation.isShowing)
    }

    @Test
    fun `process overflow uses themed bottom action sheet instead of platform popup`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = ContextThemeWrapper(activity, R.style.Theme_Kite)
        val screen = RuntimeManagementScreen(
            context = context,
            initialScrollY = 0,
            onBack = {},
            onRefresh = {},
            onAction = {},
        )
        activity.setContentView(screen.root)
        val state = RuntimeManagementProjector.project(
            RuntimeManagementSnapshot(
                processes = listOf(
                    RuntimeManagedProcess(
                        id = "process-menu",
                        pid = 73,
                        title = "bash",
                        stateLabel = "运行中",
                        ownerKind = RuntimeManagedOwnerKind.Unattributed,
                        canEndDirectly = true,
                    ),
                ),
            ),
        )
        screen.render(state)
        screen.openAllForTesting()

        screen.openProcessMenuForTesting("process-menu")

        val sheet = ShadowDialog.getLatestDialog()
        assertEquals(BottomSheetDialog::class.java, sheet.javaClass)
        val labels = sheet.window?.decorView?.textViews().orEmpty().map { it.text.toString() }
        assertTrue(labels.contains("bash"))
        assertTrue(labels.contains(context.getString(R.string.runtime_management_action_details)))
        assertTrue(labels.contains(context.getString(R.string.runtime_management_action_copy_info)))
        val dangerLabel = state.unassignedProcessGroups.single().processes.single().stopAction?.label
            ?: error("测试进程应允许结束")
        assertTrue("操作弹层缺少危险动作：$labels", labels.contains(dangerLabel))
    }

    @Test
    fun `single member scope renders one process row while multi member scope starts collapsed`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = ContextThemeWrapper(activity, R.style.Theme_Kite)
        val screen = RuntimeManagementScreen(
            context = context,
            initialScrollY = 0,
            onBack = {},
            onRefresh = {},
            onAction = {},
        )
        activity.setContentView(screen.root)
        val single = RuntimeManagementProjector.project(
            RuntimeManagementSnapshot(
                processes = listOf(
                    RuntimeManagedProcess(
                        id = "single",
                        pid = 71,
                        title = "python",
                        stateLabel = "运行中",
                        workloadScopeId = "workload:single",
                        canEndDirectly = true,
                    )
                )
            )
        )
        screen.render(single)
        screen.openAllForTesting()

        var labels = screen.root.textViews().map { it.text.toString() }
        assertEquals(1, labels.count { it == "python" })
        assertFalse(labels.contains(context.getString(R.string.runtime_management_group_process_count, 1)))

        val multiple = RuntimeManagementProjector.project(
            RuntimeManagementSnapshot(
                processes = listOf(
                    RuntimeManagedProcess(
                        id = "parent",
                        pid = 81,
                        title = "python",
                        stateLabel = "运行中",
                        workloadScopeId = "workload:multiple",
                    ),
                    RuntimeManagedProcess(
                        id = "child",
                        pid = 82,
                        parentPid = 81,
                        title = "worker",
                        stateLabel = "运行中",
                        workloadScopeId = "workload:multiple",
                    ),
                )
            )
        )
        screen.render(multiple)

        labels = screen.root.textViews().map { it.text.toString() }
        assertTrue(labels.contains(context.getString(R.string.runtime_management_group_process_count, 2)))
        assertFalse(labels.contains("worker"))
        assertNull(screen.processRootForTesting("child"))

        val groupKey = multiple.allProcessGroups.single().key
        assertTrue(screen.processGroupHeaderForTesting(groupKey)?.performClick() == true)

        labels = screen.root.textViews().map { it.text.toString() }
        assertTrue(labels.contains("worker"))
        assertNotNull(screen.processRootForTesting("child"))
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

    private fun View.textViews(): List<TextView> = buildList {
        if (this@textViews is TextView) add(this@textViews)
        if (this@textViews is ViewGroup) {
            repeat(childCount) { addAll(getChildAt(it).textViews()) }
        }
    }
}
