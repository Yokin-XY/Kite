package com.kite.app.resources

object KiteResourceInstallPlanCompiler {
    const val ACTION_MANAGED = "managed"
    const val STEP_APT = "apt"
    const val STEP_BUNDLED = "bundled"
    const val STEP_DOWNLOAD = "download"
    const val STEP_LATEST_DOWNLOAD = "latest_download"
    const val STEP_ARCHIVE = "archive"
    const val STEP_GIT = "git"
    const val STEP_NPM = "npm"
    const val STEP_PYPI = "pypi"
    const val STEP_SCRIPT = "script"
    const val STEP_SHELL = "shell"

    fun compile(
        action: KiteResourceShellAction,
        sourcePreferences: KiteResourceSourcePreferences = KiteResourceSourcePreferences(),
    ): String {
        if (action.type == "shell") return action.cmd
        require(action.type == ACTION_MANAGED) { "Unsupported resource install action: ${action.type}" }
        require(action.installSteps.isNotEmpty()) { "Managed resource install action has no steps" }
        val routedAction = KiteResourceSourcePolicy.apply(action, sourcePreferences)

        return buildString {
            appendLine("set -e")
            appendLine(sourceRoutingHelper(sourcePreferences))
            if (routedAction.installSteps.any { it.type in setOf(STEP_APT, STEP_GIT, STEP_NPM, STEP_PYPI, STEP_SCRIPT) }) {
                appendLine(stepRunnerHelper())
            }
            if (routedAction.installSteps.any { it.type in setOf(STEP_DOWNLOAD, STEP_LATEST_DOWNLOAD) }) {
                appendLine(downloadHelper())
            }
            if (routedAction.installSteps.any { it.type == STEP_GIT }) {
                appendLine(gitHelper())
            }
            val npmAttemptVerifications = routedAction.verifications.takeIf {
                routedAction.installSteps.singleOrNull()?.type == STEP_NPM
            }.orEmpty()
            routedAction.installSteps.forEach { step ->
                appendLine(compileStep(step, npmAttemptVerifications))
            }
        }.trim()
    }

    fun compileVerification(action: KiteResourceShellAction): String =
        if (action.verifications.isEmpty()) {
            ":"
        } else {
            buildString {
                action.verifications.forEach { verification ->
                    val stepId = safeId(verification.id)
                    appendLine("echo \"KITE_RESOURCE_STEP verify $stepId\"")
                    appendLine("set +e")
                    appendLine("(")
                    appendLine("  set -e")
                    verification.cmd.lineSequence().forEach { appendLine("  $it") }
                    appendLine(")")
                    appendLine("verification_status=${'$'}?")
                    appendLine("set -e")
                    appendLine("if [ \"${'$'}verification_status\" -ne 0 ]; then")
                    appendLine("  echo \"KITE_RESOURCE_FAILURE stage=verify step=$stepId exit=${'$'}verification_status\"")
                    appendLine("  exit \"${'$'}verification_status\"")
                    appendLine("fi")
                    appendLine("echo \"KITE_RESOURCE_STEP verify-complete $stepId\"")
                }
            }.trim()
        }

    fun bundledCommand(action: KiteResourceShellAction): String? =
        action.installSteps.singleOrNull()
            ?.takeIf { action.type == ACTION_MANAGED && it.type == STEP_BUNDLED }
            ?.cmd
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    fun preview(action: KiteResourceShellAction): String {
        if (action.type == "shell") {
            return action.cmd.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(160)
        }
        val step = action.installSteps.firstOrNull() ?: return ""
        return when (step.type) {
            STEP_DOWNLOAD, STEP_LATEST_DOWNLOAD -> step.urls.firstOrNull().orEmpty()
            STEP_ARCHIVE -> listOf(step.archiveFormat, step.path, step.destination).joinToString(" ")
            STEP_GIT -> step.repository
            STEP_NPM, STEP_PYPI, STEP_APT -> step.packages.joinToString(" ")
            STEP_SCRIPT -> listOf(step.interpreter, step.path).filter { it.isNotBlank() }.joinToString(" ")
            else -> step.cmd.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        }.take(160)
    }

    private fun compileStep(
        step: KiteResourceInstallStep,
        npmAttemptVerifications: List<KiteResourceInstallVerification> = emptyList(),
    ): String = when (step.type) {
        STEP_DOWNLOAD -> compileDownload(step)
        STEP_LATEST_DOWNLOAD -> compileLatestDownload(step)
        STEP_ARCHIVE -> error("Archive step ${step.id} must be compiled by the Android native archive planner")
        STEP_SCRIPT -> compileScript(step)
        STEP_NPM -> compileNpm(step, npmAttemptVerifications)
        STEP_PYPI -> compilePypi(step)
        STEP_APT -> compileApt(step)
        STEP_GIT -> compileGit(step)
        STEP_SHELL, STEP_BUNDLED -> compileInlineShell(step)
        else -> error("Unsupported resource install step: ${step.type}")
    }

    private fun compileDownload(step: KiteResourceInstallStep): String {
        require(step.urls.isNotEmpty()) { "Download step ${step.id} has no URL" }
        require(step.destination.isNotBlank()) { "Download step ${step.id} has no destination" }
        val expectedSha256 = step.sha256.trim().lowercase()
        require(expectedSha256.isBlank() || SHA256.matches(expectedSha256)) {
            "Download step ${step.id} has an invalid SHA-256"
        }
        require(step.urls.size == 1 || expectedSha256.isNotBlank()) {
            "Download step ${step.id} requires SHA-256 when mirrors are declared"
        }
        val args = buildList {
            add(shellLiteral(safeId(step.id)))
            add(shellExpression(step.destination))
            add(shellLiteral(expectedSha256))
            add(step.retryAttempts.toString())
            add(step.retryDelaySeconds.toString())
            step.urls.forEach { add(shellExpression(it)) }
        }
        return "kite_resource_download ${args.joinToString(" ")}"
    }

