package com.kite.app.resources

import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeAction
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.recipe.KiteRecipeStep

data class KiteResourceInstallSpec(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val iconName: String = KiteRecipeIcon.ICON_TOOLS,
    val operation: String = "install",
    val actionLabel: String = "安装",
    val steps: List<KiteRecipeStep>
)

object KiteResourceInstallRecipes {
    const val RUNTIME_SOURCE = "resource"
    const val WORKSPACE_RESOURCE_ROOT = "/workspace/.kf/cache/resources"
    const val WORKSPACE_SHARED_CACHE_ROOT = "/workspace/.kf/cache/shared"
    const val WORKSPACE_SOFTWARE_ROOT = "/workspace/.kf/software"
    const val WORKSPACE_BIN_ROOT = "/workspace/.kf/bin"
    const val OP_INSTALL = "install"
    const val OP_UPDATE = "update"
    const val OP_REINSTALL = "reinstall"
    const val OP_REPAIR = "repair"
    const val OP_UNINSTALL = "uninstall"
    const val OP_OPEN = "open"
    val MAINTENANCE_OPERATIONS = setOf(OP_UPDATE, OP_REINSTALL, OP_REPAIR)
    private const val TOOL_ENV_BIN_COMMANDS =
        "pnpm pnpx wget jq rg fd zip unzip zstd file tar gzip gunzip xz unxz bzip2 bunzip2 ps pgrep pkill pidof top free ip ss netstat ping dig nslookup host update-ca-certificates less tree rsync patch sed awk grep find xargs sort uniq head tail cut tr wc tee env which whoami id uname date sleep timeout kill sha256sum sha1sum md5sum base64 chmod chown chgrp ln readlink realpath mkdir rmdir rm cp mv touch du df stat systemctl service"

    @Suppress("UNUSED_PARAMETER")
    fun localPackPath(resourceId: String, packId: String = "ai-dev-pack"): String =
        "$WORKSPACE_SHARED_CACHE_ROOT/$packId"

    fun resourceCachePath(resourceId: String): String =
        "$WORKSPACE_RESOURCE_ROOT/${safeId(resourceId)}"

    fun softwarePath(resourceId: String): String =
        "$WORKSPACE_SOFTWARE_ROOT/${safeId(resourceId)}"

    fun preservedResourcePath(resourceId: String): String =
        "${softwarePath(resourceId)}.kite-retained"

    fun installRoot(resourceId: String): String =
        softwarePath(resourceId)

    fun recipeId(resourceId: String, operation: String): String =
        "resource-${safeId(resourceId)}-${safeId(operation)}"

    fun localToolchainCommand(resourceId: String, mode: String, cleanInstallRoot: Boolean = true): String {
        val packPath = localPackPath(resourceId)
        val installRoot = installRoot(resourceId)
        val cleanCommand = if (cleanInstallRoot) {
            """
                echo "KITE_RESOURCE_STEP clean-install-root ${'$'}KF_TOOLCHAIN_DIR"
                kite_clean_install_root "${'$'}KF_TOOLCHAIN_DIR"
            """.trimIndent()
        } else {
            ":"
        }
        return """
            set -e
            export KF_RESOURCE_ID="${safeId(resourceId)}"
            export KF_TOOLCHAIN_PACK_DIR="$packPath"
            export KF_TOOLCHAIN_DIR="$installRoot"
            export KF_TOOLCHAIN_BIN_DIR="${'$'}KF_TOOLCHAIN_DIR/bin"
            export UV_LINK_MODE="copy"
            $cleanCommand
            echo "KITE_RESOURCE_STEP prepare-install-root ${'$'}KF_TOOLCHAIN_DIR"
            mkdir -p "${'$'}KF_TOOLCHAIN_DIR" "${'$'}KF_TOOLCHAIN_BIN_DIR"
            echo "KITE_RESOURCE_STEP run-install-script ${'$'}KF_TOOLCHAIN_PACK_DIR/install.sh $mode"
            bash "${'$'}KF_TOOLCHAIN_PACK_DIR/install.sh" "$mode"
        """.trimIndent()
    }

    fun hermesWebUiInstallCommand(): String =
        """
            set -e
            export PATH="$WORKSPACE_BIN_ROOT:/root/.local/bin:${'$'}PATH"
            resource_root="${softwarePath("kite.hermes.webui")}"
            repo_dir="${'$'}resource_root/hermes-webui"
            state_dir="${'$'}resource_root/state"
            bin_dir="$WORKSPACE_BIN_ROOT"
            hermes_core_root="${softwarePath("kite.hermes.core")}"
            hermes_agent_dir="${'$'}hermes_core_root/hermes-agent"
            hermes_home="${'$'}hermes_core_root/home"
            agent_python="${'$'}hermes_agent_dir/venv/bin/python"
            if ! command -v hermes >/dev/null 2>&1; then
              echo "缺少 hermes：请先安装 Hermes Core 资源。"
              exit 127
            fi
            if ! command -v git >/dev/null 2>&1; then
              echo "缺少 git：请先安装 Git 资源。"
              exit 127
            fi
            if ! command -v python3 >/dev/null 2>&1; then
              echo "缺少 python3：请先安装 Python 资源。"
              exit 127
            fi
            if [ ! -f "${'$'}hermes_agent_dir/run_agent.py" ]; then
              echo "Hermes Agent 目录不存在：${'$'}hermes_agent_dir"
              exit 127
            fi
            if [ ! -x "${'$'}agent_python" ]; then
              echo "Hermes Agent Python 不存在：${'$'}agent_python"
              exit 127
            fi
            ensure_agent_pip() {
              if "${'$'}agent_python" -m pip --version >/dev/null 2>&1; then
                return 0
              fi
              echo "KITE_RESOURCE_STEP repair-hermes-core-pip"
              if ! python3 -m venv --help >/dev/null 2>&1 || ! python3 -m pip --version >/dev/null 2>&1; then
                echo "缺少 pip/venv：Kite 离线 rootfs 打包不完整。"
                exit 127
              fi
              if ! "${'$'}agent_python" -m ensurepip --upgrade; then
                python3 -m pip --python "${'$'}agent_python" install --upgrade pip
              fi
              "${'$'}agent_python" -m pip --version
            }
            echo "KITE_RESOURCE_STEP prepare-install-root ${'$'}resource_root"
            mkdir -p "${'$'}resource_root" "${'$'}state_dir" "${'$'}bin_dir"
            if [ -d "${'$'}repo_dir/.git" ]; then
              echo "KITE_RESOURCE_STEP update-repo nesquena/hermes-webui"
              git -C "${'$'}repo_dir" fetch --depth=1 origin master
              git -C "${'$'}repo_dir" reset --hard FETCH_HEAD
            else
              echo "KITE_RESOURCE_STEP clone-repo nesquena/hermes-webui"
              rm -rf "${'$'}repo_dir"
              git clone --depth=1 https://github.com/nesquena/hermes-webui.git "${'$'}repo_dir"
            fi
            chmod +x "${'$'}repo_dir/start.sh" "${'$'}repo_dir/ctl.sh" 2>/dev/null || true
            ensure_agent_pip
            echo "KITE_RESOURCE_STEP install-python-deps"
            "${'$'}agent_python" -m pip install --quiet -r "${'$'}repo_dir/requirements.txt"
            echo "KITE_RESOURCE_STEP validate-webui-runtime"
            HERMES_HOME="${'$'}hermes_home" \
            HERMES_WEBUI_AGENT_DIR="${'$'}hermes_agent_dir" \
            PYTHONPATH="${'$'}hermes_agent_dir${'$'}{PYTHONPATH:+:${'$'}PYTHONPATH}" \
            "${'$'}agent_python" - <<'PY'
import yaml
import cryptography
from run_agent import AIAgent
print("Hermes WebUI runtime import check OK")
PY
            echo "KITE_RESOURCE_STEP write-launcher ${'$'}bin_dir/hermes-webui-kite"
            cat > "${'$'}bin_dir/hermes-webui-kite" <<SH
#!/usr/bin/env bash
set -e
export PATH="${'$'}bin_dir:/root/.local/bin:\${'$'}PATH"
export HERMES_HOME="${'$'}hermes_home"
export HERMES_WEBUI_AGENT_DIR="${'$'}hermes_agent_dir"
export HERMES_WEBUI_PYTHON="${'$'}agent_python"
export HERMES_WEBUI_STATE_DIR="${'$'}state_dir"
export HERMES_WEBUI_HOST="\${'$'}HERMES_WEBUI_HOST:-127.0.0.1"
export HERMES_WEBUI_PORT="\${'$'}HERMES_WEBUI_PORT:-8787"
cd "${'$'}repo_dir"
exec ./ctl.sh "\${'$'}@"
SH
            chmod +x "${'$'}bin_dir/hermes-webui-kite"
            printf '%s\n' 'installed_by_kite' > "${'$'}resource_root/ownership"
            echo "Hermes WebUI installed from https://github.com/nesquena/hermes-webui"
        """.trimIndent()

