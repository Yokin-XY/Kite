package com.kite.app.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteResourceManagementProtocolTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private val loader by lazy { KiteResourceManifestLoader(context) }

    @Test
    fun `外部扩展解析结构化管理策略和 NPM 来源`() {
        val manifest = loader.parseManifestJson(
            manifestJson(
                management = """
                    {
                      "mode": "managed_extension",
                      "managedCommands": ["example"],
                      "versionProbe": {
                        "command": "example --version",
                        "pattern": "example ([0-9.]+)"
                      },
                      "latestVersionProbe": {
                        "command": "example latest-version",
                        "pattern": "([0-9.]+)"
                      },
                      "preservePaths": ["user-home/.config/example"]
                    }
                """.trimIndent(),
                source = """
                    {
                      "type": "npm",
                      "package": "@example/cli",
                      "channel": "stable",
                      "tag": "latest"
                    }
                """.trimIndent()
            )
        )

        assertEquals(KiteResourceManagementMode.MANAGED_EXTENSION, manifest.management.mode)
        assertTrue(manifest.management.userLifecycleEnabled)
        assertEquals(listOf("example"), manifest.management.managedCommands)
        assertEquals("example --version", manifest.management.versionProbe?.command)
        assertEquals("example ([0-9.]+)", manifest.management.versionProbe?.pattern)
        assertEquals("example latest-version", manifest.management.latestVersionProbe?.command)
        assertEquals(listOf("user-home/.config/example"), manifest.management.preservePaths)
        assertEquals("npm", manifest.source.type)
        assertEquals("@example/cli", manifest.source.packageName)
        assertEquals("stable", manifest.source.channel)
        assertEquals("latest", manifest.source.tag)
    }

    @Test
    fun `系统组件拒绝用户生命周期且保留来源事实`() {
        val manifest = loader.parseManifestJson(
            manifestJson(
                management = """{"mode":"system_component"}""",
                source = """{"type":"bundled","asset":"toolchain/ai-dev-pack"}"""
            )
        )

        assertEquals(KiteResourceManagementMode.SYSTEM_COMPONENT, manifest.management.mode)
        assertFalse(manifest.management.userLifecycleEnabled)
        assertEquals("bundled", manifest.source.type)
        assertEquals("toolchain/ai-dev-pack", manifest.source.asset)
        assertNull(manifest.management.versionProbe)
    }

    @Test
    fun `GitHub Release 来源保留仓库产物和架构映射`() {
        val manifest = loader.parseManifestJson(
            manifestJson(
                management = """{"mode":"managed_extension","managedCommands":["example"]}""",
                source = """
                    {
                      "type": "github_release",
                      "repository": "https://github.com/example/example",
                      "releaseTagTemplate": "v{version}",
                      "assetPattern": "example-{target}.tar.gz",
                      "archiveType": "tar.gz",
                      "binaryPath": "example",
                      "architectures": {
                        "arm64": "linux-arm64",
                        "x86_64": "linux-x64"
                      }
                    }
                """.trimIndent()
            )
        )

        assertEquals("github_release", manifest.source.type)
        assertEquals("https://github.com/example/example", manifest.source.repository)
        assertEquals("v{version}", manifest.source.releaseTagTemplate)
        assertEquals("example-{target}.tar.gz", manifest.source.assetPattern)
        assertEquals("tar.gz", manifest.source.archiveType)
        assertEquals("example", manifest.source.binaryPath)
        assertEquals("linux-arm64", manifest.source.architectures["arm64"])
    }

    @Test
    fun `官方脚本来源只声明脚本事实而不伪造远端版本`() {
        val manifest = loader.parseManifestJson(
            manifestJson(
                management = """{"mode":"managed_extension","managedCommands":["example"]}""",
                source = """{"type":"official_script","url":"https://example.com/install.sh"}"""
            )
        )

        assertEquals("official_script", manifest.source.type)
        assertEquals("https://example.com/install.sh", manifest.source.url)
        assertEquals("", manifest.source.tag)
        assertNull(manifest.management.versionProbe)
    }

    @Test
    fun `官方脚本可声明远端版本和隔离安装参数`() {
        val manifest = loader.parseManifestJson(
            manifestJson(
                management = """{"mode":"managed_extension","managedCommands":["example"]}""",
                source = """
                    {
                      "type":"official_script",
                      "url":"https://example.com/install.sh",
                      "latestUrl":"https://example.com/latest",
                      "latestFormat":"text",
                      "versionArguments":["--version","{version}"],
                      "environment":{"EXAMPLE_HOME":"${'$'}install_root"}
                    }
                """.trimIndent()
            )
        )

        assertEquals("https://example.com/latest", manifest.source.latestUrl)
        assertEquals("text", manifest.source.latestFormat)
        assertEquals(listOf("--version", "{version}"), manifest.source.versionArguments)
        assertEquals("${'$'}install_root", manifest.source.environment["EXAMPLE_HOME"])
    }

    @Test
    fun `旧清单缺少 management 时保持原有可管理行为并继承命令`() {
        val manifest = loader.parseManifestJson(
            """
                {
                  "schemaVersion": 1,
                  "id": "kite.legacy",
                  "base": {"name":"Legacy","description":"","version":"1"},
                  "source": {"type":"npm","package":"legacy"},
                  "actions": {
                    "install": [{
                      "type":"managed",
                      "steps":[{"id":"npm","type":"npm","packages":["legacy@latest"]}],
                      "managedCommands":["legacy"]
                    }]
                  }
                }
            """.trimIndent()
        )

        assertEquals(KiteResourceManagementMode.MANAGED_EXTENSION, manifest.management.mode)
        assertTrue(manifest.management.userLifecycleEnabled)
        assertEquals(listOf("legacy"), manifest.management.managedCommands)
    }

    @Test
    fun `正式资源全部显式声明系统组件或外部扩展`() {
        val manifests = resourceRoot().listFiles().orEmpty()
            .map { File(it, "manifest.json") }
            .filter(File::isFile)
            .map { loader.parseManifestJson(it.readText()) }

        assertEquals(20, manifests.size)
        assertTrue(manifests.all { it.rawJson.optJSONObject("management") != null })
        assertEquals(
            SYSTEM_COMPONENT_IDS,
            manifests
                .filter { it.management.mode == KiteResourceManagementMode.SYSTEM_COMPONENT }
                .mapTo(sortedSetOf(), KiteResourceManifest::id)
        )
        assertTrue(
            manifests
                .filterNot { it.id in SYSTEM_COMPONENT_IDS }
                .all { it.management.mode == KiteResourceManagementMode.MANAGED_EXTENSION }
        )
    }

    private fun manifestJson(management: String, source: String): String =
        """
            {
              "schemaVersion": 1,
              "id": "kite.example",
              "base": {"name":"Example","description":"Example resource","version":"1.0.0"},
              "management": $management,
              "source": $source,
              "actions": {}
            }
        """.trimIndent()

    private companion object {
        val SYSTEM_COMPONENT_IDS = sortedSetOf(
            "kite.curl",
            "kite.git",
            "kite.nodejs",
            "kite.python",
            "kite.tool.env",
            "kite.uv"
        )

        fun resourceRoot(): File = listOf(
            File("assets/resources"),
            File("../assets/resources")
        ).first(File::isDirectory)
    }
}
