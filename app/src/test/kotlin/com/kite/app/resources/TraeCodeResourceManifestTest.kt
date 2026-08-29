package com.kite.app.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.registration.AgentLaunchSpec
import com.kite.app.agent.registration.AgentResourceRegistrationMapper
import com.kite.app.foundation.runtime.RuntimeHardLinkMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TraeCodeResourceManifestTest {
    @Test
    fun `TraeCode卡片固定官方ARM64制品并声明原生硬链接ACP`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resourceRoot = sequenceOf(File("../assets/resources"), File("assets/resources"))
            .first(File::isDirectory)
        val raw = File(resourceRoot, "kite.trae.code/manifest.json").readText()
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(raw)

        assertEquals("0.201.6-tob", manifest.version)
        assertEquals("official_release_archive", manifest.source.type)
        assertEquals(listOf("user-home"), manifest.management.preservePaths)

        val profile = manifest.agentProfiles.single()
        assertEquals("trae", profile.agentId)
        assertEquals("acp", profile.protocol)
        assertEquals(listOf("traecli", "acp", "serve"), profile.argv)
        assertEquals("trae-code", profile.configAdapterId)
        assertEquals(RuntimeHardLinkMode.NATIVE, profile.hardLinkMode)
        assertTrue(profile.officialAccounts.all { account ->
            listOfNotNull(account.status, account.login, account.logout)
                .all { it.hardLinkMode == RuntimeHardLinkMode.NATIVE }
        })
        assertTrue(AgentResourceRegistrationMapper.registrations(manifest).single().launch is AgentLaunchSpec.Managed)

        val install = manifest.installActions.single()
        assertEquals(
            "e2ddc587cb813473c4f2635bb14c5a3da9a5eb944991600d4bdefe8267a4b1c5",
            install.installSteps.first().sha256,
        )
        assertTrue(install.installSteps.last().cmd.contains("gzip -dc"))
        assertFalse(install.verifications.any { it.cmd.contains("traecli --version") })
        assertFalse(raw.contains("PERSONAL_ACCESS_TOKEN"))
    }
}
