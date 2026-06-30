package com.kite.app

import androidx.fragment.app.FragmentActivity

/**
 * Screen 路由收口(P2 拆 God Activity 的基础设施,T6 第一步)。
 *
 * 背景:MainActivity(19591 行)原本没有中心化路由 —— 17 处分散地
 * `currentScreen = Screen.X` 紧接着各自调 show*()。T6 引入本类作为统一入口,
 * 把"切到某个 Screen"的行为收口到 navigate(),为后续把各 Screen 逐个迁到 Fragment 铺路。
 *
 * 过渡期策略(抽屉式,ADR-003):
 * - 当前所有 Screen 仍走老命令式 show* 路径(navigateLegacy),由 Activity 提供 LegacyScreenSink 回调。
 * - T6b 起逐个 Screen 改走 Fragment(routeToFragment),老路径逐步退役。
 * - 任何时候停下,项目仍可正常运行(老的分散路由仍可用,本类是新增收口层)。
 *
 * 注:本类不持有 Screen 状态(状态仍由 MainActivity.currentScreen 管理),
 * 仅提供收口入口与过渡期钩子,避免一次性大爆炸改动。
 */
internal class ScreenRouter(
    private val activity: FragmentActivity,
    private val legacySink: LegacyScreenSink
) {

    /**
     * 统一导航入口。目标:未来所有 Screen 切换都经此方法,而非分散赋值。
     * 过渡期:暂全部委托老路径。
     */
    fun navigate(screen: MainActivity.Screen) {
        legacySink.navigateToLegacy(screen)
    }

    /**
     * 由 MainActivity 实现,把 Screen 切换委托回老的 show* 方法。
     * 这是过渡期的兼容桥梁 —— 等 Screen 逐个 Fragment 化后,这些分支会被 routeToFragment 取代。
     */
    fun interface LegacyScreenSink {
        fun navigateToLegacy(screen: MainActivity.Screen)
    }
}
