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
            if ! command -v hermes >/dev/null 2>&1; then
              echo "缺少 hermes：请先安装 Hermes Core 资源。"
              exit 127
            fi
            if ! command -v npm >/dev/null 2>&1; then
              echo "缺少 npm：请先安装 Node.js 资源。"
              exit 127
            fi
            echo "KITE_RESOURCE_STEP npm-install hermes-web-ui"
            npm install -g hermes-web-ui
            if command -v hermes-web-ui >/dev/null 2>&1; then
              hermes-web-ui -v || true
            fi
        """.trimIndent()

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

    fun pythonInstallCommand(): String =
        """
            set -e
            install_root="${softwarePath("kite.python")}"
            mkdir -p "${'$'}install_root"
            if command -v python3 >/dev/null 2>&1 && python3 - <<'PY'
import sys
raise SystemExit(0 if (3, 11) <= sys.version_info < (3, 14) else 1)
PY
            then
              echo "KITE_RESOURCE_STEP python-present ${'$'}(python3 --version 2>&1)"
            else
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
            python3 -m pip --version || true
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
            if command -v npm >/dev/null 2>&1; then
              echo "KITE_RESOURCE_STEP npm-uninstall hermes-web-ui"
              npm uninstall -g hermes-web-ui
            else
              echo "npm missing; clearing Kite install record only"
            fi
            rm -rf ${softwarePath("kite.hermes.webui")}
            exit 0
        """.trimIndent()

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
