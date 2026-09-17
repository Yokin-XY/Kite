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
class ShizukuResourceManifestTest {
    @Test
    fun `Shizuku卡片查询官方最新版本并声明系统安装器交接`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resourceRoot = sequenceOf(File("../assets/resources"), File("assets/resources"))
            .first(File::isDirectory)
        val manifest = KiteResourceManifestLoader(context).parseManifestJson(
            File(resourceRoot, "kite.shizuku/manifest.json").readText(),
        )

        assertEquals("android_apk", manifest.sourceType)
        assertEquals("moe.shizuku.privileged.api", manifest.source.packageName)
        assertEquals("resources/kite.shizuku/icon.png", manifest.iconAsset)
        assertTrue(File(resourceRoot, "kite.shizuku/icon.png").isFile)
        val action = KiteResourceSourcePlanFactory.plan(manifest).installActions.single()
        val download = action.installSteps.single()
        assertEquals(KiteResourceInstallPlanCompiler.STEP_LATEST_DOWNLOAD, download.type)
        assertEquals(listOf("13.6.0", "13.5.4", "13.5.3"), download.latestVersionWindow.map { it.version })
        assertTrue(download.latestVersionWindow.all { it.sha256.length == 64 && it.url.startsWith("https://") })
        assertEquals("tag_name", download.latestJsonField)
        assertEquals("v", download.latestStripPrefix)
        assertEquals("/workspace/.kf/software/kite.shizuku/shizuku.apk", action.androidPackageHandoff?.path)
        assertEquals("moe.shizuku.privileged.api", action.androidPackageHandoff?.packageName)
    }
}