    fun manifestInstallCommand(
        resourceId: String,
        displayName: String,
        rawCommand: String,
        managedCommands: List<String>,
        cleanInstallRoot: Boolean,
        verificationCommand: String = ":",
        versionProbeCommand: String = "",
        expectedVersion: String? = null,
        preservePaths: List<String> = emptyList(),
        recordOwnership: Boolean = true,
        protectExistingInstall: Boolean = false,
        operation: String = OP_INSTALL,
    ): String {
        val safeCommands = managedCommands.map(::safeCommandName).filter { it.isNotBlank() }.distinct()
        val commandList = safeCommands.joinToString(" ")
        val cleanInstallRootFlag = if (cleanInstallRoot) "1" else "0"
        val transactionalClean = if (cleanInstallRoot && protectExistingInstall) "1" else "0"
        val transactionResourceId = safeId(resourceId)
        val transactionOperation = transactionStateValue(operation)
        val transactionTargetVersion = transactionStateValue(expectedVersion.orEmpty())
        val versionEvidence = versionEvidenceCommand(versionProbeCommand, expectedVersion)
        val restorePreservedPaths = restorePreservedPathsCommand(preservePaths)
        val stageCandidatePreservedPaths = stageCandidatePreservedPathsCommand(preservePaths)
        val clearRetainedPaths = clearRetainedPathsCommand(preservePaths).let { command ->
            if (command == ":") command else """
                if [ "${'$'}candidate_install" != "1" ]; then
                  $command
                fi
            """.trimIndent()
        }
        val ownershipCommit = if (recordOwnership) {
            """
                ownership_tmp="${'$'}install_root/.ownership.tmp-${'$'}${'$'}"
                printf '%s\n' 'installed_by_kite' > "${'$'}ownership_tmp"
                mv -f "${'$'}ownership_tmp" "${'$'}install_root/ownership"
            """.trimIndent()
        } else {
            "echo \"KITE_RESOURCE_STEP retain-install-ownership ${safeId(resourceId)}\""
        }
        return """
            set -e
            install_root="${softwarePath(resourceId)}"
            backup_root="${'$'}install_root.kite-backup"
            retained_root="${preservedResourcePath(resourceId)}"
            old_ledger_snapshot="${'$'}install_root.kite-old-commands.${'$'}${'$'}"
            failed_ledger_snapshot="${'$'}install_root.kite-failed-commands.${'$'}${'$'}"
            update_lock="${'$'}install_root.kite-update-lock"
            transaction_state="${'$'}install_root.kite-transaction"
            transaction_state_tmp="${'$'}transaction_state.tmp.${'$'}${'$'}"
            transaction_resource_id="$transactionResourceId"
            transaction_operation="$transactionOperation"
            transaction_target_version="$transactionTargetVersion"
            transactional_clean="$transactionalClean"
            clean_install_root="$cleanInstallRootFlag"
            candidate_install="${'$'}{KITE_RESOURCE_CANDIDATE:-0}"
            if [ "${'$'}candidate_install" = "1" ]; then
              transactional_clean=0
            fi
            transaction_committed=0
            update_lock_owned=0
            user_home="${'$'}install_root/user-home"
            npm_prefix="${'$'}install_root/npm-global"
            export HOME="${'$'}user_home"
            export npm_config_prefix="${'$'}npm_prefix"
            export PATH="$WORKSPACE_BIN_ROOT:${'$'}install_root/bin:${'$'}npm_prefix/bin:${'$'}HOME/.local/bin:${'$'}HOME/.kimi-code/bin:${'$'}HOME/.codex/bin:${'$'}HOME/.claude/local:${'$'}HOME/.opencode/bin:/root/.local/bin:/root/.kimi-code/bin:/root/.codex/bin:/root/.claude/local:/root/.opencode/bin:${'$'}PATH"
            command_ledger="${'$'}install_root/.kite-managed-commands"
            command_snapshot_after="${'$'}install_root/.kite-commands-after"
            candidate_input="${'$'}install_root/.kite-candidate-input"
            explicit_commands="$commandList"
            public_command_roots="${'$'}install_root/bin ${'$'}npm_prefix/bin ${'$'}HOME/.local/bin ${'$'}HOME/.kimi-code/bin ${'$'}HOME/.codex/bin ${'$'}HOME/.claude/local ${'$'}HOME/.opencode/bin"
            legacy_kite_wrapper_target() {
              wrapper_path="${'$'}1"
              [ -f "${'$'}wrapper_path" ] || return 1
              [ ! -L "${'$'}wrapper_path" ] || return 1
              [ -x "${'$'}wrapper_path" ] || return 1
              [ "${'$'}(sed -n '1p' "${'$'}wrapper_path" 2>/dev/null)" = '#!/usr/bin/env sh' ] || return 1
              wrapper_lines="${'$'}(awk 'END { print NR }' "${'$'}wrapper_path" 2>/dev/null)"
              case "${'$'}wrapper_lines" in
                2)
                  exec_line="${'$'}(sed -n '2p' "${'$'}wrapper_path" 2>/dev/null)"
                  ;;
                3)
                  env_line="${'$'}(sed -n '2p' "${'$'}wrapper_path" 2>/dev/null)"
                  case "${'$'}env_line" in
                    'export LD_LIBRARY_PATH="'*'${'$'}{LD_LIBRARY_PATH:+:${'$'}LD_LIBRARY_PATH}"') ;;
                    *) return 1 ;;
                  esac
                  exec_line="${'$'}(sed -n '3p' "${'$'}wrapper_path" 2>/dev/null)"
                  ;;
                *)
                  return 1
                  ;;
              esac
              case "${'$'}exec_line" in
                'exec "'*'" "${'$'}@"') exec_payload="${'$'}{exec_line#exec }" ;;
                'exec node "'*'" "${'$'}@"') exec_payload="${'$'}{exec_line#exec node }" ;;
                *) return 1 ;;
              esac
              wrapper_target="${'$'}{exec_payload#\"}"
              wrapper_target="${'$'}{wrapper_target%%\"*}"
              case "${'$'}wrapper_target" in
                "${'$'}install_root"/*)
                  [ -e "${'$'}wrapper_target" ] || return 1
                  printf '%s\n' "${'$'}wrapper_target"
                  ;;
                *)
                  return 1
                  ;;
              esac
            }
            remove_ledger_links() {
              ledger_path="${'$'}1"
              [ -f "${'$'}ledger_path" ] || return 0
              while IFS='	' read -r command_name target_path; do
                [ -n "${'$'}command_name" ] || continue
                link_path="$WORKSPACE_BIN_ROOT/${'$'}command_name"
                current_target="${'$'}(readlink "${'$'}link_path" 2>/dev/null || true)"
                if [ "${'$'}current_target" = "${'$'}target_path" ]; then
                  rm -f "${'$'}link_path"
                fi
              done < "${'$'}ledger_path"
            }
            restore_ledger_links() {
              ledger_path="${'$'}1"
              [ -f "${'$'}ledger_path" ] || return 0
              while IFS='	' read -r command_name target_path; do
                [ -n "${'$'}command_name" ] || continue
                link_path="$WORKSPACE_BIN_ROOT/${'$'}command_name"
                if [ -e "${'$'}link_path" ] || [ -L "${'$'}link_path" ]; then
                  continue
                fi
                if [ -x "${'$'}target_path" ] || [ -L "${'$'}target_path" ]; then
                  ln -s "${'$'}target_path" "${'$'}link_path"
                fi
              done < "${'$'}ledger_path"
            }
            write_transaction_state() {
              transaction_phase="${'$'}1"
              {
                printf 'schema=1\n'
                printf 'phase=%s\n' "${'$'}transaction_phase"
                printf 'resource_id=%s\n' "${'$'}transaction_resource_id"
                printf 'operation=%s\n' "${'$'}transaction_operation"
                printf 'target_version=%s\n' "${'$'}transaction_target_version"
              } > "${'$'}transaction_state_tmp"
              mv -f "${'$'}transaction_state_tmp" "${'$'}transaction_state"
            }
            recover_interrupted_install() {
              [ "${'$'}transactional_clean" = "1" ] || return 0
              if [ -e "${'$'}backup_root" ] || [ -L "${'$'}backup_root" ]; then
                recovered_phase="${'$'}(sed -n 's/^phase=//p' "${'$'}transaction_state" 2>/dev/null | sed -n '1p')"
                if [ "${'$'}recovered_phase" = "committed" ] &&
                   { [ -e "${'$'}install_root" ] || [ -L "${'$'}install_root" ]; }; then
                  echo "KITE_RESOURCE_STEP finish-committed-install $transactionResourceId"
                  rm -rf "${'$'}backup_root"
                else
                  echo "KITE_RESOURCE_STEP recover-interrupted-install $transactionResourceId"
                  remove_ledger_links "${'$'}command_ledger"
                  rm -rf "${'$'}install_root"
                  mv "${'$'}backup_root" "${'$'}install_root"
                  restore_ledger_links "${'$'}command_ledger"
                fi
              fi
              rm -f "${'$'}transaction_state" "${'$'}transaction_state_tmp"
            }
            restore_preserved_source() {
              preserved_source="${'$'}1"
              preserved_target="${'$'}2"
              rm -rf "${'$'}preserved_target"
              preserved_target_parent="${'$'}(dirname "${'$'}preserved_target")"
              mkdir -p "${'$'}preserved_target_parent"
              cp -a --no-preserve=timestamps "${'$'}preserved_source" "${'$'}preserved_target_parent/"
            }
            preserved_source_has_content() {
              preserved_source="${'$'}1"
              if [ -L "${'$'}preserved_source" ] || [ -f "${'$'}preserved_source" ]; then
                return 0
              fi
              [ -d "${'$'}preserved_source" ] || return 1
              find "${'$'}preserved_source" -mindepth 1 -maxdepth 1 -print -quit | grep -q .
            }
            clear_preserved_source() {
              preserved_source="${'$'}1"
              if [ -d "${'$'}preserved_source" ] && [ ! -L "${'$'}preserved_source" ]; then
                find "${'$'}preserved_source" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
                rmdir "${'$'}preserved_source" 2>/dev/null || true
              else
                rm -rf "${'$'}preserved_source"
              fi
            }
            cleanup_obsolete_command_links() {
              [ -f "${'$'}old_ledger_snapshot" ] || return 0
              while IFS='	' read -r command_name target_path; do
                [ -n "${'$'}command_name" ] || continue
                if ! cut -f1 "${'$'}command_ledger" 2>/dev/null | grep -Fxq "${'$'}command_name"; then
                  link_path="$WORKSPACE_BIN_ROOT/${'$'}command_name"
                  current_target="${'$'}(readlink "${'$'}link_path" 2>/dev/null || true)"
                  if [ "${'$'}current_target" = "${'$'}target_path" ]; then
                    rm -f "${'$'}link_path"
                  fi
                fi
              done < "${'$'}old_ledger_snapshot"
            }
            release_update_lock() {
              if [ "${'$'}update_lock_owned" = "1" ]; then
                rm -f "${'$'}update_lock/owner" 2>/dev/null || true
                rmdir "${'$'}update_lock" 2>/dev/null || true
                update_lock_owned=0
              fi
            }
            acquire_update_lock() {
              [ "${'$'}transactional_clean" = "1" ] || return 0
              if mkdir "${'$'}update_lock" 2>/dev/null; then
                update_lock_owned=1
              else
                if [ -L "${'$'}update_lock" ] || [ ! -d "${'$'}update_lock" ]; then
                  echo "KITE_RESOURCE_FAILURE stage=prepare step=update-lock reason=unsafe-lock-path"
                  return 75
                fi
                lock_pid="${'$'}(sed -n '1p' "${'$'}update_lock/owner" 2>/dev/null || true)"
                lock_start="${'$'}(sed -n '2p' "${'$'}update_lock/owner" 2>/dev/null || true)"
                live_start=
                case "${'$'}lock_pid" in
                  ''|*[!0-9]*) ;;
                  *) live_start="${'$'}(awk '{ print ${'$'}22 }' "/proc/${'$'}lock_pid/stat" 2>/dev/null || true)" ;;
                esac
                if [ -n "${'$'}lock_start" ] && [ "${'$'}live_start" = "${'$'}lock_start" ]; then
                  echo "KITE_RESOURCE_FAILURE stage=prepare step=update-lock reason=resource-busy"
                  return 75
                fi
                rm -rf "${'$'}update_lock"
                mkdir "${'$'}update_lock" || return 75
                update_lock_owned=1
              fi
              current_start="${'$'}(awk '{ print ${'$'}22 }' "/proc/${'$'}${'$'}/stat" 2>/dev/null || true)"
              printf '%s\n%s\n' "${'$'}${'$'}" "${'$'}current_start" > "${'$'}update_lock/owner"
            }
            rollback_install_transaction() {
              transaction_status=${'$'}?
              trap - EXIT
              if [ "${'$'}transactional_clean" = "1" ] &&
                 [ "${'$'}transaction_committed" != "1" ]; then
                echo "KITE_RESOURCE_STEP rollback-install ${safeId(resourceId)}"
                if [ -f "${'$'}command_ledger" ]; then
                  cp "${'$'}command_ledger" "${'$'}failed_ledger_snapshot" 2>/dev/null || true
                fi
                remove_ledger_links "${'$'}failed_ledger_snapshot"
                rm -rf "${'$'}install_root"
                if [ -e "${'$'}backup_root" ] || [ -L "${'$'}backup_root" ]; then
                  mv "${'$'}backup_root" "${'$'}install_root"
                fi
                restore_ledger_links "${'$'}old_ledger_snapshot"
                rm -f "${'$'}transaction_state" "${'$'}transaction_state_tmp"
              fi
              rm -f "${'$'}old_ledger_snapshot" "${'$'}failed_ledger_snapshot"
              release_update_lock
              exit "${'$'}transaction_status"
            }
            is_explicit_command() {
              for explicit_command in ${'$'}explicit_commands; do
                [ "${'$'}explicit_command" = "${'$'}1" ] && return 0
              done
              return 1
            }
            is_safe_explicit_command_target() {
              target_path="${'$'}1"
              target_name="${'$'}2"
              [ -n "${'$'}target_path" ] || return 1
              [ -x "${'$'}target_path" ] || return 1
              case "${'$'}target_path" in
                "${'$'}install_root"/*|\
                "${'$'}HOME"/*|\
                "/root/.local/bin/${'$'}target_name"|\
                "/root/.kimi-code/bin/${'$'}target_name"|\
                "/root/.codex/bin/${'$'}target_name"|\
                "/root/.claude/local/${'$'}target_name"|\
                "/root/.opencode/bin/${'$'}target_name"|\
                "/usr/local/bin/${'$'}target_name"|\
                "/usr/bin/${'$'}target_name"|\
                "/bin/${'$'}target_name")
                  return 0
                  ;;
              esac
              return 1
            }
            is_public_command_name() {
              case "${'$'}1" in
                ""|.*|activate|activate.*|deactivate*|python|python[0-9]*|pip|pip[0-9]*|node|npm|npx|corepack|uv|uvx)
                  return 1
                  ;;
              esac
              return 0
            }
            kite_clean_install_root() {
              clean_target="${'$'}1"
              if [ "${'$'}candidate_install" = "1" ] && [ "${'$'}clean_target" = "${'$'}install_root" ]; then
                find "${'$'}install_root" -mindepth 1 -maxdepth 1 \
                  ! -name '.kite-candidate-input' -exec rm -rf -- {} +
              else
                rm -rf "${'$'}clean_target"
              fi
            }
            snapshot_public_commands() {
              for command_root in ${'$'}public_command_roots; do
                [ -d "${'$'}command_root" ] || continue
                for command_path in "${'$'}command_root"/*; do
                  [ -e "${'$'}command_path" ] || [ -L "${'$'}command_path" ] || continue
                  [ -x "${'$'}command_path" ] || [ -L "${'$'}command_path" ] || continue
                  command_name="${'$'}{command_path##*/}"
                  is_public_command_name "${'$'}command_name" || continue
                  printf '%s\t%s\n' "${'$'}command_name" "${'$'}command_path"
                done
              done | sort -u
            }
            echo "KITE_RESOURCE_STEP prepare-install-root ${'$'}install_root"
            acquire_update_lock || exit ${'$'}?
            recover_interrupted_install
            if [ "${'$'}candidate_install" = "1" ]; then
              rm -rf "${'$'}candidate_input"
              mkdir -p "${'$'}candidate_input"
              old_ledger_snapshot="${'$'}candidate_input/old-commands"
              failed_ledger_snapshot="${'$'}candidate_input/failed-commands"
              transaction_state="${'$'}candidate_input/transaction-state"
              transaction_state_tmp="${'$'}transaction_state.tmp.${'$'}${'$'}"
              if [ -f "${'$'}command_ledger" ]; then
                cp "${'$'}command_ledger" "${'$'}old_ledger_snapshot"
              fi
              $stageCandidatePreservedPaths
            elif [ -f "${'$'}command_ledger" ]; then
              cp "${'$'}command_ledger" "${'$'}old_ledger_snapshot"
            fi
            if [ "${'$'}transactional_clean" = "1" ]; then
              rm -rf "${'$'}backup_root"
              write_transaction_state active
              if [ -e "${'$'}install_root" ] || [ -L "${'$'}install_root" ]; then
                mv "${'$'}install_root" "${'$'}backup_root"
              fi
              trap rollback_install_transaction EXIT
            elif [ "${'$'}clean_install_root" = "1" ]; then
              if [ "${'$'}candidate_install" = "1" ]; then
                find "${'$'}install_root" -mindepth 1 -maxdepth 1 \
                  ! -name '.kite-candidate-input' -exec rm -rf -- {} +
              else
                rm -rf "${'$'}install_root"
              fi
            fi
            for required_dir in "${'$'}install_root" "${'$'}install_root/bin" "${'$'}npm_prefix/bin" "${'$'}user_home" "$WORKSPACE_BIN_ROOT"; do
              [ -d "${'$'}required_dir" ] || mkdir -p "${'$'}required_dir"
            done
            kite_build_apt_proxy_conf="/etc/apt/apt.conf.d/99kite-proxy"
            if [ -f "${'$'}kite_build_apt_proxy_conf" ]; then
              echo "KITE_RESOURCE_STEP clear-build-apt-proxy ${'$'}kite_build_apt_proxy_conf"
              rm -f "${'$'}kite_build_apt_proxy_conf" || true
            fi
            echo "KITE_RESOURCE_STEP manifest-install ${safeId(resourceId)}"
            set +e
            (
              set -e
              $rawCommand
            )
            manifest_install_status=${'$'}?
            set -e
            if [ "${'$'}manifest_install_status" -ne 0 ]; then
              echo "KITE_RESOURCE_STEP manifest-install-failed ${safeId(resourceId)} exit=${'$'}manifest_install_status"
              rm -f "${'$'}command_snapshot_after"
              echo "KITE_RESOURCE_FAILURE stage=install step=${safeId(resourceId)} exit=${'$'}manifest_install_status"
              exit "${'$'}manifest_install_status"
            fi
            snapshot_public_commands > "${'$'}command_snapshot_after"
            auto_commands="${'$'}(cut -f1 "${'$'}command_snapshot_after" || true)"
            managed_commands="${'$'}(
              printf '%s\n%s\n' "${'$'}explicit_commands" "${'$'}auto_commands" |
                tr ' ' '\n' |
                sed '/^${'$'}/d' |
                sort -u |
                tr '\n' ' '
            )"
            : > "${'$'}command_ledger"
            if [ -n "${'$'}managed_commands" ]; then
              for command_name in ${'$'}managed_commands; do
                [ -n "${'$'}command_name" ] || continue
                link_path="$WORKSPACE_BIN_ROOT/${'$'}command_name"
                existing_target="${'$'}(readlink "${'$'}link_path" 2>/dev/null || true)"
                legacy_wrapper_target="${'$'}(legacy_kite_wrapper_target "${'$'}link_path" 2>/dev/null || true)"
                previous_owned_target="${'$'}(
                  awk -F '\t' -v command="${'$'}command_name" '$1 == command { print $2; exit }' \
                    "${'$'}old_ledger_snapshot" 2>/dev/null || true
                )"
                linked_command=
                if [ -e "${'$'}link_path" ] || [ -L "${'$'}link_path" ]; then
                  if [ -n "${'$'}previous_owned_target" ] && [ "${'$'}existing_target" = "${'$'}previous_owned_target" ]; then
                    echo "KITE_RESOURCE_STEP replace-owned-command ${'$'}command_name"
                    rm -f "${'$'}link_path"
                  elif [ -n "${'$'}existing_target" ] && is_safe_explicit_command_target "${'$'}existing_target" "${'$'}command_name"; then
                    echo "KITE_RESOURCE_STEP command-present ${'$'}command_name"
                    printf '%s\t%s\n' "${'$'}command_name" "${'$'}existing_target" >> "${'$'}command_ledger"
                    linked_command=1
                  elif [ -n "${'$'}legacy_wrapper_target" ]; then
                    echo "KITE_RESOURCE_STEP adopt-legacy-command ${'$'}command_name"
                    printf '%s\t%s\n' "${'$'}command_name" "${'$'}legacy_wrapper_target" >> "${'$'}command_ledger"
                    linked_command=1
                  else
                    echo "KITE_RESOURCE_STEP command-conflict ${'$'}command_name${'$'}{existing_target:+ -> ${'$'}existing_target}"
                    if is_explicit_command "${'$'}command_name"; then
                      exit 127
                    fi
                    continue
                  fi
                fi
                if [ -z "${'$'}linked_command" ]; then
                  for candidate in \
                    "$WORKSPACE_BIN_ROOT/${'$'}command_name" \
                    "${'$'}install_root/bin/${'$'}command_name" \
                    "${'$'}npm_prefix/bin/${'$'}command_name" \
                    "${'$'}HOME/.local/bin/${'$'}command_name" \
                    "${'$'}HOME/.kimi-code/bin/${'$'}command_name" \
                    "${'$'}HOME/.codex/bin/${'$'}command_name" \
                    "${'$'}HOME/.claude/local/${'$'}command_name" \
                    "${'$'}HOME/.opencode/bin/${'$'}command_name" \
                    "/root/.local/bin/${'$'}command_name" \
                    "/root/.kimi-code/bin/${'$'}command_name" \
                    "/root/.codex/bin/${'$'}command_name" \
                    "/root/.claude/local/${'$'}command_name" \
                    "/root/.opencode/bin/${'$'}command_name" \
                    "/usr/local/bin/${'$'}command_name" \
                    "/usr/bin/${'$'}command_name" \
                    "/bin/${'$'}command_name"; do
                    if [ -x "${'$'}candidate" ]; then
                      echo "KITE_RESOURCE_STEP link-command ${'$'}command_name"
                      ln -sfn "${'$'}candidate" "$WORKSPACE_BIN_ROOT/${'$'}command_name"
                      printf '%s\t%s\n' "${'$'}command_name" "${'$'}candidate" >> "${'$'}command_ledger"
                      linked_command=1
                      break
                    fi
                  done
                fi
                if [ -z "${'$'}linked_command" ]; then
                  if is_explicit_command "${'$'}command_name"; then
                    echo "KITE_RESOURCE_FAILURE stage=verify step=command-link command=${'$'}command_name reason=not-found"
                    exit 127
                  fi
                  continue
                fi
                hash -r 2>/dev/null || true
                if ! command -v "${'$'}command_name" >/dev/null 2>&1; then
                  echo "KITE_RESOURCE_FAILURE stage=verify step=command-path command=${'$'}command_name reason=not-on-path"
                  exit 127
                fi
              done
            fi
            rm -f "${'$'}command_snapshot_after"
            $restorePreservedPaths
            $verificationCommand
            $versionEvidence
            cleanup_obsolete_command_links
            echo "KITE_RESOURCE_STEP commit-install ${safeId(resourceId)}"
            $ownershipCommit
            $clearRetainedPaths
            if [ "${'$'}transactional_clean" = "1" ]; then
              write_transaction_state committed
            fi
            transaction_committed=1
            if rm -rf "${'$'}backup_root" 2>/dev/null; then
              rm -f "${'$'}transaction_state" "${'$'}transaction_state_tmp"
            fi
            rm -f "${'$'}old_ledger_snapshot" "${'$'}failed_ledger_snapshot"
            if [ "${'$'}candidate_install" = "1" ]; then
              rm -rf "${'$'}candidate_input"
            fi
            release_update_lock
            trap - EXIT
            echo "$displayName installed by manifest action"
        """.trimIndent()
    }

