package com.kite.app.platform.runtime

import android.os.Process
import android.system.Os
import android.system.OsConstants
import com.kite.app.foundation.runtime.ProotViewBinding
import java.io.File

/** 在切换或废弃 View 前只收敛仍绑定该 viewId 的 PRoot/tracee。 */
internal class ProotViewProcessGuard(
    private val procRoot: File = File("/proc"),
    private val currentPid: () -> Int = Process::myPid,
    private val sendSignal: (Int, Int) -> Unit = Os::kill,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val gracefulWaitMs: Long = 1_000L,
    private val killWaitMs: Long = 500L
) {
    fun quiesce(viewId: String): Result<Unit> = runCatching {
        require(viewId.isNotBlank()) { "missing_view_id" }
        var live = matchingPids(viewId)
        if (live.isEmpty()) return@runCatching
        live.forEach { pid -> runCatching { sendSignal(pid, OsConstants.SIGTERM) } }
        live = awaitGone(viewId, gracefulWaitMs)
        if (live.isNotEmpty()) {
            live.forEach { pid -> runCatching { sendSignal(pid, OsConstants.SIGKILL) } }
            live = awaitGone(viewId, killWaitMs)
        }
        if (live.isNotEmpty()) {
            error("proot_view_processes_still_running:${live.sorted().joinToString(",")}")
        }
    }

    private fun awaitGone(viewId: String, timeoutMs: Long): Set<Int> {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        var live = matchingPids(viewId)
        while (live.isNotEmpty() && System.nanoTime() < deadline) {
            sleep(POLL_MS)
            live = matchingPids(viewId)
        }
        return live
    }

    private fun matchingPids(viewId: String): Set<Int> {
        val expected = "${ProotViewBinding.ENV_VIEW_ID}=$viewId"
        val self = currentPid()
        return procRoot.listFiles().orEmpty().asSequence()
            .mapNotNull { directory -> directory.name.toIntOrNull()?.let { it to directory } }
            .filter { (pid, _) -> pid != self }
            .filter { (_, directory) -> environmentContains(File(directory, "environ"), expected) }
            .mapTo(linkedSetOf()) { (pid, _) -> pid }
    }

    private fun environmentContains(file: File, expected: String): Boolean = runCatching {
        expected in file.readBytes().toString(Charsets.UTF_8).split('\u0000')
    }.getOrDefault(false)

    companion object {
        private const val POLL_MS = 50L
    }
}
