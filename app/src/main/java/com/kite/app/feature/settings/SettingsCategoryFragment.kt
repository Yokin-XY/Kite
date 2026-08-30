package com.kite.app.feature.settings

import android.content.Intent
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
import com.kite.app.application.runtimebootstrap.RuntimeBootstrapDependenciesOwner
import com.kite.app.R
import com.kite.app.feature.startupreport.StartupReportActivity
import com.kite.app.foundation.devicebridge.DeviceBridgeBackendMode
import com.kite.app.foundation.devicebridge.DeviceBridgeBackendStateOwner
import com.kite.app.foundation.devicebridge.DeviceBridgeLifecycleStatus
import com.kite.app.foundation.devicebridge.ShizukuBridgeStateOwner
import com.kite.app.ui.terminal.TerminalUiPreferences
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import rikka.shizuku.ShizukuProvider

/** 设置二级页骨架。具体偏好迁移按类别分批接入，状态仍来自原 Owner。 */
internal class SettingsCategoryFragment : Fragment() {
    private val destination: SettingsCategoryDestination by lazy(LazyThreadSafetyMode.NONE) {
        requireArguments().getString(ARG_DESTINATION)
            ?.let(SettingsCategoryDestination::valueOf)
            ?: error("缺少设置分类目标")
    }
    private val controller: SettingsFeatureController by lazy(LazyThreadSafetyMode.NONE) {
        val owner = requireContext().applicationContext as? SettingsFeatureDependenciesOwner
            ?: error("Application 必须提供 SettingsGateway")
        SettingsFeatureController(owner.settingsFeatureGateway, lifecycleScope)
    }
    private val runtimeGateway by lazy(LazyThreadSafetyMode.NONE) {
        val owner = requireContext().applicationContext as? RuntimeBootstrapDependenciesOwner
            ?: error("Application 必须提供 RuntimeBootstrapGateway")
        owner.runtimeBootstrapGateway
    }
    private val prootViewInspectionGateway by lazy(LazyThreadSafetyMode.NONE) {
        val owner = requireContext().applicationContext as? com.kite.app.application.runtimemanagement.ProotViewInspectionDependenciesOwner
            ?: error("Application 必须提供 ProotViewInspectionGateway")
        owner.prootViewInspectionGateway
    }
    private var screen: SettingsCategoryScreen? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        DeviceBridgeBackendStateOwner.start(requireContext())
        return SettingsCategoryScreen(
        context = requireContext(),
        destination = destination,
        initialState = controller.state.value,
        initialRuntimeSnapshot = runtimeGateway.currentSnapshot(),
        initialDeviceBridgeSnapshot = DeviceBridgeBackendStateOwner.current(),
        initialProotViewSnapshot = prootViewInspectionGateway.currentSnapshot(),
        appInfo = readAppInfo(),
        initialTerminalFontSize = TerminalUiPreferences.loadFontSizeDp(requireContext()),
        initialTerminalTheme = TerminalUiPreferences.loadThemeMode(requireContext()),
        onBack = { send(SettingsFeatureRequest.Back) },
        onOpenCategory = { target -> send(SettingsFeatureRequest.OpenCategory(target)) },
        onOpenTheme = { send(SettingsFeatureRequest.OpenTheme) },
        onSelectAppLanguage = { language ->
            dispatch(SettingsFeatureAction.SelectAppLanguage(language))
        },
        onSelectBrowserMode = { mode ->
            dispatch(SettingsFeatureAction.SelectBrowserMode(mode))
        },
        onSetResourceSourceOrder = { sourceIds ->
            dispatch(SettingsFeatureAction.SetResourceSourceOrder(sourceIds))
        },
        onSelectTerminalFontSize = { fontSize ->
            TerminalUiPreferences.saveFontSizeDp(requireContext(), fontSize)
            screen?.renderTerminalPreferences(
                fontSize,
                TerminalUiPreferences.loadThemeMode(requireContext()),
            )
        },
        onSelectTerminalTheme = { mode ->
            TerminalUiPreferences.saveThemeMode(requireContext(), mode)
            screen?.renderTerminalPreferences(
                TerminalUiPreferences.loadFontSizeDp(requireContext()),
                mode,
            )
        },
        onRestoreLastScreen = { enabled ->
            dispatch(SettingsFeatureAction.SetRestoreLastScreen(enabled))
        },
        onHideMainTask = { enabled ->
            dispatch(SettingsFeatureAction.SetHideMainTaskFromRecents(enabled))
        },
        onOpenNotificationSettings = {
            dispatch(SettingsFeatureAction.OpenNotificationSettings)
        },
        onOpenAllFilesSettings = { send(SettingsFeatureRequest.OpenAllFilesSettings) },
        onOpenProcesses = { send(SettingsFeatureRequest.OpenProcesses) },
        onSelectDeviceBridgeMode = { mode -> selectDeviceBridgeMode(mode) },
        onManageDeviceBridge = { manageDeviceBridge() },
        onOpenStartupReport = {
            startActivity(Intent(requireContext(), StartupReportActivity::class.java))
        },
        onOpenLogs = { send(SettingsFeatureRequest.OpenLogs) },
        onOpenDropZone = { dispatch(SettingsFeatureAction.OpenDropZone) },
        onOpenAboutPage = { page -> send(SettingsFeatureRequest.OpenAboutPage(page)) },
        onOpenExternal = { url -> send(SettingsFeatureRequest.OpenExternalLink(url)) },
        onRunViewAcceptance = {
            if (destination == SettingsCategoryDestination.Engineering) {
                prootViewInspectionGateway.runAcceptance()
            }
        },
        onRunViewVerification = {
            if (destination == SettingsCategoryDestination.Engineering) {
                prootViewInspectionGateway.runVerification()
            }
        },
        onCreateViewEnvironment = {
            if (destination == SettingsCategoryDestination.Engineering) {
                prootViewInspectionGateway.createEnvironment()
            }
        },
        onSwitchViewEnvironment = { environmentId ->
            if (destination == SettingsCategoryDestination.Engineering) {
                prootViewInspectionGateway.switchEnvironment(environmentId)
            }
        },
        onRunEnvironmentIsolationVerification = {
            if (destination == SettingsCategoryDestination.Engineering) {
                prootViewInspectionGateway.runEnvironmentIsolationVerification()
            }
        },
        ).also { screen = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.state.collect { state -> screen?.render(state) }
            }
        }
        if (destination == SettingsCategoryDestination.PermissionsAndFiles ||
            destination == SettingsCategoryDestination.RuntimeEnvironment
        ) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    runtimeGateway.snapshots.collect { snapshot ->
                        screen?.renderRuntimeSnapshot(snapshot)
                    }
                }
            }
        }
        if (destination == SettingsCategoryDestination.RuntimeEnvironment) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    DeviceBridgeBackendStateOwner.state.collect { snapshot ->
                        screen?.renderDeviceBridgeSnapshot(snapshot)
                    }
                }
            }
        }
        if (destination == SettingsCategoryDestination.Engineering) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    prootViewInspectionGateway.snapshots.collect { snapshot ->
                        screen?.renderProotViewSnapshot(snapshot)
                    }
                }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    runtimeGateway.snapshots
                        .map { snapshot -> snapshot.defaultContainerReady }
                        .distinctUntilChanged()
                        .filter { ready -> ready }
                        .collect {
                            prootViewInspectionGateway.refresh()
                        }
                }
            }
        }
    }

    override fun onDestroyView() {
        screen = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        screen?.renderTerminalPreferences(
            TerminalUiPreferences.loadFontSizeDp(requireContext()),
            TerminalUiPreferences.loadThemeMode(requireContext()),
        )
        if (destination == SettingsCategoryDestination.PermissionsAndFiles ||
            destination == SettingsCategoryDestination.RuntimeEnvironment
        ) {
            runtimeGateway.refresh()
        }
        if (destination == SettingsCategoryDestination.RuntimeEnvironment) {
            DeviceBridgeBackendStateOwner.refreshSelected()
        }
        if (destination == SettingsCategoryDestination.Engineering) {
            prootViewInspectionGateway.refresh()
        }
        lifecycleScope.launch { controller.dispatch(SettingsFeatureAction.Refresh) }
    }

    private fun dispatch(action: SettingsFeatureAction) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val effect = controller.dispatch(action)) {
                is SettingsFeatureEffect.BrowserModeChanged -> Toast.makeText(
                    requireContext(),
                    getString(
                        R.string.settings_browser_mode_changed,
                        requireContext().browserModeTitle(effect.mode),
                    ),
                    Toast.LENGTH_SHORT,
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

    private fun selectDeviceBridgeMode(mode: DeviceBridgeBackendMode) {
        if (!DeviceBridgeBackendStateOwner.select(mode)) {
            Toast.makeText(
                requireContext(),
                R.string.settings_device_bridge_mode_save_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun manageDeviceBridge() {
        val snapshot = DeviceBridgeBackendStateOwner.current()
        when (snapshot.selectedMode) {
            DeviceBridgeBackendMode.RootExperimental -> DeviceBridgeBackendStateOwner.probeRoot()
            DeviceBridgeBackendMode.Shizuku -> when {
                !snapshot.managerInstalled -> openShizukuResource()
                !snapshot.binderAlive -> openShizukuManagerOrResource()
                snapshot.lifecycle == DeviceBridgeLifecycleStatus.PermissionRequired ||
                    snapshot.lifecycle == DeviceBridgeLifecycleStatus.Revoked -> requestShizukuAuthorization()
                else -> DeviceBridgeBackendStateOwner.refreshSelected()
            }
        }
    }

    private fun requestShizukuAuthorization() {
        when (ShizukuBridgeStateOwner.requestAuthorization()) {
            ShizukuBridgeStateOwner.AuthorizationRequestResult.ServiceNotRunning ->
                openShizukuManagerOrResource()
            ShizukuBridgeStateOwner.AuthorizationRequestResult.Failed,
            ShizukuBridgeStateOwner.AuthorizationRequestResult.NotStarted -> Toast.makeText(
                requireContext(),
                R.string.settings_device_bridge_authorization_failed,
                Toast.LENGTH_SHORT,
            ).show()
            ShizukuBridgeStateOwner.AuthorizationRequestResult.Requested,
            ShizukuBridgeStateOwner.AuthorizationRequestResult.AlreadyReady,
            ShizukuBridgeStateOwner.AuthorizationRequestResult.AlreadyRequesting -> Unit
        }
    }

    private fun openShizukuManagerOrResource() {
        val launchIntent = requireContext().packageManager.getLaunchIntentForPackage(
            ShizukuProvider.MANAGER_APPLICATION_ID,
        )
        if (launchIntent == null || runCatching { startActivity(launchIntent) }.isFailure) {
            openShizukuResource()
        }
    }

    private fun openShizukuResource() {
        send(SettingsFeatureRequest.OpenResource(SHIZUKU_RESOURCE_ID))
    }

    private fun readAppInfo(): SettingsAppInfo {
        val context = requireContext()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = packageInfo.versionCode.toLong()
        return SettingsAppInfo(
            versionName = packageInfo.versionName.orEmpty().ifBlank { "-" },
            versionCode = versionCode,
        )
    }

    companion object {
        private const val ARG_DESTINATION = "settings_category_destination"
        private const val SHIZUKU_RESOURCE_ID = "kite.shizuku"

        fun newInstance(destination: SettingsCategoryDestination): SettingsCategoryFragment =
            SettingsCategoryFragment().apply {
                arguments = Bundle().apply { putString(ARG_DESTINATION, destination.name) }
            }
    }
}
