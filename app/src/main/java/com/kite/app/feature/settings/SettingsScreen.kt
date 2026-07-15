package com.kite.app.feature.settings

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.theme.KiteTheme

internal class SettingsScreen(
    context: Context,
    initialState: SettingsUiState,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    private val onSelectBrowserMode: (BrowserRuntimeMode) -> Unit,
    onRestoreLastScreen: (Boolean) -> Unit,
    onHideMainTask: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenDropZone: () -> Unit
) {
    private val factory = SettingsViewFactory(context, KiteTheme.resolve(initialState.theme))
    private lateinit var browserBinding: SettingsViewFactory.NavigationBinding
    private lateinit var restoreBinding: SettingsViewFactory.SwitchBinding
    private lateinit var recentsBinding: SettingsViewFactory.SwitchBinding
    private lateinit var notificationBinding: SettingsViewFactory.NavigationBinding
    private lateinit var dropZoneBinding: SettingsViewFactory.NavigationBinding
    private var latestState = initialState

    val root: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(factory.tokens.pageBackground)
        addView(factory.topBar("设置", onBack))
        addView(ScrollView(context).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(factory.dp(22), factory.dp(8), factory.dp(22), factory.dp(96))
                addView(factory.navigationRow(
                    "主题",
                    "主题色、背景色和卡片色彩",
                    onOpenTheme
                ).root)
                browserBinding = factory.navigationRow(
                    "浏览器模式",
                    initialState.browserRuntimeMode.title
                ) { factory.showBrowserModeDialog(latestState, onSelectBrowserMode) }
                addView(browserBinding.root.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, factory.dp(12), 0, 0) }
                })
                restoreBinding = factory.switchRow(
                    "回前台保持现场",
                    "切出去复制内容再回来时，保留正在编辑的配置和当前页面。",
                    initialState.restoreLastScreen,
                    onRestoreLastScreen
                )
                addView(restoreBinding.root)
                recentsBinding = factory.switchRow(
                    "后台隐藏",
                    "开启后主应用从最近任务中隐藏；关闭后可从最近任务回到上一步。",
                    initialState.hideMainTaskFromRecents,
                    onHideMainTask
                )
                addView(recentsBinding.root)
                notificationBinding = factory.navigationRow(
                    "首页卡片通知",
                    initialState.notificationSubtitle,
                    onOpenNotificationSettings
                )
                addView(notificationBinding.root.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, factory.dp(12), 0, 0) }
                })
                dropZoneBinding = factory.navigationRow(
                    "投放区",
                    initialState.dropZoneMessage,
                    onOpenDropZone
                )
                addView(dropZoneBinding.root.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, factory.dp(12), 0, 0) }
                })
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun render(state: SettingsUiState) {
        latestState = state
        browserBinding.subtitle.text = state.browserRuntimeMode.title
        restoreBinding.bind(state.restoreLastScreen)
        recentsBinding.bind(state.hideMainTaskFromRecents)
        notificationBinding.subtitle.text = state.notificationSubtitle
        dropZoneBinding.subtitle.text = state.dropZoneMessage
    }
}