    private fun versionEvidenceCommand(versionProbeCommand: String, expectedVersion: String?): String {
        if (versionProbeCommand.isBlank()) return ":"
        val safeExpected = expectedVersion
            ?.trim()
            ?.takeIf { RESOURCE_VERSION_TOKEN.matches(it) }
            .orEmpty()
        val targetCheck = if (safeExpected.isBlank()) {
            ":"
        } else {
            """
                expected_version=${shellLiteral(safeExpected)}
                expected_version="${'$'}{expected_version#v}"
                if ! printf '%s\n' "${'$'}installed_version_output" |
                    tr '[:space:]' '\n' |
                    sed 's/^v//' |
                    grep -Fxq "${'$'}expected_version"; then
                  echo "KITE_RESOURCE_FAILURE stage=verify step=target-version reason=version-mismatch"
                  exit 65
                fi
            """.trimIndent()
        }
        return """
            echo "KITE_RESOURCE_STEP verify installed-version-evidence"
            installed_version_output="${'$'}(
              $versionProbeCommand
            )"
            [ -n "${'$'}installed_version_output" ] || {
              echo "KITE_RESOURCE_FAILURE stage=verify step=installed-version-evidence reason=empty-version"
              exit 65
            }
            $targetCheck
            installed_version_single_line="${'$'}(printf '%s' "${'$'}installed_version_output" | tr '\r\n\t' '   ' | sed 's/[[:space:]][[:space:]]*/ /g')"
            echo "KITE_RESOURCE_INSTALLED_VERSION ${'$'}installed_version_single_line"
        """.trimIndent()
    }

