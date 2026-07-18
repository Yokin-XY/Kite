package com.kite.app.feature.settings

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.kite.app.R
import com.kite.app.theme.KiteTheme

internal class ThemeSettingsScreen(
    private val context: Context,
    private val onBack: () -> Unit,
    private val onThemeColor: (Int) -> Unit,
    private val onBackgroundColor: (Int) -> Unit
) {
    private var themeSignature = ""
    val root: FrameLayout = FrameLayout(context)

    fun render(state: SettingsUiState) {
        val nextSignature = "${state.theme.themeColor}:${state.theme.backgroundColor}"
        if (nextSignature == themeSignature && root.childCount > 0) return
        themeSignature = nextSignature
        val factory = SettingsViewFactory(context, KiteTheme.resolve(state.theme))
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
                    addView(factory.sectionTitle(context.getString(R.string.settings_theme_color_section)))
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
