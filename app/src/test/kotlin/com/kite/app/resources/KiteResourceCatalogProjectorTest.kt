package com.kite.app.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteResourceCatalogProjectorTest {
    private val loader by lazy {
        KiteResourceManifestLoader(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun `manifest 自身决定分区标签页和稳定顺序`() {
        val layout = KiteResourceHomeLayout(
            sections = listOf(
                KiteResourceHomeSection("foundation", "基础环境", "list", emptyList()),
                KiteResourceHomeSection("more", "更多资源", "list", listOf("kite.legacy"))
            ),
            hero = KiteResourceHomeHero("kite.second", "banner.png", ""),
            tabs = listOf(
                KiteResourceHomeTab("all", "全部", emptyList()),
                KiteResourceHomeTab(
                    "engineering",
                    "工程验证",
                    listOf(KiteResourceHomeSection("engineering", "工程验证", "list", emptyList()))
                ),
                KiteResourceHomeTab(
                    "foundation",
                    "基础环境",
                    listOf(KiteResourceHomeSection("foundation", "基础环境", "list", emptyList()))
                )
            ),
            chips = emptyList(),
            rawJson = JSONObject()
        )
        val manifests = listOf(
            manifest("kite.first", section = "foundation", order = 20, tabs = listOf("engineering")),
            manifest("kite.second", section = "foundation", order = 10, tabs = listOf("engineering")),
            manifest("kite.legacy", section = "more", order = 30),
            manifest("kite.hidden", section = "", order = 0)
        )

        val projected = KiteResourceCatalogProjector.project(layout, manifests)

        assertEquals(listOf("kite.second", "kite.first"), projected.sections[0].items)
        assertEquals(listOf("kite.legacy"), projected.sections[1].items)
        assertEquals(emptyList<KiteResourceHomeSection>(), projected.tabs[0].sections)
        assertEquals(
            listOf("kite.second", "kite.first"),
            projected.tabs[1].sections.single().items
        )
        assertEquals(
            listOf("kite.second", "kite.first"),
            projected.tabs[2].sections.single().items
        )
        assertEquals("kite.second", projected.hero?.resourceId)
    }

    private fun manifest(
        id: String,
        section: String,
        order: Int,
        tabs: List<String> = emptyList()
    ): KiteResourceManifest = loader.parseManifestJson(
        """
            {
              "schemaVersion":2,
              "id":"$id",
              "base":{"name":"$id","description":"","version":"1"},
              "display":{
                "sections":[${if (section.isBlank()) "" else "\"$section\""}],
                "tabs":${tabs.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }},
                "order":$order
              },
              "management":{"mode":"managed_extension"},
              "source":{"type":"bundled"}
            }
        """.trimIndent()
    )
}
