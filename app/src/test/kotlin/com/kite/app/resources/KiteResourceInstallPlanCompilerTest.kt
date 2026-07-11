package com.kite.app.resources

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteResourceInstallPlanCompilerTest {
    @Test
    fun officialScriptUsesManagedDownloadAndHeartbeatWithoutPipeInstall() {
        val action = managedAction(
            steps = listOf(
                KiteResourceInstallStep(
                    id = "official-installer",
                    type = KiteResourceInstallPlanCompiler.STEP_DOWNLOAD,
                    urls = listOf("https://example.com/install.sh"),
                    destination = "${'$'}install_root/.kite-downloads/install.sh"
                ),
                KiteResourceInstallStep(
                    id = "run-installer",
                    type = KiteResourceInstallPlanCompiler.STEP_SCRIPT,
                    interpreter = "bash",
                    path = "${'$'}install_root/.kite-downloads/install.sh",
                    arguments = listOf("--dir", "${'$'}install_root/app")
                )
            )
        )

        val script = KiteResourceInstallPlanCompiler.compile(action)

        assertTrue(script.contains("kite_resource_download"))
        assertTrue(script.contains(".part"))
        assertTrue(script.contains("KITE_RESOURCE_RETRY stage=acquire"))
        assertTrue(script.contains("KITE_RESOURCE_HEARTBEAT stage="))
        assertTrue(script.contains("KITE_RESOURCE_FAILURE stage="))
        assertTrue(script.contains("bash"))
        assertFalse(Regex("curl[^\\n]*\\|\\s*(bash|sh)").containsMatchIn(script))
    }

    @Test
    fun packageAndGitStepsUseChannelSpecificRecoveryPolicies() {
        val action = managedAction(
            steps = listOf(
                KiteResourceInstallStep(
                    id = "npm-package",
                    type = KiteResourceInstallPlanCompiler.STEP_NPM,
                    packages = listOf("@example/cli"),
                    arguments = listOf("--allow-scripts=@example/cli")
                ),
                KiteResourceInstallStep(
                    id = "apt-packages",
                    type = KiteResourceInstallPlanCompiler.STEP_APT,
                    packages = listOf("jq"),
                    updateIndex = true
                ),
                KiteResourceInstallStep(
                    id = "source",
                    type = KiteResourceInstallPlanCompiler.STEP_GIT,
                    repository = "https://github.com/example/repo.git",
                    destination = "${'$'}install_root/repo"
                )
            )
        )

        val script = KiteResourceInstallPlanCompiler.compile(action)

        assertTrue(script.contains("npm_config_fetch_retries"))
        assertTrue(script.contains("npm install -g '--allow-scripts=@example/cli' '@example/cli'"))
        assertTrue(script.contains("Acquire::Retries=4"))
        assertTrue(script.contains("apt-get"))
        assertTrue(script.contains("candidate=\"${'$'}destination.kite-clone\""))
        assertTrue(script.contains("git clone --depth"))
    }

    @Test
    fun verificationEmitsFailureBeforeInstallCommit() {
        val action = managedAction(
            steps = listOf(
                KiteResourceInstallStep(id = "install", type = KiteResourceInstallPlanCompiler.STEP_SHELL, cmd = ":")
            ),
            verifications = listOf(
                KiteResourceInstallVerification(id = "version", cmd = "example --version")
            )
        )
        val verification = KiteResourceInstallPlanCompiler.compileVerification(action)
        val wrapped = KiteResourceInstallRecipes.manifestInstallCommand(
            resourceId = "kite.example",
            displayName = "Example",
            rawCommand = KiteResourceInstallPlanCompiler.compile(action),
            managedCommands = listOf("example"),
            cleanInstallRoot = true,
            verificationCommand = verification
        )

        assertTrue(wrapped.contains("KITE_RESOURCE_FAILURE stage=verify step=version"))
        assertTrue(wrapped.contains("KITE_RESOURCE_STEP rollback-install kite.example"))
        assertTrue(wrapped.contains("recover-interrupted-install"))
        assertTrue(wrapped.indexOf("verify version") < wrapped.indexOf("commit-install kite.example"))
        assertTrue(wrapped.indexOf("commit-install kite.example") < wrapped.indexOf("installed_by_kite"))
    }

    private fun managedAction(
        steps: List<KiteResourceInstallStep>,
        verifications: List<KiteResourceInstallVerification> = emptyList()
    ): KiteResourceShellAction = KiteResourceShellAction(
        type = KiteResourceInstallPlanCompiler.ACTION_MANAGED,
        cmd = "",
        surfaceMode = "panel",
        workdir = "/workspace",
        timeoutMs = 600_000L,
        managedCommands = listOf("example"),
        cleanInstallRoot = true,
        npmUninstallPackages = emptyList(),
        installSteps = steps,
        verifications = verifications
    )
}
