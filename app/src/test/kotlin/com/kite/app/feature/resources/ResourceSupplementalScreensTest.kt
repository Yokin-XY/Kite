package com.kite.app.feature.resources

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.kite.app.R
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.application.resources.ResourceFeatureDescriptor
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceUiProjection
import com.kite.app.run.CardRunHistoryEntry
import com.kite.app.run.CardRunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResourceSupplementalScreensTest {
    @Test
    fun `more screen keeps resource and history actions as data callbacks`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val opened = mutableListOf<String>()
        val maintenance = mutableListOf<KiteResourceActionIntent>()
        val screen = ResourceMoreScreen(activity, {}, {}, maintenance::add, opened::add)
        activity.setContentView(screen.root)
        val context = screen.root.context
        val history = CardRunHistoryEntry(
            historyId = "history",
            recipeId = "install",
            recipeName = "Install",
            instanceId = "instance",
            status = CardRunStatus.Completed,
            startedAt = 1L,
            endedAt = 2L
        )

        screen.render(item(maintenance = managedMaintenance()), listOf(history))
        val uninstallButton = screen.root.views().filterIsInstance<TextView>().first {
            it.text.toString() == context.getString(R.string.resource_action_uninstall)
        }
        uninstallButton.performClick()
        val historyRow = screen.root.views().first { view ->
            view.isClickable && view.texts().any {
                it.contains(context.getString(R.string.runtime_management_status_completed))
            }
        }
        historyRow.performClick()

        assertEquals(listOf("history"), opened)
        assertEquals(listOf(KiteResourceActionIntent.Uninstall), maintenance)
        assertTrue(screen.root.texts().contains(context.getString(R.string.resource_manage_title)))
        assertTrue(screen.root.texts().contains(context.getString(R.string.resource_more_create_card)))
        assertFalse(screen.root.texts().contains("修复"))
        // 更新入口已收敛到资源管理页：详情维护区不再出现检查更新/更新按钮。
        assertFalse(screen.root.views().filterIsInstance<TextView>().any {
            it.text.toString() == context.getString(R.string.resource_action_check_update) ||
                it.text.toString() == context.getString(R.string.resource_action_update)
        })

        screen.render(
            item(maintenance = managedMaintenance(
                updateStatus = com.kite.app.resources.KiteResourceInstallStore.UPDATE_STATUS_AVAILABLE,
                updateEnabled = true,
                latestVersion = "2.0.0"
            )),
            listOf(history)
        )
        // 可更新只作为状态文案呈现，不提供详情页更新按钮。
        assertTrue(screen.root.texts().contains(
            context.getString(R.string.resource_maintenance_available, "1.0.0", "2.0.0")
        ))

        screen.acknowledge(KiteResourceActionIntent.Uninstall)
        screen.render(
            item(
                phase = ResourceItemPhase.Installing,
                operation = KiteResourceInstallRecipes.OP_UPDATE,
                maintenance = managedMaintenance(
                    updateStatus = com.kite.app.resources.KiteResourceInstallStore.UPDATE_STATUS_AVAILABLE,
                    latestVersion = "2.0.0"
                )
            ),
            listOf(history)
        )
        assertTrue(screen.root.texts().contains(context.getString(R.string.resource_maintenance_updating))
            || screen.root.texts().contains(context.getString(R.string.resource_state_updating)))
    }

    @Test
    fun `raw json screen replaces loading with descriptor snapshot`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val screen = ResourceRawJsonScreen(activity) {}
        activity.setContentView(screen.root)
        val context = screen.root.context

        screen.render(null)
        assertTrue(screen.root.texts().contains(context.getString(R.string.resource_raw_json_loading)))
        screen.render(item())
        assertTrue(screen.root.texts().any { it.contains("\"id\": \"tool\"") })
    }

    private fun item(
        phase: ResourceItemPhase = ResourceItemPhase.Installed,
        operation: String = "",
        maintenance: ResourceMaintenanceUiState = ResourceMaintenanceUiState()
    ) = ResourceItemUiState(
        descriptor = ResourceFeatureDescriptor("tool", "Tool"),
        phase = phase,
        projection = KiteResourceUiProjection("已获取", "打开", true, null),
        primaryIntent = KiteResourceActionIntent.Open,
        secondaryIntent = null,
        operation = operation,
        maintenance = maintenance
    )

    private fun managedMaintenance(
        updateStatus: String = "",
        updateEnabled: Boolean = false,
        latestVersion: String = ""
    ) = ResourceMaintenanceUiState(
        userLifecycleEnabled = true,
        installedVersion = "1.0.0",
        latestVersion = latestVersion,
        updateStatus = updateStatus,
        checkUpdateEnabled = !updateEnabled,
        updateEnabled = updateEnabled,
        uninstallEnabled = true
    )

    private fun View.views(): List<View> = buildList {
        add(this@views)
        if (this@views is ViewGroup) repeat(childCount) { addAll(getChildAt(it).views()) }
    }

    private fun View.texts(): List<String> = buildList {
        if (this@texts is TextView) add(text.toString())
        if (this@texts is ViewGroup) repeat(childCount) { addAll(getChildAt(it).texts()) }
    }
}