    private fun compileLatestDownload(step: KiteResourceInstallStep): String {
        require(step.urls.isNotEmpty()) { "Latest download step ${step.id} has no metadata URL" }
        require(step.urls.all(::isSecureRegistryUrl)) {
            "Latest download step ${step.id} has an invalid HTTPS metadata URL"
        }
        require(step.destination.isNotBlank()) { "Latest download step ${step.id} has no destination" }
        require(step.maxBytes > 0L) { "Latest download step ${step.id} requires maxBytes" }
        require(step.latestFormat in setOf("json", "text", "regex")) {
            "Latest download step ${step.id} has an unsupported metadata format"
        }
        require(step.latestFormat != "json" || SAFE_JSON_FIELD.matches(step.latestJsonField)) {
            "Latest download step ${step.id} requires a safe JSON field"
        }
        require(
            step.latestFormat != "regex" || (
                step.latestRegex.isNotBlank() &&
                    step.latestRegex.length <= MAX_LATEST_REGEX_LENGTH &&
                    step.latestRegex.none { it == '\n' || it == '\r' } &&
                    runCatching { Regex(step.latestRegex) }.isSuccess
                )
        ) {
            "Latest download step ${step.id} requires a valid bounded regex"
        }
        require(step.latestStripPrefix.none { it == '|' || it == '\n' || it == '\r' }) {
            "Latest download step ${step.id} has an invalid version prefix"
        }
        val window = step.latestVersionWindow
        require(window.size in 1..MAX_LATEST_VERSION_WINDOW) {
            "Latest download step ${step.id} latest version window must contain 1 to $MAX_LATEST_VERSION_WINDOW entries"
        }
        require(window.map { it.version }.distinct().size == window.size) {
            "Latest download step ${step.id} latest version window contains duplicate versions"
        }
        window.forEach { candidate ->
            require(isSafeNpmVersion(candidate.version) && SHA256.matches(candidate.sha256)) {
                "Latest download step ${step.id} window contains an invalid version or SHA-256"
            }
            require(isSecureRegistryUrl(candidate.url) && '|' !in candidate.url) {
                "Latest download step ${step.id} window contains an invalid artifact URL"
            }
        }
        val routes = step.urls.distinct().joinToString(" ") { metadataUrl ->
            shellLiteral("${KiteResourceSourceCatalog.sourceIdFor(metadataUrl)}|$metadataUrl")
        }
        val versionWindow = window.joinToString(" ") { candidate ->
            shellLiteral("${candidate.version}|${candidate.sha256}|${candidate.url}")
        }
        return """
            command -v python3 >/dev/null 2>&1 || { echo "KITE_RESOURCE_FAILURE stage=prepare step=${safeId(step.id)} reason=python-missing"; exit 127; }
            latest_download_status=1
            latest_download_window=($versionWindow)
            for latest_route in $routes; do
              source_id="${'$'}{latest_route%%|*}"
              metadata_url="${'$'}{latest_route#*|}"
              attempt_root="${'$'}install_root/.kite-source-attempt/latest-download/${safeId(step.id)}/${'$'}source_id"
              metadata_file="${'$'}attempt_root/metadata"
              mkdir -p "${'$'}attempt_root"
              echo "KITE_RESOURCE_ROUTE stage=acquire step=${safeId(step.id)} source=${'$'}source_id request=latest"
              set +e
              (set +e; kite_resource_download ${shellLiteral("${safeId(step.id)}-metadata")} "${'$'}metadata_file" '' ${step.retryAttempts} ${step.retryDelaySeconds} "${'$'}metadata_url")
              metadata_status=${'$'}?
              set -e
              if [ "${'$'}metadata_status" -ne 0 ]; then
                latest_download_status="${'$'}metadata_status"
                echo "KITE_RESOURCE_RETRY stage=acquire step=${safeId(step.id)} source=${'$'}source_id exit=${'$'}metadata_status reason=latest-query-failed"
                rm -rf "${'$'}attempt_root"
                continue
              fi
              set +e
              metadata_bytes="${'$'}(wc -c < "${'$'}metadata_file")"
              if [ "${'$'}metadata_bytes" -gt $MAX_LATEST_METADATA_BYTES ]; then
                latest_download_status=69
                echo "KITE_RESOURCE_SOURCE_REJECTED step=${safeId(step.id)} source=${'$'}source_id reason=latest-metadata-size-limit bytes=${'$'}metadata_bytes"
                rm -rf "${'$'}attempt_root"
                continue
              fi
              latest_version="${'$'}(python3 - ${shellLiteral(step.latestFormat)} ${shellLiteral(if (step.latestFormat == "regex") step.latestRegex else step.latestJsonField)} "${'$'}metadata_file" <<'KITE_LATEST_METADATA'
            import json
            import re
            import sys

            metadata_format, selector, path = sys.argv[1:]
            raw = open(path, encoding='utf-8', errors='strict').read()
            if metadata_format == 'text':
                value = raw.strip().splitlines()[0]
            elif metadata_format == 'regex':
                match = re.search(selector, raw)
                if match is None or match.lastindex is None or match.lastindex < 1:
                    raise ValueError('latest version regex did not capture a value')
                value = match.group(1)
            else:
                value = json.loads(raw)
                for part in selector.split('.'):
                    value = value[part]
            if not isinstance(value, (str, int, float)):
                raise TypeError('latest version is not scalar')
            print(str(value).strip())
            KITE_LATEST_METADATA
              )"
              metadata_parse_status=${'$'}?
              set -e
              if [ "${'$'}metadata_parse_status" -ne 0 ]; then
                latest_download_status=69
                echo "KITE_RESOURCE_SOURCE_REJECTED step=${safeId(step.id)} source=${'$'}source_id reason=latest-metadata-invalid"
                rm -rf "${'$'}attempt_root"
                continue
              fi
              strip_prefix=${shellLiteral(step.latestStripPrefix)}
              if [ -n "${'$'}strip_prefix" ]; then
                case "${'$'}latest_version" in
                  "${'$'}strip_prefix"*) latest_version="${'$'}{latest_version#"${'$'}strip_prefix"}" ;;
                esac
              fi
              case "${'$'}latest_version" in
                ''|*[!A-Za-z0-9._+-]*)
                  latest_download_status=69
                  echo "KITE_RESOURCE_SOURCE_REJECTED step=${safeId(step.id)} source=${'$'}source_id reason=latest-version-invalid"
                  rm -rf "${'$'}attempt_root"
                  continue
                  ;;
              esac
              selected_sha256=
              selected_url=
              for window_entry in "${'$'}{latest_download_window[@]}"; do
                window_version="${'$'}{window_entry%%|*}"
                window_tail="${'$'}{window_entry#*|}"
                if [ "${'$'}latest_version" = "${'$'}window_version" ]; then
                  selected_sha256="${'$'}{window_tail%%|*}"
                  selected_url="${'$'}{window_tail#*|}"
                  break
                fi
              done
              if [ -z "${'$'}selected_sha256" ] || [ -z "${'$'}selected_url" ]; then
                latest_download_status=69
                echo "KITE_RESOURCE_SOURCE_REJECTED step=${safeId(step.id)} source=${'$'}source_id reason=latest-version-outside-window version=${'$'}latest_version"
                rm -rf "${'$'}attempt_root"
                continue
              fi
              set +e
              (set +e; kite_resource_download ${shellLiteral(safeId(step.id))} ${shellExpression(step.destination)} "${'$'}selected_sha256" ${step.retryAttempts} ${step.retryDelaySeconds} "${'$'}selected_url")
              latest_download_status=${'$'}?
              set -e
              if [ "${'$'}latest_download_status" -ne 0 ]; then
                echo "KITE_RESOURCE_RETRY stage=acquire step=${safeId(step.id)} source=${'$'}source_id exit=${'$'}latest_download_status reason=verified-artifact-unavailable"
                rm -rf "${'$'}attempt_root"
                continue
              fi
              actual_bytes="${'$'}(wc -c < ${shellExpression(step.destination)})"
              if [ "${'$'}actual_bytes" -gt ${step.maxBytes} ]; then
                rm -f ${shellExpression(step.destination)}
                latest_download_status=65
                echo "KITE_RESOURCE_SOURCE_REJECTED step=${safeId(step.id)} source=${'$'}source_id reason=artifact-size-limit bytes=${'$'}actual_bytes"
                rm -rf "${'$'}attempt_root"
                continue
              fi
              selection_root="${'$'}install_root/.kite-source-selection"
              mkdir -p "${'$'}selection_root"
              printf '%s\n' "${'$'}latest_version" > "${'$'}selection_root/${safeId(step.id)}.version"
              printf '%s\n' "${'$'}selected_sha256" > "${'$'}selection_root/${safeId(step.id)}.sha256"
              printf '%s\n' "${'$'}selected_url" > "${'$'}selection_root/${safeId(step.id)}.url"
              latest_download_status=0
              rm -rf "${'$'}attempt_root"
              echo "KITE_RESOURCE_STEP acquire-complete ${safeId(step.id)} source=${'$'}source_id version=${'$'}latest_version"
              break
            done
            [ "${'$'}latest_download_status" -eq 0 ] || { rm -rf "${'$'}install_root/.kite-source-attempt/latest-download/${safeId(step.id)}"; echo "KITE_RESOURCE_FAILURE stage=acquire step=${safeId(step.id)} exit=${'$'}latest_download_status reason=no-verified-latest-source"; exit "${'$'}latest_download_status"; }
        """.trimIndent()
    }

    private val SHA256 = Regex("[a-f0-9]{64}")

    private fun compileScript(step: KiteResourceInstallStep): String {
        require(step.path.isNotBlank()) { "Script step ${step.id} has no path" }
        val interpreter = step.interpreter.ifBlank { "bash" }
        val command = buildList {
            if (step.environment.isNotEmpty()) {
                add("env")
                step.environment.toSortedMap().forEach { (key, value) ->
                    require(SAFE_ENV_NAME.matches(key)) { "Unsafe environment name: $key" }
                    add("$key=${shellExpression(value)}")
                }
            }
            add(shellLiteral(interpreter))
            add(shellExpression(step.path))
            step.arguments.forEach { add(shellExpression(it)) }
        }.joinToString(" ")
        return "kite_resource_run ${shellLiteral(safeId(step.id))} install $command"
    }

