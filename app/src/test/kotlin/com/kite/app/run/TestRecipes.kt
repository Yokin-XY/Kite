package com.kite.app.run

import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep

/**
 * CardRunStore 测试共用的 Recipe 夹具。
 * 把构造细节收敛在一处,让单个测试只关心状态流转。
 */
internal object TestRecipes {

    /** 一个最小的服务卡片 recipe,带单步 shell,可被 start。 */
    fun serviceRecipe(
        id: String = "recipe.test",
        name: String = "Test Recipe"
    ): KiteRecipe = KiteRecipe(
        id = id,
        name = name,
        description = "",
        type = KiteRecipe.TYPE_START_SERVICE,
        defaultUrl = "",
        shortcut = false,
        execution = KiteExecution.steps(
            listOf(
                KiteRecipeStep(
                    id = "step-1",
                    type = KiteRecipe.STEP_SHELL,
                    cmd = "echo hello"
                )
            )
        )
    )

    /** 带 open_web 步骤的 recipe,用于测 nextActionUrl 分支。 */
    fun webRecipe(
        id: String = "recipe.web",
        name: String = "Web Recipe"
    ): KiteRecipe = KiteRecipe(
        id = id,
        name = name,
        description = "",
        type = KiteRecipe.TYPE_START_SERVICE,
        defaultUrl = "",
        shortcut = false,
        execution = KiteExecution.steps(
            listOf(
                KiteRecipeStep(
                    id = "open",
                    type = KiteRecipe.STEP_OPEN_WEB,
                    url = "http://127.0.0.1:8648"
                )
            )
        )
    )
}
