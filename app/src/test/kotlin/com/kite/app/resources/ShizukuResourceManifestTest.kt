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
    fun `Shizuku卡片固定官方APK身份并声明系统安装器交接`() {
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
        val action = manifest.installActions.single()
        assertEquals(
            "6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f",
            action.installSteps.single().sha256,
        )
        assertEquals("/workspace/.kf/software/kite.shizuku/shizuku.apk", action.androidPackageHandoff?.path)
        assertEquals("moe.shizuku.privileged.api", action.androidPackageHandoff?.packageName)
    }
}
