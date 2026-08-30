package com.kite.app.platform.resources

import com.kite.app.foundation.runtime.AndroidNativeDownloadCapabilityProvider
import com.kite.app.foundation.runtime.AndroidNativeArchiveCapabilityProvider
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
    fun `Android APK资源先下载校验再交接并等待真实包事实`() {
        val recipe = factory.recipe("kite.shizuku", KiteResourceInstallRecipes.OP_INSTALL)
        val steps = recipe?.steps.orEmpty()

        assertNotNull(recipe)
        assertEquals(
            listOf(
                KiteRecipe.STEP_NATIVE_CAPABILITY,
                KiteRecipe.STEP_SHELL,
                KiteRecipe.STEP_ANDROID_ACTION,
                KiteRecipe.STEP_ANDROID_ACTION,
            ),
            steps.map { it.type },
        )
        assertEquals(KiteRecipe.ANDROID_ACTION_INSTALL_APK, steps[2].action)
        assertEquals(KiteRecipe.ANDROID_ACTION_AWAIT_PACKAGE, steps[3].action)
        assertEquals("moe.shizuku.privileged.api", steps[3].params?.optString("packageName"))
        assertEquals(
            "android-package:moe.shizuku.privileged.api",
            factory.writeScopes("kite.shizuku", KiteResourceInstallRecipes.OP_INSTALL)
                .single { it.startsWith("android-package:") },
        )
    }

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
    fun `Hermes 从官方与 GitCode 的共同固定版本安装`() {
        val recipe = factory.recipe("kite.hermes.core", KiteResourceInstallRecipes.OP_INSTALL)
        val steps = recipe?.steps.orEmpty()
        val shell = steps.single { it.type == KiteRecipe.STEP_SHELL }.cmd.orEmpty()

        assertTrue(steps.none { it.type == KiteRecipe.STEP_NATIVE_CAPABILITY })
        assertTrue(shell.indexOf("gitcode.com/GitHub_Trending/he/hermes-agent.git") < shell.indexOf("github.com/NousResearch/hermes-agent.git"))
        assertTrue(shell.contains("v2026.8.27"))
        assertTrue(shell.contains("5fc308a70719a83cccdbba4c0e39c23f5a8239d5"))
        assertTrue(shell.contains("kite-install-core-acp.sh"))
        assertTrue(shell.contains("transactional_clean=\"0\""))
        assertTrue(shell.contains("install-hermes-core-acp"))
        assertTrue(shell.contains("uv sync --project"))
        assertTrue(shell.contains("--locked --no-default-groups --extra acp"))
        assertTrue(shell.contains("/usr/bin/python3.12"))
        assertTrue(shell.contains("compatible-system-python-missing"))
        assertTrue(shell.contains("https://mirrors.aliyun.com/pypi/simple/"))
        assertTrue(shell.contains("KITE_RESOURCE_PYPI_ROUTES"))
        assertTrue(shell.contains("for pypi_route in \$KITE_RESOURCE_PYPI_ROUTES"))
        assertTrue(shell.contains("UV_CACHE_DIR=\"\$attempt_cache\""))
        assertTrue(shell.contains("timeout 900 env UV_DEFAULT_INDEX=\"\$pypi_index\""))
        assertTrue(shell.contains("kite_resource_is_source_failure"))
        assertFalse(shell.contains("timeout 600 env UV_DEFAULT_INDEX="))
        assertTrue(shell.contains("KITE_RESOURCE_RETRY stage=acquire step=hermes-python-packages"))
        assertTrue(shell.contains("patch-hermes-acp-provider-identity"))
        assertTrue(shell.contains("canonical_custom_identity"))
        assertTrue(shell.contains("from hermes_cli.runtime_provider import canonical_custom_identity"))
        assertTrue(shell.contains("patch-hermes-acp-model-selection"))
        assertTrue(shell.contains("_configured_custom_provider_ids"))
        assertTrue(shell.contains("patch-hermes-acp-bare-provider-selection"))
        assertTrue(shell.contains("candidate.removeprefix(\"custom:\")"))
        assertTrue(shell.contains("export HERMES_DISABLE_LAZY_INSTALLS=1"))
        assertTrue(shell.indexOf("1:-") < shell.indexOf("export HERMES_DISABLE_LAZY_INSTALLS=1"))
        assertFalse(shell.contains("uv python install"))
        assertFalse(shell.contains("UV_NO_CONFIG"))
        assertFalse(shell.contains("ensurepip"))
        assertFalse(shell.contains("apt install"))
        assertFalse(shell.contains("ffmpeg"))
        assertTrue(shell.contains("verify hermes-source-commit"))
        assertTrue(shell.contains("verify hermes-command"))
        assertTrue(shell.indexOf("acquire-hermes-source") < shell.indexOf("install-hermes-core-acp"))
        assertTrue(shell.indexOf("verify hermes-command") < shell.indexOf("commit-install kite.hermes.core"))
    }

    @Test
    fun `声明式归档在资源事务外准备并在事务内导入候选安装根`() {
        val steps = factory.recipe("kite.cursor.cli", KiteResourceInstallRecipes.OP_INSTALL)
            ?.steps.orEmpty()
        val nativeSteps = steps.filter { it.type == KiteRecipe.STEP_NATIVE_CAPABILITY }
        val archive = nativeSteps.single {
            it.action == AndroidNativeArchiveCapabilityProvider.CAPABILITY_ID
        }
        val script = steps.single { it.type == KiteRecipe.STEP_SHELL }.cmd.orEmpty()

        assertEquals(2, nativeSteps.size)
        assertEquals("tar.gz", archive.params?.getString(AndroidNativeArchiveCapabilityProvider.PARAM_FORMAT))
        assertEquals(
            "ea13f92e295f523a99ce8d8f57d6894d21e5d1e2d030ffad718ccd5955ca2eed",
            archive.params?.getString(AndroidNativeArchiveCapabilityProvider.PARAM_EXPECTED_SHA256),
        )
        assertTrue(
            archive.params?.getString(AndroidNativeArchiveCapabilityProvider.PARAM_DESTINATION)
                .orEmpty().startsWith("/workspace/.kf/cache/resources/kite.cursor.cli/native-archives/")
        )
        assertFalse(script.contains("tar -x"))
        assertTrue(script.contains("test -f \"${'$'}native_archive_cache/.kite-archive-ready\""))
        assertTrue(script.indexOf("recover-interrupted-install") < script.indexOf("cp -a \"${'$'}native_archive_cache/.\""))
        assertTrue(script.contains("chmod 755 \"${'$'}install_root/dist-package/cursor-agent\""))
    }

    @Test
    fun `Hermes 合同升级只重写启动器而不重复安装运行时`() {
        val recipe = factory.recipe(
            "kite.hermes.core",
            KiteResourceInstallRecipes.OP_UPDATE,
            "v2026.8.27.3",
        )
        val shell = recipe?.steps.orEmpty().single { it.type == KiteRecipe.STEP_SHELL }.cmd.orEmpty()

        assertTrue(shell.contains("update-hermes-launcher"))
        assertTrue(shell.contains("patch-hermes-acp-provider-identity"))
        assertTrue(shell.contains("canonical_custom_identity"))
        assertTrue(shell.contains("from hermes_cli.runtime_provider import canonical_custom_identity"))
        assertTrue(shell.contains("patch-hermes-acp-model-selection"))
        assertTrue(shell.contains("return candidate, new_model[len(prefix):]"))
        assertTrue(shell.contains("patch-hermes-acp-bare-provider-selection"))
        assertTrue(shell.contains("candidate.removeprefix(\"custom:\")"))
        assertTrue(shell.contains("HERMES_DISABLE_LAZY_INSTALLS=1"))
        assertTrue(shell.contains("v2026.8.27.4"))
        assertTrue(shell.contains("hermes acp --help"))
        assertFalse(shell.contains("git clone"))
        assertFalse(shell.contains("uv sync"))
        assertFalse(shell.contains("kite-install-core-acp.sh"))
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
    fun `多下载源和摘要被编译为单个可信原生下载步骤`() {
        val resourceId = "demo.trusted-mirror"
        val digest = "1".repeat(64)
        val source = object : KiteResourceDefinitionSource {
            override fun snapshot(): KiteResourceDefinitionSnapshot = KiteResourceDefinitionSnapshot(
                revision = "trusted-mirror",
                manifests = mapOf(
                    resourceId to """
                        {
                          "schemaVersion":1,
                          "id":"$resourceId",
                          "base":{"name":"Demo","description":"Demo","version":"1.0.0"},
                          "management":{"mode":"managed_extension","managedCommands":["demo"]},
                          "display":{"sections":["more"],"category":"开发验证"},
                          "relations":{"base":[],"defaults":[],"extensions":[]},
                          "source":{"type":"official_script","url":"https://official.example.test/install.sh"},
                          "paths":{"installRoot":"/workspace/.kf/software/$resourceId"},
                          "actions":{"install":[{"type":"managed","steps":[
                            {"id":"download","type":"download","urls":[
                              "https://official.example.test/install.sh",
                              "https://mirror.example.test/install.sh"
                            ],"destination":"${'$'}install_root/.downloads/install.sh","maxBytes":1048576,"sha256":"$digest"},
                            {"id":"run","type":"script","path":"${'$'}install_root/.downloads/install.sh"}
                          ]}]}
                        }
                    """.trimIndent()
                ),
                homeLayoutJson = """{"schemaVersion":1,"sections":[]}""",
            )

            override fun invalidate() = Unit
        }
        val loader = KiteResourceManifestLoader(isDebugBuild = true, definitionSources = listOf(source))
        val steps = AndroidResourceRecipeFactory(loader)
            .recipe(resourceId, KiteResourceInstallRecipes.OP_INSTALL)
            ?.steps.orEmpty()
        val native = steps.single { it.type == KiteRecipe.STEP_NATIVE_CAPABILITY }
        val urls = native.params
            ?.getString(AndroidNativeDownloadCapabilityProvider.PARAM_URLS)
            .orEmpty()
            .lineSequence()
            .toList()
        val shell = steps.single { it.type == KiteRecipe.STEP_SHELL }.cmd.orEmpty()

        assertEquals(2, urls.size)
        assertEquals("https://official.example.test/install.sh", urls[0])
        assertEquals("https://mirror.example.test/install.sh", urls[1])
        assertEquals(digest, native.params?.getString(AndroidNativeDownloadCapabilityProvider.PARAM_EXPECTED_SHA256))
        assertFalse(shell.contains("kite_resource_download"))
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
