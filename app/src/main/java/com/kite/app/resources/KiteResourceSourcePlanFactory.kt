package com.kite.app.resources

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed interface KiteResourceLatestVersionProbe

data class KiteResourceCommandVersionProbe(
    val probe: KiteResourceVersionProbeSpec
) : KiteResourceLatestVersionProbe

data class KiteResourceRemoteVersionProbe(
    val url: String,
    val jsonField: String = "",
    val format: String = "json",
    val stripPrefix: String = "",
    val fallbackUrl: String = ""
) : KiteResourceLatestVersionProbe

data class KiteResourceVersionCheckPlan(
    val installed: KiteResourceVersionProbeSpec?,
    val latest: KiteResourceLatestVersionProbe?
) {
    val supported: Boolean
        get() = installed != null && latest != null
}

data class KiteResourceSourceCapabilities(
    val install: Boolean,
    val checkUpdate: Boolean,
    val update: Boolean,
    val uninstall: Boolean
)

data class KiteResourceSourcePlan(
    val installActions: List<KiteResourceShellAction>,
    val uninstallActions: List<KiteResourceShellAction>,
    val versionCheck: KiteResourceVersionCheckPlan,
    val capabilities: KiteResourceSourceCapabilities,
    val generatedFromSource: Boolean
)

/**
 * 把标准来源声明编译成资源动作。复杂资源仍可提供显式 actions，显式动作优先。
 */
object KiteResourceSourcePlanFactory {
    fun plan(manifest: KiteResourceManifest, targetVersion: String? = null): KiteResourceSourcePlan {
        if (!manifest.management.userLifecycleEnabled) {
            return KiteResourceSourcePlan(
                installActions = manifest.installActions,
                uninstallActions = manifest.uninstallActions,
                versionCheck = KiteResourceVersionCheckPlan(null, null),
                capabilities = KiteResourceSourceCapabilities(
                    install = false,
                    checkUpdate = false,
                    update = false,
                    uninstall = false
                ),
                generatedFromSource = false
            )
        }

        val explicitInstall = manifest.installActions.isNotEmpty()
        val explicitUpdate = manifest.updateActions.isNotEmpty()
        val explicitUninstall = manifest.uninstallActions.isNotEmpty()
        val generatedInstall = if (explicitInstall || explicitUpdate && targetVersion != null) {
            emptyList()
        } else {
            generatedInstallActions(manifest, targetVersion)
        }
        val generatedUninstall = if (explicitUninstall) emptyList() else generatedUninstallActions(manifest)
        val installActions = when {
            targetVersion != null && explicitUpdate -> manifest.updateActions
            explicitInstall -> manifest.installActions
            else -> generatedInstall
        }
        val uninstallActions = manifest.uninstallActions.ifEmpty { generatedUninstall }
        val versionCheck = versionCheckPlan(manifest)
        val supportsTargetVersion = when {
            explicitUpdate -> true
            explicitInstall -> false
            manifest.source.type in TARGET_VERSION_SOURCES -> true
            isManagedScriptProfile(manifest) -> true
            manifest.source.type == SOURCE_OFFICIAL_SCRIPT ->
                manifest.source.versionArguments.any { "{version}" in it }
            else -> false
        }

        return KiteResourceSourcePlan(
            installActions = installActions,
            uninstallActions = uninstallActions,
            versionCheck = versionCheck,
            capabilities = KiteResourceSourceCapabilities(
                install = installActions.isNotEmpty(),
                checkUpdate = versionCheck.supported,
                update = versionCheck.supported && supportsTargetVersion,
                uninstall = uninstallActions.isNotEmpty()
            ),
            generatedFromSource = !explicitInstall && !explicitUpdate && generatedInstall.isNotEmpty()
        )
    }

