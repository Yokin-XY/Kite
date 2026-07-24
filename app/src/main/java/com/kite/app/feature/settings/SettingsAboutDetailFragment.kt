package com.kite.app.feature.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.kite.app.application.settings.SettingsFeatureDependenciesOwner

/** 帮助与关于的声明型详情页；页面只读取随 APK 发布的静态说明。 */
internal class SettingsAboutDetailFragment : Fragment() {
    private val initialPage: SettingsAboutPage by lazy(LazyThreadSafetyMode.NONE) {
        requireArguments().getString(ARG_PAGE)
            ?.let(SettingsAboutPage::valueOf)
            ?: error("缺少帮助详情页目标")
    }
    private var screen: SettingsAboutDetailScreen? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val owner = requireContext().applicationContext as? SettingsFeatureDependenciesOwner
            ?: error("Application 必须提供 SettingsGateway")
        val page = savedInstanceState?.getString(STATE_PAGE)
            ?.let { value -> runCatching { SettingsAboutPage.valueOf(value) }.getOrNull() }
            ?: initialPage
        return SettingsAboutDetailScreen(
            context = requireContext(),
            initialPage = page,
            initialState = SettingsProjector.project(owner.settingsFeatureGateway.currentSnapshot()),
            onBack = { SettingsFeatureResultContract.send(this, SettingsFeatureRequest.Back) },
        ).also { screen = it }.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PAGE, screen?.currentPage?.name ?: initialPage.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        screen = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_PAGE = "settings_about_page"
        private const val STATE_PAGE = "settings_about_current_page"

        fun newInstance(page: SettingsAboutPage): SettingsAboutDetailFragment =
            SettingsAboutDetailFragment().apply {
                arguments = Bundle().apply { putString(ARG_PAGE, page.name) }
            }
    }
}
