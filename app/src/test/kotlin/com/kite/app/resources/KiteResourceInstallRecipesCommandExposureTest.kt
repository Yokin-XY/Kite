package com.kite.app.resources

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteResourceInstallRecipesCommandExposureTest {
    @Test
    fun manifestInstallAutoDiscoversResourceOwnedCommands() {
        val script = KiteResourceInstallRecipes.manifestInstallCommand(
            resourceId = "kite.example",
            displayName = "Example",
            rawCommand = "mkdir -p \"${'$'}install_root/bin\" && touch \"${'$'}install_root/bin/example\" && chmod +x \"${'$'}install_root/bin/example\"",
            managedCommands = emptyList(),
            cleanInstallRoot = true
        )

        assertTrue(script.contains("snapshot_public_commands"))
        assertTrue(script.contains("auto_commands="))
        assertTrue(script.contains(".kite-managed-commands"))
        assertTrue(script.contains("remove_recorded_command_links"))
        assertTrue(script.contains("export npm_config_prefix=\"${'$'}npm_prefix\""))
        assertTrue(script.contains("clear-build-apt-proxy"))
        assertTrue(script.contains("/etc/apt/apt.conf.d/99kite-proxy"))
        assertTrue(script.contains("python|python[0-9]*|pip|pip[0-9]*|node|npm|npx|corepack|uv|uvx"))
        assertTrue(script.contains("command -v \"${'$'}command_name\""))
        assertFalse(script.contains("--version"))
        assertFalse(script.contains("--help"))
        assertFalse(script.contains("managedCommands"))
    }

    @Test
    fun manifestInstallLinksExplicitSystemCommandsAndFailsOnExplicitConflicts() {
        val script = KiteResourceInstallRecipes.manifestInstallCommand(
            resourceId = "kite.example",
            displayName = "Example",
            rawCommand = ":",
            managedCommands = listOf("example"),
            cleanInstallRoot = false
        )

        assertTrue(script.contains("is_explicit_command"))
        assertTrue(script.contains("is_safe_explicit_command_target"))
        assertTrue(script.contains("if is_explicit_command \"${'$'}command_name\"; then"))
        assertTrue(script.contains("is_explicit_command \"${'$'}command_name\" && is_safe_explicit_command_target"))
        assertTrue(script.contains("exit 127"))
        assertTrue(script.contains("\"/usr/bin/${'$'}command_name\""))
        assertTrue(script.contains("\"/bin/${'$'}command_name\""))
        assertTrue(script.contains("command ${'$'}command_name could not be linked"))
    }

    @Test
    fun manifestUninstallRemovesOnlyLedgerOwnedCommandLinks() {
        val script = KiteResourceInstallRecipes.manifestUninstallCommand(
            resourceId = "kite.example",
            rawCommand = ":",
            managedCommands = listOf("example"),
            npmUninstallPackages = emptyList()
        )

        assertTrue(script.contains(".kite-managed-commands"))
        assertTrue(script.contains("readlink"))
        assertTrue(script.contains("export npm_config_prefix=\"${'$'}npm_prefix\""))
        assertTrue(script.contains("if [ \"${'$'}current_target\" = \"${'$'}target_path\" ]; then"))
        assertTrue(script.contains("\"${'$'}install_root\"/*)"))
    }
}