    private fun compileNpm(
        step: KiteResourceInstallStep,
        attemptVerifications: List<KiteResourceInstallVerification>,
    ): String {
        require(step.packages.isNotEmpty()) { "npm step ${step.id} has no packages" }
        step.registries.forEach { registry ->
            require(isSecureRegistryUrl(registry)) {
                "npm step ${step.id} has an invalid HTTPS registry"
            }
        }
        if (step.latestVersionWindow.isNotEmpty()) {
            return compileVerifiedLatestNpm(step, attemptVerifications)
        }
        val arguments = step.arguments.joinToString(" ") { shellLiteral(it) }
        val packages = step.packages.joinToString(" ") { shellLiteral(it) }
        val installArguments = listOf(arguments, packages).filter { it.isNotBlank() }.joinToString(" ")
        val attemptVerification = compileNpmAttemptVerification(attemptVerifications)
        val installCommand = if (step.registries.isEmpty()) {
            "npm install -g $installArguments"
        } else {
            val functionSuffix = safeId(step.id).replace(Regex("[^a-z0-9_]"), "_")
            val functionName = "kite_resource_npm_$functionSuffix"
            val routes = step.registries.distinct().joinToString(" ") { registry ->
                shellLiteral("${KiteResourceSourceCatalog.sourceIdFor(registry)}|$registry")
            }
            """
                $functionName() {
                  last_status=1
                  for npm_route in $routes; do
                    source_id="${'$'}{npm_route%%|*}"
                    npm_registry="${'$'}{npm_route#*|}"
                    attempt_root="${'$'}install_root/.kite-source-attempt/npm/${'$'}source_id"
                    attempt_prefix="${'$'}npm_config_prefix"
                    attempt_cache="${'$'}install_root/.kite-source-cache/npm/${'$'}source_id"
                    attempt_log="${'$'}attempt_root/npm-install.log"
                    rm -rf "${'$'}attempt_root"
                    rm -rf "${'$'}attempt_prefix"
                    mkdir -p "${'$'}attempt_root" "${'$'}attempt_prefix" "${'$'}attempt_cache"
                    echo "KITE_RESOURCE_ROUTE stage=acquire step=${safeId(step.id)} source=${'$'}source_id registry=${'$'}npm_registry"
                    set +e
                    npm install -g --loglevel=http --prefix="${'$'}attempt_prefix" --cache="${'$'}attempt_cache" --registry="${'$'}npm_registry" $installArguments >"${'$'}attempt_log" 2>&1
                    last_status=${'$'}?
                    set -e
                    source_unavailable=0
                    retry_reason=source-unavailable
                    if [ "${'$'}last_status" -ne 0 ] && kite_resource_is_source_failure "${'$'}last_status" "${'$'}attempt_log"; then
                      source_unavailable=1
                    fi
                    if [ "${'$'}last_status" -eq 0 ]; then
                      set +e
                      (
                        export PATH="${'$'}attempt_prefix/bin:${'$'}PATH"
                        export npm_config_prefix="${'$'}attempt_prefix"
                        export HOME="${'$'}attempt_root/home"
                        mkdir -p "${'$'}HOME"
                        $attemptVerification
                      ) >>"${'$'}attempt_log" 2>&1
                      attempt_verification_status=${'$'}?
                      set -e
                      if [ "${'$'}attempt_verification_status" -ne 0 ]; then
                        last_status=69
                        source_unavailable=1
                        retry_reason=source-incomplete
                        echo "KITE_RESOURCE_SOURCE_REJECTED step=${safeId(step.id)} source=${'$'}source_id reason=candidate-verification exit=${'$'}attempt_verification_status" >>"${'$'}attempt_log"
                      fi
                    fi
                    cat "${'$'}attempt_log"
                    if [ "${'$'}last_status" -eq 0 ]; then
                      rm -rf "${'$'}install_root/.kite-source-attempt"
                      echo "KITE_RESOURCE_STEP acquire-complete ${safeId(step.id)} source=${'$'}source_id"
                      return 0
                    fi
                    if [ "${'$'}source_unavailable" -eq 0 ]; then
                      echo "KITE_RESOURCE_FAILURE stage=install step=${safeId(step.id)} source=${'$'}source_id exit=${'$'}last_status reason=non-network"
                      rm -rf "${'$'}install_root/.kite-source-attempt"
                      return "${'$'}last_status"
                    fi
                    echo "KITE_RESOURCE_RETRY stage=acquire step=${safeId(step.id)} source=${'$'}source_id exit=${'$'}last_status reason=${'$'}retry_reason"
                    rm -rf "${'$'}attempt_prefix"
                    rm -rf "${'$'}attempt_root"
                  done
                  rm -rf "${'$'}install_root/.kite-source-attempt"
                  return "${'$'}last_status"
                }
                kite_resource_run ${shellLiteral(safeId(step.id))} install $functionName
            """.trimIndent()
        }
        return """
            command -v npm >/dev/null 2>&1 || { echo "KITE_RESOURCE_FAILURE stage=prepare step=${safeId(step.id)} reason=npm-missing"; exit 127; }
            export npm_config_fetch_retries=${step.retryAttempts}
            export npm_config_fetch_retry_mintimeout=$(( ${step.retryDelaySeconds} * 1000 ))
            export npm_config_fetch_retry_maxtimeout=$(( ${step.retryDelaySeconds} * ${step.retryAttempts} * 4000 ))
            $installCommand
        """.trimIndent()
    }

