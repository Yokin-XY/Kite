package com.kite.app.resources

import org.json.JSONObject
import org.junit.Assert.assertEquals
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
        assertEquals(
            KiteResourceInstallContractResolution.RepairRequired,
            KiteResourceInstallContract.resolve(current, installed.toString()),
        )
    }

    @Test
    fun versionedContractChangeWithExplicitUpdateIsMigratable() {
        val installed = manifest(description = "说明", command = "agent", version = "1.0.0")
        val current = manifest(
            description = "说明",
            command = "agent-next",
            version = "1.1.0",
            updateCommand = "migrate-agent",
        )

        assertEquals(
            KiteResourceInstallContractResolution.UpdateAvailable("1.0.0", "1.1.0"),
            KiteResourceInstallContract.resolve(current, installed.toString()),
        )
    }

    @Test
    fun missingOrMalformedSnapshotRequiresOneRepair() {
        val current = manifest(description = "说明", command = "agent")

        assertTrue(KiteResourceInstallContract.hasDrift(current, null))
        assertTrue(KiteResourceInstallContract.hasDrift(current, "not-json"))
    }

    @Test
    fun latestVersionMaintenanceDoesNotInvalidateInstalledContent() {
        val installed = officialCommandManifest(latestVersion = "2.1.272")
        val current = officialCommandManifest(latestVersion = "2.1.274")

        assertFalse(KiteResourceInstallContract.hasDrift(current, installed.toString()))
        assertEquals(
            KiteResourceInstallContractResolution.Current,
            KiteResourceInstallContract.resolve(current, installed.toString()),
        )
    }

    @Test
    fun officialCommandChangeStillInvalidateInstalledContent() {
        val installed = officialCommandManifest(latestVersion = "2.1.272", command = "npm install -g agent@latest")
        val current = officialCommandManifest(latestVersion = "2.1.290", command = "npm install -g agent2@latest")

        assertTrue(KiteResourceInstallContract.hasDrift(current, installed.toString()))
    }

    private fun officialCommandManifest(
        latestVersion: String,
        command: String = "npm install -g agent@latest",
    ): JSONObject = JSONObject(
        """
        {
          "id":"test.official",
          "base":{"name":"Official","description":"官方机制","version":"npm"},
          "management":{"mode":"managed_extension","managedCommands":["agent"]},
          "source":{
            "type":"official_command",
            "package":"agent",
            "command":"$command",
            "uninstallCommand":"npm uninstall -g agent",
            "latestVersion":"$latestVersion"
          },
          "paths":{"installRoot":"/workspace/.kf/software/test.official","binRoot":"/workspace/.kf/bin"}
        }
        """.trimIndent()
    )

    private fun manifest(
        description: String,
        command: String,
        version: String = "1.0.0",
        updateCommand: String = "",
    ): JSONObject = JSONObject(
        """
        {
          "id":"test.agent",
          "base":{"name":"Agent","description":"$description","version":"$version"},
          "management":{"mode":"managed_extension","managedCommands":["$command"]},
          "display":{"longDescription":"$description"},
          "relations":{"base":["test.runtime"],"defaults":[]},
          "source":{"type":"bundled","asset":"agent"},
          "paths":{"installRoot":"/workspace/.kf/software/test.agent","binRoot":"/workspace/.kf/bin"},
          "actions":{
            "install":[{"type":"managed","managedCommands":["$command"]}]
            ${if (updateCommand.isBlank()) "" else ",\"update\":[{\"type\":\"managed\",\"steps\":[{\"type\":\"shell\",\"cmd\":\"$updateCommand\"}]}]"}
          }
        }
        """.trimIndent()
    )
}
