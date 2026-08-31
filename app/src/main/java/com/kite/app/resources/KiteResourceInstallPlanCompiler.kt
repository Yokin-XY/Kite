package com.kite.app.resources

object KiteResourceInstallPlanCompiler {
    const val ACTION_MANAGED = "managed"
    const val STEP_APT = "apt"
    const val STEP_BUNDLED = "bundled"
    const val STEP_DOWNLOAD = "download"
    const val STEP_ARCHIVE = "archive"
    const val STEP_GIT = "git"
    const val STEP_NPM = "npm"
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
            if (routedAction.installSteps.any { it.type in setOf(STEP_APT, STEP_GIT, STEP_NPM, STEP_SCRIPT) }) {
                appendLine(stepRunnerHelper())
            }
            if (routedAction.installSteps.any { it.type == STEP_DOWNLOAD }) {
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
            STEP_DOWNLOAD -> step.urls.firstOrNull().orEmpty()
            STEP_ARCHIVE -> listOf(step.archiveFormat, step.path, step.destination).joinToString(" ")
            STEP_GIT -> step.repository
            STEP_NPM, STEP_APT -> step.packages.joinToString(" ")
            STEP_SCRIPT -> listOf(step.interpreter, step.path).filter { it.isNotBlank() }.joinToString(" ")
            else -> step.cmd.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        }.take(160)
    }

    private fun compileStep(
        step: KiteResourceInstallStep,
        npmAttemptVerifications: List<KiteResourceInstallVerification> = emptyList(),
    ): String = when (step.type) {
        STEP_DOWNLOAD -> compileDownload(step)
        STEP_ARCHIVE -> error("Archive step ${step.id} must be compiled by the Android native archive planner")
        STEP_SCRIPT -> compileScript(step)
        STEP_NPM -> compileNpm(step, npmAttemptVerifications)
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
                require(isSafeGitWindowValue(candidate.version) && isSafeGitWindowValue(candidate.ref)) {
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
            shellLiteral("${candidate.version}|${candidate.ref}|${candidate.commit}")
        } + listOf("--") + repositories.map(::shellExpression)).joinToString(" ", prefix = "kite_resource_git ")
    }

    private fun isSafeGitWindowValue(value: String): Boolean =
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
              if grep -Eiq 'missing an upload date|has no publish time|metadata[^[:cntrl:]]*(missing|incomplete)' "${'$'}source_log"; then
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
    private val GIT_COMMIT = Regex("[a-f0-9]{40}")
    private const val MAX_LATEST_VERSION_WINDOW = 3
}
