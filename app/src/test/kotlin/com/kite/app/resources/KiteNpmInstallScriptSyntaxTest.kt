package com.kite.app.resources

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 真实资源清单编译出的安装脚本必须能被 bash 解析。
 *
 * 历史缺陷：trimIndent 对含插值的最终字符串不可靠，插值块自身顶格时模板缩进不被剥掉，
 * here-document 结束标记带缩进而被 bash 拒绝（真机表现：unexpected EOF while looking for matching ')'）。
 * 真实清单携带 versionProbe（verification 插值）才会触发，瘦夹具不会，因此必须用真实清单回归。
 */
@RunWith(RobolectricTestRunner::class)
class KiteNpmInstallScriptSyntaxTest {
    @Test
    fun realManifestScriptsKeepHeredocTerminatorsAtColumnZero() {
        val workingDirectory = File(System.getProperty("user.dir").orEmpty())
        val repoRoot = sequenceOf(workingDirectory, workingDirectory.parentFile)
            .filterNotNull()
            .firstOrNull { File(it, "assets/resources/kite.opencode/manifest.json").isFile }
            ?: error("找不到仓库根目录：${workingDirectory.absolutePath}")
        val loader = KiteResourceManifestLoader(isDebugBuild = true, definitionSources = emptyList())

        listOf("kite.hermes.core").forEach { resourceId ->
            val raw = File(repoRoot, "assets/resources/$resourceId/manifest.json").readText()
            val manifest = loader.parseManifestJson(raw)
            val plan = KiteResourceSourcePlanFactory.plan(manifest)
            val action = plan.installActions.firstOrNull()
                ?: error("$resourceId 没有生成安装动作")
            val script = KiteResourceInstallPlanCompiler.compile(action)

            val knownTerminators = Regex("(?m)^KITE_[A-Z0-9_]+[ \\t]*$")
            val indentedTerminator = Regex("(?m)^[ \\t]+KITE_[A-Z0-9_]+[ \\t]*$")
            assertTrue("$resourceId 未生成任何 heredoc 结束标记", knownTerminators.containsMatchIn(script))
            assertTrue(
                "$resourceId 存在带缩进的 heredoc 结束标记：${indentedTerminator.findAll(script).map { it.value }.toList()}",
                indentedTerminator.containsMatchIn(script).not()
            )
            if (System.getenv("KITE_REPRO_DUMP") != null) {
                File(repoRoot, "build/kite-repro").apply { mkdirs() }
                    .resolve("$resourceId.sh").writeText(script)
            }
        }
    }
}
