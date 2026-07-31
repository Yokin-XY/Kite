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
