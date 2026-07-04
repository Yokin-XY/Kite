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
    const val WORKSPACE_SOFTWARE_ROOT = "/workspace/.kf/software"
    const val WORKSPACE_BIN_ROOT = "/workspace/.kf/bin"
    const val OP_INSTALL = "install"
    const val OP_UNINSTALL = "uninstall"
    private const val TOOL_ENV_BIN_COMMANDS =
        "pnpm pnpx wget jq rg fd zip unzip zstd file tar gzip gunzip xz unxz bzip2 bunzip2 ps pgrep pkill pidof top free ip ss netstat ping dig nslookup host update-ca-certificates less tree rsync patch sed awk grep find xargs sort uniq head tail cut tr wc tee env which whoami id uname date sleep timeout kill sha256sum sha1sum md5sum base64 chmod chown chgrp ln readlink realpath mkdir rmdir rm cp mv touch du df stat systemctl service"

    fun localPackPath(resourceId: String, packId: String = "ai-dev-pack"): String =
        "$WORKSPACE_RESOURCE_ROOT/${safeId(resourceId)}/$packId"

    fun softwarePath(resourceId: String): String =
        "$WORKSPACE_SOFTWARE_ROOT/${safeId(resourceId)}"

    fun installRoot(resourceId: String): String =
        softwarePath(resourceId)

    fun recipeId(resourceId: String, operation: String): String =
        "resource-${safeId(resourceId)}-${safeId(operation)}"

    fun localToolchainCommand(resourceId: String, mode: String): String {
        val packPath = localPackPath(resourceId)
        val installRoot = installRoot(resourceId)
        return """
            set -e
            export KF_RESOURCE_ID="${safeId(resourceId)}"
            export KF_TOOLCHAIN_PACK_DIR="$packPath"
            export KF_TOOLCHAIN_DIR="$installRoot"
            export KF_TOOLCHAIN_BIN_DIR="$WORKSPACE_BIN_ROOT"
            export UV_LINK_MODE="copy"
            echo "KITE_RESOURCE_STEP clean-install-root ${'$'}KF_TOOLCHAIN_DIR"
            rm -rf "${'$'}KF_TOOLCHAIN_DIR"
            echo "KITE_RESOURCE_STEP prepare-install-root ${'$'}KF_TOOLCHAIN_DIR"
            mkdir -p "${'$'}KF_TOOLCHAIN_DIR" "${'$'}KF_TOOLCHAIN_BIN_DIR"
            chmod +x "${'$'}KF_TOOLCHAIN_PACK_DIR/install.sh" 2>/dev/null || true
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
        cleanInstallRoot: Boolean
    ): String {
        val safeCommands = managedCommands.map(::safeCommandName).filter { it.isNotBlank() }.distinct()
        val commandList = safeCommands.joinToString(" ")
        val cleanLine = if (cleanInstallRoot) """rm -rf "${'$'}install_root"""" else ":"
        return """
            set -e
            install_root="${softwarePath(resourceId)}"
            user_home="${'$'}install_root/user-home"
            npm_prefix="${'$'}install_root/npm-global"
            export HOME="${'$'}user_home"
            export npm_config_prefix="${'$'}npm_prefix"
            export PATH="$WORKSPACE_BIN_ROOT:${'$'}install_root/bin:${'$'}npm_prefix/bin:${'$'}HOME/.local/bin:${'$'}HOME/.kimi-code/bin:${'$'}HOME/.codex/bin:${'$'}HOME/.claude/local:${'$'}HOME/.opencode/bin:/root/.local/bin:/root/.kimi-code/bin:/root/.codex/bin:/root/.claude/local:/root/.opencode/bin:${'$'}PATH"
            command_ledger="${'$'}install_root/.kite-managed-commands"
            command_snapshot_after="${'$'}install_root/.kite-commands-after"
            explicit_commands="$commandList"
            public_command_roots="${'$'}install_root/bin ${'$'}npm_prefix/bin ${'$'}HOME/.local/bin ${'$'}HOME/.kimi-code/bin ${'$'}HOME/.codex/bin ${'$'}HOME/.claude/local ${'$'}HOME/.opencode/bin"
            remove_recorded_command_links() {
              [ -f "${'$'}command_ledger" ] || return 0
              while IFS='	' read -r command_name target_path; do
                [ -n "${'$'}command_name" ] || continue
                link_path="$WORKSPACE_BIN_ROOT/${'$'}command_name"
                current_target="${'$'}(readlink "${'$'}link_path" 2>/dev/null || true)"
                if [ "${'$'}current_target" = "${'$'}target_path" ]; then
                  rm -f "${'$'}link_path"
                fi
              done < "${'$'}command_ledger"
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
                "${'$'}install_root/bin/${'$'}target_name"|\
                "${'$'}npm_prefix/bin/${'$'}target_name"|\
                "${'$'}HOME/.local/bin/${'$'}target_name"|\
                "${'$'}HOME/.kimi-code/bin/${'$'}target_name"|\
                "${'$'}HOME/.codex/bin/${'$'}target_name"|\
                "${'$'}HOME/.claude/local/${'$'}target_name"|\
                "${'$'}HOME/.opencode/bin/${'$'}target_name"|\
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
            remove_recorded_command_links
            $cleanLine
            mkdir -p "${'$'}install_root" "${'$'}install_root/bin" "${'$'}npm_prefix/bin" "${'$'}user_home" "$WORKSPACE_BIN_ROOT"
            kite_build_apt_proxy_conf="/etc/apt/apt.conf.d/99kite-proxy"
            if [ -f "${'$'}kite_build_apt_proxy_conf" ]; then
              echo "KITE_RESOURCE_STEP clear-build-apt-proxy ${'$'}kite_build_apt_proxy_conf"
              rm -f "${'$'}kite_build_apt_proxy_conf" || true
            fi
            echo "KITE_RESOURCE_STEP manifest-install ${safeId(resourceId)}"
            $rawCommand
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
                if [ -e "${'$'}link_path" ] || [ -L "${'$'}link_path" ]; then
                  case "${'$'}existing_target" in
                    "${'$'}install_root"/*|"${'$'}HOME"/*)
                      rm -f "${'$'}link_path"
                      ;;
                    "")
                      echo "KITE_RESOURCE_STEP command-conflict ${'$'}command_name"
                      if is_explicit_command "${'$'}command_name"; then
                        exit 127
                      fi
                      continue
                      ;;
                    *)
                      if is_explicit_command "${'$'}command_name" && is_safe_explicit_command_target "${'$'}existing_target" "${'$'}command_name"; then
                        rm -f "${'$'}link_path"
                      else
                        echo "KITE_RESOURCE_STEP command-conflict ${'$'}command_name -> ${'$'}existing_target"
                        if is_explicit_command "${'$'}command_name"; then
                          exit 127
                        fi
                        continue
                      fi
                      ;;
                  esac
                fi
                linked_command=
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
                if [ -z "${'$'}linked_command" ]; then
                  if is_explicit_command "${'$'}command_name"; then
                    echo "$displayName installed, but command ${'$'}command_name could not be linked."
                    exit 127
                  fi
                  continue
                fi
                hash -r 2>/dev/null || true
                if ! command -v "${'$'}command_name" >/dev/null 2>&1; then
                  echo "$displayName installed, but command ${'$'}command_name was not found."
                  exit 127
                fi
              done
            fi
            rm -f "${'$'}command_snapshot_after"
            printf '%s\n' 'installed_by_kite' > "${'$'}install_root/ownership"
            echo "$displayName installed by manifest action"
        """.trimIndent()
    }

    fun manifestUninstallCommand(
        resourceId: String,
        rawCommand: String,
        managedCommands: List<String>,
        npmUninstallPackages: List<String>
    ): String {
        val safeCommands = managedCommands.map(::safeCommandName).filter { it.isNotBlank() }.distinct()
        val commandList = safeCommands.joinToString(" ")
        val packageList = npmUninstallPackages.map(::safeNpmPackage).filter { it.isNotBlank() }.distinct().joinToString(" ")
        return """
            set +e
            install_root="${softwarePath(resourceId)}"
            user_home="${'$'}install_root/user-home"
            npm_prefix="${'$'}install_root/npm-global"
            export HOME="${'$'}user_home"
            export npm_config_prefix="${'$'}npm_prefix"
            export PATH="$WORKSPACE_BIN_ROOT:${'$'}install_root/bin:${'$'}npm_prefix/bin:${'$'}HOME/.local/bin:${'$'}HOME/.kimi-code/bin:${'$'}HOME/.codex/bin:${'$'}HOME/.claude/local:${'$'}HOME/.opencode/bin:/root/.local/bin:${'$'}PATH"
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
            rm -rf "${'$'}install_root"
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
            rm -rf "${'$'}resource_root"
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
            rm -rf ${localPackPath("kite.uv").substringBeforeLast("/")}
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
            rm -rf ${localPackPath("kite.nodejs").substringBeforeLast("/")}
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
            rm -rf ${localPackPath("kite.tool.env").substringBeforeLast("/")}
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
            rm -rf ${localPackPath("kite.hermes.core").substringBeforeLast("/")}
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

    private fun safeNpmPackage(value: String): String =
        value.trim().replace(Regex("[^A-Za-z0-9@/._-]+"), "").take(160)
}

private fun KiteRecipe.Companion.inferTypeForResourceSteps(steps: List<KiteRecipeStep>): String {
    val hasCommand = steps.any { it.type == KiteRecipe.STEP_SHELL || it.type == KiteRecipe.STEP_TERMINAL || it.type == KiteRecipe.STEP_X11 }
    val hasOpenWeb = steps.any { it.type == KiteRecipe.STEP_OPEN_WEB }
    return when {
        hasCommand && hasOpenWeb -> KiteRecipe.TYPE_COMMAND_WEB
        hasCommand -> KiteRecipe.TYPE_START_SERVICE
        hasOpenWeb -> KiteRecipe.TYPE_OPEN_URL
        else -> KiteRecipe.TYPE_TEMPLATE
    }
}
