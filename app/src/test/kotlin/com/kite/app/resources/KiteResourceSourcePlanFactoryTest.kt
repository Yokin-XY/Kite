package com.kite.app.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteResourceSourcePlanFactoryTest {
    private val loader by lazy {
        KiteResourceManifestLoader(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun `NPM 声明生成同一套安装更新卸载和版本检查计划`() {
        val manifest = parse(
            source = """{"type":"npm","package":"@openai/codex","companionPackages":["@agentclientprotocol/codex-acp"],"registries":["https://registry.npmmirror.com","https://registry.npmjs.org"],"installArguments":["--foreground-scripts"],"tag":"latest"}""",
            management = managed(listOf("codex", "codex-acp"), "codex --version")
        )

        val install = KiteResourceSourcePlanFactory.plan(manifest)
        val update = KiteResourceSourcePlanFactory.plan(manifest, targetVersion = "1.2.3")
        val latest = install.versionCheck.latest as KiteResourceRemoteVersionProbe

        assertTrue(install.generatedFromSource)
        assertEquals(
            listOf("@openai/codex@latest", "@agentclientprotocol/codex-acp@latest"),
            install.installActions.single().installSteps.single().packages
        )
        assertEquals(
            listOf("@openai/codex@1.2.3", "@agentclientprotocol/codex-acp@latest"),
            update.installActions.single().installSteps.single().packages
        )
        assertEquals(listOf("--foreground-scripts"), install.installActions.single().installSteps.single().arguments)
        assertEquals(
            listOf("https://registry.npmmirror.com", "https://registry.npmjs.org"),
            install.installActions.single().installSteps.single().registries,
        )
        assertEquals(listOf("codex", "codex-acp"), install.installActions.single().managedCommands)
        assertEquals(
            listOf("@openai/codex", "@agentclientprotocol/codex-acp"),
            install.uninstallActions.single().npmUninstallPackages
        )
        assertTrue(install.installActions.single().verifications.any { it.id == "installed-version" })
        assertTrue(install.installActions.single().verifications.any { it.id == "command-codex-acp" })
        assertTrue(install.versionCheck.latest?.url.orEmpty().contains("%40openai%2Fcodex/latest"))
        assertEquals("version", install.versionCheck.latest?.jsonField)
        assertEquals(latest, install.versionCheck.latest)
        assertTrue(install.capabilities.update)
    }

    @Test
    fun `默认 NPM 已安装版本同时保留结构化元数据与原命令回退`() {
        val generated = parse(
            source = """{"type":"npm","package":"@scope/example"}""",
            management = """{"mode":"managed_extension","managedCommands":["example"]}""",
        )
        val explicit = parse(
            source = """{"type":"npm","package":"@scope/example"}""",
            management = managed("example", "example --version"),
        )

        val generatedProbe = KiteResourceSourcePlanFactory.versionCheckPlan(generated).installed
        val metadata = checkNotNull(generatedProbe?.structuredMetadata)

        assertEquals(
            "/workspace/.kf/software/kite.example/npm-global/lib/node_modules/@scope/example/package.json",
            metadata.containerPath,
        )
        assertEquals("version", metadata.jsonField)
        assertEquals(256L * 1024L, metadata.maximumBytes)
        assertTrue(generatedProbe.command.contains("node -p"))
        assertEquals(null, KiteResourceSourcePlanFactory.versionCheckPlan(explicit).installed?.structuredMetadata)
    }

    @Test
    fun `正式资源至少两个复用同一结构化元数据合同`() {
        val resourceDirectory = sequenceOf(File("../assets/resources"), File("assets/resources"))
            .first(File::isDirectory)
        val probes = resourceDirectory.listFiles().orEmpty()
            .map { File(it, "manifest.json") }
            .filter(File::isFile)
            .map { loader.parseManifestJson(it.readText()) }
            .filter { manifest ->
                manifest.source.type == "npm" && manifest.management.versionProbe == null
            }
            .mapNotNull { manifest ->
                KiteResourceSourcePlanFactory.versionCheckPlan(manifest).installed?.structuredMetadata
            }

        assertTrue("至少两个正式资源必须复用结构化元数据合同", probes.size >= 2)
        assertTrue(probes.all { it.jsonField == "version" && it.containerPath.endsWith("/package.json") })
    }

    @Test
    fun `NPM 辅助包非法时不能静默降级成只安装主包`() {
        val manifest = parse(
            source = """{"type":"npm","package":"example","companionPackages":["bad package; echo unsafe"]}""",
            management = managed(listOf("example", "example-acp"), "example --version")
        )

        val plan = KiteResourceSourcePlanFactory.plan(manifest)

        assertTrue(plan.installActions.isEmpty())
        assertTrue(plan.uninstallActions.isEmpty())
        assertFalse(plan.capabilities.install)
    }

    @Test
    fun `GitHub Release 声明生成架构选择下载解压和目标版本地址`() {
        val manifest = parse(
            source = """
                {
                  "type":"github_release",
                  "repository":"https://github.com/anomalyco/opencode",
                  "releaseTagTemplate":"v{version}",
                  "assetPattern":"opencode-{target}.tar.gz",
                  "archiveType":"tar.gz",
                  "binaryPath":"opencode",
                  "architectures":{"aarch64|arm64":"linux-arm64","x86_64|amd64":"linux-x64"}
                }
            """.trimIndent(),
            management = managed("opencode", "opencode --version")
        )

        val plan = KiteResourceSourcePlanFactory.plan(manifest, targetVersion = "1.2.3")
        val action = plan.installActions.single()
        val script = KiteResourceInstallPlanCompiler.compile(action)
        val latest = plan.versionCheck.latest as KiteResourceRemoteVersionProbe

        assertTrue(plan.generatedFromSource)
        assertTrue(script.contains("aarch64|arm64) target='linux-arm64'"))
        assertTrue(script.contains("releases/download/v1.2.3/${'$'}asset_name"))
        assertTrue(script.contains("tar --touch -xzf"))
        assertTrue(script.contains("install -m 0755"))
        assertEquals("tag_name", latest.jsonField)
        assertEquals("github_release", latest.format)
        assertEquals(
            "https://github.com/anomalyco/opencode/releases/latest",
            latest.fallbackUrl
        )
        assertTrue(plan.capabilities.update)
    }

    @Test
    fun `GitHub Release 标签模板兼容已经带前缀的目标版本`() {
        val manifest = parse(
            source = """
                {
                  "type":"github_release",
                  "repository":"https://github.com/example/example",
                  "releaseTagTemplate":"v{version}",
                  "assetPattern":"example-{target}",
                  "archiveType":"binary",
                  "binaryPath":"example",
                  "architectures":{"aarch64":"linux-arm64"}
                }
            """.trimIndent(),
            management = managed("example", "example --version")
        )

        val script = KiteResourceInstallPlanCompiler.compile(
            KiteResourceSourcePlanFactory.plan(manifest, targetVersion = "v1.2.3").installActions.single()
        )

        assertTrue(script.contains("releases/download/v1.2.3/${'$'}asset_name"))
        assertFalse(script.contains("releases/download/vv1.2.3/"))
    }

    @Test
    fun `官方脚本可集中安装卸载但缺少远端版本事实时不开放更新`() {
        val manifest = parse(
            source = """{"type":"official_script","url":"https://example.com/install.sh"}""",
            management = managed("example", "example --version")
        )

        val plan = KiteResourceSourcePlanFactory.plan(manifest)
        val script = KiteResourceInstallPlanCompiler.compile(plan.installActions.single())

        assertTrue(script.contains("kite_resource_download"))
        assertTrue(script.contains("run-official-installer"))
        assertTrue(plan.capabilities.install)
        assertTrue(plan.capabilities.uninstall)
        assertFalse(plan.capabilities.checkUpdate)
        assertFalse(plan.capabilities.update)
    }

    @Test
    fun `官方脚本显式声明版本参数后复用同一来源更新`() {
        val manifest = parse(
            source = """
                {
                  "type":"official_script",
                  "url":"https://example.com/install.sh",
                  "latestUrl":"https://example.com/latest",
                  "latestFormat":"text",
                  "versionArguments":["--version","{version}"],
                  "environment":{"EXAMPLE_INSTALL_DIR":"${'$'}install_root","EXAMPLE_NO_PATH":"1"}
                }
            """.trimIndent(),
            management = managed("example", "example --version")
        )

        val plan = KiteResourceSourcePlanFactory.plan(manifest, "2.3.4")
        val script = KiteResourceInstallPlanCompiler.compile(plan.installActions.single())
        val latest = plan.versionCheck.latest as KiteResourceRemoteVersionProbe

        assertTrue(plan.capabilities.checkUpdate)
        assertTrue(plan.capabilities.update)
        assertEquals("text", latest.format)
        assertTrue(script.contains("EXAMPLE_INSTALL_DIR=\"${'$'}install_root\""))
        assertTrue(script.contains("\"--version\" \"2.3.4\""))
    }

    @Test
    fun `系统组件和显式动作分别保持只读与兼容边界`() {
        val system = parse(
            source = """{"type":"bundled","asset":"toolchain"}""",
            management = """{"mode":"system_component"}"""
        )
        val explicit = loader.parseManifestJson(
            """
                {
                  "schemaVersion":1,
                  "id":"kite.explicit",
                  "base":{"name":"Explicit","description":"","version":"1"},
                  "management":{"mode":"managed_extension","managedCommands":["explicit"]},
                  "source":{"type":"npm","package":"explicit"},
                  "actions":{"install":[{"type":"shell","cmd":"echo custom"}]}
                }
            """.trimIndent()
        )

        val systemPlan = KiteResourceSourcePlanFactory.plan(system)
        val explicitPlan = KiteResourceSourcePlanFactory.plan(explicit)

        assertFalse(systemPlan.capabilities.install)
        assertTrue(systemPlan.installActions.isEmpty())
        assertFalse(explicitPlan.generatedFromSource)
        assertEquals("echo custom", explicitPlan.installActions.single().cmd)
        assertFalse(explicitPlan.capabilities.update)
    }

    @Test
    fun `内置受管脚本只声明入口即可生成完整维护合同`() {
        val manifest = parse(
            source = """
                {
                  "type":"bundled",
                  "asset":"resources/kite.example/payload",
                  "profile":"managed_script_v1",
                  "interpreter":"python3",
                  "entry":"fixture.py"
                }
            """.trimIndent(),
            management = """{"mode":"managed_extension"}"""
        )

        val install = KiteResourceSourcePlanFactory.plan(manifest)
        val update = KiteResourceSourcePlanFactory.plan(manifest, targetVersion = "2.3.4")
        val installAction = install.installActions.single()
        val updateAction = update.installActions.single()
        val uninstallAction = install.uninstallActions.single()
        val installedProbe = install.versionCheck.installed
        val latestProbe = install.versionCheck.latest as KiteResourceCommandVersionProbe

        assertTrue(install.generatedFromSource)
        assertTrue(install.capabilities.install)
        assertTrue(install.capabilities.checkUpdate)
        assertTrue(install.capabilities.update)
        assertTrue(install.capabilities.uninstall)
        assertTrue(KiteResourceInstallPlanCompiler.compile(installAction).contains("fixture.py"))
        assertTrue(KiteResourceInstallPlanCompiler.compile(installAction).contains("install"))
        assertTrue(KiteResourceInstallPlanCompiler.compile(updateAction).contains("2.3.4"))
        assertTrue(KiteResourceInstallPlanCompiler.compile(updateAction).contains("update"))
        assertTrue(KiteResourceInstallPlanCompiler.compileVerification(updateAction).contains("verify"))
        assertTrue(KiteResourceInstallPlanCompiler.compileVerification(updateAction).contains("2.3.4"))
        assertTrue(uninstallAction.cmd.contains("uninstall"))
        assertTrue(installedProbe?.command.orEmpty().contains("current-version"))
        assertTrue(latestProbe.probe.command.contains("latest-version"))
    }

    private fun parse(source: String, management: String): KiteResourceManifest =
        loader.parseManifestJson(
            """
                {
                  "schemaVersion":1,
                  "id":"kite.example",
                  "base":{"name":"Example","description":"Example","version":"1"},
                  "management":$management,
                  "source":$source,
                  "actions":{}
                }
            """.trimIndent()
        )

    private fun managed(command: String, versionCommand: String): String =
        managed(listOf(command), versionCommand)

    private fun managed(commands: List<String>, versionCommand: String): String =
        """
            {
              "mode":"managed_extension",
              "managedCommands":[${commands.joinToString(",") { "\"$it\"" }}],
              "versionProbe":{"command":"$versionCommand"}
            }
        """.trimIndent()
}