    private fun restorePreservedPathsCommand(paths: List<String>): String {
        val safePaths = normalizedPreservePaths(paths)
        if (safePaths.isEmpty()) return ":"
        return safePaths.joinToString("\n") { relativePath ->
            val literalPath = shellLiteral(relativePath)
            """
                preserved_relative=$literalPath
                preserved_retained="${'$'}retained_root/${'$'}preserved_relative"
                preserved_backup="${'$'}backup_root/${'$'}preserved_relative"
                preserved_candidate="${'$'}candidate_input/preserved/${'$'}preserved_relative"
                preserved_new="${'$'}install_root/${'$'}preserved_relative"
                if [ "${'$'}candidate_install" = "1" ] && preserved_source_has_content "${'$'}preserved_candidate"; then
                  echo "KITE_RESOURCE_STEP restore-candidate-path ${'$'}preserved_relative"
                  restore_preserved_source "${'$'}preserved_candidate" "${'$'}preserved_new"
                fi
                if preserved_source_has_content "${'$'}preserved_retained"; then
                  echo "KITE_RESOURCE_STEP restore-retained-path ${'$'}preserved_relative"
                  restore_preserved_source "${'$'}preserved_retained" "${'$'}preserved_new"
                fi
                if [ "${'$'}transactional_clean" = "1" ] &&
                   { [ -e "${'$'}preserved_backup" ] || [ -L "${'$'}preserved_backup" ]; }; then
                  echo "KITE_RESOURCE_STEP restore-preserved-path ${'$'}preserved_relative"
                  restore_preserved_source "${'$'}preserved_backup" "${'$'}preserved_new"
                fi
            """.trimIndent()
        }
    }

