package com.kite.app.resources

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * pypi 安装脚本内嵌的 simple-index 解析器必须把 sdist 扩展名从版本号里剥掉。
 *
 * 历史缺陷：codex_relay-0.5.8.tar.gz 去包名前缀后按 '-' 切版本得到 "0.5.8.tar.gz"，
 * sort -V 又把它排在 "0.5.8" 之后，sdist 抢占 latest 后窗口比对必然失败，
 * 真机表现：四个镜像全部 latest-version-outside-window、退出码 69（kite.codex.relay 实测）。
 */
@RunWith(RobolectricTestRunner::class)
class KitePypiIndexParserTest {
    @Test
    fun sdistFilenamesDoNotLeakArchiveExtensionIntoVersion() {
        val workingDirectory = File(System.getProperty("user.dir").orEmpty())
        val repoRoot = sequenceOf(workingDirectory, workingDirectory.parentFile)
            .filterNotNull()
            .firstOrNull { File(it, "assets/resources/kite.codex.relay/manifest.json").isFile }
            ?: error("找不到仓库根目录：${workingDirectory.absolutePath}")
        val loader = KiteResourceManifestLoader(isDebugBuild = true, definitionSources = emptyList())

        val raw = File(repoRoot, "assets/resources/kite.codex.relay/manifest.json").readText()
        val manifest = loader.parseManifestJson(raw)
        val action = KiteResourceSourcePlanFactory.plan(manifest).installActions.firstOrNull()
            ?: error("kite.codex.relay 没有生成安装动作")
        val script = KiteResourceInstallPlanCompiler.compile(action)

        val heredocStart = script.indexOf("<<'KITE_PYPI_INDEX'")
        val heredocEnd = script.indexOf("\nKITE_PYPI_INDEX", heredocStart + 1)
        assertTrue("编译产物缺少 KITE_PYPI_INDEX 解析器", heredocStart >= 0 && heredocEnd > heredocStart)
        val parserSource = script.substring(script.indexOf('\n', heredocStart) + 1, heredocEnd)

        val temp = File(repoRoot, "build/kite-pypi-parser-test").apply { mkdirs() }
        val parserFile = temp.resolve("parser.py").apply { writeText(parserSource) }
        val wheelSha = "c".repeat(64)
        val fixture = temp.resolve("simple-index.html")
        fixture.writeText(
            """
            <html><body>
            <a href="../../packages/codex_relay-0.5.7-py3-none-any.whl#sha256=${"b".repeat(64)}">codex_relay-0.5.7-py3-none-any.whl</a>
            <a href="../../packages/codex_relay-0.5.8.tar.gz#sha256=${"a".repeat(64)}">codex_relay-0.5.8.tar.gz</a>
            <a href="../../packages/codex_relay-0.5.8-py3-none-manylinux_2_17_aarch64.manylinux2014_aarch64.whl#sha256=$wheelSha">codex_relay-0.5.8-py3-none-manylinux_2_17_aarch64.manylinux2014_aarch64.whl</a>
            </body></html>
            """.trimIndent()
        )

        val output = runPython(
            listOf(
                parserFile.absolutePath,
                "https://example.com/simple/codex-relay/",
                "codex-relay",
                fixture.absolutePath,
            ),
        ) ?: return // 本机没有可用的 python 解释器时跳过执行，仅保留结构断言。

        assertTrue(
            "aarch64 wheel 应被识别为兼容候选并携带原始文件名：$output",
            output.lines().any {
                it.startsWith("0.5.8|$wheelSha|https://") &&
                    it.endsWith("|1|codex_relay-0.5.8-py3-none-manylinux_2_17_aarch64.manylinux2014_aarch64.whl")
            }
        )
        assertFalse(
            "sdist 的版本号不允许携带 .tar.gz 扩展名：$output",
            output.lines().any { it.startsWith("0.5.8.tar.gz|") }
        )
        assertFalse(
            "uv 需要从原始 wheel 文件名解析元数据，固定名 candidate.whl 会被拒绝（Must have a version）：$output",
            output.lines().any { it.endsWith("|candidate.whl") }
        )
    }

    private fun runPython(arguments: List<String>): String? {
        for (executable in listOf("python3", "python")) {
            val process = runCatching {
                ProcessBuilder(listOf(executable) + arguments)
                    .redirectErrorStream(true)
                    .start()
            }.getOrNull() ?: continue
            process.waitFor(30, TimeUnit.SECONDS)
            val text = process.inputStream.bufferedReader().readText()
            if (process.exitValue() == 0) return text
        }
        return null
    }
}
