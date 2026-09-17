package com.kite.app.resources

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * official_command 源：安装=官方命令原样执行，尾部追加 versionProbe 首行作为
 * KITE_RESOURCE_INSTALLED_VERSION 信号；卸载=官方卸载命令。小房间机器不参与。
 */
@RunWith(RobolectricTestRunner::class)
class KiteOfficialCommandPlanTest {
    @Test
    fun officialCommandGeneratesShellActionWithVersionSignal() {
        val workingDirectory = File(System.getProperty("user.dir").orEmpty())
        val repoRoot = sequenceOf(workingDirectory, workingDirectory.parentFile)
            .filterNotNull()
            .firstOrNull { File(it, "assets/resources/kite.codex.cli/manifest.json").isFile }
            ?: error("找不到仓库根目录：${workingDirectory.absolutePath}")
        val loader = KiteResourceManifestLoader(isDebugBuild = true, definitionSources = emptyList())
        val manifest = loader.parseManifestJson(
            File(repoRoot, "assets/resources/kite.codex.cli/manifest.json").readText()
        )

        val plan = KiteResourceSourcePlanFactory.plan(manifest)
        assertTrue("应生成安装动作", plan.installActions.isNotEmpty())
        assertTrue("应支持更新（重跑官方命令）", plan.capabilities.update)
        assertTrue("应支持卸载", plan.capabilities.uninstall)

        val install = plan.installActions.single()
        assertEquals("shell", install.type)
        assertTrue("必须原样包含官方安装命令", install.cmd.contains("npm install -g @openai/codex@latest"))
        assertTrue(
            "必须输出安装版本信号供记账",
            install.cmd.contains("KITE_RESOURCE_INSTALLED_VERSION") && install.cmd.contains("codex --version"),
        )
        assertTrue(
            "版本信号必须容忍探测失败（不阻断安装结果）",
            install.cmd.contains("if [ -n ") || install.cmd.contains("[ -n "),
        )

        val uninstall = plan.uninstallActions.single()
        assertEquals("shell", uninstall.type)
        assertTrue("卸载必须是官方命令", uninstall.cmd.contains("npm uninstall -g @openai/codex"))

        val script = KiteResourceInstallPlanCompiler.compile(install)
        val lines = script.lineSequence().toList()
        assertTrue("编译产物不应引用小房间 install_root", "\$install_root" !in script)
        assertTrue(lines.any { it.trim() == "npm install -g @openai/codex@latest" })
    }
}