    private fun stageCandidatePreservedPathsCommand(paths: List<String>): String {
        val safePaths = normalizedPreservePaths(paths)
        if (safePaths.isEmpty()) return ":"
        return safePaths.joinToString("\n") { relativePath ->
            val literalPath = shellLiteral(relativePath)
            """
                candidate_relative=$literalPath
                candidate_source="${'$'}install_root/${'$'}candidate_relative"
                candidate_preserved="${'$'}candidate_input/preserved/${'$'}candidate_relative"
                if preserved_source_has_content "${'$'}candidate_source"; then
                  echo "KITE_RESOURCE_STEP stage-candidate-path ${'$'}candidate_relative"
                  candidate_parent="${'$'}(dirname "${'$'}candidate_preserved")"
                  mkdir -p "${'$'}candidate_parent"
                  cp -a --no-preserve=timestamps "${'$'}candidate_source" "${'$'}candidate_parent/"
                fi
            """.trimIndent()
        }
    }

    private fun clearRetainedPathsCommand(paths: List<String>): String {
        val safePaths = normalizedPreservePaths(paths)
        if (safePaths.isEmpty()) return ":"
        return buildString {
            safePaths.forEach { relativePath ->
                append("clear_preserved_source \"${'$'}retained_root/")
                append(relativePath)
                append("\"\n")
            }
            append("rmdir \"${'$'}retained_root\" 2>/dev/null || true")
        }
    }

    private fun retainPathsForUninstallCommand(paths: List<String>): String {
        val safePaths = normalizedPreservePaths(paths)
        if (safePaths.isEmpty()) return ":"
        return safePaths.joinToString("\n") { relativePath ->
            val literalPath = shellLiteral(relativePath)
            """
                preserved_relative=$literalPath
                preserved_live="${'$'}install_root/${'$'}preserved_relative"
                preserved_retained="${'$'}retained_root/${'$'}preserved_relative"
                if [ -e "${'$'}preserved_live" ] || [ -L "${'$'}preserved_live" ]; then
                  echo "KITE_RESOURCE_STEP retain-user-path ${'$'}preserved_relative"
                  rm -rf "${'$'}preserved_retained" || exit 74
                  preserved_retained_parent="${'$'}(dirname "${'$'}preserved_retained")"
                  mkdir -p "${'$'}preserved_retained_parent" || exit 74
                  cp -a --no-preserve=timestamps "${'$'}preserved_live" "${'$'}preserved_retained_parent/" || exit 74
                fi
            """.trimIndent()
        }
    }

