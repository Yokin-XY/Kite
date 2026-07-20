package com.kite.app.feature.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kite.app.application.settings.SettingsFeatureDependenciesOwner
import kotlinx.coroutines.launch

/** 主题页仅重绑自身颜色；主壳 Chrome/终端主题通过明确 effect 同步。 */
internal class ThemeSettingsFragment : Fragment() {
    private val controller: SettingsFeatureController by lazy(LazyThreadSafetyMode.NONE) {
        val owner = requireContext().applicationContext as? SettingsFeatureDependenciesOwner
            ?: error("Application 必须提供 SettingsGateway")
        SettingsFeatureController(owner.settingsFeatureGateway, lifecycleScope)
    }
    private var screen: ThemeSettingsScreen? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ThemeSettingsScreen(
        context = requireContext(),
        onBack = { send(SettingsFeatureRequest.Back) },
        onThemeColor = { color -> dispatch(SettingsFeatureAction.SelectThemeColor(color)) },
        onBackgroundColor = { color -> dispatch(SettingsFeatureAction.SelectBackgroundColor(color)) },
        onThemeMode = { mode -> dispatch(SettingsFeatureAction.SelectThemeMode(mode)) },
        onThemeStyle = { styleKey -> dispatch(SettingsFeatureAction.SelectThemeStyle(styleKey)) },
    ).also { created ->
        screen = created
        created.render(controller.state.value)
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.state.collect { state -> screen?.render(state) }
            }
        }
    }

    override fun onDestroyView() {
        screen = null
        super.onDestroyView()
    }

    private fun dispatch(action: SettingsFeatureAction) {
        viewLifecycleOwner.lifecycleScope.launch {
            val effect = controller.dispatch(action)
            if (effect is SettingsFeatureEffect.ThemeChanged) {
                send(SettingsFeatureRequest.ApplyTheme(effect.theme))
            }
        }
    }

    private fun send(request: SettingsFeatureRequest) {
        SettingsFeatureResultContract.send(this, request)
    }
}
