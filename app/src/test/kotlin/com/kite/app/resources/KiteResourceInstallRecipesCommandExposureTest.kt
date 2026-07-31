package com.kite.app.resources

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteResourceInstallRecipesCommandExposureTest {
    @Test
    fun bundledToolchainStagesCommandsInResourceOwnedBin() {
        val script = KiteResourceInstallRecipes.localToolchainCommand(
            resourceId = "kite.nodejs",
            mode = "--install-node"
        )

        assertTrue(script.contains("export KF_TOOLCHAIN_BIN_DIR=\"${'$'}KF_TOOLCHAIN_DIR/bin\""))
        assertFalse(script.contains("export KF_TOOLCHAIN_BIN_DIR=\"${KiteResourceInstallRecipes.WORKSPACE_BIN_ROOT}\""))
    }

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
        assertTrue(script.contains("rollback_install_transaction"))
        assertFalse(script.contains("KITE_RESOURCE_VIEW_TRANSACTION"))
        assertFalse(script.contains("clean-view-install-root"))
        assertTrue(script.contains("cleanup_obsolete_command_links"))
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
        assertTrue(script.contains("is_safe_explicit_command_target \"${'$'}existing_target\" \"${'$'}command_name\""))
        assertTrue(script.contains("legacy_kite_wrapper_target"))
        assertTrue(script.contains("adopt-legacy-command"))
        assertTrue(script.contains("\"${'$'}install_root\"/*)"))
        assertTrue(script.contains("exit 127"))
        assertTrue(script.contains("\"/usr/bin/${'$'}command_name\""))
        assertTrue(script.contains("\"/bin/${'$'}command_name\""))
        assertTrue(script.contains("KITE_RESOURCE_FAILURE stage=verify step=command-link"))
    }

    @Test
    fun manifestInstallPropagatesManifestCommandFailureBeforeRecordingOwnership() {
        val script = KiteResourceInstallRecipes.manifestInstallCommand(
            resourceId = "kite.example",
            displayName = "Example",
            rawCommand = "curl -fsSL https://invalid.example/install.sh -o install.sh && bash install.sh",
            managedCommands = listOf("example"),
            cleanInstallRoot = true
        )

        val commandIndex = script.indexOf("curl -fsSL https://invalid.example/install.sh")
        val failureCheckIndex = script.indexOf("manifest-install-failed kite.example")
        val ownershipIndex = script.indexOf("installed_by_kite")

        assertTrue(script.contains("manifest_install_status=${'$'}?"))
        assertTrue(script.contains("exit \"${'$'}manifest_install_status\""))
        assertTrue(commandIndex >= 0)
        assertTrue(failureCheckIndex > commandIndex)
        assertTrue(ownershipIndex > failureCheckIndex)
    }

    @Test
    fun manifestUninstallRemovesOnlyLedgerOwnedCommandLinks() {
        val script = KiteResourceInstallRecipes.manifestUninstallCommand(
            resourceId = "kite.example",
            rawCommand = ":",
            managedCommands = listOf("example"),
            npmUninstallPackages = emptyList(),
            preservePaths = listOf("user-home", "user-home/.config/example", "../escape")
        )

        assertTrue(script.contains(".kite-managed-commands"))
        assertTrue(script.contains("readlink"))
        assertTrue(script.contains("export npm_config_prefix=\"${'$'}npm_prefix\""))
        assertTrue(script.contains("if [ \"${'$'}current_target\" = \"${'$'}target_path\" ]; then"))
        assertTrue(script.contains("legacy_kite_wrapper_target"))
        assertTrue(script.contains("[ \"${'$'}wrapper_target\" = \"${'$'}target_path\" ]"))
        assertTrue(script.contains("\"${'$'}install_root\"/*)"))
        assertTrue(script.contains("kite.example.kite-retained"))
        assertTrue(script.contains("KITE_RESOURCE_STEP retain-user-path ${'$'}preserved_relative"))
        assertTrue(script.contains("preserved_relative='user-home'"))
        assertFalse(script.contains("preserved_relative='user-home/.config/example'"))
        assertFalse(script.contains("preserved_relative='../escape'"))
        assertTrue(script.contains("kite-uninstall-staging-${'$'}${'$'}"))
        assertTrue(script.contains("mv \"${'$'}install_root\" \"${'$'}uninstall_staging\""))
        assertTrue(script.contains("KITE_RESOURCE_FAILURE stage=uninstall step=remove-install-root"))
    }

    @Test
    fun manifestRollbackKeepsPreexistingPublicCommands() {
        val script = KiteResourceInstallRecipes.manifestInstallCommand(
            resourceId = "kite.example",
            displayName = "Example",
            rawCommand = ":",
            managedCommands = listOf("example"),
            cleanInstallRoot = true
        )

        assertTrue(script.contains("if [ -e \"${'$'}link_path\" ] || [ -L \"${'$'}link_path\" ]; then"))
        assertTrue(script.contains("ln -s \"${'$'}target_path\" \"${'$'}link_path\""))
        assertFalse(
            script.contains(
                "ln -sfn \"${'$'}target_path\" \"${KiteResourceInstallRecipes.WORKSPACE_BIN_ROOT}/${'$'}command_name\""
            )
        )
    }

    @Test
    fun manifestUpdateCapturesActualVersionAndRejectsWrongTargetBeforeCommit() {
        val script = KiteResourceInstallRecipes.manifestInstallCommand(
            resourceId = "kite.example",
            displayName = "Example",
            rawCommand = ":",
            managedCommands = listOf("example"),
            cleanInstallRoot = true,
            versionProbeCommand = "example --version",
            expectedVersion = "v1.2.3",
            preservePaths = listOf("user-home", "../escape")
        )

        val targetCheckIndex = script.indexOf("KITE_RESOURCE_FAILURE stage=verify step=target-version")
        val commitIndex = script.indexOf("KITE_RESOURCE_STEP commit-install")

        assertTrue(script.contains("example --version"))
        assertTrue(script.contains("expected_version='v1.2.3'"))
        assertTrue(script.contains("grep -Fxq \"${'$'}expected_version\""))
        assertTrue(script.contains("KITE_RESOURCE_INSTALLED_VERSION ${'$'}installed_version_single_line"))
        assertTrue(script.contains("KITE_RESOURCE_STEP replace-owned-command ${'$'}command_name"))
        assertTrue(script.contains("KITE_RESOURCE_STEP restore-preserved-path ${'$'}preserved_relative"))
        assertTrue(script.contains("KITE_RESOURCE_STEP restore-retained-path ${'$'}preserved_relative"))
        assertTrue(script.contains("preserved_source_has_content \"${'$'}preserved_retained\""))
        assertFalse(script.contains("view_transaction"))
        assertTrue(script.contains("clear_preserved_source \"${'$'}retained_root/user-home\""))
        assertTrue(script.contains("kite.example.kite-retained"))
        assertTrue(script.contains("preserved_relative='user-home'"))
        assertTrue(script.contains("cp -a --no-preserve=timestamps"))
        assertFalse(script.contains("preserved_relative='../escape'"))
        assertTrue(targetCheckIndex >= 0)
        assertTrue(commitIndex > targetCheckIndex)
        assertTrue(script.contains("ownership_tmp=\"${'$'}install_root/.ownership.tmp-${'$'}${'$'}\""))
        assertTrue(script.contains("mv -f \"${'$'}ownership_tmp\" \"${'$'}install_root/ownership\""))
        assertFalse(script.contains("'installed_by_kite' > \"${'$'}install_root/ownership\""))
    }

    @Test
    fun manifestUpdateRetainsExistingOwnershipWithoutRewritingProtectedLowerFile() {
        val script = KiteResourceInstallRecipes.manifestInstallCommand(
            resourceId = "kite.example",
            displayName = "Example",
            rawCommand = ":",
            managedCommands = emptyList(),
            cleanInstallRoot = false,
            recordOwnership = false
        )

        assertTrue(script.contains("KITE_RESOURCE_STEP retain-install-ownership kite.example"))
        assertFalse(script.contains("ownership_tmp="))
        assertFalse(script.contains("installed_by_kite"))
    }
}
