package com.kite.app.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.registration.AgentLaunchSpec
import com.kite.app.agent.registration.AgentResourceRegistrationMapper
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZCodeResourceManifestTest {
    @Test
    fun `ZCode卡片只安装官方原生运行核心并注册app-server`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resourceRoot = sequenceOf(File("../assets/resources"), File("assets/resources"))
            .first(File::isDirectory)
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(
            File(resourceRoot, "kite.zcode/manifest.json").readText(),
        )

        assertEquals("resources/kite.zcode/icon.png", manifest.iconAsset)
        assertTrue(File(resourceRoot, "kite.zcode/icon.png").isFile)
        assertFalse(File(resourceRoot, "kite.zai.coding.helper/manifest.json").exists())
        assertFalse(manifest.displayRecommendations.any { it.resourceId == "kite.zai.coding.helper" })

        val profile = manifest.agentProfiles.single()
        assertEquals("zcode", profile.agentId)
        assertEquals("zcode-app-server", profile.protocol)
        assertEquals(listOf("zcode", "app-server", "--surface", "desktop"), profile.argv)
        assertTrue(AgentResourceRegistrationMapper.registrations(manifest).single().launch is AgentLaunchSpec.Managed)

        val action = manifest.installActions.single()
        val download = action.installSteps.first()
        assertEquals("download", download.type)
        assertEquals(
            "22b79babe3b00fb6fbfcf7dcc033b7564a734f53df4f28998c18556071286b2c",
            download.sha256,
        )
        val script = KiteResourceInstallPlanCompiler.compile(action)
        assertTrue(script.contains("dpkg-deb --fsys-tarfile"))
        assertTrue(script.contains("resources/glm/zcode.cjs"))
        assertTrue(!script.contains("dpkg-deb -x"))
    }
}
