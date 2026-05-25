package com.kite.app.recipe

data class KiteRecipe(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val taskLabel: String,
    val defaultUrl: String,
    val shortcut: Boolean,
    val taskMode: String,
    val steps: List<KiteRecipeStep>
)

data class KiteRecipeStep(
    val type: String,
    val cmd: String?,
    val wait: Boolean?,
    val url: String?
)