    fun manifestUninstallCommand(
        resourceId: String,
        rawCommand: String,
        managedCommands: List<String>,
        npmUninstallPackages: List<String>,
        preservePaths: List<String> = emptyList()
    ): String {
        val safeCommands = managedCommands.map(::safeCommandName).filter { it.isNotBlank() }.distinct()
        val commandList = safeCommands.joinToString(" ")
        val packageList = npmUninstallPackages.map(::safeNpmPackage).filter { it.isNotBlank() }.distinct().joinToString(" ")
        val retainUserPaths = retainPathsForUninstallCommand(preservePaths)
        return """
            set +e
            install_root="${softwarePath(resourceId)}"
            retained_root="${preservedResourcePath(resourceId)}"
            uninstall_staging="${'$'}install_root.kite-uninstall-staging-${'$'}${'$'}"
            user_home="${'$'}install_root/user-home"
            npm_prefix="${'$'}install_root/npm-global"
            export HOME="${'$'}user_home"
            export npm_config_prefix="${'$'}npm_prefix"
            export PATH="$WORKSPACE_BIN_ROOT:${'$'}install_root/bin:${'$'}npm_prefix/bin:${'$'}HOME/.local/bin:${'$'}HOME/.kimi-code/bin:${'$'}HOME/.codex/bin:${'$'}HOME/.claude/local:${'$'}HOME/.opencode/bin:/root/.local/bin:${'$'}PATH"
            legacy_kite_wrapper_target() {
              wrapper_path="${'$'}1"
              [ -f "${'$'}wrapper_path" ] || return 1
              [ ! -L "${'$'}wrapper_path" ] || return 1
              [ -x "${'$'}wrapper_path" ] || return 1
              [ "${'$'}(sed -n '1p' "${'$'}wrapper_path" 2>/dev/null)" = '#!/usr/bin/env sh' ] || return 1
              wrapper_lines="${'$'}(awk 'END { print NR }' "${'$'}wrapper_path" 2>/dev/null)"
              case "${'$'}wrapper_lines" in
                2)
                  exec_line="${'$'}(sed -n '2p' "${'$'}wrapper_path" 2>/dev/null)"
                  ;;
                3)
                  env_line="${'$'}(sed -n '2p' "${'$'}wrapper_path" 2>/dev/null)"
                  case "${'$'}env_line" in
                    'export LD_LIBRARY_PATH="'*'${'$'}{LD_LIBRARY_PATH:+:${'$'}LD_LIBRARY_PATH}"') ;;
                    *) return 1 ;;
                  esac
                  exec_line="${'$'}(sed -n '3p' "${'$'}wrapper_path" 2>/dev/null)"
                  ;;
                *)
                  return 1
                  ;;
              esac
              case "${'$'}exec_line" in
                'exec "'*'" "${'$'}@"') exec_payload="${'$'}{exec_line#exec }" ;;
                'exec node "'*'" "${'$'}@"') exec_payload="${'$'}{exec_line#exec node }" ;;
                *) return 1 ;;
              esac
              wrapper_target="${'$'}{exec_payload#\"}"
              wrapper_target="${'$'}{wrapper_target%%\"*}"
              printf '%s\n' "${'$'}wrapper_target"
            }
            echo "KITE_RESOURCE_STEP manifest-uninstall ${safeId(resourceId)}"
            $rawCommand
            if command -v npm >/dev/null 2>&1; then
              npm_uninstall_packages="$packageList"
              if [ -n "${'$'}npm_uninstall_packages" ]; then
                for package_name in ${'$'}npm_uninstall_packages; do
                  [ -n "${'$'}package_name" ] || continue
                  echo "KITE_RESOURCE_STEP npm-uninstall ${'$'}package_name"
                  npm uninstall -g "${'$'}package_name" >/dev/null 2>&1 || true
                done
              fi
            fi
            managed_commands="$commandList"
            command_ledger="${'$'}install_root/.kite-managed-commands"
            if [ -f "${'$'}command_ledger" ]; then
              while IFS='	' read -r command_name target_path; do
                [ -n "${'$'}command_name" ] || continue
                link_path="$WORKSPACE_BIN_ROOT/${'$'}command_name"
                current_target="${'$'}(readlink "${'$'}link_path" 2>/dev/null || true)"
                if [ "${'$'}current_target" = "${'$'}target_path" ]; then
                  rm -f "${'$'}link_path"
                  continue
                fi
                wrapper_target="${'$'}(legacy_kite_wrapper_target "${'$'}link_path" 2>/dev/null || true)"
                if [ -n "${'$'}wrapper_target" ] && [ "${'$'}wrapper_target" = "${'$'}target_path" ]; then
                  rm -f "${'$'}link_path"
                fi
              done < "${'$'}command_ledger"
            fi
            if [ -n "${'$'}managed_commands" ]; then
              for command_name in ${'$'}managed_commands; do
                [ -n "${'$'}command_name" ] || continue
                link_path="$WORKSPACE_BIN_ROOT/${'$'}command_name"
                current_target="${'$'}(readlink "${'$'}link_path" 2>/dev/null || true)"
                case "${'$'}current_target" in
                  "${'$'}install_root"/*)
                    rm -f "${'$'}link_path"
                    ;;
                esac
              done
            fi
            $retainUserPaths
            if [ -e "${'$'}install_root" ] || [ -L "${'$'}install_root" ]; then
              rm -rf "${'$'}uninstall_staging"
              mv "${'$'}install_root" "${'$'}uninstall_staging" || exit 74
            fi
            if [ -e "${'$'}install_root" ] || [ -L "${'$'}install_root" ]; then
              echo "KITE_RESOURCE_FAILURE stage=uninstall step=remove-install-root reason=not-removed"
              exit 74
            fi
            exit 0
        """.trimIndent()
    }

    fun hermesCoreInstallCommand(): String =
        """
            set -e
            export PATH="$WORKSPACE_BIN_ROOT:/root/.local/bin:${'$'}PATH"
            export UV_LINK_MODE="copy"
            export UV_NO_CONFIG=1
            resource_root="${softwarePath("kite.hermes.core")}"
            repo_dir="${'$'}resource_root/hermes-agent"
            home_dir="${'$'}resource_root/home"
            user_home="${'$'}resource_root/user-home"
            installer="${'$'}resource_root/install.sh"
            bin_dir="$WORKSPACE_BIN_ROOT"
            if ! command -v git >/dev/null 2>&1; then
              echo "缺少 git：请先安装 Git 资源。"
              exit 127
            fi
            if ! command -v curl >/dev/null 2>&1; then
              echo "缺少 curl：当前 Ubuntu 环境无法下载 Hermes 官方安装脚本。"
              exit 127
            fi
            echo "KITE_RESOURCE_STEP clean-install-root ${'$'}resource_root"
            kite_clean_install_root "${'$'}resource_root"
            echo "KITE_RESOURCE_STEP prepare-install-root ${'$'}resource_root"
            mkdir -p "${'$'}resource_root" "${'$'}home_dir" "${'$'}user_home" "${'$'}bin_dir"
            export HOME="${'$'}user_home"
            export HERMES_HOME="${'$'}home_dir"
            export HERMES_INSTALL_DIR="${'$'}repo_dir"
            export UV_PYTHON_INSTALL_DIR="${'$'}resource_root/uv-python"
            export UV_PYTHON_BIN_DIR="${'$'}resource_root/uv-bin"
            export UV_CACHE_DIR="${'$'}resource_root/uv-cache"
            echo "KITE_RESOURCE_STEP download-official-installer"
            curl -fsSL https://hermes-agent.nousresearch.com/install.sh -o "${'$'}installer"
            chmod +x "${'$'}installer"
            echo "KITE_RESOURCE_STEP run-official-installer"
            bash "${'$'}installer" \
              --dir "${'$'}repo_dir" \
              --hermes-home "${'$'}home_dir" \
              --skip-setup \
              --skip-browser \
              --non-interactive
            agent_python="${'$'}repo_dir/venv/bin/python"
            if ! "${'$'}agent_python" -m pip --version >/dev/null 2>&1; then
              echo "KITE_RESOURCE_STEP bootstrap-hermes-core-pip"
              if ! python3 -m venv --help >/dev/null 2>&1 || ! python3 -m pip --version >/dev/null 2>&1; then
                echo "缺少 pip/venv：Kite 离线 rootfs 打包不完整。"
                exit 127
              fi
              if ! "${'$'}agent_python" -m ensurepip --upgrade; then
                python3 -m pip --python "${'$'}agent_python" install --upgrade pip
              fi
            fi
            "${'$'}agent_python" -m pip --version
            echo "KITE_RESOURCE_STEP link-command ${'$'}bin_dir/hermes"
            rm -f "${'$'}bin_dir/hermes"
            {
              echo '#!/usr/bin/env bash'
              echo "export HERMES_HOME=\"${'$'}home_dir\""
              echo "export PATH=\"${'$'}bin_dir:/root/.local/bin:\${'$'}PATH\""
              echo 'unset PYTHONPATH'
              echo 'unset PYTHONHOME'
              echo "exec \"${'$'}repo_dir/venv/bin/hermes\" \"\${'$'}@\""
            } > "${'$'}bin_dir/hermes"
            chmod +x "${'$'}bin_dir/hermes"
            echo "KITE_RESOURCE_STEP validate-hermes-command"
            "${'$'}bin_dir/hermes" --help >/dev/null
            printf '%s\n' 'installed_by_kite' > "${'$'}resource_root/ownership"
            echo "Hermes Core installed at ${'$'}repo_dir"
        """.trimIndent()

