package com.kite.app.foundation.toolchain

import com.kite.app.resources.KiteResourceInstallRecipes
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToolchainSharedPackTest {
    @Test
    fun bundledInstallerDoesNotShadowSystemServiceApplets() {
        val installScript = sequenceOf(
            File("../assets/toolchain/ai-dev-pack/install.sh"),
            File("assets/toolchain/ai-dev-pack/install.sh"),
        ).first(File::isFile).readText()

        assertFalse(installScript.contains("write_wrapper systemctl"))
        assertFalse(installScript.contains("write_wrapper service"))
        assertTrue(installScript.contains("remove_owned_legacy_wrapper systemctl"))
        assertTrue(installScript.contains("remove_owned_legacy_wrapper service"))
    }

    @Test
    fun resourceRecipesUseOneSharedPackAndKeepPrivateCachePaths() {
        val nodePack = KiteResourceInstallRecipes.localPackPath("kite.nodejs")
        val pythonPack = KiteResourceInstallRecipes.localPackPath("kite.python")

        assertTrue(nodePack == pythonPack)
        assertTrue(nodePack == "/workspace/.kf/cache/shared/ai-dev-pack")
        assertTrue(KiteResourceInstallRecipes.resourceCachePath("kite.nodejs").endsWith("/resources/kite.nodejs"))
        assertFalse(KiteResourceInstallRecipes.resourceCachePath("kite.nodejs").contains("/shared/"))
    }

}
