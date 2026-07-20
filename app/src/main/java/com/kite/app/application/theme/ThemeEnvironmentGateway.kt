package com.kite.app.application.theme

import com.kite.app.theme.ScopedThemeEnvironment
import com.kite.app.theme.ThemeEnvironment
import com.kite.app.theme.ThemeScope

/** application 层只声明主题环境能力，不依赖 Android Context 或 Configuration。 */
interface ThemeEnvironmentGateway {
    fun current(): ThemeEnvironment
    fun current(scope: ThemeScope): ScopedThemeEnvironment
}

interface ThemeEnvironmentDependenciesOwner {
    val themeEnvironmentGateway: ThemeEnvironmentGateway
}
