package com.kite.app.resources

object KiteResourceInstallPlanCompiler {
    const val ACTION_MANAGED = "managed"
    const val STEP_APT = "apt"
    const val STEP_BUNDLED = "bundled"
    const val STEP_DOWNLOAD = "download"
    const val STEP_GIT = "git"
    const val STEP_NPM = "npm"
    const val STEP_SCRIPT = "script"
    const val STEP_SHELL = "shell"

    fun compile(action: KiteResourceShellAction): String {
        if (action.type == "shell") return action.cmd
        require(action.type == ACTION_MANAGED) { "Unsupported resource install action: ${action.type}" }
        require(action.installSteps.isNotEmpty()) { "Managed resource install action has no steps" }

        return buildString {
            appendLine("set -e")
            if (action.installSteps.any { it.type in setOf(STEP_APT, STEP_GIT, STEP_NPM, STEP_SCRIPT) }) {
                appendLine(heartbeatHelper())
            }
            if (action.installSteps.any { it.type == STEP_DOWNLOAD }) {
                appendLine(downloadHelper())
            }
            if (action.installSteps.any { it.type == STEP_GIT }) {
                appendLine(gitHelper())
            }
            action.installSteps.forEach { step ->
                appendLine(compileStep(step))
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
            STEP_GIT -> step.repository
            STEP_NPM, STEP_APT -> step.packages.joinToString(" ")
            STEP_SCRIPT -> listOf(step.interpreter, step.path).filter { it.isNotBlank() }.joinToString(" ")
            else -> step.cmd.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        }.take(160)
    }

    private fun compileStep(step: KiteResourceInstallStep): String = when (step.type) {
        STEP_DOWNLOAD -> compileDownload(step)
        STEP_SCRIPT -> compileScript(step)
        STEP_NPM -> compileNpm(step)
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

    private fun compileNpm(step: KiteResourceInstallStep): String {
        require(step.packages.isNotEmpty()) { "npm step ${step.id} has no packages" }
        step.registries.forEach { registry ->
            require(isSecureRegistryUrl(registry)) {
                "npm step ${step.id} has an invalid HTTPS registry"
            }
        }
        val arguments = step.arguments.joinToString(" ") { shellLiteral(it) }
        val packages = step.packages.joinToString(" ") { shellLiteral(it) }
        val installArguments = listOf(arguments, packages).filter { it.isNotBlank() }.joinToString(" ")
        val installCommand = if (step.registries.isEmpty()) {
            "npm install -g $installArguments"
        } else {
            val functionSuffix = safeId(step.id).replace(Regex("[^a-z0-9_]"), "_")
            val functionName = "kite_resource_npm_$functionSuffix"
            val registries = step.registries.distinct().joinToString(" ") { shellLiteral(it) }
            """
                $functionName() {
                  last_status=1
                  for npm_registry in $registries; do
                    echo "KITE_RESOURCE_ROUTE stage=install step=${safeId(step.id)} registry=${'$'}npm_registry"
                    if npm install -g --registry="${'$'}npm_registry" $installArguments; then
                      return 0
                    else
                      last_status=${'$'}?
                    fi
                  done
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
        val options = "-o Acquire::Retries=${step.retryAttempts} -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30"
        return buildString {
            appendLine("command -v apt-get >/dev/null 2>&1 || { echo \"KITE_RESOURCE_FAILURE stage=prepare step=$id reason=apt-missing\"; exit 127; }")
            appendLine("export DEBIAN_FRONTEND=noninteractive")
            if (step.updateIndex) {
                appendLine("kite_resource_run ${shellLiteral(id)} acquire apt-get $options update")
            }
            if (step.packages.isNotEmpty()) {
                val packages = step.packages.joinToString(" ") { shellLiteral(it) }
                appendLine("kite_resource_run ${shellLiteral(id)} install apt-get $options install -y $packages")
            }
        }.trim()
    }

    private fun compileGit(step: KiteResourceInstallStep): String {
        val repositories = step.repositories.ifEmpty {
            listOf(step.repository).filter(String::isNotBlank)
        }.distinct()
        require(repositories.isNotEmpty()) { "git step ${step.id} has no repository" }
        require(step.destination.isNotBlank()) { "git step ${step.id} has no destination" }
        val expectedCommit = step.commit.trim().lowercase()
        require(expectedCommit.isBlank() || GIT_COMMIT.matches(expectedCommit)) {
            "git step ${step.id} has an invalid pinned commit"
        }
        require(repositories.size == 1 || expectedCommit.isNotBlank()) {
            "git step ${step.id} requires a pinned commit when mirrors are declared"
        }
        return (listOf(
            shellLiteral(safeId(step.id)),
            shellExpression(step.destination),
            step.depth.toString(),
            shellExpression(step.ref),
            shellLiteral(expectedCommit),
            step.retryAttempts.toString(),
            step.retryDelaySeconds.toString()
        ) + repositories.map(::shellExpression)).joinToString(" ", prefix = "kite_resource_git ")
    }

    private fun compileInlineShell(step: KiteResourceInstallStep): String {
        require(step.cmd.isNotBlank()) { "Shell step ${step.id} has no command" }
        return buildString {
            appendLine("echo \"KITE_RESOURCE_STEP ${step.type} ${safeId(step.id)}\"")
            append(step.cmd.trim())
        }
    }

    private fun heartbeatHelper(): String = """
        kite_resource_run() {
          step_id="${'$'}1"
          stage="${'$'}2"
          shift 2
          echo "KITE_RESOURCE_STEP ${'$'}stage ${'$'}step_id"
          "${'$'}@" &
          task_pid=${'$'}!
          elapsed=0
          while kill -0 "${'$'}task_pid" 2>/dev/null; do
            sleep 1
            elapsed=$((elapsed + 1))
            if [ $((elapsed % 5)) -eq 0 ] && kill -0 "${'$'}task_pid" 2>/dev/null; then
              echo "KITE_RESOURCE_HEARTBEAT stage=${'$'}stage step=${'$'}step_id elapsed=${'$'}elapsed"
            fi
          done
          if wait "${'$'}task_pid"; then
            echo "KITE_RESOURCE_STEP ${'$'}stage-complete ${'$'}step_id"
            return 0
          else
            task_status=${'$'}?
          fi
          echo "KITE_RESOURCE_FAILURE stage=${'$'}stage step=${'$'}step_id exit=${'$'}task_status"
          return "${'$'}task_status"
        }
    """.trimIndent()

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
          shift 7
          command -v git >/dev/null 2>&1 || {
            echo "KITE_RESOURCE_FAILURE stage=prepare step=${'$'}step_id reason=git-missing"
            return 127
          }
          candidate="${'$'}destination.kite-clone"
          mkdir -p "${'$'}(dirname "${'$'}destination")"
          last_status=1
          for repository in "${'$'}@"; do
            attempt=1
            while [ "${'$'}attempt" -le "${'$'}max_attempts" ]; do
              rm -rf "${'$'}candidate"
              if [ -n "${'$'}ref" ]; then
                set +e
                kite_resource_run "${'$'}step_id" acquire git clone --depth "${'$'}depth" --branch "${'$'}ref" "${'$'}repository" "${'$'}candidate"
                last_status=${'$'}?
                set -e
              else
                set +e
                kite_resource_run "${'$'}step_id" acquire git clone --depth "${'$'}depth" "${'$'}repository" "${'$'}candidate"
                last_status=${'$'}?
                set -e
              fi
              if [ "${'$'}last_status" -eq 0 ]; then
                if [ -n "${'$'}expected_commit" ]; then
                  actual_commit="${'$'}(git -C "${'$'}candidate" rev-parse HEAD 2>/dev/null || true)"
                  if [ "${'$'}actual_commit" != "${'$'}expected_commit" ]; then
                    rm -rf "${'$'}candidate"
                    last_status=65
                    echo "KITE_RESOURCE_RETRY stage=verify-download step=${'$'}step_id source=${'$'}repository reason=git-commit-mismatch expected=${'$'}expected_commit actual=${'$'}actual_commit"
                    break
                  fi
                fi
                rm -rf "${'$'}destination"
                mv "${'$'}candidate" "${'$'}destination"
                echo "KITE_RESOURCE_STEP acquire-complete ${'$'}step_id source=${'$'}repository commit=${'$'}expected_commit"
                return 0
              fi
              echo "KITE_RESOURCE_RETRY stage=acquire step=${'$'}step_id source=${'$'}repository attempt=${'$'}attempt exit=${'$'}last_status"
              sleep "$((retry_delay * attempt))"
              attempt=$((attempt + 1))
            done
          done
          rm -rf "${'$'}candidate"
          echo "KITE_RESOURCE_FAILURE stage=acquire step=${'$'}step_id exit=${'$'}last_status"
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
}
