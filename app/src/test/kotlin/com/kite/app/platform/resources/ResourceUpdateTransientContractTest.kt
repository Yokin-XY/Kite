package com.kite.app.platform.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class ResourceUpdateTransientContractTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private val loader by lazy { KiteResourceManifestLoader(context, isDebugBuild = true) }
    private val recipeFactory by lazy { AndroidResourceRecipeFactory(loader) }

    @Test
    fun `所有可更新资源只使用一次性安装根保护`() {
        val updateResources = manifests().filter { manifest ->
            KiteResourceSourcePlanFactory.plan(manifest).capabilities.update
        }

        assertTrue(updateResources.isNotEmpty())
        updateResources.forEach { manifest ->
            val targetVersion = if (manifest.updateActions.isNotEmpty()) manifest.version else "9.8.7"
            val recipe = recipeFactory.recipe(
                manifest.id,
                KiteResourceInstallRecipes.OP_UPDATE,
                targetVersion
            )
            assertNotNull("更新动作没有生成运行配方: ${manifest.id}", recipe)
            val script = recipe?.steps.orEmpty().joinToString("\n") { it.cmd.orEmpty() }
            assertTrue("更新没有启用临时安装根保护: ${manifest.id}", script.contains("transactional_clean=\"1\""))
            assertTrue("更新没有获取资源级排他锁: ${manifest.id}", script.contains("acquire_update_lock || exit"))
            assertTrue("更新锁没有进程代次校验: ${manifest.id}", script.contains("/proc/${'$'}lock_pid/stat"))
            assertTrue("更新没有失败自动回退: ${manifest.id}", script.contains("rollback_install_transaction"))
            assertTrue("更新成功后没有清理临时备份: ${manifest.id}", script.contains("rm -rf \"${'$'}backup_root\""))
            assertTrue("更新成功后没有释放资源锁: ${manifest.id}", script.contains("release_update_lock"))
            assertFalse("更新仍依赖外部长生命周期保护: ${manifest.id}", script.contains("transactional_clean=\"0\""))
        }
    }

    @Test
    fun `首次获取不进入更新事务`() {
        val installResources = manifests().filter { manifest ->
            KiteResourceSourcePlanFactory.plan(manifest).installActions.isNotEmpty()
        }

        assertTrue(installResources.isNotEmpty())
        installResources.forEach { manifest ->
            val recipe = recipeFactory.recipe(manifest.id, KiteResourceInstallRecipes.OP_INSTALL)
            assertNotNull("获取动作没有生成运行配方: ${manifest.id}", recipe)
            val script = recipe?.steps.orEmpty().joinToString("\n") { it.cmd.orEmpty() }
            assertFalse("首次获取误入更新事务: ${manifest.id}", script.contains("transactional_clean=\"1\""))
        }
    }

    @Test
    fun `更新运行网关不再创建或提交资源 View 子层`() {
        val source = sourceFile(
            "src/main/java/com/kite/app/platform/resources/AndroidResourceRunGateway.kt",
            "app/src/main/java/com/kite/app/platform/resources/AndroidResourceRunGateway.kt"
        ).readText()

        assertFalse(source.contains("transactionCoordinator.beginUpdate("))
        assertFalse(source.contains("transactionCoordinator.commitUpdate("))
        assertFalse(source.contains("transactionCoordinator.finalizeUpdate("))
        assertFalse(source.contains("transactionCoordinator.rollbackUpdate("))

        val application = sourceFile(
            "src/main/kotlin/com/kite/app/foundation/bootstrap/KFApplication.kt",
            "app/src/main/kotlin/com/kite/app/foundation/bootstrap/KFApplication.kt"
        ).readText()
        val graph = sourceFile(
            "src/main/java/com/kite/app/shell/KiteAppGraph.kt",
            "app/src/main/java/com/kite/app/shell/KiteAppGraph.kt"
        ).readText()
        assertFalse(application.contains("resourceTransactionCoordinator"))
        assertFalse(graph.contains("resourceTransactionCoordinator"))
    }

    private fun manifests(): List<KiteResourceManifest> = resourceRoot().listFiles().orEmpty()
        .map { File(it, "manifest.json") }
        .filter(File::isFile)
        .sortedBy { it.parentFile?.name }
        .map { loader.parseManifestJson(it.readText()) }

    private fun resourceRoot(): File = sourceFile("assets/resources", "../assets/resources")

    private fun sourceFile(vararg candidates: String): File = candidates
        .map(::File)
        .first(File::exists)
}
