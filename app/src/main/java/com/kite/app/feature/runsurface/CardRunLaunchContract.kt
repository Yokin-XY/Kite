package com.kite.app.feature.runsurface

import com.kite.app.recipe.KiteRecipe

internal data class CardRunLaunchRequest(
    val recipeId: String,
    val instanceId: String?,
    val autoStart: Boolean,
    val launchSource: String,
    val temporaryUrl: String? = null,
    val temporaryTitle: String? = null,
    val installTargetResourceId: String? = null,
    val installPlanResourceIds: List<String> = emptyList()
)

internal data class CardRunLaunchTarget(
    val recipe: KiteRecipe,
    val instanceId: String,
    val autoStart: Boolean,
    val launchSource: String,
    val missingStatePolicy: CardRunMissingStatePolicy,
    val installTargetResourceId: String?,
    val installPlanResourceIds: List<String>
)

internal enum class CardRunMissingStatePolicy {
    Create,
    RequireExisting
}

internal sealed interface CardRunLaunchResolution {
    data class Resolved(val target: CardRunLaunchTarget) : CardRunLaunchResolution
    data class Rejected(val reason: String) : CardRunLaunchResolution
}

/** 只决定启动目标，不启动任务、不写 Store，也不创建页面。 */
internal class CardRunLaunchResolver(
    private val catalogRecipes: () -> List<KiteRecipe>,
    private val registeredRecipe: (String) -> KiteRecipe?,
    private val specialRecipe: (CardRunLaunchRequest) -> KiteRecipe?
) {
    fun resolve(request: CardRunLaunchRequest): CardRunLaunchResolution {
        val recipeId = request.recipeId.trim()
        if (recipeId.isBlank()) return CardRunLaunchResolution.Rejected("missing_recipe_id")
        val recipe = specialRecipe(request)
            ?: catalogRecipes().firstOrNull { it.id == recipeId }
            ?: registeredRecipe(recipeId)
            ?: return CardRunLaunchResolution.Rejected("missing_recipe:$recipeId")
        if (recipe.id != recipeId) {
            return CardRunLaunchResolution.Rejected("recipe_id_mismatch:${recipe.id}:$recipeId")
        }
        val instanceId = request.instanceId?.trim()?.takeIf { it.isNotBlank() } ?: recipeId
        return CardRunLaunchResolution.Resolved(
            CardRunLaunchTarget(
                recipe = recipe,
                instanceId = instanceId,
                autoStart = request.autoStart,
                launchSource = request.launchSource.trim(),
                missingStatePolicy = if (
                    request.autoStart ||
                    !request.temporaryUrl.isNullOrBlank() ||
                    !request.installTargetResourceId.isNullOrBlank()
                ) {
                    CardRunMissingStatePolicy.Create
                } else {
                    CardRunMissingStatePolicy.RequireExisting
                },
                installTargetResourceId = request.installTargetResourceId?.trim()?.takeIf { it.isNotBlank() },
                installPlanResourceIds = request.installPlanResourceIds
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
            )
        )
    }
}
