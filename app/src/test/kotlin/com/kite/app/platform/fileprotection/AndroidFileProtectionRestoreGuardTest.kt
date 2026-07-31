package com.kite.app.platform.fileprotection

import com.kite.app.application.fileprotection.FileProtectionBackendId
import com.kite.app.application.fileprotection.FileProtectionPhase
import com.kite.app.foundation.runtime.ProotFileProtectionRuntime
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidFileProtectionRestoreGuardTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `只结束带有同一操作身份的进程并忽略当前进程`() {
        val proc = temporaryFolder.newFolder("proc")
        writeEnvironment(proc, 101, OPERATION_ID)
        writeEnvironment(proc, 102, OPERATION_ID)
        writeEnvironment(proc, 103, "another-operation")
        val signals = mutableListOf<Int>()
        val guard = AndroidFileProtectionRestoreGuard(
            procRoot = proc,
            currentPid = { 101 },
            sendSignal = { pid, _ ->
                signals += pid
                File(File(proc, pid.toString()), "environ").delete()
            },
            sleep = {},
            gracefulWaitMs = 0L,
            killWaitMs = 0L
        )

        guard.quiesce(record()).getOrThrow()

        assertEquals(listOf(102), signals)
        assertTrue(File(File(proc, "103"), "environ").isFile)
    }

    @Test
    fun `温和退出无效时升级强制信号并在仍存活时拒绝恢复`() {
        val proc = temporaryFolder.newFolder("proc-stubborn")
        writeEnvironment(proc, 201, OPERATION_ID)
        val signals = mutableListOf<Int>()
        val guard = AndroidFileProtectionRestoreGuard(
            procRoot = proc,
            currentPid = { 999 },
            sendSignal = { pid, signal -> signals += pid * 100 + signal },
            sleep = {},
            gracefulWaitMs = 0L,
            killWaitMs = 0L
        )

        val result = guard.quiesce(record())

        assertTrue(result.isFailure)
        assertTrue(signals.size == 2)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("201"))
    }

    private fun writeEnvironment(proc: File, pid: Int, operationId: String) {
        val directory = File(proc, pid.toString()).also(File::mkdirs)
        File(directory, "environ").writeBytes(
            "HOME=/root\u0000${ProotFileProtectionRuntime.OPERATION_ENV}=$operationId\u0000".toByteArray()
        )
    }

    private fun record() = FileProtectionRecord(
        operationId = OPERATION_ID,
        ownerId = "fixture",
        operationKind = "fixture_change",
        rootHostPath = "/scope",
        journalHostPath = "/journal",
        backendId = FileProtectionBackendId.RangeUndo,
        phase = FileProtectionPhase.RollingBack,
        startedAt = 1L
    )

    companion object {
        private const val OPERATION_ID = "fixture-operation-1"
    }
}
