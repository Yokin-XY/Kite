package com.kite.app.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteResourceCardAuthoringPolicyTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }

    @Test
    fun `当前资源目录的每一种安装方式都有统一规则`() {
        val loader = KiteResourceManifestLoader(context)
        resourceRoot().listFiles().orEmpty()
            .map { File(it, "manifest.json") }
            .filter(File::isFile)
            .forEach { manifestFile ->
                val manifest = loader.parseManifestJson(manifestFile.readText())
                assertNotNull(
                    "Unclassified source type ${manifest.source.type} from ${manifest.id}",
                    KiteResourceCardAuthoringPolicy.ruleFor(manifest.source.type),
                )
            }
    }

    @Test
    fun `官方包管理方式优先于发布包和安装脚本`() {
        assertEquals("npm", KiteResourceCardAuthoringPolicy.prefer("official_script", "npm"))
        assertEquals("pypi", KiteResourceCardAuthoringPolicy.prefer("pypi", "git"))
        assertEquals("github_release", KiteResourceCardAuthoringPolicy.prefer("git", "github_release"))
    }

    @Test
    fun `安装方式不在运行时静默互换`() {
        listOf("npm", "pypi", "github_release", "official_script", "git").forEach { sourceType ->
            assertFalse(
                KiteResourceCardAuthoringPolicy.ruleFor(sourceType)?.runtimeMethodFallbackAllowed ?: true,
            )
        }
    }

    private fun resourceRoot(): File = listOf(
        File("assets/resources"),
        File("../assets/resources"),
    ).first(File::isDirectory)
}
