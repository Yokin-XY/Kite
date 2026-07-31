package com.kite.app.recipe

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteRecipeMigrationMarkerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun createsMissingCardsDirectoryBeforeWritingMarker() {
        val cardsDir = File(temp.root, "Download/Kite/cards")
        val markerName = ".legacy-private-migrated-v1"

        val result = writeRecipeMaintenanceMarker(cardsDir, markerName, "completed")

        assertTrue(result.isSuccess)
        assertTrue(cardsDir.isDirectory)
        assertEquals("completed", File(cardsDir, markerName).readText())
    }

    @Test
    fun returnsFailureInsteadOfThrowingWhenCardsPathCannotBeCreated() {
        val cardsPathOccupiedByFile = temp.newFile("cards")

        val result = writeRecipeMaintenanceMarker(
            cardsPathOccupiedByFile,
            ".legacy-private-migrated-v1",
            "completed"
        )

        assertTrue(result.isFailure)
        assertFalse(File(cardsPathOccupiedByFile, ".legacy-private-migrated-v1").exists())
    }

    @Test
    fun migratesOnlyTheDeprecatedOpenClawOnboardHomeCard() {
        val legacy = JSONObject()
            .put(
                "base",
                JSONObject()
                    .put("id", "1782789184211")
                    .put("name", "OpenClaw")
                    .put("description", "在终端里启动 OpenClaw onboard")
            )
            .put(
                "recipe",
                JSONArray().put(
                    JSONObject()
                        .put("type", "terminal")
                        .put(
                            "text",
                            "cd /workspace\necho \"OpenClaw 首次启动建议完成 onboard。\"\n" +
                                "openclaw onboard --install-daemon\n"
                        )
                )
            )
            .put("card", JSONObject().put("accent", "mint"))

        val migrated = requireNotNull(migrateDeprecatedOpenClawHomeCard(legacy))

        assertEquals("1782789184211", migrated.getJSONObject("base").getString("id"))
        assertEquals("在终端里直接启动 OpenClaw 对话", migrated.getJSONObject("base").getString("description"))
        assertEquals("openclaw chat", migrated.getJSONArray("recipe").getJSONObject(0).getString("cmd"))
        assertEquals("/workspace", migrated.getJSONArray("recipe").getJSONObject(0).getString("workdir"))
        assertEquals("mint", migrated.getJSONObject("card").getString("accent"))
        assertEquals("openclaw onboard --install-daemon", legacy.getJSONArray("recipe").getJSONObject(0)
            .getString("text").lineSequence().last { it.isNotBlank() })
    }

    @Test
    fun preservesUserAuthoredOpenClawCardsThatDoNotMatchTheShippedLegacyTemplate() {
        val custom = JSONObject()
            .put("base", JSONObject().put("id", "custom").put("name", "OpenClaw"))
            .put(
                "recipe",
                JSONArray().put(
                    JSONObject()
                        .put("type", "terminal")
                        .put("text", "openclaw onboard --install-daemon\necho custom\n")
                )
            )

        assertNull(migrateDeprecatedOpenClawHomeCard(custom))
    }
}
