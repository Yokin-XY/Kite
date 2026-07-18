package com.kite.app.feature.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kite.app.application.settings.SettingsFeatureDependenciesOwner
import com.kite.app.R
import kotlinx.coroutines.launch

/** 设置首页。页面只投影 Gateway 快照，系统页面与主壳副作用通过 Result 上交。 */
internal class SettingsFragment : Fragment() {
    private val controller: SettingsFeatureController by lazy(LazyThreadSafetyMode.NONE) {
        val owner = requireContext().applicationContext as? SettingsFeatureDependenciesOwner
            ?: error("Application 必须提供 SettingsGateway")
        SettingsFeatureController(owner.settingsFeatureGateway, lifecycleScope)
    }
    private var screen: SettingsScreen? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SettingsScreen(
        context = requireContext(),
        initialState = controller.state.value,
        onBack = { send(SettingsFeatureRequest.Back) },
        onOpenTheme = { send(SettingsFeatureRequest.OpenTheme) },
        onSelectAppLanguage = { language ->
            dispatch(SettingsFeatureAction.SelectAppLanguage(language))
        },
        onSelectBrowserMode = { mode ->
            dispatch(SettingsFeatureAction.SelectBrowserMode(mode))
        },
        onRestoreLastScreen = { enabled ->
            dispatch(SettingsFeatureAction.SetRestoreLastScreen(enabled))
        },
        onHideMainTask = { enabled ->
            dispatch(SettingsFeatureAction.SetHideMainTaskFromRecents(enabled))
        },
        onOpenNotificationSettings = { dispatch(SettingsFeatureAction.OpenNotificationSettings) },
        onOpenDropZone = { dispatch(SettingsFeatureAction.OpenDropZone) }
    ).also { screen = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.state.collect { state -> screen?.render(state) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        screen = null
        super.onDestroyView()
    }

    fun refresh() {
        if (!isAdded) return
        lifecycleScope.launch { controller.dispatch(SettingsFeatureAction.Refresh) }
    }

    private fun dispatch(action: SettingsFeatureAction) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val effect = controller.dispatch(action)) {
                is SettingsFeatureEffect.BrowserModeChanged -> Toast.makeText(
                    requireContext(),
                    getString(
                        R.string.settings_browser_mode_changed,
                        requireContext().browserModeTitle(effect.mode)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                SettingsFeatureEffect.RecentTaskVisibilityChanged ->
                    send(SettingsFeatureRequest.ApplyRecentTaskVisibility)
                SettingsFeatureEffect.NotificationSettingsRequested ->
                    send(SettingsFeatureRequest.OpenNotificationSettings)
                is SettingsFeatureEffect.DropZoneRequested ->
                    send(SettingsFeatureRequest.OpenDropZone(effect.available))
                is SettingsFeatureEffect.AppLanguageChanged,
                is SettingsFeatureEffect.ThemeChanged,
                null -> Unit
            }
        }
    }

    private fun send(request: SettingsFeatureRequest) {
        SettingsFeatureResultContract.send(this, request)
    }
}
