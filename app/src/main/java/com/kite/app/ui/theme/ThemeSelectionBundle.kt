package com.kite.app.ui.theme

import android.os.Bundle
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.KiteThemeMode
import com.kite.app.theme.ThemeColorSchemeKey
import com.kite.app.theme.ThemeColorSeed
import com.kite.app.theme.ThemeColorSelection
import com.kite.app.theme.ThemeSelection
import com.kite.app.theme.ThemeStylePackKey

/** Android 页面边界的统一主题协议编码；Bundle 不泄漏到主题规则层。 */
fun Bundle.putThemeSelection(prefix: String, selection: ThemeSelection) {
    putString("${prefix}_mode", selection.mode.storageKey)
    putString("${prefix}_style_pack", selection.stylePack.value)
    when (val colors = selection.colors) {
        is ThemeColorSelection.Registered -> {
            putString("${prefix}_color_kind", COLOR_KIND_REGISTERED)
            putString("${prefix}_color_scheme", colors.key.value)
        }
        is ThemeColorSelection.Custom -> {
            putString("${prefix}_color_kind", COLOR_KIND_CUSTOM)
            putInt("${prefix}_custom_accent", colors.seed.accent)
            putInt("${prefix}_custom_background", colors.seed.background)
        }
    }
}

fun Bundle.getThemeSelection(prefix: String): ThemeSelection = KiteTheme.normalize(
    ThemeSelection(
        mode = KiteThemeMode.fromStorageKey(getString("${prefix}_mode")),
        colors = if (getString("${prefix}_color_kind") == COLOR_KIND_CUSTOM) {
            ThemeColorSelection.Custom(
                ThemeColorSeed(
                    accent = getInt("${prefix}_custom_accent", KiteTheme.defaultThemeColor),
                    background = getInt(
                        "${prefix}_custom_background",
                        KiteTheme.defaultBackgroundColor,
                    ),
                )
            )
        } else {
            ThemeColorSelection.Registered(
                ThemeColorSchemeKey(
                    getString("${prefix}_color_scheme") ?: KiteTheme.defaultColorSchemeKey
                )
            )
        },
        stylePack = ThemeStylePackKey(
            getString("${prefix}_style_pack") ?: KiteTheme.defaultStyleKey
        ),
    )
)

private const val COLOR_KIND_REGISTERED = "registered"
private const val COLOR_KIND_CUSTOM = "custom"
