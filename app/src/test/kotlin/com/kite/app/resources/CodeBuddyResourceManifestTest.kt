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
class CodeBuddyResourceManifestTest {
    @Test
    fun `CodeBuddy卡片安装官方npm包并注册原生ACP`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resourceRoot = sequenceOf(File("../assets/resources"), File("assets/resources"))
            .first(File::isDirectory)
        val raw = File(resourceRoot, "kite.codebuddy.code/manifest.json").readText()
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(raw)

        assertEquals("@tencent-ai/codebuddy-code", manifest.source.packageName)
        assertEquals(listOf("kite.nodejs", "kite.git"), manifest.baseRequirements)
        assertEquals(listOf("user-home"), manifest.management.preservePaths)

        val profile = manifest.agentProfiles.single()
        assertEquals("codebuddy", profile.agentId)
        assertEquals("acp", profile.protocol)
        assertEquals(listOf("codebuddy", "--acp"), profile.argv)
        assertEquals("codebuddy-code", profile.configAdapterId)
        assertTrue(AgentResourceRegistrationMapper.registrations(manifest).single().launch is AgentLaunchSpec.Managed)

        val install = manifest.installActions.single()
        assertEquals(listOf("@tencent-ai/codebuddy-code@latest"), install.installSteps.single().packages)
        assertTrue(install.verifications.single().cmd.contains("codebuddy --version"))
        assertFalse(raw.contains("CODEBUDDY_API_KEY"))
    }
}
