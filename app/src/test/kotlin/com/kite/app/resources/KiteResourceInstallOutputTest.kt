package com.kite.app.resources

import org.junit.Assert.assertEquals
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
    fun retryAndHeartbeatBecomeReadableProgress() {
        assertEquals(
            "网络出现波动，正在重试（第 2 次，退出码 56）",
            KiteResourceInstallOutput.summary("KITE_RESOURCE_RETRY stage=acquire step=download attempt=2 exit=56")
        )
        assertEquals(
            "安装器仍在运行（已运行 15 秒）",
            KiteResourceInstallOutput.summary("KITE_RESOURCE_HEARTBEAT stage=install step=installer elapsed=15")
        )
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
}
