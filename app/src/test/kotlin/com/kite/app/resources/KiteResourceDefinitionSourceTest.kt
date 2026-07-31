package com.kite.app.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteResourceDefinitionSourceTest {
    @Test
    fun `APK 内置来源一次提供完整目录快照`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val snapshot = KiteResourceAssetDefinitionSource(context).snapshot()

        assertTrue(snapshot.manifests.keys.toString(), "kite.opencode" in snapshot.manifests)
        assertTrue(snapshot.manifests.keys.toString(), "kite.openclaw" in snapshot.manifests)
        assertTrue(snapshot.homeLayoutJson.orEmpty().contains("\"sections\""))
    }

    @Test
    fun `高优先级来源覆盖同名资源且沿用首个布局`() {
        val remote = FixedSource(
            KiteResourceDefinitionSnapshot(
                revision = "remote-2",
                manifests = mapOf(
                    "kite.remote" to "remote-only",
                    "kite.shared" to "remote-shared"
                )
            )
        )
        val bundled = FixedSource(
            KiteResourceDefinitionSnapshot(
                revision = "apk-1",
                manifests = mapOf(
                    "kite.bundled" to "bundled-only",
                    "kite.shared" to "bundled-shared"
                ),
                homeLayoutJson = "bundled-layout"
            )
        )

        val snapshot = KiteResourceCompositeDefinitionSource(listOf(remote, bundled)).snapshot()

        assertEquals("remote-2|apk-1", snapshot.revision)
        assertEquals("remote-shared", snapshot.manifests.getValue("kite.shared"))
        assertEquals("remote-only", snapshot.manifests.getValue("kite.remote"))
        assertEquals("bundled-only", snapshot.manifests.getValue("kite.bundled"))
        assertEquals("bundled-layout", snapshot.homeLayoutJson)
    }

    @Test
    fun `任意来源都由同一个 Loader 解析并投影目录`() {
        val source = FixedSource(
            KiteResourceDefinitionSnapshot(
                revision = "memory-1",
                manifests = mapOf(
                    "kite.memory" to """
                        {
                          "schemaVersion":2,
                          "id":"kite.memory",
                          "base":{"name":"Memory","description":"","version":"1"},
                          "display":{"sections":["more"],"order":5},
                          "management":{"mode":"managed_extension"},
                          "source":{"type":"npm","package":"memory"}
                        }
                    """.trimIndent()
                ),
                homeLayoutJson = """
                    {
                      "schemaVersion":2,
                      "sections":[{"id":"more","title":"更多资源","style":"list"}],
                      "tabs":[{"id":"all","label":"全部"}]
                    }
                """.trimIndent()
            )
        )
        val loader = KiteResourceManifestLoader(
            isDebugBuild = false,
            definitionSources = listOf(source)
        )

        assertNotNull(loader.requestManifest("kite.memory"))
        assertEquals(listOf("kite.memory"), loader.requestHomeLayout()?.sections?.single()?.items)
    }

    private class FixedSource(
        private val value: KiteResourceDefinitionSnapshot
    ) : KiteResourceDefinitionSource {
        override fun snapshot(): KiteResourceDefinitionSnapshot = value

        override fun invalidate() = Unit
    }
}
