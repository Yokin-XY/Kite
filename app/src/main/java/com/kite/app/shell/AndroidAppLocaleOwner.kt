package com.kite.app.shell

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.kite.app.application.settings.AppLanguagePreference

/** AppCompat 语言环境属于应用壳显示环境，不下沉到 Platform 设置持久化适配器。 */
internal object AndroidAppLocaleOwner {
    fun current(): AppLanguagePreference = AppLanguagePreference.fromLanguageTags(
        AppCompatDelegate.getApplicationLocales().toLanguageTags(),
    )

    fun apply(language: AppLanguagePreference) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language.languageTag.orEmpty()),
        )
    }
}