    private fun compileVerifiedLatestNpm(
        step: KiteResourceInstallStep,
        attemptVerifications: List<KiteResourceInstallVerification>,
    ): String {
        require(step.registries.isNotEmpty()) {
            "npm step ${step.id} requires at least one registry for latest verification"
        }
        val packageNames = step.packages.map { packageSpec ->
            npmLatestPackageName(packageSpec)
                ?: throw IllegalArgumentException(
                    "npm step ${step.id} must request bare packages or @latest when a signed version window is used"
                )
        }
        require(packageNames.distinct().size == packageNames.size) {
            "npm step ${step.id} contains duplicate packages"
        }
        val resolvedWindow = step.latestVersionWindow.map { candidate ->
            val artifact = candidate.artifact.ifBlank { packageNames.singleOrNull().orEmpty() }
            candidate.copy(artifact = artifact)
        }
        require(resolvedWindow.all { it.artifact in packageNames }) {
            "npm step ${step.id} latest version window contains an unknown package"
        }
        require(resolvedWindow.groupBy(KiteResourceSourceVersion::artifact).keys == packageNames.toSet()) {
            "npm step ${step.id} latest version window does not cover every package"
        }
        resolvedWindow.groupBy(KiteResourceSourceVersion::artifact).forEach { (artifact, candidates) ->
            require(candidates.size in 1..MAX_LATEST_VERSION_WINDOW) {
                "npm step ${step.id} latest version window for $artifact must contain 1 to $MAX_LATEST_VERSION_WINDOW entries"
            }
            require(candidates.map { it.version }.distinct().size == candidates.size) {
                "npm step ${step.id} latest version window for $artifact contains duplicate versions"
            }
            candidates.forEach { candidate ->
                require(isSafeNpmVersion(candidate.version)) {
                    "npm step ${step.id} latest version window for $artifact contains an unsafe version"
                }
                require(NPM_INTEGRITY.matches(candidate.integrity)) {
                    "npm step ${step.id} latest version window for $artifact contains an invalid integrity"
                }
            }
        }
        val selectionTokens = packageNames.associateWith(::safeId)
        require(selectionTokens.values.distinct().size == selectionTokens.size) {
            "npm step ${step.id} package selection file names collide"
        }
        val routes = step.registries.distinct().joinToString(" ") { registry ->
            shellLiteral("${KiteResourceSourceCatalog.sourceIdFor(registry)}|$registry")
        }
        val packages = packageNames.joinToString(" ", transform = ::shellLiteral)
        val versionWindow = resolvedWindow.joinToString(" ") { candidate ->
            shellLiteral("${candidate.artifact}|${candidate.version}|${candidate.integrity}")
        }
        val arguments = step.arguments.joinToString(" ") { shellLiteral(it) }
        val attemptVerification = compileNpmAttemptVerification(attemptVerifications)
        val selectionCases = selectionTokens.entries.joinToString(" ") { (artifact, token) ->
            "${shellLiteral(artifact)}) selection_token=${shellLiteral(token)} ;;"
        }
        val primaryPackage = packageNames.first()
        val functionSuffix = safeId(step.id).replace(Regex("[^a-z0-9_]"), "_")
        val functionName = "kite_resource_npm_$functionSuffix"
        return """
            command -v npm >/dev/null 2>&1 || { echo "KITE_RESOURCE_FAILURE stage=prepare step=${safeId(step.id)} reason=npm-missing"; exit 127; }
            command -v node >/dev/null 2>&1 || { echo "KITE_RESOURCE_FAILURE stage=prepare step=${safeId(step.id)} reason=node-missing"; exit 127; }
            export npm_config_fetch_retries=${step.retryAttempts}
            export npm_config_fetch_retry_mintimeout=$(( ${step.retryDelaySeconds} * 1000 ))
            export npm_config_fetch_retry_maxtimeout=$(( ${step.retryDelaySeconds} * ${step.retryAttempts} * 4000 ))
            $functionName() {
              last_status=1
              package_names=($packages)
              version_window=($versionWindow)
              for npm_route in $routes; do
                source_id="${'$'}{npm_route%%|*}"
                npm_registry="${'$'}{npm_route#*|}"
                attempt_root="${'$'}install_root/.kite-source-attempt/npm/${'$'}source_id"
                attempt_prefix="${'$'}npm_config_prefix"
                attempt_cache="${'$'}install_root/.kite-source-cache/npm/${'$'}source_id"
                attempt_log="${'$'}attempt_root/npm-install.log"
                rm -rf "${'$'}attempt_root" "${'$'}attempt_prefix"
                mkdir -p "${'$'}attempt_root" "${'$'}attempt_prefix" "${'$'}attempt_cache"
                : > "${'$'}attempt_log"
                echo "KITE_RESOURCE_ROUTE stage=acquire step=${safeId(step.id)} source=${'$'}source_id registry=${'$'}npm_registry request=latest"
                selected_records=()
                selected_specs=()
                source_rejected=0
                source_unavailable=0
                retry_reason=source-unavailable
                for package_name in "${'$'}{package_names[@]}"; do
                  metadata_token="${'$'}(printf '%s' "${'$'}package_name" | tr -c 'A-Za-z0-9._-' '-')"
                  metadata_file="${'$'}attempt_root/${'$'}metadata_token.latest.json"
                  set +e
                  npm view "${'$'}package_name@latest" version dist.integrity --json --registry="${'$'}npm_registry" >"${'$'}metadata_file" 2>>"${'$'}attempt_log"
                  last_status=${'$'}?
                  set -e
                  if [ "${'$'}last_status" -ne 0 ]; then
                    if kite_resource_is_source_failure "${'$'}last_status" "${'$'}attempt_log"; then
                      source_unavailable=1
                      break
                    fi
                    cat "${'$'}attempt_log"
                    echo "KITE_RESOURCE_FAILURE stage=acquire step=${safeId(step.id)} source=${'$'}source_id package=${'$'}package_name exit=${'$'}last_status reason=non-network"
                    return "${'$'}last_status"
                  fi
                  latest_version="${'$'}(node - "${'$'}metadata_file" <<'KITE_NPM_VERSION'
            const fs = require('fs');
            let value = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
            if (Array.isArray(value)) value = value[value.length - 1] || {};
            process.stdout.write(String(value.version || ''));
            KITE_NPM_VERSION
            )"
                  latest_integrity="${'$'}(node - "${'$'}metadata_file" <<'KITE_NPM_INTEGRITY'
            const fs = require('fs');
            let value = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
            if (Array.isArray(value)) value = value[value.length - 1] || {};
            process.stdout.write(String(value['dist.integrity'] || (value.dist && value.dist.integrity) || ''));
            KITE_NPM_INTEGRITY
            )"
                  window_match=0
                  for window_entry in "${'$'}{version_window[@]}"; do
                    window_artifact="${'$'}{window_entry%%|*}"
                    window_tail="${'$'}{window_entry#*|}"
                    window_version="${'$'}{window_tail%%|*}"
                    window_integrity="${'$'}{window_tail#*|}"
                    if [ "${'$'}package_name" = "${'$'}window_artifact" ] && [ "${'$'}latest_version" = "${'$'}window_version" ] && [ "${'$'}latest_integrity" = "${'$'}window_integrity" ]; then
                      window_match=1
                      break
                    fi
                  done
                  if [ "${'$'}window_match" -ne 1 ]; then
                    last_status=69
                    source_rejected=1
                    echo "KITE_RESOURCE_SOURCE_REJECTED step=${safeId(step.id)} source=${'$'}source_id package=${'$'}package_name reason=latest-version-or-integrity-outside-window version=${'$'}latest_version" >>"${'$'}attempt_log"
                    break
                  fi
                  selected_specs+=("${'$'}package_name@${'$'}latest_version")
                  selected_records+=("${'$'}package_name|${'$'}latest_version|${'$'}latest_integrity")
                done
                if [ "${'$'}source_rejected" -eq 1 ]; then
                  cat "${'$'}attempt_log"
                  echo "KITE_RESOURCE_RETRY stage=acquire step=${safeId(step.id)} source=${'$'}source_id exit=${'$'}last_status reason=source-unverified"
                  rm -rf "${'$'}attempt_root" "${'$'}attempt_prefix"
                  continue
                fi
                if [ "${'$'}source_unavailable" -eq 1 ]; then
                  cat "${'$'}attempt_log"
                  echo "KITE_RESOURCE_RETRY stage=acquire step=${safeId(step.id)} source=${'$'}source_id exit=${'$'}last_status reason=source-unavailable"
                  rm -rf "${'$'}attempt_root" "${'$'}attempt_prefix"
                  continue
                fi
                set +e
                npm install -g --loglevel=http --prefix="${'$'}attempt_prefix" --cache="${'$'}attempt_cache" --registry="${'$'}npm_registry" $arguments "${'$'}{selected_specs[@]}" >>"${'$'}attempt_log" 2>&1
                last_status=${'$'}?
                set -e
                if [ "${'$'}last_status" -ne 0 ] && kite_resource_is_source_failure "${'$'}last_status" "${'$'}attempt_log"; then
                  source_unavailable=1
                fi
                if [ "${'$'}last_status" -eq 0 ]; then
                  set +e
                  (
                    export PATH="${'$'}attempt_prefix/bin:${'$'}PATH"
                    export npm_config_prefix="${'$'}attempt_prefix"
                    export HOME="${'$'}attempt_root/home"
                    mkdir -p "${'$'}HOME"
                    $attemptVerification
                  ) >>"${'$'}attempt_log" 2>&1
                  attempt_verification_status=${'$'}?
                  set -e
                  if [ "${'$'}attempt_verification_status" -ne 0 ]; then
                    last_status=69
                    source_unavailable=1
                    retry_reason=source-incomplete
                    echo "KITE_RESOURCE_SOURCE_REJECTED step=${safeId(step.id)} source=${'$'}source_id reason=candidate-verification exit=${'$'}attempt_verification_status" >>"${'$'}attempt_log"
                  fi
                fi
                cat "${'$'}attempt_log"
                if [ "${'$'}last_status" -eq 0 ]; then
                  selection_root="${'$'}install_root/.kite-source-selection"
                  mkdir -p "${'$'}selection_root"
                  for selected_record in "${'$'}{selected_records[@]}"; do
                    selected_artifact="${'$'}{selected_record%%|*}"
                    selected_tail="${'$'}{selected_record#*|}"
                    selected_version="${'$'}{selected_tail%%|*}"
                    selected_integrity="${'$'}{selected_tail#*|}"
                    selection_token=
                    case "${'$'}selected_artifact" in $selectionCases esac
                    [ -n "${'$'}selection_token" ] || { echo "KITE_RESOURCE_FAILURE stage=publish step=${safeId(step.id)} reason=selection-token-missing"; return 70; }
                    printf '%s\n' "${'$'}selected_version" > "${'$'}selection_root/${safeId(step.id)}.${'$'}selection_token.version"
                    printf '%s\n' "${'$'}selected_integrity" > "${'$'}selection_root/${safeId(step.id)}.${'$'}selection_token.integrity"
                    if [ "${'$'}selected_artifact" = ${shellLiteral(primaryPackage)} ]; then
                      printf '%s\n' "${'$'}selected_version" > "${'$'}selection_root/${safeId(step.id)}.version"
                      printf '%s\n' "${'$'}selected_integrity" > "${'$'}selection_root/${safeId(step.id)}.integrity"
                    fi
                  done
                  rm -rf "${'$'}install_root/.kite-source-attempt"
                  echo "KITE_RESOURCE_STEP acquire-complete ${safeId(step.id)} source=${'$'}source_id version=${'$'}(cat "${'$'}selection_root/${safeId(step.id)}.version")"
                  return 0
                fi
                if [ "${'$'}source_unavailable" -eq 0 ]; then
                  echo "KITE_RESOURCE_FAILURE stage=install step=${safeId(step.id)} source=${'$'}source_id exit=${'$'}last_status reason=non-network"
                  rm -rf "${'$'}install_root/.kite-source-attempt"
                  return "${'$'}last_status"
                fi
                echo "KITE_RESOURCE_RETRY stage=acquire step=${safeId(step.id)} source=${'$'}source_id exit=${'$'}last_status reason=${'$'}retry_reason"
                rm -rf "${'$'}attempt_root" "${'$'}attempt_prefix"
              done
              rm -rf "${'$'}install_root/.kite-source-attempt"
              echo "KITE_RESOURCE_FAILURE stage=acquire step=${safeId(step.id)} exit=${'$'}last_status reason=no-verified-latest-source"
              return "${'$'}last_status"
            }
            kite_resource_run ${shellLiteral(safeId(step.id))} install $functionName
        """.trimIndent()
    }

    private fun npmLatestPackageName(spec: String): String? {
        val value = spec.trim()
        val separator = value.lastIndexOf('@')
        val packageName: String
        val selector: String
        if (value.startsWith('@')) {
            if (separator > 0) {
                packageName = value.substring(0, separator)
                selector = value.substring(separator + 1)
            } else {
                packageName = value
                selector = ""
            }
        } else if (separator > 0) {
            packageName = value.substring(0, separator)
            selector = value.substring(separator + 1)
        } else {
            packageName = value
            selector = ""
        }
        return packageName.takeIf {
            SAFE_NPM_PACKAGE.matches(it) && (selector.isBlank() || selector == "latest")
        }
    }