    fun versionCheckPlan(manifest: KiteResourceManifest): KiteResourceVersionCheckPlan {
        if (!manifest.management.userLifecycleEnabled) return KiteResourceVersionCheckPlan(null, null)
        val latest = manifest.management.latestVersionProbe
            ?.let(::KiteResourceCommandVersionProbe)
            ?: when (manifest.source.type) {
                SOURCE_NPM -> npmVersionProbe(manifest.source)
                SOURCE_GITHUB_RELEASE -> githubVersionProbe(manifest.source)
                SOURCE_OFFICIAL_SCRIPT -> officialScriptVersionProbe(manifest.source)
                SOURCE_BUNDLED -> managedScriptProbe(manifest, "latest-version")
                else -> null
            }
        return KiteResourceVersionCheckPlan(
            installed = manifest.management.versionProbe ?: defaultInstalledVersionProbe(manifest),
            latest = latest
        )
    }

    private fun defaultInstalledVersionProbe(manifest: KiteResourceManifest): KiteResourceVersionProbeSpec? {
        managedScriptProbe(manifest, "current-version")?.let { return it.probe }
        if (manifest.source.type != SOURCE_NPM) return null
        val packageName = manifest.source.packageName.takeIf(SAFE_NPM_PACKAGE::matches) ?: return null
        val resourceId = manifest.id.takeIf(SAFE_RESOURCE_ID::matches) ?: return null
        val packageJson = "/workspace/.kf/software/$resourceId/npm-global/lib/node_modules/$packageName/package.json"
        return KiteResourceVersionProbeSpec(
            command = "node -p \"require('$packageJson').version\"",
            group = 0,
            structuredMetadata = KiteResourceMetadataVersionProbeSpec(
                containerPath = packageJson,
                maximumBytes = MAXIMUM_PACKAGE_METADATA_BYTES,
                jsonField = "version",
            ),
        )
    }

    private fun generatedInstallActions(
        manifest: KiteResourceManifest,
        targetVersion: String?
    ): List<KiteResourceShellAction> = when (manifest.source.type) {
        SOURCE_NPM -> npmInstallAction(manifest, targetVersion)?.let(::listOf).orEmpty()
        SOURCE_GITHUB_RELEASE -> githubReleaseInstallAction(manifest, targetVersion)?.let(::listOf).orEmpty()
        SOURCE_OFFICIAL_SCRIPT -> officialScriptInstallAction(manifest, targetVersion)?.let(::listOf).orEmpty()
        SOURCE_BUNDLED -> managedScriptInstallAction(manifest, targetVersion)?.let(::listOf).orEmpty()
        else -> emptyList()
    }

    private fun generatedUninstallActions(manifest: KiteResourceManifest): List<KiteResourceShellAction> {
        managedScriptUninstallAction(manifest)?.let { return listOf(it) }
        if (manifest.management.managedCommands.isEmpty()) return emptyList()
        val npmPackages = if (manifest.source.type == SOURCE_NPM) {
            npmPackageNames(manifest.source) ?: return emptyList()
        } else {
            emptyList()
        }
        return listOf(
            shellAction(
                command = ":",
                managedCommands = manifest.management.managedCommands,
                npmUninstallPackages = npmPackages
            )
        )
    }

    private fun managedScriptInstallAction(
        manifest: KiteResourceManifest,
        targetVersion: String?
    ): KiteResourceShellAction? {
        val contract = managedScriptContract(manifest) ?: return null
        val version = safeVersion(targetVersion ?: manifest.version)
        val operation = if (targetVersion == null) "install" else "update"
        val action = managedAction(
            steps = listOf(
                KiteResourceInstallStep(
                    id = "$operation-managed-script",
                    type = KiteResourceInstallPlanCompiler.STEP_SCRIPT,
                    interpreter = contract.interpreter,
                    path = contract.scriptPath,
                    arguments = contract.arguments(
                        operation = operation,
                        targetVersion = version
                    )
                )
            ),
            manifest = manifest
        )
        return action.copy(
            verifications = listOf(
                KiteResourceInstallVerification(
                    id = "managed-script-version",
                    cmd = contract.command(operation = "verify", expectedVersion = version)
                )
            )
        )
    }

