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
        val raw = File(resourceRoot, "kite.zcode/manifest.json").readText()
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(raw)

        assertEquals("resources/kite.zcode/icon.png", manifest.iconAsset)
        assertTrue(File(resourceRoot, "kite.zcode/icon.png").isFile)
        assertFalse(File(resourceRoot, "kite.zai.coding.helper/manifest.json").exists())
        assertFalse(manifest.displayRecommendations.any { it.resourceId == "kite.zai.coding.helper" })

        val profile = manifest.agentProfiles.single()

        assertEquals("zcode", profile.agentId)
        assertEquals("zcode-app-server", profile.protocol)
        assertEquals(listOf("zcode", "app-server", "--surface", "desktop"), profile.argv)
        assertEquals("zcode", profile.configAdapterId)
        assertTrue(AgentResourceRegistrationMapper.registrations(manifest).single().launch is AgentLaunchSpec.Managed)
        val account = profile.officialAccounts.single()
        assertTrue(account.modelGroupIds.contains("zai"))
        assertEquals("node", account.status?.argv?.first())
        assertTrue(account.status?.loggedInPatterns?.contains("\"loggedIn\":true") == true)
        assertEquals(listOf("zcode", "login", "--no-browser", "--json"), account.login.argv)
        assertEquals(listOf("\"status\":\"ready\""), account.login.successPatterns)

        assertEquals("regex", manifest.source.latestFormat)
        assertEquals(3, manifest.source.latestVersionWindow.size)
        val action = KiteResourceSourcePlanFactory.plan(manifest).installActions.single()
        val download = action.installSteps.first()
        assertEquals(KiteResourceInstallPlanCompiler.STEP_LATEST_DOWNLOAD, download.type)
        assertEquals(3, download.latestVersionWindow.size)
        val script = KiteResourceInstallPlanCompiler.compile(action)
        assertTrue(script.contains("zcode.z.ai/en"))
        assertTrue(script.contains("request=latest"))
        assertTrue(script.contains("dpkg-deb --fsys-tarfile"))
        assertTrue(script.contains("resources/glm/zcode.cjs"))
        assertFalse(script.contains("dpkg-deb -x"))

        val launcher = action.installSteps.last().cmd
        assertTrue(launcher.contains("export HOME=\"\\${'$'}managed_home\""))
        assertTrue(launcher.contains("export ZCODE_DATA_BASE_DIR=\"\\${'$'}managed_home\""))
        assertTrue(launcher.contains(".zcode/cli/config.json"))
    }
}
