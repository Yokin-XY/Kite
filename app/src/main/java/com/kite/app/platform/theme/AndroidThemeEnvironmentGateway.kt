package com.kite.app.platform.theme

import android.content.Context
import android.content.res.Configuration
import com.kite.app.application.settings.SettingsGateway
import com.kite.app.application.theme.ThemeEnvironmentGateway
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeEnvironment

class AndroidThemeEnvironmentGateway(
    context: Context,
    private val settingsGateway: SettingsGateway,
) : ThemeEnvironmentGateway {
    private val appContext = context.applicationContext

    override fun current(): ThemeEnvironment = KiteTheme.resolve(
        selection = settingsGateway.currentSnapshot().themeSelection,
        systemDark = appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES,
    )

}