    private fun managedScriptUninstallAction(manifest: KiteResourceManifest): KiteResourceShellAction? {
        val contract = managedScriptContract(manifest) ?: return null
        return shellAction(
            command = """
                echo "KITE_RESOURCE_STEP uninstall-managed-script ${contract.resourceId}"
                rm -rf ${shellLiteral(contract.controlRoot)}
            """.trimIndent(),
            managedCommands = manifest.management.managedCommands,
            npmUninstallPackages = emptyList()
        )
    }

    private fun managedScriptProbe(
        manifest: KiteResourceManifest,
        operation: String
    ): KiteResourceCommandVersionProbe? = managedScriptContract(manifest)?.let { contract ->
        KiteResourceCommandVersionProbe(
            KiteResourceVersionProbeSpec(
                command = contract.command(operation = operation),
                group = 0
            )
        )
    }

    private fun managedScriptContract(manifest: KiteResourceManifest): ManagedScriptContract? {
        val source = manifest.source
        if (source.type != SOURCE_BUNDLED || source.profile != PROFILE_MANAGED_SCRIPT_V1) return null
        if (source.asset.isBlank()) return null
        val resourceId = manifest.id.takeIf(SAFE_RESOURCE_ID::matches) ?: return null
        val interpreter = source.interpreter.takeIf(SAFE_INTERPRETER::matches) ?: return null
        val entry = source.entry.takeIf(SAFE_RELATIVE_ENTRY::matches)
            ?.takeUnless { it.split('/').any { segment -> segment == "." || segment == ".." } }
            ?: return null
        val cacheRoot = KiteResourceInstallRecipes.resourceCachePath(resourceId)
        return ManagedScriptContract(
            resourceId = resourceId,
            interpreter = interpreter,
            scriptPath = "$cacheRoot/bundle/$entry",
            installRoot = KiteResourceInstallRecipes.softwarePath(resourceId),
            controlRoot = "$cacheRoot/control"
        )
    }

    private data class ManagedScriptContract(
        val resourceId: String,
        val interpreter: String,
        val scriptPath: String,
        val installRoot: String,
        val controlRoot: String
    ) {
        fun arguments(
            operation: String,
            targetVersion: String? = null,
            expectedVersion: String? = null
        ): List<String> = buildList {
            add("--root")
            add(installRoot)
            add("--control-root")
            add(controlRoot)
            targetVersion?.let {
                add("--target-version")
                add(it)
            }
            expectedVersion?.let {
                add("--expect")
                add(it)
            }
            add(operation)
        }

        fun command(
            operation: String,
            targetVersion: String? = null,
            expectedVersion: String? = null
        ): String = buildList {
            add(shellLiteral(interpreter))
            add(shellLiteral(scriptPath))
            addAll(arguments(operation, targetVersion, expectedVersion).map(::shellLiteral))
        }.joinToString(" ")
    }

    private fun npmInstallAction(
        manifest: KiteResourceManifest,
        targetVersion: String?
    ): KiteResourceShellAction? {
        val packageNames = npmPackageNames(manifest.source) ?: return null
        val packageName = packageNames.first()
        val selector = targetVersion?.let(::safeVersion) ?: manifest.source.tag.ifBlank { "latest" }
        val packageSpec = "$packageName@$selector"
        val companionSpecs = packageNames.drop(1).map { "$it@latest" }
        return managedAction(
            steps = listOf(
                KiteResourceInstallStep(
                    id = "install-npm-package",
                    type = KiteResourceInstallPlanCompiler.STEP_NPM,
                    packages = listOf(packageSpec) + companionSpecs,
                    arguments = manifest.source.installArguments,
                    retryAttempts = 5,
                    retryDelaySeconds = 3
                )
            ),
            manifest = manifest
        )
    }

