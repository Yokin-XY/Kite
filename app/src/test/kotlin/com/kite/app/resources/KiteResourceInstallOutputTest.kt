package com.kite.app.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteResourceInstallOutputTest {
    @Test
    fun failureSummaryKeepsStageAndExitCode() {
        val line = "KITE_RESOURCE_FAILURE stage=acquire step=download exit=56"

        assertTrue(KiteResourceInstallOutput.isFailure(line))
        assertEquals(
            "资源下载失败，网络或资源来源暂时不可用（退出码 56）",
            KiteResourceInstallOutput.summary(line)
        )
    }

    @Test
    fun retryBecomesReadableAndHeartbeatStaysInternal() {
        assertEquals(
            "网络出现波动，正在重试（第 2 次，退出码 56）",
            KiteResourceInstallOutput.summary("KITE_RESOURCE_RETRY stage=acquire step=download attempt=2 exit=56")
        )
        val heartbeat = "KITE_RESOURCE_HEARTBEAT stage=install step=installer elapsed=15"
        assertTrue(KiteResourceInstallOutput.isHeartbeat(heartbeat))
        assertNull(KiteResourceInstallOutput.summary(heartbeat))
        assertEquals("", KiteResourceInstallOutput.compactProgress(heartbeat))
    }

    @Test
    fun `实时报告提取源码百分比和安装组件来源`() {
        assertEquals(
            "源码写入 76%",
            KiteResourceInstallOutput.progressDetail("Updating files:  76% (7971/10488)"),
        )
        assertEquals(
            "正在从 mirrors.aliyun.com 下载第 17 个安装组件（3407 kB）",
            KiteResourceInstallOutput.progressDetail(
                "Get:17 https://mirrors.aliyun.com/ubuntu-ports noble/main arm64 systemd [3407 kB]"
            ),
        )
    }

    @Test
    fun `卡片进度移除终端样式并压缩空白`() {
        assertEquals(
            "Downloading codex-relay (3.1MiB)",
            KiteResourceInstallOutput.compactProgress(
                "\u001B[36m\u001B[1mDownloading\u001B[0m\u001B[39m  codex-relay\n(3.1MiB)"
            ),
        )
    }

    @Test
    fun `实时报告隐藏心跳并按终端重绘保留最新进度`() {
        val report = KiteResourceInstallOutput.userVisibleReport(
            "KITE_RESOURCE_STEP install hermes\n" +
                "Downloading uvloop 10%\rDownloading uvloop 80%\rDownloading uvloop 100%\n" +
                "KITE_RESOURCE_HEARTBEAT stage=install step=hermes elapsed=5\n" +
                "KITE_RESOURCE_HEARTBEAT stage=install step=hermes elapsed=10\n" +
                "Installed 63 packages\n"
        )

        assertEquals(
            "正在执行安装器\nDownloading uvloop 100%\nInstalled 63 packages",
            report,
        )
        assertFalse(report.contains("HEARTBEAT"))
        assertFalse(report.contains("10%"))
    }
}
