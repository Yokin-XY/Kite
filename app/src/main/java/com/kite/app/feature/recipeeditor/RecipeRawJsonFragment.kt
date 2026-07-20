package com.kite.app.feature.recipeeditor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kite.app.application.recipes.RecipeFeatureDependenciesOwner
import com.kite.app.application.recipes.RecipeFeatureGateway
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeConfig
import com.kite.app.theme.KiteThemeMode
import com.kite.app.theme.ThemeScope
import com.kite.app.ui.theme.isSystemDarkTheme
import kotlinx.coroutines.launch

/** 原始 JSON 只读页。数据来自 RecipeFeatureGateway，返回通过 Feature Result。 */
internal class RecipeRawJsonFragment : Fragment() {
    private val gateway: RecipeFeatureGateway by lazy(LazyThreadSafetyMode.NONE) {
        val owner = requireContext().applicationContext as? RecipeFeatureDependenciesOwner
            ?: error("Application 必须提供 RecipeFeatureGateway")
        owner.recipeFeatureGateway
    }
    private var screen: RecipeRawJsonScreen? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val theme = ThemeConfig(
            themeColor = requireArguments().getInt(ARG_THEME_COLOR),
            backgroundColor = requireArguments().getInt(ARG_BACKGROUND_COLOR),
            mode = KiteThemeMode.fromStorageKey(requireArguments().getString(ARG_THEME_MODE)),
            styleKey = requireArguments().getString(ARG_THEME_STYLE) ?: KiteTheme.defaultStyleKey,
        )
        return RecipeRawJsonScreen(
            context = requireContext(),
            tokens = KiteTheme.resolveEnvironment(
                theme,
                requireContext().isSystemDarkTheme(),
            ).forScope(ThemeScope.EDITOR).tokens,
            onBack = { RecipeEditorResultContract.send(this, RecipeEditorRequest.CloseRawJson) }
        ).also {
            screen = it
            it.renderLoading()
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recipeKey = requireArguments().getString(ARG_RECIPE_KEY).orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            val recipe = gateway.loadRecipes(forceRefresh = false).firstOrNull { candidate ->
                candidate.id == recipeKey || candidate.name == recipeKey
            }
            if (recipe == null) screen?.renderError(recipeKey) else {
                screen?.renderJson(recipe.toJson(includeLocalIdentity = true).toString(2))
            }
        }
    }

    override fun onDestroyView() {
        screen = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_RECIPE_KEY = "recipe_key"
        private const val ARG_THEME_COLOR = "theme_color"
        private const val ARG_BACKGROUND_COLOR = "background_color"
        private const val ARG_THEME_MODE = "theme_mode"
        private const val ARG_THEME_STYLE = "theme_style"

        fun newInstance(recipeKey: String, theme: ThemeConfig): RecipeRawJsonFragment =
            RecipeRawJsonFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RECIPE_KEY, recipeKey)
                    putInt(ARG_THEME_COLOR, theme.themeColor)
                    putInt(ARG_BACKGROUND_COLOR, theme.backgroundColor)
                    putString(ARG_THEME_MODE, theme.mode.storageKey)
                    putString(ARG_THEME_STYLE, theme.styleKey)
                }
            }
    }
}
