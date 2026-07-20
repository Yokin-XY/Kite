package com.kite.app.feature.settings

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.kite.app.R
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.KiteThemeMode
import com.kite.app.theme.ThemeScope
import com.kite.app.ui.theme.isSystemDarkTheme

internal class ThemeSettingsScreen(
    private val context: Context,
    private val onBack: () -> Unit,
    private val onThemeColor: (Int) -> Unit,
    private val onBackgroundColor: (Int) -> Unit,
    private val onThemeMode: (KiteThemeMode) -> Unit = {},
    private val onThemeStyle: (String) -> Unit = {},
) {
    private var themeSignature = ""
    val root: FrameLayout = FrameLayout(context)

    fun render(state: SettingsUiState) {
        val systemDark = context.isSystemDarkTheme()
        val nextSignature = "${state.theme.themeColor}:${state.theme.backgroundColor}:${state.theme.mode}:${state.theme.styleKey}:$systemDark"
        if (nextSignature == themeSignature && root.childCount > 0) return
        themeSignature = nextSignature
        val environment = KiteTheme.resolveEnvironment(state.theme, systemDark).forScope(ThemeScope.SETTINGS)
        val factory = SettingsViewFactory(context, environment.tokens, environment.components)
        root.setBackgroundColor(factory.tokens.pageBackground)
        root.removeAllViews()
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(factory.tokens.pageBackground)
            addView(factory.topBar(context.getString(R.string.settings_theme_title), onBack))
            addView(ScrollView(context).apply {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(factory.dp(22), factory.dp(8), factory.dp(22), factory.dp(96))
                    addView(factory.sectionTitle(context.getString(R.string.settings_theme_mode_section)))
                    addView(factory.navigationRow(
                        context.getString(R.string.settings_theme_mode_title),
                        context.themeModeLabel(state.theme.mode),
                    ) {
                        val modes = KiteThemeMode.entries
                        factory.showTextChoiceDialog(
                            title = context.getString(R.string.settings_theme_mode_dialog_title),
                            summary = context.getString(R.string.settings_theme_mode_dialog_summary),
                            options = modes.map(context::themeModeLabel),
                            selectedIndex = modes.indexOf(state.theme.mode),
                        ) { index -> onThemeMode(modes[index]) }
                    }.root)
                    addView(factory.sectionTitle(context.getString(R.string.settings_theme_style_section)).apply {
                        setPadding(0, factory.dp(24), 0, factory.dp(16))
                    })
                    val styles = KiteTheme.styleDefinitions
                    val selectedStyle = styles.indexOfFirst { it.key == state.theme.styleKey }.coerceAtLeast(0)
                    addView(factory.navigationRow(
                        context.getString(R.string.settings_theme_style_title),
                        context.themeStyleLabel(styles[selectedStyle]),
                    ) {
                        factory.showTextChoiceDialog(
                            title = context.getString(R.string.settings_theme_style_dialog_title),
                            summary = context.getString(R.string.settings_theme_style_dialog_summary),
                            options = styles.map(context::themeStyleLabel),
                            selectedIndex = selectedStyle,
                        ) { index -> onThemeStyle(styles[index].key) }
                    }.root)
                    addView(factory.sectionTitle(context.getString(R.string.settings_theme_color_section)).apply {
                        setPadding(0, factory.dp(24), 0, factory.dp(16))
                    })
                    addView(factory.colorPresetRow(
                        KiteTheme.themeColorChoices.map { context.themeChoiceLabel(it) to it.color },
                        state.theme.themeColor,
                        onThemeColor
                    ))
                    addView(factory.sectionTitle(context.getString(R.string.settings_background_color_section)).apply {
                        setPadding(0, factory.dp(24), 0, factory.dp(16))
                    })
                    addView(factory.colorPresetRow(
                        KiteTheme.backgroundColorChoices.map { context.themeChoiceLabel(it) to it.color },
                        state.theme.backgroundColor,
                        onBackgroundColor
                    ))
                    addView(factory.themePreviewCard())
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }
}
