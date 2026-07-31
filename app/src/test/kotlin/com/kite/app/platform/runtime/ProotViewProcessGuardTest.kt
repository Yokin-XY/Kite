package com.kite.app.platform.runtime

import android.system.OsConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ProotViewProcessGuardTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `只终止精确绑定目标 View 的进程`() {
        val proc = temporaryFolder.newFolder("proc")
        process(proc, 100, "KF_PROOT_VIEW_ID=view-one")
        val target = process(proc, 200, "A=1", "KF_PROOT_VIEW_ID=view-one")
        process(proc, 300, "KF_PROOT_VIEW_ID=view-one-suffix")
        val signals = mutableListOf<Pair<Int, Int>>()
        val guard = ProotViewProcessGuard(
            procRoot = proc,
            currentPid = { 100 },
            sendSignal = { pid, signal ->
                signals += pid to signal
                if (pid == 200 && signal == OsConstants.SIGTERM) {
                    target.deleteRecursively()
                }
            },
            sleep = {},
            gracefulWaitMs = 10,
            killWaitMs = 10
        )

        guard.quiesce("view-one").getOrThrow()

        assertEquals(listOf(200 to OsConstants.SIGTERM), signals)
    }

    @Test
    fun `拒绝在强制信号后仍存活的 View 进程`() {
        val proc = temporaryFolder.newFolder("stubborn-proc")
        process(proc, 200, "KF_PROOT_VIEW_ID=view-stubborn")
        val signals = mutableListOf<Int>()
        val guard = ProotViewProcessGuard(
            procRoot = proc,
            currentPid = { 100 },
            sendSignal = { _, signal -> signals += signal },
            sleep = {},
            gracefulWaitMs = 0,
            killWaitMs = 0
        )

        val failure = guard.quiesce("view-stubborn").exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("200"))
        assertEquals(listOf(OsConstants.SIGTERM, OsConstants.SIGKILL), signals)
    }

    private fun process(proc: File, pid: Int, vararg environment: String): File =
        File(proc, pid.toString()).apply {
            mkdirs()
            File(this, "environ").writeBytes(
                (environment.joinToString("\u0000") + "\u0000").toByteArray()
            )
        }
}
