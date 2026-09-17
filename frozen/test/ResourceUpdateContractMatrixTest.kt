package com.kite.app.platform.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.resources.KiteResourceAvailability
import com.kite.app.resources.KiteResourceInstallPlanCompiler
import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceManifestLoader
import com.kite.app.resources.KiteResourceSourcePlanFactory
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResourceUpdateContractMatrixTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private val loader by lazy { KiteResourceManifestLoader(context, isDebugBuild = true) }
    private val recipeFactory by lazy { AndroidResourceRecipeFactory(loader) }

    @Test
    fun `所有声明支持更新的真实资源都满足统一版本与执行合同`() {
        val updateResources = manifests().filter { manifest ->
            KiteResourceSourcePlanFactory.plan(manifest).capabilities.update
        }

        assertTrue("仓库至少应保留一个可更新资源以固定合同", updateResources.isNotEmpty())
        updateResources.forEach { manifest ->
            val basePlan = KiteResourceSourcePlanFactory.plan(manifest)
            val targetVersion = targetVersionFor(manifest)
            val targetPlan = KiteResourceSourcePlanFactory.plan(manifest, targetVersion)
            val recipe = recipeFactory.recipe(
                manifest.id,
                KiteResourceInstallRecipes.OP_UPDATE,
                targetVersion
            )

            assertTrue("缺少安装能力: ${manifest.id}", basePlan.capabilities.install)
            assertTrue("缺少检查更新能力: ${manifest.id}", basePlan.capabilities.checkUpdate)
            assertNotNull("缺少已安装版本探针: ${manifest.id}", basePlan.versionCheck.installed)
            assertNotNull("缺少最新版本探针: ${manifest.id}", basePlan.versionCheck.latest)
            assertTrue("目标版本没有更新动作: ${manifest.id}", targetPlan.installActions.isNotEmpty())
            targetPlan.installActions.forEach { action ->
                assertTrue(
                    "更新动作未进入受管安装协议: ${manifest.id}",
                    action.type == KiteResourceInstallPlanCompiler.ACTION_MANAGED
                )
                assertTrue("更新动作没有执行步骤: ${manifest.id}", action.installSteps.isNotEmpty())
            }
            assertNotNull("更新动作没有生成运行配方: ${manifest.id}", recipe)
            val script = recipe?.steps.orEmpty().joinToString("\n") { it.cmd.orEmpty() }
            assertTrue("更新配方没有目标版本确认: ${manifest.id}", script.contains("expected_version='$targetVersion'"))
            assertTrue(
                "更新配方没有输出实际安装版本证据: ${manifest.id}",
                script.contains("KITE_RESOURCE_INSTALLED_VERSION")
            )
        }
    }

    @Test
    fun `系统组件和 Release 目录不会因更新矩阵扩大能力边界`() {
        manifests().filterNot { it.management.userLifecycleEnabled }.forEach { manifest ->
            val capabilities = KiteResourceSourcePlanFactory.plan(manifest).capabilities
            assertFalse("系统组件不应支持检查更新: ${manifest.id}", capabilities.checkUpdate)
            assertFalse("系统组件不应支持更新: ${manifest.id}", capabilities.update)
        }

        val releaseLoader = KiteResourceManifestLoader(context, isDebugBuild = false)
        manifests().filter { it.availability == KiteResourceAvailability.DEBUG_ONLY }.forEach { manifest ->
            assertTrue(
                "Debug 资源不应进入 Release 目录: ${manifest.id}",
                releaseLoader.requestManifest(manifest.id) == null
            )
        }
    }

    private fun targetVersionFor(manifest: KiteResourceManifest): String =
        if (manifest.updateActions.isNotEmpty()) manifest.version else SYNTHETIC_TARGET_VERSION

    private fun manifests(): List<KiteResourceManifest> = resourceRoot().listFiles().orEmpty()
        .map { File(it, "manifest.json") }
        .filter(File::isFile)
        .sortedBy { it.parentFile?.name }
        .map { loader.parseManifestJson(it.readText()) }

    private companion object {
        const val SYNTHETIC_TARGET_VERSION = "9.8.7"

        fun resourceRoot(): File = listOf(
            File("assets/resources"),
            File("../assets/resources")
        ).first(File::isDirectory)
    }
}
