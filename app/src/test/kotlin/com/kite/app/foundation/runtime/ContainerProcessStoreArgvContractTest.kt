package com.kite.app.foundation.runtime

import android.system.OsConstants
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerProcessStoreArgvContractTest {
    @Test
    fun `进程表查询进入有界只读 probe 而信号命令不进入`() {
        val plan = ContainerProcessStore.containerProcessListPlanForTests("process-list-1")

        assertEquals("process-list-1", plan.admission.jobId)
        assertEquals("system:container-process-store", plan.admission.ownerId)
        assertEquals(RuntimeLaneKind.PROBE, plan.admission.lane)
        assertEquals(ProotJobAccess.READ_ONLY, plan.admission.access)
        assertEquals(ProotJobCancellationMode.TIMEOUT_AND_OWNER, plan.admission.cancellationMode)
        assertEquals(ProotJobResultMode.CAPTURED_STDIO, plan.admission.resultMode)
        assertEquals(
            listOf("/usr/bin/ps", "-eo", "pid=,ppid=,pgid=,sid=,stat=,comm=,args="),
            plan.job.argv,
        )
        assertEquals(12_000L, plan.job.timeoutMs)
        assertEquals(1024 * 1024, plan.job.maxOutputBytesPerStream)
    }

    @Test
    fun `存活探测直接构造 kill argv`() {
        assertEquals(
            listOf("/usr/bin/kill", "-0", "4321"),
            ContainerProcessStore.buildContainerSignalArgv(pid = 4321, signal = 0),
        )
    }

    @Test
    fun `受管终止信号直接构造 kill argv`() {
        assertEquals(
            listOf("/usr/bin/kill", "-${OsConstants.SIGTERM}", "4321"),
            ContainerProcessStore.buildContainerSignalArgv(pid = 4321, signal = OsConstants.SIGTERM),
        )
        assertEquals(
            listOf("/usr/bin/kill", "-${OsConstants.SIGKILL}", "4321"),
            ContainerProcessStore.buildContainerSignalArgv(pid = 4321, signal = OsConstants.SIGKILL),
        )
    }

    @Test
    fun `无效 pid 和信号 fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContainerProcessStore.buildContainerSignalArgv(pid = 1, signal = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContainerProcessStore.buildContainerSignalArgv(pid = 4321, signal = 2)
        }
    }

    @Test
    fun `生产探测入口不再构造 shell kill`() {
        val source = listOf(
            File("src/main/kotlin/com/kite/app/foundation/runtime/ContainerProcessStore.kt"),
            File("app/src/main/kotlin/com/kite/app/foundation/runtime/ContainerProcessStore.kt"),
        ).first(File::exists).readText()

        assertTrue(source.contains("argv = buildContainerSignalArgv(pid, 0)"))
        assertTrue(source.contains("argv = buildContainerSignalArgv(pid, signal)"))
        assertFalse(source.contains("payload = \"kill -0"))
        assertFalse(source.contains("payload = \"kill -${'$'}signal"))
    }
}
