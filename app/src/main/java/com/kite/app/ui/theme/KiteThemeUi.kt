package com.kite.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.core.view.WindowInsetsControllerCompat
import com.kite.app.application.theme.ThemeEnvironmentDependenciesOwner
import com.kite.app.theme.ScopedThemeEnvironment
import com.kite.app.theme.ThemeScope
import com.kite.app.theme.ThemeTokens

fun Context.kiteThemeEnvironment(scope: ThemeScope = ThemeScope.APP): ScopedThemeEnvironment {
    val owner = applicationContext as? ThemeEnvironmentDependenciesOwner
        ?: error("Application 必须提供 ThemeEnvironmentGateway")
    return owner.themeEnvironmentGateway.current(scope)
}

fun Context.isSystemDarkTheme(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

fun Activity.applyKiteWindowTheme(tokens: ThemeTokens, dark: Boolean) {
    window.statusBarColor = tokens.pageBackground
    window.navigationBarColor = tokens.pageBackground
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) window.navigationBarDividerColor = tokens.border
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
    }
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
}
