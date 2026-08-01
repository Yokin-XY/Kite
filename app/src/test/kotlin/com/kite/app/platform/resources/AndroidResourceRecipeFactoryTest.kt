package com.kite.app.platform.resources

import com.kite.app.foundation.runtime.AndroidNativeDownloadCapabilityProvider
import com.kite.app.recipe.KiteRecipe
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceDefinitionSnapshot
import com.kite.app.resources.KiteResourceDefinitionSource
import com.kite.app.resources.KiteResourceManifestLoader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidResourceRecipeFactoryTest {
    private val factory = AndroidResourceRecipeFactory(
        KiteResourceManifestLoader(
            context = RuntimeEnvironment.getApplication(),
            isDebugBuild = true
        )
    )

    @Test
    fun `网络资源安装和卸载都由清单编译为有限配方`() {
        val install = factory.recipe("kite.opencode", KiteResourceInstallRecipes.OP_INSTALL)
        val uninstall = factory.recipe("kite.opencode", KiteResourceInstallRecipes.OP_UNINSTALL)

        assertNotNull(install)
        assertNotNull(uninstall)
        assertEquals(KiteResourceInstallRecipes.RUNTIME_SOURCE, install?.runtimeSource)
        assertTrue(install?.steps.orEmpty().all { it.type == "shell" })
        assertTrue(install?.steps.orEmpty().joinToString("\n") { it.cmd.orEmpty() }
            .contains("KITE_RESOURCE_STEP manifest-install kite.opencode"))
        assertTrue(uninstall?.steps.orEmpty().joinToString("\n") { it.cmd.orEmpty() }
            .contains("KITE_RESOURCE_STEP manifest-uninstall kite.opencode"))
    }

    @Test
    fun `本地打包资源使用本地工具链命令并保留有限运行语义`() {
        val recipe = factory.recipe("kite.nodejs", KiteResourceInstallRecipes.OP_INSTALL)

        assertNotNull(recipe)
        assertTrue(factory.isBundled("kite.nodejs"))
        assertTrue(recipe?.steps.orEmpty().joinToString("\n") { it.cmd.orEmpty() }
            .contains("KITE_RESOURCE_STEP run-install-script"))
        assertEquals(KiteResourceInstallRecipes.RUNTIME_SOURCE, recipe?.runtimeSource)
    }

    @Test
    fun `任意新内置资源不改 Kotlin 和首页 ID 即可获得目录与维护配方`() {
        val resourceId = "demo.catalog.script"
        val source = object : KiteResourceDefinitionSource {
            override fun snapshot(): KiteResourceDefinitionSnapshot = KiteResourceDefinitionSnapshot(
                revision = "demo",
                manifests = mapOf(
                    resourceId to """
                        {
                          "schemaVersion":1,
                          "id":"$resourceId",
                          "base":{"name":"Demo","description":"Demo","version":"1.0.0"},
                          "management":{"mode":"managed_extension"},
                          "display":{"sections":["more"],"category":"开发验证","order":1},
                          "relations":{"base":[],"defaults":[],"extensions":[]},
                          "source":{
                            "type":"bundled",
                            "asset":"resources/$resourceId/payload",
                            "profile":"managed_script_v1",
                            "interpreter":"python3",
                            "entry":"resource.py"
                          },
                          "paths":{"installRoot":"/workspace/.kf/software/$resourceId"}
                        }
                    """.trimIndent()
                ),
                homeLayoutJson = """
                    {"schemaVersion":1,"sections":[{"id":"more","title":"更多","items":[]}]}
                """.trimIndent()
            )

            override fun invalidate() = Unit
        }
        val loader = KiteResourceManifestLoader(isDebugBuild = true, definitionSources = listOf(source))
        val generatedFactory = AndroidResourceRecipeFactory(loader)

        assertTrue(loader.requestHomeLayout()?.sections?.single()?.items == listOf(resourceId))
        assertNotNull(generatedFactory.recipe(resourceId, KiteResourceInstallRecipes.OP_INSTALL))
        assertNotNull(generatedFactory.recipe(resourceId, KiteResourceInstallRecipes.OP_UPDATE, "1.0.0"))
        assertNotNull(generatedFactory.recipe(resourceId, KiteResourceInstallRecipes.OP_UNINSTALL))
    }

    @Test
    fun `资源配方工厂不保留单卡 ID fallback`() {
        val source = listOf(
            File("src/main/java/com/kite/app/platform/resources/AndroidResourceRecipeFactory.kt"),
            File("app/src/main/java/com/kite/app/platform/resources/AndroidResourceRecipeFactory.kt")
        ).first(File::isFile).readText()

        assertFalse(source.contains("legacyStep("))
        assertFalse(source.contains("private const val RESOURCE_"))
        assertFalse(Regex("\\\"kite\\.[a-z0-9._-]+\\\"").containsMatchIn(source))
    }

    @Test
    fun `Codex 样板从 NPM 来源生成确定版本更新配方`() {
        val update = factory.recipe("kite.codex.cli", KiteResourceInstallRecipes.OP_UPDATE, "1.2.3")
        val script = update?.steps.orEmpty().joinToString("\n") { it.cmd.orEmpty() }

        assertNotNull(update)
        assertTrue(script.contains("@openai/codex@1.2.3"))
        assertTrue(script.contains("/workspace/.kf/software/kite.codex.cli"))
        assertTrue(script.contains("KITE_RESOURCE_STEP manifest-install kite.codex.cli"))
        assertTrue(script.contains("codex --version"))
        assertTrue(script.contains("KITE_RESOURCE_INSTALLED_VERSION"))
        assertTrue(script.contains("restore-preserved-path"))
    }

    @Test
    fun `OpenCode 样板从 GitHub Release 来源生成架构和版本配方`() {
        val update = factory.recipe("kite.opencode", KiteResourceInstallRecipes.OP_UPDATE, "v1.18.4")
        val steps = update?.steps.orEmpty()
        val script = steps.joinToString("\n") { it.cmd.orEmpty() }

        assertNotNull(update)
        assertTrue(steps.all { it.type == KiteRecipe.STEP_SHELL })
        assertTrue(script.contains("kite_resource_download"))
        assertTrue(script.contains("aarch64|arm64) target='linux-arm64'"))
        assertTrue(script.contains("releases/download/v1.18.4/${'$'}asset_name"))
        assertTrue(script.contains("opencode-${'$'}target.tar.gz"))
        assertTrue(script.contains("transactional_clean=\"1\""))
    }

    @Test
    fun `Kimi 样板从官方脚本来源生成隔离目录和确定版本配方`() {
        val update = factory.recipe("kite.kimi.code", KiteResourceInstallRecipes.OP_UPDATE, "0.27.0")
        val steps = update?.steps.orEmpty()
        val native = steps.single { it.type == KiteRecipe.STEP_NATIVE_CAPABILITY }
        val script = steps.single { it.type == KiteRecipe.STEP_SHELL }.cmd.orEmpty()

        assertNotNull(update)
        assertEquals(AndroidNativeDownloadCapabilityProvider.CAPABILITY_ID, native.action)
        assertEquals("16777216", native.params?.getString(AndroidNativeDownloadCapabilityProvider.PARAM_MAX_BYTES))
        assertTrue(
            native.params?.getString(AndroidNativeDownloadCapabilityProvider.PARAM_DESTINATION)
                .orEmpty().startsWith("/workspace/.kf/cache/resources/kite.kimi.code/native-downloads/")
        )
        assertFalse(script.contains("kite_resource_download"))
        assertTrue(script.contains("native_cache='/workspace/.kf/cache/resources/kite.kimi.code/native-downloads/"))
        assertTrue(script.contains("update_lock=\"${'$'}install_root.kite-update-lock\""))
        assertTrue(script.indexOf("recover-interrupted-install") < script.indexOf("mv -f \"${'$'}native_cache\""))
        assertTrue(script.contains("KIMI_INSTALL_DIR=\"${'$'}install_root\""))
        assertTrue(script.contains("KIMI_NO_MODIFY_PATH=\"1\""))
        assertTrue(script.contains("\"--version\" \"0.27.0\""))
    }

    @Test
    fun `显式官方脚本下载也走原生缓存而安装与校验仍在资源事务内`() {
        val recipe = factory.recipe("kite.hermes.core", KiteResourceInstallRecipes.OP_INSTALL)
        val steps = recipe?.steps.orEmpty()
        val native = steps.single { it.type == KiteRecipe.STEP_NATIVE_CAPABILITY }
        val shell = steps.single { it.type == KiteRecipe.STEP_SHELL }.cmd.orEmpty()

        assertEquals("https://hermes-agent.nousresearch.com/install.sh", native.params?.getString("url"))
        assertTrue(shell.contains("transactional_clean=\"0\""))
        assertTrue(shell.contains("run-hermes-installer"))
        assertTrue(shell.contains("verify hermes-command"))
        assertTrue(shell.indexOf("mv -f \"${'$'}native_cache\"") < shell.indexOf("run-hermes-installer"))
        assertTrue(shell.indexOf("verify hermes-command") < shell.indexOf("commit-install kite.hermes.core"))
    }

    @Test
    fun `没有尺寸上限的资源下载保持 PRoot 而不猜测原生安全边界`() {
        val resourceId = "demo.unbounded-download"
        val source = object : KiteResourceDefinitionSource {
            override fun snapshot(): KiteResourceDefinitionSnapshot = KiteResourceDefinitionSnapshot(
                revision = "unbounded",
                manifests = mapOf(
                    resourceId to """
                        {
                          "schemaVersion":1,
                          "id":"$resourceId",
                          "base":{"name":"Demo","description":"Demo","version":"1.0.0"},
                          "management":{"mode":"managed_extension","managedCommands":["demo"]},
                          "display":{"sections":["more"],"category":"开发验证"},
                          "relations":{"base":[],"defaults":[],"extensions":[]},
                          "source":{"type":"official_script","url":"https://example.test/install.sh"},
                          "paths":{"installRoot":"/workspace/.kf/software/$resourceId"}
                        }
                    """.trimIndent()
                ),
                homeLayoutJson = """{"schemaVersion":1,"sections":[]}""",
            )

            override fun invalidate() = Unit
        }
        val loader = KiteResourceManifestLoader(isDebugBuild = true, definitionSources = listOf(source))
        val recipe = AndroidResourceRecipeFactory(loader)
            .recipe(resourceId, KiteResourceInstallRecipes.OP_INSTALL)
        val steps = recipe?.steps.orEmpty()

        assertTrue(steps.all { it.type == KiteRecipe.STEP_SHELL })
        assertTrue(steps.single().cmd.orEmpty().contains("kite_resource_download"))
    }

    @Test
    fun `重新安装复用受管安装事务而不是先破坏性卸载`() {
        val reinstall = factory.recipe("kite.codex.cli", KiteResourceInstallRecipes.OP_REINSTALL)
        val script = reinstall?.steps.orEmpty().joinToString("\n") { it.cmd.orEmpty() }

        assertNotNull(reinstall)
        assertTrue(script.contains("transactional_clean=\"1\""))
        assertTrue(script.contains("rollback_install_transaction"))
        assertTrue(script.contains("restore-preserved-path"))
        assertTrue(script.contains("@openai/codex@latest"))
    }

}
