package com.kite.app.resources

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteResourceInstallPlanCompilerTest {
    @Test
    fun officialScriptUsesManagedDownloadWithoutHeartbeatOrPipeInstall() {
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
        assertTrue(script.contains("mv -f \"${'$'}partial\" \"${'$'}destination\""))
        assertFalse(script.contains("KITE_RESOURCE_VIEW_TRANSACTION"))
        assertTrue(script.contains("KITE_RESOURCE_RETRY stage=acquire"))
        assertFalse(script.contains("KITE_RESOURCE_HEARTBEAT stage="))
        assertTrue(script.contains("KITE_RESOURCE_FAILURE stage="))
        assertTrue(script.contains("bash"))
        assertFalse(Regex("curl[^\\n]*\\|\\s*(bash|sh)").containsMatchIn(script))
    }

    @Test
    fun multipleDownloadSourcesRequirePinnedDigest() {
        val failure = runCatching {
            KiteResourceInstallPlanCompiler.compile(
                managedAction(
                    steps = listOf(
                        KiteResourceInstallStep(
                            id = "untrusted-mirrors",
                            type = KiteResourceInstallPlanCompiler.STEP_DOWNLOAD,
                            urls = listOf(
                                "https://official.example.test/payload",
                                "https://mirror.example.test/payload",
                            ),
                            destination = "${'$'}install_root/payload",
                        )
                    )
                )
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("requires SHA-256"))
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
        assertTrue(script.contains("npm install -g --loglevel=http --prefix=\"${'$'}attempt_prefix\""))
        assertTrue(script.contains("'--allow-scripts=@example/cli' '@example/cli'"))
        assertTrue(script.contains("Acquire::Retries=4"))
        assertTrue(script.contains("apt-get"))
        assertTrue(script.contains("candidate=\"${'$'}destination.kite-clone\""))
        assertTrue(script.contains("git clone --depth"))
    }

    @Test
    fun npmRegistriesUseOneSharedFallbackTransaction() {
        val action = managedAction(
            steps = listOf(
                KiteResourceInstallStep(
                    id = "npm-package",
                    type = KiteResourceInstallPlanCompiler.STEP_NPM,
                    packages = listOf("@example/cli"),
                    registries = listOf(
                        "https://registry.npmmirror.com",
                        "https://registry.npmjs.org",
                    ),
                )
            ),
            verifications = listOf(
                KiteResourceInstallVerification(
                    id = "installed-version",
                    cmd = "example --version",
                ),
            ),
        )

        val script = KiteResourceInstallPlanCompiler.compile(action)

        assertTrue(script.indexOf("registry.npmmirror.com") < script.indexOf("registry.npmjs.org"))
        assertTrue(script.contains("KITE_RESOURCE_ROUTE stage=acquire"))
        assertTrue(script.contains("npm install -g --loglevel=http"))
        assertTrue(script.contains("--registry=\"${'$'}npm_registry\""))
        assertTrue(script.contains("[ \"${'$'}last_status\" -ne 0 ] && kite_resource_is_source_failure"))
        assertFalse(script.contains("[ \"${'$'}last_status\" -eq 0 ] && [ \"${'$'}source_unavailable\" -eq 0 ]"))
        assertTrue(script.contains("attempt_prefix=\"${'$'}npm_config_prefix\""))
        assertTrue(script.contains("mkdir -p \"${'$'}attempt_root\" \"${'$'}attempt_prefix\""))
        assertTrue(script.contains("export PATH=\"${'$'}attempt_prefix/bin:${'$'}PATH\""))
        assertTrue(script.contains("example --version"))
        assertTrue(script.contains("reason=candidate-verification"))
        assertTrue(script.contains("retry_reason=source-incomplete"))
        assertFalse(script.contains("if [ \"${'$'}last_status\" -eq 0 ]; then last_status=69"))
        assertFalse(script.contains("mv \"${'$'}attempt_prefix\" \"${'$'}npm_config_prefix\""))
        assertTrue(script.contains("kite_resource_run 'npm-package' install kite_resource_npm_npm_package"))
    }

    @Test
    fun npmRegistryRejectsInsecureOrCredentialBearingUrls() {
        listOf(
            "http://registry.example.test",
            "https://user:password@registry.example.test",
            "https://registry.example.test?token=secret",
        ).forEach { registry ->
            val failure = runCatching {
                KiteResourceInstallPlanCompiler.compile(
                    managedAction(
                        steps = listOf(
                            KiteResourceInstallStep(
                                id = "npm-package",
                                type = KiteResourceInstallPlanCompiler.STEP_NPM,
                                packages = listOf("example"),
                                registries = listOf(registry),
                            )
                        )
                    )
                )
            }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
        }
    }

    @Test
    fun gitMirrorsShareOnePinnedCommitRespectSourcePriorityAndStopOnMismatch() {
        val commit = "5fc308a70719a83cccdbba4c0e39c23f5a8239d5"
        val action = managedAction(
            steps = listOf(
                KiteResourceInstallStep(
                    id = "source",
                    type = KiteResourceInstallPlanCompiler.STEP_GIT,
                    repositories = listOf(
                        "https://github.com/NousResearch/hermes-agent.git",
                        "https://gitcode.com/GitHub_Trending/he/hermes-agent.git",
                    ),
                    destination = "${'$'}install_root/hermes-agent",
                    ref = "v2026.8.27",
                    commit = commit,
                    retryAttempts = 1,
                    retryDelaySeconds = 0,
                )
            )
        )

        val script = KiteResourceInstallPlanCompiler.compile(action)

        assertTrue(script.indexOf("gitcode.com/GitHub_Trending") < script.indexOf("github.com/NousResearch"))
        assertTrue(script.contains("git-commit-mismatch"))
        assertTrue(script.contains(commit))
        assertTrue(script.contains("for repository in"))
        assertFalse(script.contains("if wait \"${'$'}task_pid\"; then"))
        val mismatchBlock = script.substringAfter("reason=git-commit-mismatch")
        assertTrue(mismatchBlock.substringBefore("KITE_RESOURCE_FAILURE stage=acquire").contains("return 65"))
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
