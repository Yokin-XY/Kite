package com.kite.app.platform.resources

import com.kite.app.resources.KiteResourceInstallRecipes
import com.kite.app.resources.KiteResourceManifestLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidResourceRecipeFactoryTest {
    private val factory = AndroidResourceRecipeFactory(
        KiteResourceManifestLoader(RuntimeEnvironment.getApplication())
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
}