    private fun compileNpmAttemptVerification(
        verifications: List<KiteResourceInstallVerification>,
    ): String = if (verifications.isEmpty()) {
        ":"
    } else {
        buildString {
            appendLine("set -e")
            appendLine("echo \"KITE_RESOURCE_STEP verify-source-candidate\"")
            verifications.forEach { verification ->
                val stepId = safeId(verification.id)
                appendLine("echo \"KITE_RESOURCE_STEP verify-source $stepId\"")
                appendLine("(")
                appendLine("  set -e")
                verification.cmd.lineSequence().forEach { appendLine("  $it") }
                appendLine(")")
            }
        }.trim()
    }

    private fun compilePypi(step: KiteResourceInstallStep): String {
        require(step.packages.size == 1) { "pypi step ${step.id} requires exactly one package" }
        val packageName = step.packages.single().trim()
        require(SAFE_PYPI_PACKAGE.matches(packageName)) { "pypi step ${step.id} has an invalid package" }
        require(step.registries.isNotEmpty()) { "pypi step ${step.id} requires at least one index" }
        step.registries.forEach { index ->
            require(isSecureRegistryUrl(index)) { "pypi step ${step.id} has an invalid HTTPS index" }
        }
        val window = step.latestVersionWindow.map { candidate ->
            candidate.copy(artifact = candidate.artifact.ifBlank { packageName })
        }
        require(window.size in 1..MAX_LATEST_VERSION_WINDOW) {
            "pypi step ${step.id} latest version window must contain 1 to $MAX_LATEST_VERSION_WINDOW entries"
        }
        require(window.all { it.artifact == packageName }) {
            "pypi step ${step.id} latest version window contains an unknown package"
        }
        require(window.map { it.version }.distinct().size == window.size) {
            "pypi step ${step.id} latest version window contains duplicate versions"
        }
        window.forEach { candidate ->
            require(isSafeNpmVersion(candidate.version) && SHA256.matches(candidate.sha256)) {
                "pypi step ${step.id} latest version window contains an invalid version or SHA-256"
            }
        }
        val routes = step.registries.distinct().joinToString(" ") { index ->
            shellLiteral("${KiteResourceSourceCatalog.sourceIdFor(index)}|$index")
        }
        val versionWindow = window.joinToString(" ") { candidate ->
            shellLiteral("${candidate.version}|${candidate.sha256}")
        }
        val normalizedName = packageName.lowercase().replace(Regex("[-_.]+"), "-")
        val uvArguments = step.arguments.joinToString(" ") { shellLiteral(it) }
        return """
            command -v curl >/dev/null 2>&1 || { echo "KITE_RESOURCE_FAILURE stage=prepare step=${safeId(step.id)} reason=curl-missing"; exit 127; }
            command -v python3 >/dev/null 2>&1 || { echo "KITE_RESOURCE_FAILURE stage=prepare step=${safeId(step.id)} reason=python-missing"; exit 127; }
            command -v uv >/dev/null 2>&1 || { echo "KITE_RESOURCE_FAILURE stage=prepare step=${safeId(step.id)} reason=uv-missing"; exit 127; }
            export UV_TOOL_DIR="${'$'}install_root/uv-tools"
            export UV_TOOL_BIN_DIR="${'$'}install_root/bin"
            pypi_last_status=1
            version_window=($versionWindow)
            for pypi_route in $routes; do
              source_id="${'$'}{pypi_route%%|*}"
              pypi_index="${'$'}{pypi_route#*|}"
              project_url="${'$'}{pypi_index%/}/${normalizedName}/"
              attempt_root="${'$'}install_root/.kite-source-attempt/pypi/${'$'}source_id"
              attempt_cache="${'$'}install_root/.kite-source-cache/pypi/${'$'}source_id"
              attempt_log="${'$'}attempt_root/uv-tool-install.log"
              index_file="${'$'}attempt_root/simple-index.html"
              candidates_file="${'$'}attempt_root/candidates.txt"
              wheel_file="${'$'}attempt_root/candidate.whl"
              rm -rf "${'$'}attempt_root" "${'$'}UV_TOOL_DIR" "${'$'}UV_TOOL_BIN_DIR"
              mkdir -p "${'$'}attempt_root" "${'$'}attempt_cache" "${'$'}UV_TOOL_BIN_DIR"
              : > "${'$'}attempt_log"
              echo "KITE_RESOURCE_ROUTE stage=acquire step=${safeId(step.id)} source=${'$'}source_id index=${'$'}pypi_index request=latest"
              set +e
              curl -fL --compressed --connect-timeout 30 --speed-time 60 --speed-limit 1 -o "${'$'}index_file" "${'$'}project_url" 2>>"${'$'}attempt_log"
              pypi_last_status=${'$'}?
              set -e
              if [ "${'$'}pypi_last_status" -ne 0 ]; then
                cat "${'$'}attempt_log"
                echo "KITE_RESOURCE_RETRY stage=acquire step=${safeId(step.id)} source=${'$'}source_id exit=${'$'}pypi_last_status reason=source-unavailable"
                rm -rf "${'$'}attempt_root"
                continue
              fi
              set +e
              python3 - "${'$'}project_url" ${shellLiteral(packageName)} "${'$'}index_file" >"${'$'}candidates_file" 2>>"${'$'}attempt_log" <<'KITE_PYPI_INDEX'
            import html.parser
            import sys
            import urllib.parse

            project_url, package_name, index_path = sys.argv[1:]
            normalized = package_name.lower().replace('-', '_').replace('.', '_') + '-'

            class Links(html.parser.HTMLParser):
                def __init__(self):
                    super().__init__()
                    self.hrefs = []
                def handle_starttag(self, tag, attrs):
                    if tag.lower() == 'a':
                        href = dict(attrs).get('href')
                        if href:
                            self.hrefs.append(href)

            parser = Links()
            parser.feed(open(index_path, encoding='utf-8', errors='replace').read())
            for href in parser.hrefs:
                absolute = urllib.parse.urljoin(project_url, href)
                parsed = urllib.parse.urlparse(absolute)
                filename = urllib.parse.unquote(parsed.path.rsplit('/', 1)[-1])
                lowered = filename.lower()
                if parsed.scheme != 'https' or '|' in absolute:
                    continue
                if not lowered.startswith(normalized):
                    continue
                if not (lowered.endswith('.whl') or lowered.endswith('.tar.gz') or lowered.endswith('.zip')):
                    continue
                version = filename[len(normalized):].split('-', 1)[0]
                sha256 = urllib.parse.parse_qs(parsed.fragment).get('sha256', [''])[0].lower()
                if version and len(sha256) == 64:
                    compatible = int(
                        lowered.endswith('.whl') and (
                            ('aarch64' in lowered and 'manylinux' in lowered) or
                            lowered.endswith('-none-any.whl')
                        )
                    )
                    print(f'{version}|{sha256}|{absolute}|{compatible}')
            KITE_PYPI_INDEX
              metadata_status=${'$'}?
              set -e
              if [ "${'$'}metadata_status" -ne 0 ] || [ ! -s "${'$'}candidates_file" ]; then
                cat "${'$'}attempt_log"
                pypi_last_status=69
                echo "KITE_RESOURCE_RETRY stage=acquire step=${safeId(step.id)} source=${'$'}source_id exit=69 reason=source-incomplete"
                rm -rf "${'$'}attempt_root"
                continue
              fi
              latest_version="${'$'}(cut -d '|' -f 1 "${'$'}candidates_file" | LC_ALL=C sort -V | tail -n 1)"
              expected_sha256=
              for window_entry in "${'$'}{version_window[@]}"; do
                window_version="${'$'}{window_entry%%|*}"
                window_sha256="${'$'}{window_entry#*|}"
                if [ "${'$'}latest_version" = "${'$'}window_version" ]; then
                  expected_sha256="${'$'}window_sha256"
                  break
                fi
              done
              if [ -z "${'$'}expected_sha256" ]; then
                pypi_last_status=69
                echo "KITE_RESOURCE_SOURCE_REJECTED step=${safeId(step.id)} source=${'$'}source_id reason=latest-version-outside-window version=${'$'}latest_version"
                rm -rf "${'$'}attempt_root"
                continue
              fi
              latest_record="${'$'}(awk -F '|' -v version="${'$'}latest_version" -v sha="${'$'}expected_sha256" '${'$'}1 == version && ${'$'}2 == sha && ${'$'}4 == 1 { print; exit }' "${'$'}candidates_file")"
              if [ -z "${'$'}latest_record" ]; then
                pypi_last_status=69
                echo "KITE_RESOURCE_SOURCE_REJECTED step=${safeId(step.id)} source=${'$'}source_id reason=latest-artifact-hash-mismatch version=${'$'}latest_version"
                rm -rf "${'$'}attempt_root"
                continue
              fi
              artifact_url="${'$'}(printf '%s\n' "${'$'}latest_record" | cut -d '|' -f 3)"
              set +e
              curl -fL --compressed --connect-timeout 30 --speed-time 60 --speed-limit 1 -o "${'$'}wheel_file" "${'$'}artifact_url" 2>>"${'$'}attempt_log"
              pypi_last_status=${'$'}?
              set -e
              if [ "${'$'}pypi_last_status" -ne 0 ]; then
                cat "${'$'}attempt_log"
                echo "KITE_RESOURCE_RETRY stage=acquire step=${safeId(step.id)} source=${'$'}source_id exit=${'$'}pypi_last_status reason=source-unavailable"
                rm -rf "${'$'}attempt_root"
                continue
              fi
              actual_sha256="${'$'}(sha256sum "${'$'}wheel_file" | cut -d ' ' -f 1)"
              if [ "${'$'}actual_sha256" != "${'$'}expected_sha256" ]; then
                pypi_last_status=69
                echo "KITE_RESOURCE_SOURCE_REJECTED step=${safeId(step.id)} source=${'$'}source_id reason=artifact-sha256-mismatch version=${'$'}latest_version"
                rm -rf "${'$'}attempt_root"
                continue
              fi
              set +e
              timeout 300 env UV_DEFAULT_INDEX="${'$'}pypi_index" UV_CACHE_DIR="${'$'}attempt_cache" uv tool install --force --python /workspace/.kf/bin/python3 $uvArguments "${'$'}wheel_file" >>"${'$'}attempt_log" 2>&1
              pypi_last_status=${'$'}?
              set -e
              cat "${'$'}attempt_log"
              if [ "${'$'}pypi_last_status" -eq 0 ]; then
                selection_root="${'$'}install_root/.kite-source-selection"
                mkdir -p "${'$'}selection_root"
                printf '%s\n' "${'$'}latest_version" > "${'$'}selection_root/${safeId(step.id)}.version"
                printf '%s\n' "${'$'}expected_sha256" > "${'$'}selection_root/${safeId(step.id)}.sha256"
                rm -rf "${'$'}install_root/.kite-source-attempt"
                echo "KITE_RESOURCE_STEP acquire-complete ${safeId(step.id)} source=${'$'}source_id version=${'$'}latest_version"
                break
              fi
              rm -rf "${'$'}UV_TOOL_DIR" "${'$'}UV_TOOL_BIN_DIR"
              if ! kite_resource_is_source_failure "${'$'}pypi_last_status" "${'$'}attempt_log"; then
                echo "KITE_RESOURCE_FAILURE stage=install step=${safeId(step.id)} source=${'$'}source_id exit=${'$'}pypi_last_status reason=non-network"
                exit "${'$'}pypi_last_status"
              fi
              echo "KITE_RESOURCE_RETRY stage=acquire step=${safeId(step.id)} source=${'$'}source_id exit=${'$'}pypi_last_status reason=source-unavailable"
              rm -rf "${'$'}attempt_root"
            done
            [ "${'$'}pypi_last_status" -eq 0 ] || { rm -rf "${'$'}install_root/.kite-source-attempt"; echo "KITE_RESOURCE_FAILURE stage=acquire step=${safeId(step.id)} exit=${'$'}pypi_last_status reason=no-verified-latest-source"; exit "${'$'}pypi_last_status"; }
        """.trimIndent()
    }