    private fun officialScriptInstallAction(
        manifest: KiteResourceManifest,
        targetVersion: String?
    ): KiteResourceShellAction? {
        if (targetVersion != null && manifest.source.versionArguments.none { "{version}" in it }) return null
        val url = manifest.source.url.takeIf(::isHttpUrl) ?: return null
        val arguments = if (targetVersion == null) {
            manifest.source.installArguments
        } else {
            val safeTarget = safeVersion(targetVersion)
            manifest.source.versionArguments.map { it.replace("{version}", safeTarget) }
        }
        return managedAction(
            steps = listOf(
                KiteResourceInstallStep(
                    id = "download-official-installer",
                    type = KiteResourceInstallPlanCompiler.STEP_DOWNLOAD,
                    urls = listOf(url),
                    destination = "${'$'}install_root/.kite-downloads/install.sh",
                    maxBytes = manifest.source.maxBytes,
                ),
                KiteResourceInstallStep(
                    id = "run-official-installer",
                    type = KiteResourceInstallPlanCompiler.STEP_SCRIPT,
                    interpreter = "bash",
                    path = "${'$'}install_root/.kite-downloads/install.sh",
                    arguments = arguments,
                    environment = manifest.source.environment
                )
            ),
            manifest = manifest
        )
    }

    private fun githubReleaseInstallAction(
        manifest: KiteResourceManifest,
        targetVersion: String?
    ): KiteResourceShellAction? {
        val slug = githubSlug(manifest.source.repository) ?: return null
        val assetPattern = manifest.source.assetPattern.takeIf(String::isNotBlank) ?: return null
        val command = manifest.management.managedCommands.singleOrNull() ?: return null
        val binaryPathTemplate = manifest.source.binaryPath.ifBlank { command }
        val archiveType = manifest.source.archiveType.ifBlank { inferArchiveType(assetPattern) }
        val version = targetVersion?.let(::safeVersion)
        val releaseTag = version?.let { githubReleaseTag(manifest.source, it) }
        val releaseBase = if (releaseTag == null) {
            "https://github.com/$slug/releases/latest/download"
        } else {
            "https://github.com/$slug/releases/download/$releaseTag"
        }
        val assetExpression = assetPattern
            .replace("{target}", "${'$'}target")
            .replace("{version}", version.orEmpty())
        val binaryExpression = binaryPathTemplate
            .replace("{target}", "${'$'}target")
            .replace("{version}", version.orEmpty())
        val architectureCase = architectureCase(manifest.source.architectures)
        val prepare = """
            arch="${'$'}(uname -m)"
            $architectureCase
            asset_name="$assetExpression"
            archive="${'$'}install_root/.kite-downloads/${'$'}asset_name"
            tmp_dir="${'$'}install_root/.kite-unpack"
            rm -rf "${'$'}tmp_dir"
            mkdir -p "${'$'}tmp_dir" "${'$'}install_root/bin"
        """.trimIndent()
        val install = when (archiveType) {
            "tar.gz", "tgz" -> """
                tar -tzf "${'$'}archive" >/dev/null
                tar --touch -xzf "${'$'}archive" -C "${'$'}tmp_dir"
                test -f "${'$'}tmp_dir/$binaryExpression"
                install -m 0755 "${'$'}tmp_dir/$binaryExpression" "${'$'}install_root/bin/$command"
                rm -rf "${'$'}tmp_dir" "${'$'}archive"
            """.trimIndent()
            "zip" -> """
                unzip -tq "${'$'}archive" >/dev/null
                unzip -q "${'$'}archive" -d "${'$'}tmp_dir"
                test -f "${'$'}tmp_dir/$binaryExpression"
                install -m 0755 "${'$'}tmp_dir/$binaryExpression" "${'$'}install_root/bin/$command"
                rm -rf "${'$'}tmp_dir" "${'$'}archive"
            """.trimIndent()
            "binary" -> """
                test -s "${'$'}archive"
                install -m 0755 "${'$'}archive" "${'$'}install_root/bin/$command"
                rm -f "${'$'}archive"
            """.trimIndent()
            else -> return null
        }
        return managedAction(
            steps = listOf(
                KiteResourceInstallStep(
                    id = "select-release-asset",
                    type = KiteResourceInstallPlanCompiler.STEP_SHELL,
                    cmd = prepare
                ),
                KiteResourceInstallStep(
                    id = "download-release-asset",
                    type = KiteResourceInstallPlanCompiler.STEP_DOWNLOAD,
                    urls = listOf("$releaseBase/${'$'}asset_name"),
                    destination = "${'$'}archive",
                    maxBytes = manifest.source.maxBytes,
                ),
                KiteResourceInstallStep(
                    id = "install-release-asset",
                    type = KiteResourceInstallPlanCompiler.STEP_SHELL,
                    cmd = install
                )
            ),
            manifest = manifest
        )
    }

