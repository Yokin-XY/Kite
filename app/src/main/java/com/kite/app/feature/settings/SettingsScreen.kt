package com.kite.app.feature.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.kite.app.R
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeScope
import com.kite.app.ui.theme.isSystemDarkTheme

internal class SettingsScreen(
    private val context: Context,
    initialState: SettingsUiState,
    initialScrollY: Int = 0,
    onBack: () -> Unit,
    onOpenCategory: (SettingsCategoryDestination) -> Unit,
) {
    private val themeEnvironment = KiteTheme.resolveEnvironment(
        initialState.theme,
        context.isSystemDarkTheme(),
    ).forScope(ThemeScope.SETTINGS)
    private val factory = SettingsViewFactory(context, themeEnvironment.tokens, themeEnvironment.components)
    private val scrollView = ScrollView(context)

    val root: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(factory.tokens.pageBackground)
        addView(factory.topBar(context.getString(R.string.settings_title), onBack))
        addView(scrollView.apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(factory.dp(22), factory.dp(8), factory.dp(22), factory.dp(96))
                val isDebugBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                SettingsCatalog.visibleCategories(isDebugBuild)
                    .groupBy(SettingsCategorySpec::section)
                    .forEach { (section, categories) ->
                        addView(factory.sectionTitle(context.getString(section.titleRes())).apply {
                            if (childCount > 0) setPadding(0, factory.dp(22), 0, factory.dp(12))
                        })
                        categories.forEach { category ->
                            addView(factory.navigationRow(
                                context.getString(category.titleRes),
                                context.getString(category.summaryRes),
                            ) { onOpenCategory(category.destination) }.root.apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                ).apply { setMargins(0, factory.dp(10), 0, 0) }
                            })
                        }
                    }
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    init {
        scrollView.post { scrollView.scrollTo(0, initialScrollY.coerceAtLeast(0)) }
    }

    fun render(@Suppress("UNUSED_PARAMETER") state: SettingsUiState) = Unit

    fun currentScrollY(): Int = scrollView.scrollY

    private fun SettingsSection.titleRes(): Int = when (this) {
        SettingsSection.Personalization -> R.string.settings_section_personalization
        SettingsSection.Usage -> R.string.settings_section_usage
        SettingsSection.System -> R.string.settings_section_system
        SettingsSection.Other -> R.string.settings_section_other
    }
}
