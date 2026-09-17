package com.kite.app.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CodeBuddyResourceManifestTest {
    private val context: Context by lazy { ApplicationProvider.getApplicationContext() }
    private val loader by lazy { KiteResourceManifestLoader(context) }

    private fun resourceRoot(): File =
        sequenceOf(File("../assets/resources"), File("assets/resources"))
            .first(File::isDirectory)

    @Test
    fun `CodeBuddy卡片安装官方npm包并注册原生ACP`() {
        val manifest = loader.parseManifestJson(
            File(resourceRoot(), "kite.codebuddy.code/manifest.json").readText()
        )
        assertEquals("official_command", manifest.sourceType)
        val plan = KiteResourceSourcePlanFactory.plan(manifest)
        assertEquals(KiteResourceInstallPlanCompiler.STEP_SHELL, plan.installActions.single().type)
        assertTrue(manifest.agentProfiles.isNotEmpty())
    }
}