    fun gitInstallCommand(): String =
        """
            set -e
            export PATH="$WORKSPACE_BIN_ROOT:/root/.local/bin:${'$'}PATH"
            install_root="${softwarePath("kite.git")}"
            mkdir -p "${'$'}install_root"
            if command -v git >/dev/null 2>&1; then
              echo "KITE_RESOURCE_STEP git-present ${'$'}(command -v git)"
              echo "preexisting" > "${'$'}install_root/ownership"
            else
              echo "缺少 git：Kite 离线 rootfs 打包不完整。"
              exit 127
            fi
            git --version
        """.trimIndent()

    fun gitUninstallCommand(): String =
        """
            set +e
            install_root="${softwarePath("kite.git")}"
            ownership="${'$'}install_root/ownership"
            echo "Git belongs to Kite offline rootfs; clearing Kite resource record only"
            rm -rf "${'$'}install_root"
            exit 0
        """.trimIndent()

    fun curlInstallCommand(): String =
        """
            set -e
            install_root="${softwarePath("kite.curl")}"
            mkdir -p "${'$'}install_root"
            if command -v curl >/dev/null 2>&1; then
              echo "KITE_RESOURCE_STEP curl-present ${'$'}(command -v curl)"
              echo "preexisting" > "${'$'}install_root/ownership"
            else
              echo "缺少 curl：Kite 离线 rootfs 打包不完整。"
              exit 127
            fi
            curl --version | head -1
        """.trimIndent()

    fun curlUninstallCommand(): String =
        """
            set +e
            install_root="${softwarePath("kite.curl")}"
            ownership="${'$'}install_root/ownership"
            echo "curl belongs to Kite offline rootfs; clearing Kite resource record only"
            rm -rf "${'$'}install_root"
            exit 0
        """.trimIndent()

    fun pythonInstallCommand(): String =
        """
            set -e
            install_root="${softwarePath("kite.python")}"
            mkdir -p "${'$'}install_root"
            python_ready=0
            if command -v python3 >/dev/null 2>&1 && python3 - <<'PY'
import sys
raise SystemExit(0 if (3, 14) <= sys.version_info < (3, 15) else 1)
PY
            then
              echo "KITE_RESOURCE_STEP python-present ${'$'}(python3 --version 2>&1)"
              if python3 -m venv --help >/dev/null 2>&1 && python3 -m pip --version >/dev/null 2>&1; then
                python_ready=1
              else
                echo "KITE_RESOURCE_STEP python-substrate-missing pip-or-venv"
              fi
            fi
            if [ "${'$'}python_ready" -ne 1 ]; then
              echo "缺少 Python 3.14 / pip / venv：Kite 离线 rootfs 打包不完整。"
              exit 127
            fi
            python3 - <<'PY'
import sys
if not ((3, 14) <= sys.version_info < (3, 15)):
    raise SystemExit(f"Python {sys.version.split()[0]} is outside Kite range >=3.14,<3.15")
print("python_version=" + sys.version.split()[0])
PY
            python3 -m venv --help >/dev/null
            python3 -m pip --version
            echo "system_python" > "${'$'}install_root/ownership"
        """.trimIndent()

    fun pythonUninstallCommand(): String =
        """
            set +e
            echo "Python is a shared system substrate; clearing Kite resource record only"
            rm -rf ${softwarePath("kite.python")}
            exit 0
        """.trimIndent()

    fun uvUninstallCommand(): String =
        """
            set -e
            echo "KITE_RESOURCE_STEP remove-software ${softwarePath("kite.uv")}"
            rm -rf ${softwarePath("kite.uv")}
            echo "KITE_RESOURCE_STEP remove-bin uv uvx"
            rm -f $WORKSPACE_BIN_ROOT/uv $WORKSPACE_BIN_ROOT/uvx
            rm -rf ${resourceCachePath("kite.uv")}
            echo "uv resource removed"
        """.trimIndent()

    fun nodeUninstallCommand(): String =
        """
            set -e
            echo "KITE_RESOURCE_STEP remove-software ${softwarePath("kite.nodejs")}"
            rm -rf ${softwarePath("kite.nodejs")}
            rm -rf /workspace/.kf/components/kite.nodejs
            rm -rf /workspace/.kf/toolchains/node-v26.4.0
            echo "KITE_RESOURCE_STEP remove-bin node npm npx"
            rm -f $WORKSPACE_BIN_ROOT/node $WORKSPACE_BIN_ROOT/npm $WORKSPACE_BIN_ROOT/npx
            rm -rf ${resourceCachePath("kite.nodejs")}
            echo "Node.js resource removed"
        """.trimIndent()

    fun toolEnvUninstallCommand(): String =
        """
            set -e
            echo "KITE_RESOURCE_STEP remove-software ${softwarePath("kite.tool.env")}"
            rm -rf ${softwarePath("kite.tool.env")}
            rm -rf /workspace/.kf/components/kite.tool.env
            rm -rf /workspace/.kf/toolchains/pnpm-11.9.0
            echo "KITE_RESOURCE_STEP remove-bin tool-env"
            for command_name in $TOOL_ENV_BIN_COMMANDS; do
              rm -f "$WORKSPACE_BIN_ROOT/${'$'}command_name"
            done
            rm -rf ${resourceCachePath("kite.tool.env")}
            echo "KF tool environment resource removed"
        """.trimIndent()

    fun hermesCoreUninstallCommand(): String =
        """
            set +e
            resource_root="${softwarePath("kite.hermes.core")}"
            echo "KITE_RESOURCE_STEP remove-command $WORKSPACE_BIN_ROOT/hermes"
            rm -f $WORKSPACE_BIN_ROOT/hermes
            for user_launcher in "${'$'}resource_root/user-home/.local/bin/hermes" "/root/.local/bin/hermes"; do
              if [ -f "${'$'}user_launcher" ] && grep -q "${'$'}resource_root" "${'$'}user_launcher"; then
                echo "KITE_RESOURCE_STEP remove-official-launcher ${'$'}user_launcher"
                rm -f "${'$'}user_launcher"
              fi
            done
            echo "KITE_RESOURCE_STEP remove-software ${'$'}resource_root"
            rm -rf "${'$'}resource_root"
            rm -rf ${resourceCachePath("kite.hermes.core")}
            echo "Hermes Core resource removed"
            exit 0
        """.trimIndent()

    fun hermesWebUiUninstallCommand(): String =
        """
            set +e
            export PATH="$WORKSPACE_BIN_ROOT:/root/.local/bin:${'$'}PATH"
            resource_root="${softwarePath("kite.hermes.webui")}"
            repo_dir="${'$'}resource_root/hermes-webui"
            hermes_core_root="${softwarePath("kite.hermes.core")}"
            if [ -x "${'$'}repo_dir/ctl.sh" ]; then
              echo "KITE_RESOURCE_STEP stop-webui"
              HERMES_HOME="${'$'}hermes_core_root/home" \
              HERMES_WEBUI_AGENT_DIR="${'$'}hermes_core_root/hermes-agent" \
              HERMES_WEBUI_PYTHON="${'$'}hermes_core_root/hermes-agent/venv/bin/python" \
              HERMES_WEBUI_STATE_DIR="${'$'}resource_root/state" \
              HERMES_WEBUI_HOST="127.0.0.1" \
              HERMES_WEBUI_PORT="8787" \
              "${'$'}repo_dir/ctl.sh" stop || true
            fi
            echo "KITE_RESOURCE_STEP remove-command $WORKSPACE_BIN_ROOT/hermes-webui-kite"
            rm -f "$WORKSPACE_BIN_ROOT/hermes-webui-kite"
            echo "KITE_RESOURCE_STEP remove-software ${'$'}resource_root"
            rm -rf "${'$'}resource_root"
            exit 0
        """.trimIndent()

