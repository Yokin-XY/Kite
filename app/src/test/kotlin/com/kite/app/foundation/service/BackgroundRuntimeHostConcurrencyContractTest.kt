package com.kite.app.foundation.service

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRuntimeHostConcurrencyContractTest {
    @Test
    fun `all runtime modes acquire single flight before prerequisites`() {
        val source = hostSource()
        val body = source.substring(
            source.indexOf("private suspend fun startRuntimeInternal"),
            source.indexOf("private suspend fun stopRuntimeInternal"),
        )

        val acquireAt = body.indexOf("startSingleFlight.tryAcquire(runtimeId)")
        val prerequisitesAt = body.indexOf("ensureRuntimePrerequisites(appContext, record)")
        assertTrue(acquireAt >= 0)
        assertTrue(prerequisitesAt > acquireAt)
        assertTrue(body.contains("BackgroundRuntimeMode.PROCESS -> startProcessRuntime"))
        assertTrue(body.contains("BackgroundRuntimeMode.SERVICE -> startServiceRuntime"))
    }

    @Test
    fun `process monitor starts only after handle and running state publication`() {
        val source = hostSource()
        val body = source.substring(
            source.indexOf("private fun startProcessRuntime"),
            source.indexOf("private fun stopProcessRuntime"),
        )

        val lazyMonitorAt = body.indexOf("hostScope.launch(start = CoroutineStart.LAZY)")
        val handleAt = body.indexOf("handles[record.id] = RuntimeHandle")
        val monitorStartAt = body.indexOf("monitorJob.start()")
        val runningStatusAt = body.lastIndexOf("status = BackgroundRuntimeStatus.RUNNING", monitorStartAt)
        assertTrue(lazyMonitorAt >= 0)
        assertTrue(handleAt > lazyMonitorAt)
        assertTrue(runningStatusAt > handleAt)
        assertTrue(monitorStartAt > runningStatusAt)
    }

    @Test
    fun `timed out one shot is terminated before reader join and output is bounded`() {
        val source = hostSource()
        val body = source.substring(
            source.indexOf("private fun executeOneShotCommand"),
            source.indexOf("private fun buildRuntimeLogHeader"),
        )

        val destroyAt = body.indexOf("process.destroy()")
        val joinAt = body.indexOf("readerThread.join(2000L)")
        assertTrue(body.contains("BoundedProcessOutput(ONE_SHOT_OUTPUT_MAX_CHARS)"))
        assertTrue(destroyAt >= 0)
        assertTrue(joinAt > destroyAt)
    }

    private fun hostSource(): String = File(
        repositoryRoot(),
        "app/src/main/kotlin/com/kite/app/foundation/service/BackgroundRuntimeHost.kt",
    ).readText()

    private fun repositoryRoot(): File {
        val workingDirectory = File(System.getProperty("user.dir").orEmpty())
        return sequenceOf(workingDirectory, workingDirectory.parentFile)
            .filterNotNull()
            .firstOrNull {
                File(
                    it,
                    "app/src/main/kotlin/com/kite/app/foundation/service/BackgroundRuntimeHost.kt",
                ).isFile
            }
            ?: error("找不到 Kite 仓库根目录，当前目录：${workingDirectory.absolutePath}")
    }
}