    private fun managedAction(
        steps: List<KiteResourceInstallStep>,
        manifest: KiteResourceManifest
    ): KiteResourceShellAction = KiteResourceShellAction(
        type = KiteResourceInstallPlanCompiler.ACTION_MANAGED,
        cmd = "",
        surfaceMode = "panel",
        workdir = "/workspace",
        timeoutMs = 600_000L,
        managedCommands = manifest.management.managedCommands,
        cleanInstallRoot = true,
        npmUninstallPackages = emptyList(),
        installSteps = steps,
        verifications = verificationSteps(manifest)
    )

    private fun shellAction(
        command: String,
        managedCommands: List<String>,
        npmUninstallPackages: List<String>
    ): KiteResourceShellAction = KiteResourceShellAction(
        type = KiteResourceInstallPlanCompiler.STEP_SHELL,
        cmd = command,
        surfaceMode = "panel",
        workdir = "/workspace",
        timeoutMs = 180_000L,
        managedCommands = managedCommands,
        cleanInstallRoot = false,
        npmUninstallPackages = npmUninstallPackages
    )

    private fun verificationSteps(manifest: KiteResourceManifest): List<KiteResourceInstallVerification> {
        val probe = manifest.management.versionProbe
        return buildList {
            if (probe != null) {
                add(KiteResourceInstallVerification(id = "installed-version", cmd = probe.command))
            }
            manifest.management.managedCommands.forEach { command ->
                add(
                    KiteResourceInstallVerification(
                        id = "command-$command",
                        cmd = "command -v '$command' >/dev/null 2>&1"
                    )
                )
            }
        }
    }

