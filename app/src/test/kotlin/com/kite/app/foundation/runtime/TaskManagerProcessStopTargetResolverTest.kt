package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskManagerProcessStopTargetResolverTest {
    @Test
    fun `owner root resolves card owner id`() {
        val item = process(id = "root-CARD-card-1", ownerId = "card:card-1")

        assertEquals("card:card-1", TaskManagerProcessStopTargetResolver.ownerId(item))
    }

    @Test
    fun `ordinary child process never escalates to owner stop`() {
        val item = process(id = "process-52", ownerId = "card:card-1")

        assertNull(TaskManagerProcessStopTargetResolver.ownerId(item))
    }

    @Test
    fun `background and unattributed roots do not use proot owner stop`() {
        assertNull(
            TaskManagerProcessStopTargetResolver.ownerId(
                process(id = "root-BACKGROUND-runtime-1", ownerId = "runtime-1")
            )
        )
        assertNull(
            TaskManagerProcessStopTargetResolver.ownerId(
                process(id = "root-UNATTRIBUTED-77", ownerId = null)
            )
        )
    }

    private fun process(id: String, ownerId: String?): TaskManagerProcessItem = TaskManagerProcessItem(
        id = id,
        pid = 41,
        parentPid = 0,
        title = "proc",
        subtitle = "",
        sourceLabel = "",
        stateLabel = "运行中",
        rawState = "R",
        command = "proc",
        commandLine = "proc",
        runtimeOwnerId = ownerId
    )
}
