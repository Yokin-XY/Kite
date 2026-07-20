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
import com.kite.app.theme.ThemeColorSelection
import com.kite.app.theme.ThemeCommand
import com.kite.app.ui.theme.isSystemDarkTheme

internal class ThemeSettingsScreen(
    private val context: Context,
    private val onBack: () -> Unit,
    private val onThemeCommand: (ThemeCommand) -> Unit,
) {
    private var themeSignature = ""
    val root: FrameLayout = FrameLayout(context)

    fun render(state: SettingsUiState) {
        val systemDark = context.isSystemDarkTheme()
        val nextSignature = "${state.theme}:$systemDark"
        if (nextSignature == themeSignature && root.childCount > 0) return
        themeSignature = nextSignature
        val environment = KiteTheme.resolve(state.theme, systemDark)
        val factory = SettingsViewFactory(
            context,
            environment.tokens,
            environment.foundations,
            environment.components,
        )
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
                        ) { index -> onThemeCommand(ThemeCommand.SetMode(modes[index])) }
                    }.root)
                    val colorSchemes = KiteTheme.catalog.selectableColorSchemes
                    if (colorSchemes.size >= 2) {
                        addView(factory.sectionTitle(context.getString(R.string.settings_theme_color_section)).apply {
                            setPadding(0, factory.dp(24), 0, factory.dp(16))
                        })
                        val selectedKey = (state.theme.colors as? ThemeColorSelection.Registered)?.key
                        val selectedIndex = colorSchemes.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
                        addView(factory.navigationRow(
                            context.getString(R.string.settings_theme_color_section),
                            context.themeColorSchemeLabel(colorSchemes[selectedIndex]),
                        ) {
                            factory.showTextChoiceDialog(
                                title = context.getString(R.string.settings_theme_color_section),
                                summary = context.getString(R.string.settings_theme_summary),
                                options = colorSchemes.map(context::themeColorSchemeLabel),
                                selectedIndex = selectedIndex,
                            ) { index ->
                                onThemeCommand(ThemeCommand.SetColorScheme(colorSchemes[index].key))
                            }
                        }.root)
                    }
                    val stylePacks = KiteTheme.catalog.selectableStylePacks
                    if (stylePacks.size >= 2) {
                        addView(factory.sectionTitle(context.getString(R.string.settings_theme_style_section)).apply {
                            setPadding(0, factory.dp(24), 0, factory.dp(16))
                        })
                        val selectedStyle = stylePacks.indexOfFirst {
                            it.key == state.theme.stylePack
                        }.coerceAtLeast(0)
                        addView(factory.navigationRow(
                            context.getString(R.string.settings_theme_style_title),
                            context.themeStyleLabel(stylePacks[selectedStyle]),
                        ) {
                            factory.showTextChoiceDialog(
                                title = context.getString(R.string.settings_theme_style_dialog_title),
                                summary = context.getString(R.string.settings_theme_style_dialog_summary),
                                options = stylePacks.map(context::themeStyleLabel),
                                selectedIndex = selectedStyle,
                            ) { index ->
                                onThemeCommand(ThemeCommand.SetStylePack(stylePacks[index].key))
                            }
                        }.root)
                    }
                    addView(factory.themePreviewCard())
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }
}
