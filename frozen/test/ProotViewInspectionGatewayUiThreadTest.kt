package com.kite.app.platform.runtimemanagement

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotViewInspectionGatewayUiThreadTest {
    @Test
    fun `工程页网关构造不得同步读取 View catalog`() {
        val source = locateSource().readText()

        assertTrue(source.contains("MutableStateFlow(ProotViewInspectionSnapshot())"))
        assertFalse(source.contains("MutableStateFlow(probe())"))
    }

    @Test
    fun `工程页在容器就绪后通过信号刷新 View 快照`() {
        val source = locateSettingsFragmentSource().readText()

        assertTrue(source.contains("snapshot.defaultContainerReady"))
        assertTrue(source.contains(".distinctUntilChanged()"))
        assertTrue(source.contains("prootViewInspectionGateway.refresh()"))
        assertFalse(source.contains("delay("))
    }

    private fun locateSource(): File {
        val workingDirectory = File(System.getProperty("user.dir").orEmpty())
        return sequenceOf(
            File(
                workingDirectory,
                "src/main/java/com/kite/app/platform/runtimemanagement/AndroidProotViewInspectionGateway.kt",
            ),
            File(
                workingDirectory,
                "app/src/main/java/com/kite/app/platform/runtimemanagement/AndroidProotViewInspectionGateway.kt",
            ),
        ).firstOrNull(File::isFile)
            ?: error("找不到 AndroidProotViewInspectionGateway.kt，当前目录：${workingDirectory.absolutePath}")
    }

    private fun locateSettingsFragmentSource(): File {
        val workingDirectory = File(System.getProperty("user.dir").orEmpty())
        return sequenceOf(
            File(
                workingDirectory,
                "src/main/java/com/kite/app/feature/settings/SettingsCategoryFragment.kt",
            ),
            File(
                workingDirectory,
                "app/src/main/java/com/kite/app/feature/settings/SettingsCategoryFragment.kt",
            ),
        ).firstOrNull(File::isFile)
            ?: error("找不到 SettingsCategoryFragment.kt，当前目录：${workingDirectory.absolutePath}")
    }
}
