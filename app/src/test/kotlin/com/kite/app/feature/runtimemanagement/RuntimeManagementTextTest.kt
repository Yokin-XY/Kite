package com.kite.app.feature.runtimemanagement

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.application.runtimemanagement.RuntimeManagedOwnerKind
import com.kite.app.application.runtimemanagement.RuntimeManagedProcess
import com.kite.app.application.runtimemanagement.RuntimeManagementSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class RuntimeManagementTextTest {
    @Test
    fun `English presentation localizes process ownership state and action`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = RuntimeManagementProjector.project(
            snapshot = RuntimeManagementSnapshot(
                processes = listOf(
                    RuntimeManagedProcess(
                        id = "process-42",
                        pid = 42,
                        title = "bash",
                        stateLabel = "运行中",
                        ownerKind = RuntimeManagedOwnerKind.Unattributed,
                        canEndDirectly = true,
                    )
                )
            ),
            text = RuntimeManagementText.from(context),
        )

        val section = state.unassignedProcessGroups.single()
        val process = section.processes.single()
        assertEquals("bash", section.title)
        assertEquals("Unlinked", process.ownerLabel)
        assertEquals("Running", process.subtitle)
        assertEquals("End process", process.stopAction?.label)
    }
}
