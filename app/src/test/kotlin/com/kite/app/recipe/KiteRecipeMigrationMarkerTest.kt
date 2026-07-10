package com.kite.app.recipe

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
}