    fun cancelCleanupCommand(resourceIds: List<String>): String {
        val ids = resourceIds.map(::safeId).filter { it.isNotBlank() }.distinct()
        val quotedIds = ids.joinToString(" ") { "'$it'" }
        return """
            set +e
            export PATH="$WORKSPACE_BIN_ROOT:/root/.local/bin:${'$'}PATH"
            for resource_id in $quotedIds; do
              [ -n "${'$'}resource_id" ] || continue
              software="$WORKSPACE_SOFTWARE_ROOT/${'$'}resource_id"
              cache="$WORKSPACE_RESOURCE_ROOT/${'$'}resource_id"
              echo "KITE_RESOURCE_STEP cancel-clean ${'$'}resource_id"
              case "${'$'}resource_id" in
                kite.nodejs)
                  rm -f "$WORKSPACE_BIN_ROOT/node" "$WORKSPACE_BIN_ROOT/npm" "$WORKSPACE_BIN_ROOT/npx"
                  rm -rf /workspace/.kf/components/kite.nodejs /workspace/.kf/toolchains/node-v26.4.0
                  ;;
                kite.uv)
                  rm -f "$WORKSPACE_BIN_ROOT/uv" "$WORKSPACE_BIN_ROOT/uvx"
                  rm -rf /workspace/.kf/toolchains/uv-0.11.25
                  ;;
                kite.tool.env)
                  for command_name in $TOOL_ENV_BIN_COMMANDS; do
                    rm -f "$WORKSPACE_BIN_ROOT/${'$'}command_name"
                  done
                  rm -rf /workspace/.kf/components/kite.tool.env
                  rm -rf /workspace/.kf/toolchains/pnpm-11.9.0
                  ;;
                kite.hermes.core)
                  rm -f "$WORKSPACE_BIN_ROOT/hermes"
                  for user_launcher in "${'$'}software/user-home/.local/bin/hermes" "/root/.local/bin/hermes"; do
                    if [ -f "${'$'}user_launcher" ] && grep -q "${'$'}software" "${'$'}user_launcher"; then
                      rm -f "${'$'}user_launcher"
                    fi
                  done
                  ;;
                kite.hermes.webui)
                  if [ -x "${'$'}software/hermes-webui/ctl.sh" ]; then
                    HERMES_HOME="${softwarePath("kite.hermes.core")}/home" \
                    HERMES_WEBUI_AGENT_DIR="${softwarePath("kite.hermes.core")}/hermes-agent" \
                    HERMES_WEBUI_PYTHON="${softwarePath("kite.hermes.core")}/hermes-agent/venv/bin/python" \
                    HERMES_WEBUI_STATE_DIR="${'$'}software/state" \
                    HERMES_WEBUI_HOST="127.0.0.1" \
                    HERMES_WEBUI_PORT="8787" \
                    "${'$'}software/hermes-webui/ctl.sh" stop >/dev/null 2>&1 || true
                  fi
                  rm -f "$WORKSPACE_BIN_ROOT/hermes-webui-kite"
                  if command -v npm >/dev/null 2>&1; then
                    npm uninstall -g hermes-web-ui >/dev/null 2>&1 || true
                  fi
                  ;;
                kite.reasonix)
                  rm -f "$WORKSPACE_BIN_ROOT/reasonix" "$WORKSPACE_BIN_ROOT/dsnix"
                  if command -v npm >/dev/null 2>&1; then
                    npm uninstall -g reasonix >/dev/null 2>&1 || true
                  fi
                  ;;
                kite.mimo.code)
                  rm -f "$WORKSPACE_BIN_ROOT/mimo"
                  if command -v npm >/dev/null 2>&1; then
                    npm uninstall -g @mimo-ai/cli >/dev/null 2>&1 || true
                  fi
                  ;;
                kite.codex.cli)
                  rm -f "$WORKSPACE_BIN_ROOT/codex"
                  ;;
                kite.claude.code)
                  rm -f "$WORKSPACE_BIN_ROOT/claude"
                  ;;
                kite.opencode)
                  rm -f "$WORKSPACE_BIN_ROOT/opencode"
                  ;;
                kite.openclaw)
                  rm -f "$WORKSPACE_BIN_ROOT/openclaw"
                  if command -v npm >/dev/null 2>&1; then
                    npm uninstall -g openclaw >/dev/null 2>&1 || true
                  fi
                  ;;
              esac
              rm -rf "${'$'}software" "${'$'}cache"
            done
            if command -v apt-get >/dev/null 2>&1; then
              apt-get clean >/dev/null 2>&1 || true
            fi
            echo "资源取消清理完成"
            exit 0
        """.trimIndent()
    }

    fun toRecipe(spec: KiteResourceInstallSpec): KiteRecipe =
        KiteRecipe(
            id = recipeId(spec.id, spec.operation),
            name = spec.name,
            description = spec.description,
            type = KiteRecipe.inferTypeForResourceSteps(spec.steps),
            category = spec.category,
            defaultUrl = spec.steps.firstOrNull { it.type == KiteRecipe.STEP_OPEN_WEB && !it.url.isNullOrBlank() }?.url.orEmpty(),
            shortcut = false,
            icon = KiteRecipeIcon(name = spec.iconName),
            launch = KiteLaunchConfig(openInstance = true),
            execution = KiteExecution.steps(spec.steps),
            actions = linkedMapOf(
                KiteRecipe.ACTION_START to KiteRecipeAction(
                    id = KiteRecipe.ACTION_START,
                    label = spec.actionLabel,
                    steps = spec.steps
                )
            ),
            runtimeSource = RUNTIME_SOURCE
        )

    fun safeId(value: String): String =
        value.trim().lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-').ifBlank { "resource" }

    private fun safeCommandName(value: String): String =
        value.trim().replace(Regex("[^A-Za-z0-9._-]+"), "").take(80)

    private fun transactionStateValue(value: String): String =
        value.trim().replace(Regex("[^A-Za-z0-9._:+-]+"), "").take(128)

    private fun safeNpmPackage(value: String): String =
        value.trim().replace(Regex("[^A-Za-z0-9@/._-]+"), "").take(160)

    private fun safePreservePath(value: String): String? {
        val clean = value.trim().trim('/')
        if (clean.isBlank() || clean.length > 240 || !RESOURCE_RELATIVE_PATH.matches(clean)) return null
        if (clean.split('/').any { it == "." || it == ".." }) return null
        return clean
    }

    private fun normalizedPreservePaths(paths: List<String>): List<String> {
        val accepted = mutableListOf<String>()
        paths.mapNotNull(::safePreservePath)
            .distinct()
            .sortedWith(compareBy<String>({ it.count { character -> character == '/' } }, { it }))
            .forEach { candidate ->
                if (accepted.none { ancestor ->
                        candidate == ancestor || candidate.startsWith("$ancestor/")
                    }
                ) {
                    accepted += candidate
                }
            }
        return accepted
    }

    private fun shellLiteral(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private val RESOURCE_VERSION_TOKEN = Regex("v?[0-9]+(?:\\.[0-9]+)*(?:[-+][0-9A-Za-z.-]+)?")
    private val RESOURCE_RELATIVE_PATH = Regex("[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)*")
}

private fun KiteRecipe.Companion.inferTypeForResourceSteps(steps: List<KiteRecipeStep>): String {
    val hasAgent = steps.any { it.type == KiteRecipe.STEP_AGENT }
    val hasCommand = steps.any {
        it.type == KiteRecipe.STEP_SHELL || it.type == KiteRecipe.STEP_TERMINAL ||
            it.type == KiteRecipe.STEP_X11 || it.type == KiteRecipe.STEP_NATIVE_CAPABILITY
    }
    val hasOpenWeb = steps.any { it.type == KiteRecipe.STEP_OPEN_WEB }
    return when {
        hasAgent && !hasCommand && !hasOpenWeb -> KiteRecipe.TYPE_AGENT
        hasCommand && hasOpenWeb -> KiteRecipe.TYPE_COMMAND_WEB
        hasCommand || hasAgent -> KiteRecipe.TYPE_START_SERVICE
        hasOpenWeb -> KiteRecipe.TYPE_OPEN_URL
        else -> KiteRecipe.TYPE_TEMPLATE
    }
}
