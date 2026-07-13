package com.kite.app.application.runs

import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeAction
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.resources.KiteResourceInstallRecipes

/** 无页面依赖的特殊运行配方工厂，供 Shell 和 Platform 共同创建可恢复运行。 */
internal object CardRunSpecialRecipes {
    const val RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE = "resource_install_wizard"

    fun temporaryBrowser(recipeId: String, url: String, title: String = "临时网页"): KiteRecipe =
        KiteRecipe(
            id = recipeId,
            name = title,
            description = "由 Ubuntu 浏览器请求临时打开",
            type = KiteRecipe.TYPE_OPEN_URL,
            category = "temporary",
            defaultUrl = url,
            shortcut = false,
            execution = KiteExecution.steps(
                listOf(KiteRecipeStep(id = "open_$recipeId", type = KiteRecipe.STEP_OPEN_WEB, url = url))
            ),
            actions = linkedMapOf(
                KiteRecipe.ACTION_START to KiteRecipeAction(
                    id = KiteRecipe.ACTION_START,
                    steps = listOf(KiteRecipeStep(id = "open_$recipeId", type = KiteRecipe.STEP_OPEN_WEB, url = url))
                )
            ),
            runtimeSource = "temporary"
        )

    fun installWizard(targetResourceId: String, targetName: String, recipeId: String? = null): KiteRecipe =
        KiteRecipe(
            id = recipeId?.takeIf { it.isNotBlank() }
                ?: "resource-install-wizard-${KiteResourceInstallRecipes.safeId(targetResourceId)}",
            name = "${targetName.ifBlank { targetResourceId }} 获取向导",
            description = "管理资源执行队列",
            type = KiteRecipe.TYPE_TEMPLATE,
            category = "resource",
            defaultUrl = "",
            shortcut = false,
            icon = KiteRecipeIcon(name = KiteRecipeIcon.ICON_TOOLS),
            launch = KiteLaunchConfig(openInstance = true),
            execution = KiteExecution.steps(emptyList()),
            actions = emptyMap(),
            runtimeSource = RESOURCE_INSTALL_WIZARD_RUNTIME_SOURCE
        )
}
