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
                if ! command -v apt-get >/dev/null 2>&1; then
                  echo "缺少 pip/venv，且当前 Ubuntu 环境无法通过 apt-get 补齐。"
                  exit 127
                fi
                echo "KITE_RESOURCE_STEP apt-install python-pip-venv"
                apt-get update
                DEBIAN_FRONTEND=noninteractive apt-get install -y python3-venv python3-pip ca-certificates
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

    fun reasonixInstallCommand(): String =
        """
            set -e
            export PATH="$WORKSPACE_BIN_ROOT:/root/.local/bin:${'$'}PATH"
            install_root="${softwarePath("kite.reasonix")}"
            if ! command -v npm >/dev/null 2>&1; then
              echo "缺少 npm：请先安装 Node.js 资源。"
              exit 127
            fi
            echo "KITE_RESOURCE_STEP prepare-install-root ${'$'}install_root"
            mkdir -p "${'$'}install_root" "$WORKSPACE_BIN_ROOT"
            echo "KITE_RESOURCE_STEP npm-install reasonix"
            npm install -g reasonix
            npm_prefix="${'$'}(npm prefix -g 2>/dev/null || true)"
            for bin_name in reasonix dsnix; do
              rm -f "$WORKSPACE_BIN_ROOT/${'$'}bin_name"
              for candidate in "${'$'}npm_prefix/bin/${'$'}bin_name" "/root/.local/bin/${'$'}bin_name"; do
                if [ -x "${'$'}candidate" ]; then
                  echo "KITE_RESOURCE_STEP link-command ${'$'}bin_name"
                  ln -sfn "${'$'}candidate" "$WORKSPACE_BIN_ROOT/${'$'}bin_name"
                  break
                fi
              done
            done
            hash -r 2>/dev/null || true
            if ! command -v reasonix >/dev/null 2>&1; then
              echo "Reasonix npm package installed, but the reasonix command was not found."
              exit 127
            fi
            reasonix --version || reasonix --help | head -40 || true
            printf '%s\n' 'installed_by_kite' > "${'$'}install_root/ownership"
            echo "Reasonix installed at ${'$'}(command -v reasonix)"
        """.trimIndent()

    fun mimoCodeInstallCommand(): String =
        """
            set -e
            export PATH="$WORKSPACE_BIN_ROOT:/root/.local/bin:${'$'}PATH"
            install_root="${softwarePath("kite.mimo.code")}"
            if ! command -v npm >/dev/null 2>&1; then
              echo "缺少 npm：请先安装 Node.js 资源。"
              exit 127
            fi
            echo "KITE_RESOURCE_STEP prepare-install-root ${'$'}install_root"
            mkdir -p "${'$'}install_root" "$WORKSPACE_BIN_ROOT"
            echo "KITE_RESOURCE_STEP npm-install @mimo-ai/cli"
            npm install -g @mimo-ai/cli
            npm_prefix="${'$'}(npm prefix -g 2>/dev/null || true)"
            rm -f "$WORKSPACE_BIN_ROOT/mimo"
            for candidate in "${'$'}npm_prefix/bin/mimo" "/root/.local/bin/mimo"; do
              if [ -x "${'$'}candidate" ]; then
                echo "KITE_RESOURCE_STEP link-command mimo"
                ln -sfn "${'$'}candidate" "$WORKSPACE_BIN_ROOT/mimo"
                break
              fi
            done
            hash -r 2>/dev/null || true
            if ! command -v mimo >/dev/null 2>&1; then
              echo "MiMo Code npm package installed, but the mimo command was not found."
              exit 127
            fi
            mimo --version || mimo --help | head -40 || true
            printf '%s\n' 'installed_by_kite' > "${'$'}install_root/ownership"
            echo "MiMo Code installed at ${'$'}(command -v mimo)"
        """.trimIndent()

    fun codexCliInstallCommand(): String =
        linkedCliInstallCommand(
            resourceId = "kite.codex.cli",
            displayName = "Codex CLI",
            commandName = "codex",
            installStep = "install-official-codex-cli",
            installCommands = """
                if ! command -v curl >/dev/null 2>&1; then
                  echo "缺少 curl：请先安装 curl 资源。"
                  exit 127
                fi
                curl -fsSL https://chatgpt.com/codex/install.sh | CODEX_NON_INTERACTIVE=1 sh
            """.trimIndent(),
            extraCandidatePaths = listOf(
                "/root/.codex/bin/codex",
                "${'$'}HOME/.codex/bin/codex"
            )
        )

    fun claudeCodeInstallCommand(): String =
        linkedCliInstallCommand(
            resourceId = "kite.claude.code",
            displayName = "Claude Code",
            commandName = "claude",
            installStep = "install-official-claude-code",
            installCommands = """
                if ! command -v curl >/dev/null 2>&1; then
                  echo "缺少 curl：请先安装 curl 资源。"
                  exit 127
                fi
                curl -fsSL https://claude.ai/install.sh | bash
            """.trimIndent(),
            extraCandidatePaths = listOf(
                "/root/.claude/local/claude",
                "${'$'}HOME/.claude/local/claude"
            )
        )

    fun openCodeInstallCommand(): String =
        linkedCliInstallCommand(
            resourceId = "kite.opencode",
            displayName = "OpenCode",
            commandName = "opencode",
            installStep = "install-official-opencode",
            installCommands = """
                if ! command -v curl >/dev/null 2>&1; then
                  echo "缺少 curl：请先安装 curl 资源。"
                  exit 127
                fi
                export OPENCODE_INSTALL_DIR="${'$'}install_root/bin"
                curl -fsSL https://opencode.ai/install | bash
            """.trimIndent(),
            extraCandidatePaths = listOf(
                "/root/.opencode/bin/opencode",
                "${'$'}HOME/.opencode/bin/opencode"
            )
        )

    fun openClawInstallCommand(): String =
        linkedCliInstallCommand(
            resourceId = "kite.openclaw",
            displayName = "OpenClaw",
            commandName = "openclaw",
            installStep = "npm-install openclaw",
            installCommands = """
                if ! command -v npm >/dev/null 2>&1; then
                  echo "缺少 npm：请先安装 Node.js 资源。"
                  exit 127
                fi
                npm install -g openclaw@latest
            """.trimIndent()
        )

    fun codexCliUninstallCommand(): String =
        linkedCliUninstallCommand("kite.codex.cli", "codex")

    fun claudeCodeUninstallCommand(): String =
        linkedCliUninstallCommand("kite.claude.code", "claude")

    fun openCodeUninstallCommand(): String =
        linkedCliUninstallCommand("kite.opencode", "opencode")

    fun openClawUninstallCommand(): String =
        linkedCliUninstallCommand("kite.openclaw", "openclaw", npmPackage = "openclaw")

    private fun linkedCliInstallCommand(
        resourceId: String,
        displayName: String,
        commandName: String,
        installStep: String,
        installCommands: String,
        extraCandidatePaths: List<String> = emptyList()
    ): String {
        val candidates = (listOf(
            "$WORKSPACE_BIN_ROOT/$commandName",
            "${'$'}install_root/bin/$commandName",
            "${'$'}npm_prefix/bin/$commandName",
            "/root/.local/bin/$commandName",
            "${'$'}HOME/.local/bin/$commandName",
            "/usr/local/bin/$commandName"
        ) + extraCandidatePaths).joinToString(" ")
        return """
            set -e
            install_root="${softwarePath(resourceId)}"
            user_home="${'$'}install_root/user-home"
            export HOME="${'$'}user_home"
            export PATH="$WORKSPACE_BIN_ROOT:${'$'}install_root/bin:${'$'}HOME/.local/bin:${'$'}HOME/.codex/bin:${'$'}HOME/.claude/local:${'$'}HOME/.opencode/bin:/root/.local/bin:/root/.codex/bin:/root/.claude/local:/root/.opencode/bin:${'$'}PATH"
            echo "KITE_RESOURCE_STEP prepare-install-root ${'$'}install_root"
            rm -rf "${'$'}install_root"
            mkdir -p "${'$'}install_root" "${'$'}install_root/bin" "${'$'}user_home" "$WORKSPACE_BIN_ROOT"
            echo "KITE_RESOURCE_STEP $installStep"
            $installCommands
            npm_prefix="${'$'}(npm prefix -g 2>/dev/null || true)"
            rm -f "$WORKSPACE_BIN_ROOT/$commandName"
            for candidate in $candidates; do
              if [ -x "${'$'}candidate" ]; then
                echo "KITE_RESOURCE_STEP link-command $commandName"
                ln -sfn "${'$'}candidate" "$WORKSPACE_BIN_ROOT/$commandName"
                break
              fi
            done
            hash -r 2>/dev/null || true
            if ! command -v $commandName >/dev/null 2>&1; then
              echo "$displayName installed, but the $commandName command was not found."
              exit 127
            fi
            $commandName --version || $commandName --help | head -40 || true
            printf '%s\n' 'installed_by_kite' > "${'$'}install_root/ownership"
            echo "$displayName installed at ${'$'}(command -v $commandName)"
        """.trimIndent()
    }

    private fun linkedCliUninstallCommand(
        resourceId: String,
        commandName: String,
        npmPackage: String? = null
    ): String {
        val npmUninstall = npmPackage?.let { pkg ->
            """
                if command -v npm >/dev/null 2>&1; then
                  echo "KITE_RESOURCE_STEP npm-uninstall $pkg"
                  npm uninstall -g $pkg
                fi
            """.trimIndent()
        }.orEmpty()
        return """
            set +e
            export PATH="$WORKSPACE_BIN_ROOT:/root/.local/bin:${'$'}PATH"
            $npmUninstall
            rm -f "$WORKSPACE_BIN_ROOT/$commandName"
            rm -rf ${softwarePath(resourceId)}
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
                if ! command -v apt-get >/dev/null 2>&1; then
                  echo "缺少 pip/venv，且当前 Ubuntu 环境无法通过 apt-get 补齐。"
                  exit 127
                fi
                echo "KITE_RESOURCE_STEP apt-install python-pip-venv"
                apt-get update
                DEBIAN_FRONTEND=noninteractive apt-get install -y python3-venv python3-pip ca-certificates
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
            "${'$'}bin_dir/hermes" --help >/dev/null
            "${'$'}bin_dir/hermes" doctor || true
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
              if ! command -v apt-get >/dev/null 2>&1; then
                echo "缺少 apt-get：当前 Ubuntu 环境无法安装 Git。"
                exit 127
              fi
              echo "KITE_RESOURCE_STEP apt-update"
              apt-get update
              echo "KITE_RESOURCE_STEP apt-install git ca-certificates"
              DEBIAN_FRONTEND=noninteractive apt-get install -y git ca-certificates
              echo "installed_by_kite" > "${'$'}install_root/ownership"
            fi
            git --version
        """.trimIndent()

    fun gitUninstallCommand(): String =
        """
            set +e
            install_root="${softwarePath("kite.git")}"
            ownership="${'$'}install_root/ownership"
            if [ -f "${'$'}ownership" ] && grep -q '^installed_by_kite${'$'}' "${'$'}ownership"; then
              if command -v apt-get >/dev/null 2>&1; then
                echo "KITE_RESOURCE_STEP apt-remove git"
                DEBIAN_FRONTEND=noninteractive apt-get remove -y git
              else
                echo "apt-get missing; cannot remove git package"
              fi
            else
              echo "Git was preexisting or ownership is unknown; clearing Kite resource record only"
            fi
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
              if ! command -v apt-get >/dev/null 2>&1; then
                echo "缺少 apt-get：当前 Ubuntu 环境无法安装 curl。"
                exit 127
              fi
              echo "KITE_RESOURCE_STEP apt-update"
              apt-get update
              echo "KITE_RESOURCE_STEP apt-install curl ca-certificates"
              DEBIAN_FRONTEND=noninteractive apt-get install -y curl ca-certificates
              echo "installed_by_kite" > "${'$'}install_root/ownership"
            fi
            curl --version | head -1
        """.trimIndent()

    fun curlUninstallCommand(): String =
        """
            set +e
            install_root="${softwarePath("kite.curl")}"
            ownership="${'$'}install_root/ownership"
            if [ -f "${'$'}ownership" ] && grep -q '^installed_by_kite${'$'}' "${'$'}ownership"; then
              if command -v apt-get >/dev/null 2>&1; then
                echo "KITE_RESOURCE_STEP apt-remove curl"
                DEBIAN_FRONTEND=noninteractive apt-get remove -y curl
              else
                echo "apt-get missing; cannot remove curl package"
              fi
            else
              echo "curl was preexisting or ownership is unknown; clearing Kite resource record only"
            fi
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
raise SystemExit(0 if (3, 11) <= sys.version_info < (3, 14) else 1)
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
              if ! command -v apt-get >/dev/null 2>&1; then
                echo "缺少 apt-get：当前 Ubuntu 环境无法安装 Python。"
                exit 127
              fi
              echo "KITE_RESOURCE_STEP apt-update"
              apt-get update
              echo "KITE_RESOURCE_STEP apt-install python3 python3-venv python3-pip ca-certificates"
              DEBIAN_FRONTEND=noninteractive apt-get install -y python3 python3-venv python3-pip ca-certificates
            fi
            python3 - <<'PY'
import sys
if not ((3, 11) <= sys.version_info < (3, 14)):
    raise SystemExit(f"Python {sys.version.split()[0]} is outside Hermes range >=3.11,<3.14")
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
            rm -rf /workspace/.kf/toolchains/node-v24.15.0
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
            rm -rf /workspace/.kf/toolchains/node-v24.15.0
            rm -rf /workspace/.kf/toolchains/uv-0.11.1
            rm -rf /workspace/.kf/toolchains/pnpm-10.33.2
            echo "KITE_RESOURCE_STEP remove-bin tool-env"
            rm -f $WORKSPACE_BIN_ROOT/node $WORKSPACE_BIN_ROOT/npm $WORKSPACE_BIN_ROOT/npx
            rm -f $WORKSPACE_BIN_ROOT/pnpm $WORKSPACE_BIN_ROOT/uv $WORKSPACE_BIN_ROOT/uvx
            rm -f $WORKSPACE_BIN_ROOT/adb $WORKSPACE_BIN_ROOT/fastboot
            rm -f $WORKSPACE_BIN_ROOT/fd $WORKSPACE_BIN_ROOT/systemctl $WORKSPACE_BIN_ROOT/service
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

    fun reasonixUninstallCommand(): String =
        """
            set +e
            export PATH="$WORKSPACE_BIN_ROOT:/root/.local/bin:${'$'}PATH"
            if command -v npm >/dev/null 2>&1; then
              echo "KITE_RESOURCE_STEP npm-uninstall reasonix"
              npm uninstall -g reasonix
            else
              echo "npm missing; clearing Kite install record only"
            fi
            rm -f "$WORKSPACE_BIN_ROOT/reasonix" "$WORKSPACE_BIN_ROOT/dsnix"
            rm -rf ${softwarePath("kite.reasonix")}
            exit 0
        """.trimIndent()

    fun mimoCodeUninstallCommand(): String =
        """
            set +e
            export PATH="$WORKSPACE_BIN_ROOT:/root/.local/bin:${'$'}PATH"
            if command -v npm >/dev/null 2>&1; then
              echo "KITE_RESOURCE_STEP npm-uninstall @mimo-ai/cli"
              npm uninstall -g @mimo-ai/cli
            else
              echo "npm missing; clearing Kite install record only"
            fi
            rm -f "$WORKSPACE_BIN_ROOT/mimo"
            rm -rf ${softwarePath("kite.mimo.code")}
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
                  rm -rf /workspace/.kf/components/kite.nodejs /workspace/.kf/toolchains/node-v24.15.0
                  ;;
                kite.uv)
                  rm -f "$WORKSPACE_BIN_ROOT/uv" "$WORKSPACE_BIN_ROOT/uvx"
                  rm -rf /workspace/.kf/toolchains/uv-0.11.1
                  ;;
                kite.tool.env)
                  rm -f "$WORKSPACE_BIN_ROOT/node" "$WORKSPACE_BIN_ROOT/npm" "$WORKSPACE_BIN_ROOT/npx"
                  rm -f "$WORKSPACE_BIN_ROOT/pnpm" "$WORKSPACE_BIN_ROOT/uv" "$WORKSPACE_BIN_ROOT/uvx"
                  rm -f "$WORKSPACE_BIN_ROOT/adb" "$WORKSPACE_BIN_ROOT/fastboot"
                  rm -f "$WORKSPACE_BIN_ROOT/fd" "$WORKSPACE_BIN_ROOT/systemctl" "$WORKSPACE_BIN_ROOT/service"
                  rm -rf /workspace/.kf/components/kite.tool.env
                  rm -rf /workspace/.kf/toolchains/node-v24.15.0 /workspace/.kf/toolchains/uv-0.11.1 /workspace/.kf/toolchains/pnpm-10.33.2
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
}

private fun KiteRecipe.Companion.inferTypeForResourceSteps(steps: List<KiteRecipeStep>): String {
    val hasCommand = steps.any { it.type == KiteRecipe.STEP_SHELL || it.type == KiteRecipe.STEP_TERMINAL }
    val hasOpenWeb = steps.any { it.type == KiteRecipe.STEP_OPEN_WEB }
    return when {
        hasCommand && hasOpenWeb -> KiteRecipe.TYPE_COMMAND_WEB
        hasCommand -> KiteRecipe.TYPE_START_SERVICE
        hasOpenWeb -> KiteRecipe.TYPE_OPEN_URL
        else -> KiteRecipe.TYPE_TEMPLATE
    }
}
