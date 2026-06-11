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
