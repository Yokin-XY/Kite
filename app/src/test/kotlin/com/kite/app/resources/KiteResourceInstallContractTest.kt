package com.kite.app.resources

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteResourceInstallContractTest {
    @Test
    fun displayChangesDoNotInvalidateInstalledContent() {
        val installed = manifest(description = "旧说明", command = "agent")
        val current = manifest(description = "新说明", command = "agent")

        assertFalse(KiteResourceInstallContract.hasDrift(current, installed.toString()))
    }

    @Test
    fun installActionChangesInvalidateSameVersionInstallation() {
        val installed = manifest(description = "说明", command = "agent")
        val current = manifest(description = "说明", command = "agent-next")

        assertTrue(KiteResourceInstallContract.hasDrift(current, installed.toString()))
    }

    @Test
    fun missingOrMalformedSnapshotRequiresOneRepair() {
        val current = manifest(description = "说明", command = "agent")

        assertTrue(KiteResourceInstallContract.hasDrift(current, null))
        assertTrue(KiteResourceInstallContract.hasDrift(current, "not-json"))
    }

    private fun manifest(description: String, command: String): JSONObject = JSONObject(
        """
        {
          "id":"test.agent",
          "base":{"name":"Agent","description":"$description","version":"1.0.0"},
          "management":{"mode":"managed_extension","managedCommands":["$command"]},
          "display":{"longDescription":"$description"},
          "relations":{"base":["test.runtime"],"defaults":[]},
          "source":{"type":"bundled","asset":"agent"},
          "paths":{"installRoot":"/workspace/.kf/software/test.agent","binRoot":"/workspace/.kf/bin"},
          "actions":{"install":[{"type":"managed","managedCommands":["$command"]}]}
        }
        """.trimIndent()
    )
}