    private fun isSecureRegistryUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value.trim())
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.query == null &&
            uri.fragment == null
    }.getOrDefault(false)

    private fun compileApt(step: KiteResourceInstallStep): String {
        require(step.updateIndex || step.packages.isNotEmpty()) { "apt step ${step.id} has no operation" }
        val id = safeId(step.id)
        val packages = step.packages.joinToString(" ") { shellLiteral(it) }
        return buildString {
            appendLine("command -v apt-get >/dev/null 2>&1 || { echo \"KITE_RESOURCE_FAILURE stage=prepare step=$id reason=apt-missing\"; exit 127; }")
            appendLine("export DEBIAN_FRONTEND=noninteractive")
            appendLine("apt_codename=\"${'$'}(. /etc/os-release 2>/dev/null; printf '%s' \"${'$'}{VERSION_CODENAME:-}\")\"")
            appendLine("[ -n \"${'$'}apt_codename\" ] || { echo \"KITE_RESOURCE_FAILURE stage=prepare step=$id reason=ubuntu-codename-missing\"; exit 65; }")
            appendLine("apt_selected_root=")
            appendLine("apt_last_status=1")
            appendLine("for apt_route in ${'$'}KITE_RESOURCE_UBUNTU_PORTS_ROUTES; do")
            appendLine("  source_id=\"${'$'}{apt_route%%|*}\"")
            appendLine("  apt_base=\"${'$'}{apt_route#*|}\"")
            appendLine("  attempt_root=\"${'$'}install_root/.kite-source-attempt/apt/${'$'}source_id\"")
            appendLine("  rm -rf \"${'$'}attempt_root\"")
            appendLine("  mkdir -p \"${'$'}attempt_root/lists/partial\" \"${'$'}attempt_root/archives/partial\"")
            appendLine("  sources_file=\"${'$'}attempt_root/sources.list\"")
            appendLine("  for pocket in \"\" -updates -backports -security; do printf 'deb %s %s%s main restricted universe multiverse\\n' \"${'$'}apt_base\" \"${'$'}apt_codename\" \"${'$'}pocket\"; done > \"${'$'}sources_file\"")
            appendLine("  apt_options=\"-o Dir::Etc::sourcelist=${'$'}sources_file -o Dir::Etc::sourceparts=- -o Dir::State::lists=${'$'}attempt_root/lists -o Dir::Cache::archives=${'$'}attempt_root/archives -o Acquire::Retries=${step.retryAttempts} -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30\"")
            appendLine("  echo \"KITE_RESOURCE_ROUTE stage=acquire step=$id source=${'$'}source_id base=${'$'}apt_base\"")
            appendLine("  attempt_log=\"${'$'}attempt_root/apt-acquire.log\"")
            appendLine("  : > \"${'$'}attempt_log\"")
            appendLine("  set +e")
            appendLine("  apt-get ${'$'}apt_options update >>\"${'$'}attempt_log\" 2>&1")
            appendLine("  apt_last_status=${'$'}?")
            if (step.packages.isNotEmpty()) {
                appendLine("  if [ \"${'$'}apt_last_status\" -eq 0 ]; then apt-get ${'$'}apt_options --download-only install -y $packages >>\"${'$'}attempt_log\" 2>&1; apt_last_status=${'$'}?; fi")
            }
            appendLine("  set -e")
            appendLine("  cat \"${'$'}attempt_log\"")
            appendLine("  if [ \"${'$'}apt_last_status\" -eq 0 ]; then apt_selected_root=\"${'$'}attempt_root\"; break; fi")
            appendLine("  if ! kite_resource_is_source_failure \"${'$'}apt_last_status\" \"${'$'}attempt_log\"; then")
            appendLine("    echo \"KITE_RESOURCE_FAILURE stage=install step=$id source=${'$'}source_id exit=${'$'}apt_last_status reason=non-network\"")
            appendLine("    exit \"${'$'}apt_last_status\"")
            appendLine("  fi")
            appendLine("  echo \"KITE_RESOURCE_RETRY stage=acquire step=$id source=${'$'}source_id exit=${'$'}apt_last_status\"")
            appendLine("done")
            appendLine("[ -n \"${'$'}apt_selected_root\" ] || { echo \"KITE_RESOURCE_FAILURE stage=acquire step=$id exit=${'$'}apt_last_status\"; exit \"${'$'}apt_last_status\"; }")
            if (step.packages.isNotEmpty()) {
                appendLine("apt_options=\"-o Dir::Etc::sourcelist=${'$'}apt_selected_root/sources.list -o Dir::Etc::sourceparts=- -o Dir::State::lists=${'$'}apt_selected_root/lists -o Dir::Cache::archives=${'$'}apt_selected_root/archives\"")
                appendLine("kite_resource_run ${shellLiteral(id)} install apt-get ${'$'}apt_options --no-download install -y $packages")
            }
            appendLine("rm -rf \"${'$'}install_root/.kite-source-attempt/apt\"")
        }.trim()
    }

    private fun compileGit(step: KiteResourceInstallStep): String {
        val repositories = step.repositories.ifEmpty {
            listOf(step.repository).filter(String::isNotBlank)
        }.distinct()
        require(repositories.isNotEmpty()) { "git step ${step.id} has no repository" }
        require(step.destination.isNotBlank()) { "git step ${step.id} has no destination" }
        val expectedCommit = step.commit.trim().lowercase()
        val latestVersionWindow = step.latestVersionWindow
        if (latestVersionWindow.isNotEmpty()) {
            require(latestVersionWindow.size <= MAX_LATEST_VERSION_WINDOW) {
                "git step ${step.id} latest version window exceeds $MAX_LATEST_VERSION_WINDOW entries"
            }
            require(step.ref.isBlank() && expectedCommit.isBlank()) {
                "git step ${step.id} cannot combine a fixed ref with a latest version window"
            }
            require(latestVersionWindow.map { it.version }.distinct().size == latestVersionWindow.size) {
                "git step ${step.id} latest version window contains duplicate versions"
            }
            require(latestVersionWindow.map { it.ref }.distinct().size == latestVersionWindow.size) {
                "git step ${step.id} latest version window contains duplicate refs"
            }
            latestVersionWindow.forEach { candidate ->
                val candidateRef = candidate.ref.ifBlank { candidate.version }
                require(isSafeGitWindowValue(candidate.version) && isSafeGitWindowValue(candidateRef)) {
                    "git step ${step.id} latest version window contains an unsafe version or ref"
                }
                require(GIT_COMMIT.matches(candidate.commit)) {
                    "git step ${step.id} latest version window contains an invalid commit"
                }
            }
        }
        require(expectedCommit.isBlank() || GIT_COMMIT.matches(expectedCommit)) {
            "git step ${step.id} has an invalid pinned commit"
        }
        require(repositories.size == 1 || expectedCommit.isNotBlank() || latestVersionWindow.isNotEmpty()) {
            "git step ${step.id} requires a pinned commit when mirrors are declared"
        }
        return (listOf(
            shellLiteral(safeId(step.id)),
            shellExpression(step.destination),
            step.depth.toString(),
            shellExpression(step.ref),
            shellLiteral(expectedCommit),
            step.retryAttempts.toString(),
            step.retryDelaySeconds.toString(),
            latestVersionWindow.size.toString(),
        ) + latestVersionWindow.map { candidate ->
            shellLiteral("${candidate.version}|${candidate.ref.ifBlank { candidate.version }}|${candidate.commit}")
        } + listOf("--") + repositories.map(::shellExpression)).joinToString(" ", prefix = "kite_resource_git ")
    }

    private fun isSafeGitWindowValue(value: String): Boolean =
        value.isNotBlank() && !value.startsWith('-') && value.none { it == '|' || it == '\n' || it == '\r' }

    private fun isSafeNpmVersion(value: String): Boolean =
        value.isNotBlank() && !value.startsWith('-') && value.none { it == '|' || it == '\n' || it == '\r' }

    private fun compileInlineShell(step: KiteResourceInstallStep): String {
        require(step.cmd.isNotBlank()) { "Shell step ${step.id} has no command" }
        return buildString {
            appendLine("echo \"KITE_RESOURCE_STEP ${step.type} ${safeId(step.id)}\"")
            append(step.cmd.trim())
        }
    }

    private fun stepRunnerHelper(): String = """
        kite_resource_run() {
          step_id="${'$'}1"
          stage="${'$'}2"
          shift 2
          echo "KITE_RESOURCE_STEP ${'$'}stage ${'$'}step_id"
          if "${'$'}@"; then
            echo "KITE_RESOURCE_STEP ${'$'}stage-complete ${'$'}step_id"
            return 0
          else
            task_status=${'$'}?
          fi
          echo "KITE_RESOURCE_FAILURE stage=${'$'}stage step=${'$'}step_id exit=${'$'}task_status"
          return "${'$'}task_status"
        }
    """.trimIndent()

    private fun sourceRoutingHelper(preferences: KiteResourceSourcePreferences): String {
        val normalized = preferences.normalized()
        val pypiRoutes = KiteResourceSourcePolicy.pypiRoutes(normalized)
        val pypiRouteValue = pypiRoutes.joinToString(" ") { "${it.sourceId}|${it.endpoint}" }
        val pypiIndexes = pypiRoutes.joinToString(" ") { it.endpoint }
        val ubuntuRoutes = KiteResourceSourcePolicy.ubuntuPortsRoutes(normalized)
            .joinToString(" ") { "${it.sourceId}|${it.endpoint}" }
        return """
            export KITE_RESOURCE_SOURCE_ORDER=${shellLiteral(normalized.orderedSourceIds.joinToString(","))}
            export KITE_RESOURCE_PYPI_ROUTES=${shellLiteral(pypiRouteValue)}
            export KITE_RESOURCE_PYPI_INDEXES=${shellLiteral(pypiIndexes)}
            export KITE_RESOURCE_UBUNTU_PORTS_ROUTES=${shellLiteral(ubuntuRoutes)}
            export UV_DEFAULT_INDEX="${'$'}(printf '%s\n' ${'$'}KITE_RESOURCE_PYPI_INDEXES | head -n 1)"
            KITE_RESOURCE_SOURCE_RUNTIME="${'$'}install_root/.kite-source-runtime"
            KITE_RESOURCE_SOURCE_HELPER="${'$'}KITE_RESOURCE_SOURCE_RUNTIME/helpers.sh"
            mkdir -p "${'$'}KITE_RESOURCE_SOURCE_RUNTIME"
            cat > "${'$'}KITE_RESOURCE_SOURCE_HELPER" <<'KITE_RESOURCE_SOURCE_HELPER_EOF'
            kite_resource_is_source_failure() {
              source_status="${'$'}1"
              source_log="${'$'}2"
              [ "${'$'}source_status" -eq 124 ] && return 0
              if grep -Eiq 'missing an upload date|has no publish time|metadata[^[:cntrl:]]*(missing|incomplete)|lockfile[^[:cntrl:]]*needs to be updated[^[:cntrl:]]*--locked' "${'$'}source_log"; then
                return 0
              fi
              if grep -Eiq 'sha256-mismatch|checksum mismatch|hash sum mismatch|signature[^[:cntrl:]]*(invalid|failed)|NO_PUBKEY|repository[^[:cntrl:]]*not signed|Unable to locate package|dependency conflict|ResolutionImpossible' "${'$'}source_log"; then
                return 1
              fi
              grep -Eiq 'EAI_AGAIN|ENETUNREACH|ECONNRESET|ECONNREFUSED|ETIMEDOUT|ERR_SOCKET_TIMEOUT|Temporary failure resolving|Could not resolve|Connection (failed|timed out|refused)|Network is unreachable|Failed to fetch|error sending request|dns error|Request failed after [0-9]+ retries|E(404|429|5[0-9][0-9])|status code (404|429|5[0-9][0-9])|HTTP[^[:cntrl:]]*(404|429|5[0-9][0-9])' "${'$'}source_log"
            }
            KITE_RESOURCE_SOURCE_HELPER_EOF
            export BASH_ENV="${'$'}KITE_RESOURCE_SOURCE_HELPER"
            . "${'$'}KITE_RESOURCE_SOURCE_HELPER"
        """.trimIndent()
    }

    private fun downloadHelper(): String = """
        kite_resource_download() {
          step_id="${'$'}1"
          destination="${'$'}2"
          expected_sha256="${'$'}3"
          max_attempts="${'$'}4"
          retry_delay="${'$'}5"
          shift 5
          command -v curl >/dev/null 2>&1 || {
            echo "KITE_RESOURCE_FAILURE stage=prepare step=${'$'}step_id reason=curl-missing"
            return 127
          }
          mkdir -p "${'$'}(dirname "${'$'}destination")"
          partial="${'$'}destination.part"
          last_status=1
          for download_url in "${'$'}@"; do
            attempt=1
            while [ "${'$'}attempt" -le "${'$'}max_attempts" ]; do
              echo "KITE_RESOURCE_STEP acquire ${'$'}step_id attempt=${'$'}attempt url=${'$'}download_url"
              set +e
              if [ -s "${'$'}partial" ]; then
                curl -fL --connect-timeout 30 --speed-time 60 --speed-limit 1 -C - -o "${'$'}partial" "${'$'}download_url"
              else
                curl -fL --connect-timeout 30 --speed-time 60 --speed-limit 1 -o "${'$'}partial" "${'$'}download_url"
              fi
              last_status=${'$'}?
              set -e
              if [ "${'$'}last_status" -eq 0 ] && [ -s "${'$'}partial" ]; then
                if [ -n "${'$'}expected_sha256" ]; then
                  actual_sha256="${'$'}(sha256sum "${'$'}partial" | cut -d ' ' -f 1)"
                  if [ "${'$'}actual_sha256" != "${'$'}expected_sha256" ]; then
                    echo "KITE_RESOURCE_FAILURE stage=verify-download step=${'$'}step_id reason=sha256-mismatch"
                    rm -f "${'$'}partial"
                    return 65
                  fi
                fi
                mv -f "${'$'}partial" "${'$'}destination"
                echo "KITE_RESOURCE_STEP acquire-complete ${'$'}step_id bytes=${'$'}(wc -c < "${'$'}destination")"
                return 0
              fi
              if [ "${'$'}last_status" -eq 33 ]; then
                rm -f "${'$'}partial"
              fi
              echo "KITE_RESOURCE_RETRY stage=acquire step=${'$'}step_id attempt=${'$'}attempt exit=${'$'}last_status"
              sleep "$((retry_delay * attempt))"
              attempt=$((attempt + 1))
            done
            rm -f "${'$'}partial"
          done
          echo "KITE_RESOURCE_FAILURE stage=acquire step=${'$'}step_id exit=${'$'}last_status"
          return "${'$'}last_status"
        }
    """.trimIndent()

    private fun gitHelper(): String = """
        kite_resource_git() {
          step_id="${'$'}1"
          destination="${'$'}2"
          depth="${'$'}3"
          ref="${'$'}4"
          expected_commit="${'$'}5"
          max_attempts="${'$'}6"
          retry_delay="${'$'}7"
          window_count="${'$'}8"
          shift 8
          version_window=()
          window_index=0
          while [ "${'$'}window_index" -lt "${'$'}window_count" ]; do
            version_window+=("${'$'}1")
            shift
            window_index=$((window_index + 1))
          done
          [ "${'$'}{1:-}" = "--" ] || {
            echo "KITE_RESOURCE_FAILURE stage=prepare step=${'$'}step_id reason=git-window-contract-invalid"
            return 64
          }
          shift
          command -v git >/dev/null 2>&1 || {
            echo "KITE_RESOURCE_FAILURE stage=prepare step=${'$'}step_id reason=git-missing"
            return 127
          }
          candidate="${'$'}destination.kite-clone"
          attempt_root="${'$'}install_root/.kite-source-attempt/git/${'$'}step_id"
          refs_file="${'$'}attempt_root/remote-refs"
          query_log="${'$'}attempt_root/latest-query.log"
          mkdir -p "${'$'}(dirname "${'$'}destination")"
          last_status=1
          for repository in "${'$'}@"; do
            attempt=1
            source_rejected=0
            while [ "${'$'}attempt" -le "${'$'}max_attempts" ]; do
              rm -rf "${'$'}candidate" "${'$'}attempt_root"
              mkdir -p "${'$'}attempt_root"
              selected_version=
              selected_ref="${'$'}ref"
              selected_commit="${'$'}expected_commit"
              if [ "${'$'}window_count" -gt 0 ]; then
                echo "KITE_RESOURCE_ROUTE stage=acquire step=${'$'}step_id source=${'$'}repository request=latest"
                set +e
                git ls-remote --tags --refs "${'$'}repository" >"${'$'}refs_file" 2>"${'$'}query_log"
                last_status=${'$'}?
                set -e
                if [ "${'$'}last_status" -ne 0 ]; then
                  cat "${'$'}query_log"
                  echo "KITE_RESOURCE_RETRY stage=acquire step=${'$'}step_id source=${'$'}repository attempt=${'$'}attempt exit=${'$'}last_status reason=latest-query-failed"
                  sleep "$((retry_delay * attempt))"
                  attempt=$((attempt + 1))
                  continue
                fi
                latest_ref="${'$'}(awk '{ sub(/^refs\/tags\//, "", ${'$'}2); print ${'$'}2 }' "${'$'}refs_file" | LC_ALL=C sort -V | tail -n 1)"
                if [ -z "${'$'}latest_ref" ]; then
                  last_status=69
                  source_rejected=1
                  echo "KITE_RESOURCE_SOURCE_REJECTED step=${'$'}step_id source=${'$'}repository reason=latest-version-missing"
                  break
                fi
                for window_entry in "${'$'}{version_window[@]}"; do
                  window_version="${'$'}{window_entry%%|*}"
                  window_tail="${'$'}{window_entry#*|}"
                  window_ref="${'$'}{window_tail%%|*}"
                  window_commit="${'$'}{window_tail#*|}"
                  if [ "${'$'}latest_ref" = "${'$'}window_ref" ]; then
                    selected_version="${'$'}window_version"
                    selected_ref="${'$'}window_ref"
                    selected_commit="${'$'}window_commit"
                    break
                  fi
                done
                if [ -z "${'$'}selected_version" ]; then
                  last_status=69
                  source_rejected=1
                  echo "KITE_RESOURCE_SOURCE_REJECTED step=${'$'}step_id source=${'$'}repository reason=latest-version-outside-window version=${'$'}latest_ref"
                  break
                fi
              fi
              if [ -n "${'$'}selected_ref" ]; then
                set +e
                kite_resource_run "${'$'}step_id" acquire git clone --depth "${'$'}depth" --branch "${'$'}selected_ref" "${'$'}repository" "${'$'}candidate"
                last_status=${'$'}?
                set -e
              else
                set +e
                kite_resource_run "${'$'}step_id" acquire git clone --depth "${'$'}depth" "${'$'}repository" "${'$'}candidate"
                last_status=${'$'}?
                set -e
              fi
              if [ "${'$'}last_status" -eq 0 ]; then
                if [ -n "${'$'}selected_commit" ]; then
                  actual_commit="${'$'}(git -C "${'$'}candidate" rev-parse HEAD 2>/dev/null || true)"
                  if [ "${'$'}actual_commit" != "${'$'}selected_commit" ]; then
                    rm -rf "${'$'}candidate"
                    last_status=65
                    if [ "${'$'}window_count" -gt 0 ]; then
                      source_rejected=1
                      echo "KITE_RESOURCE_SOURCE_REJECTED step=${'$'}step_id source=${'$'}repository reason=git-commit-mismatch version=${'$'}selected_version expected=${'$'}selected_commit actual=${'$'}actual_commit"
                      break
                    fi
                    echo "KITE_RESOURCE_FAILURE stage=verify-download step=${'$'}step_id source=${'$'}repository reason=git-commit-mismatch expected=${'$'}selected_commit actual=${'$'}actual_commit"
                    return 65
                  fi
                fi
                rm -rf "${'$'}destination"
                mv "${'$'}candidate" "${'$'}destination"
                selection_root="${'$'}install_root/.kite-source-selection"
                mkdir -p "${'$'}selection_root"
                printf '%s\n' "${'$'}selected_version" > "${'$'}selection_root/${'$'}step_id.version"
                printf '%s\n' "${'$'}selected_ref" > "${'$'}selection_root/${'$'}step_id.ref"
                printf '%s\n' "${'$'}{actual_commit:-}" > "${'$'}selection_root/${'$'}step_id.commit"
                rm -rf "${'$'}attempt_root"
                echo "KITE_RESOURCE_STEP acquire-complete ${'$'}step_id source=${'$'}repository version=${'$'}selected_version commit=${'$'}{actual_commit:-}"
                return 0
              fi
              echo "KITE_RESOURCE_RETRY stage=acquire step=${'$'}step_id source=${'$'}repository attempt=${'$'}attempt exit=${'$'}last_status"
              sleep "$((retry_delay * attempt))"
              attempt=$((attempt + 1))
            done
            if [ "${'$'}source_rejected" -eq 1 ]; then
              rm -rf "${'$'}candidate" "${'$'}attempt_root"
              continue
            fi
          done
          rm -rf "${'$'}candidate" "${'$'}attempt_root"
          if [ "${'$'}window_count" -gt 0 ]; then
            echo "KITE_RESOURCE_FAILURE stage=acquire step=${'$'}step_id exit=${'$'}last_status reason=no-verified-latest-source"
          else
            echo "KITE_RESOURCE_FAILURE stage=acquire step=${'$'}step_id exit=${'$'}last_status"
          fi
          return "${'$'}last_status"
        }
    """.trimIndent()

    private fun shellLiteral(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun shellExpression(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("`", "\\`") + "\""

    private fun safeId(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-').ifBlank { "step" }

    private val SAFE_ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val SAFE_NPM_PACKAGE = Regex("(?:@[a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*")
    private val SAFE_PYPI_PACKAGE = Regex("[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?")
    private val SAFE_JSON_FIELD = Regex("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*")
    private val NPM_INTEGRITY = Regex("sha(?:1|256|384|512)-[A-Za-z0-9+/]+={0,2}")
    private val GIT_COMMIT = Regex("[a-f0-9]{40}")
    private const val MAX_LATEST_VERSION_WINDOW = 3
    private const val MAX_LATEST_REGEX_LENGTH = 512
    private const val MAX_LATEST_METADATA_BYTES = 4_194_304
}
