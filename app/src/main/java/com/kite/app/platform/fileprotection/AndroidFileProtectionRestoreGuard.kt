package com.kite.app.platform.fileprotection

import android.os.Process
import android.system.Os
import android.system.OsConstants
import com.kite.app.foundation.runtime.ProotFileProtectionRuntime
import java.io.File

/** 在恢复文件前收敛仍属于该操作的 PRoot/tracee，避免旧 FD 或 mmap 重新污染恢复结果。 */
internal class AndroidFileProtectionRestoreGuard(
    private val procRoot: File = File("/proc"),
    private val currentPid: () -> Int = Process::myPid,
    private val sendSignal: (Int, Int) -> Unit = Os::kill,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val gracefulWaitMs: Long = 1_000L,
    private val killWaitMs: Long = 500L
) : FileProtectionRestoreGuard {
    override fun quiesce(record: FileProtectionRecord): Result<Unit> = runCatching {
        var live = matchingPids(record.operationId)
        if (live.isEmpty()) return@runCatching
        live.forEach { pid -> runCatching { sendSignal(pid, OsConstants.SIGTERM) } }
        live = awaitGone(record.operationId, gracefulWaitMs)
        if (live.isNotEmpty()) {
            live.forEach { pid -> runCatching { sendSignal(pid, OsConstants.SIGKILL) } }
            live = awaitGone(record.operationId, killWaitMs)
        }
        if (live.isNotEmpty()) {
            error("file_protection_processes_still_running:${live.sorted().joinToString(",")}")
        }
    }

    private fun awaitGone(operationId: String, timeoutMs: Long): Set<Int> {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        var live = matchingPids(operationId)
        while (live.isNotEmpty() && System.nanoTime() < deadline) {
            sleep(POLL_MS)
            live = matchingPids(operationId)
        }
        return live
    }

    private fun matchingPids(operationId: String): Set<Int> {
        val expected = "${ProotFileProtectionRuntime.OPERATION_ENV}=$operationId"
        val self = currentPid()
        return procRoot.listFiles().orEmpty().asSequence()
            .mapNotNull { directory -> directory.name.toIntOrNull()?.let { it to directory } }
            .filter { (pid, _) -> pid != self }
            .filter { (_, directory) -> environmentContains(File(directory, "environ"), expected) }
            .mapTo(linkedSetOf()) { (pid, _) -> pid }
    }

    private fun environmentContains(file: File, expected: String): Boolean = runCatching {
        val fields = file.readBytes().toString(Charsets.UTF_8).split('\u0000')
        expected in fields
    }.getOrDefault(false)

    companion object {
        private const val POLL_MS = 50L
    }
}