    private fun npmVersionProbe(source: KiteResourceSourceSpec): KiteResourceRemoteVersionProbe? {
        val packageName = source.packageName.takeIf(SAFE_NPM_PACKAGE::matches) ?: return null
        val encodedPackage = URLEncoder.encode(packageName, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        val encodedTag = URLEncoder.encode(source.tag.ifBlank { "latest" }, StandardCharsets.UTF_8.name())
        return KiteResourceRemoteVersionProbe(
            url = "https://registry.npmjs.org/$encodedPackage/$encodedTag",
            jsonField = "version"
        )
    }

    private fun npmPackageNames(source: KiteResourceSourceSpec): List<String>? {
        val names = listOf(source.packageName) + source.companionPackages
        if (names.any { !SAFE_NPM_PACKAGE.matches(it) }) return null
        return names.distinct()
    }

    private fun githubVersionProbe(source: KiteResourceSourceSpec): KiteResourceRemoteVersionProbe? {
        val slug = githubSlug(source.repository) ?: return null
        return KiteResourceRemoteVersionProbe(
            url = "https://api.github.com/repos/$slug/releases/latest",
            jsonField = "tag_name",
            format = "github_release",
            fallbackUrl = "https://github.com/$slug/releases/latest"
        )
    }

    private fun officialScriptVersionProbe(source: KiteResourceSourceSpec): KiteResourceRemoteVersionProbe? {
        val url = source.latestUrl.takeIf(::isHttpUrl) ?: return null
        val format = source.latestFormat.lowercase()
        if (format !in setOf("json", "text")) return null
        if (format == "json" && source.latestJsonField.isBlank()) return null
        return KiteResourceRemoteVersionProbe(
            url = url,
            jsonField = source.latestJsonField,
            format = format,
            stripPrefix = source.latestStripPrefix
        )
    }

    private fun githubSlug(repository: String): String? {
        val clean = repository.trim().removeSuffix(".git").trimEnd('/')
        val slug = clean.substringAfter("https://github.com/", missingDelimiterValue = "")
        return slug.takeIf { GITHUB_SLUG.matches(it) }
    }

    private fun architectureCase(architectures: Map<String, String>): String {
        if (architectures.isEmpty()) return "target=\"${'$'}arch\""
        val branches = architectures.entries.mapNotNull { (pattern, target) ->
            val safePattern = pattern.takeIf(SAFE_ARCH_PATTERN::matches) ?: return@mapNotNull null
            val safeTarget = target.takeIf(SAFE_ARCH_TARGET::matches) ?: return@mapNotNull null
            "  $safePattern) target='$safeTarget' ;;"
        }
        if (branches.isEmpty()) return "target=\"${'$'}arch\""
        return buildString {
            appendLine("case \"${'$'}arch\" in")
            branches.forEach(::appendLine)
            appendLine("  *) echo \"Unsupported architecture: ${'$'}arch\"; exit 65 ;;")
            append("esac")
        }
    }

    private fun inferArchiveType(assetPattern: String): String = when {
        assetPattern.endsWith(".tar.gz") || assetPattern.endsWith(".tgz") -> "tar.gz"
        assetPattern.endsWith(".zip") -> "zip"
        else -> "binary"
    }

    private fun safeVersion(value: String): String {
        val clean = value.trim()
        require(SAFE_VERSION.matches(clean)) { "Unsafe resource version: $value" }
        return clean
    }

    private fun githubReleaseTag(source: KiteResourceSourceSpec, version: String): String {
        val template = source.releaseTagTemplate.ifBlank { "{version}" }
        require(template.countTemplate("{version}") == 1) {
            "GitHub releaseTagTemplate must contain exactly one {version}: $template"
        }
        val normalizedVersion = if (template.contains("v{version}") && version.startsWith("v")) {
            version.removePrefix("v")
        } else {
            version
        }
        return safeVersion(template.replace("{version}", normalizedVersion))
    }

    private fun String.countTemplate(value: String): Int {
        var count = 0
        var index = indexOf(value)
        while (index >= 0) {
            count += 1
            index = indexOf(value, index + value.length)
        }
        return count
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://") || value.startsWith("http://")

    private fun isManagedScriptProfile(manifest: KiteResourceManifest): Boolean =
        managedScriptContract(manifest) != null

    private fun shellLiteral(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private const val SOURCE_NPM = "npm"
    private const val SOURCE_GITHUB_RELEASE = "github_release"
    private const val SOURCE_OFFICIAL_SCRIPT = "official_script"
    private const val SOURCE_BUNDLED = "bundled"
    private const val PROFILE_MANAGED_SCRIPT_V1 = "managed_script_v1"
    private const val MAXIMUM_PACKAGE_METADATA_BYTES = 256L * 1024L
    private val TARGET_VERSION_SOURCES = setOf(SOURCE_NPM, SOURCE_GITHUB_RELEASE)
    private val SAFE_NPM_PACKAGE = Regex("(?:@[A-Za-z0-9._~-]+/)?[A-Za-z0-9._~-]+")
    private val SAFE_RESOURCE_ID = Regex("[A-Za-z0-9._-]+")
    private val SAFE_VERSION = Regex("v?[0-9A-Za-z][0-9A-Za-z._+-]*")
    private val GITHUB_SLUG = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
    private val SAFE_ARCH_PATTERN = Regex("[A-Za-z0-9_*?|.-]+")
    private val SAFE_ARCH_TARGET = Regex("[A-Za-z0-9_.-]+")
    private val SAFE_INTERPRETER = Regex("[A-Za-z0-9._+-]+")
    private val SAFE_RELATIVE_ENTRY = Regex("[A-Za-z0-9._+/-]+")
}
