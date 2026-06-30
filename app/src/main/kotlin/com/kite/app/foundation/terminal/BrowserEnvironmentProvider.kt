package com.kite.app.foundation.terminal

import android.content.Context

/**
 * foundation 层对"浏览器代理环境变量"的依赖反转契约。
 *
 * 背景:TerminalSessionController(底层终端会话控制器)原本直接 import
 * com.kite.app.bridge.KiteBrowserProxyInstaller(上层业务层),调它的
 * defaultEnvironment(context, source) 拿浏览器代理环境变量。这构成反向依赖。
 *
 * 解法:foundation 只定义本接口,业务层(KiteTaskContractInitializer ContentProvider)
 * 在启动时注入实现,TerminalSessionController 通过 Host 读取,不再 hardcode 上层类。
 */
interface BrowserEnvironmentProvider {
    /**
     * 返回指定来源(如 "terminal_page")的浏览器代理环境变量,
     * 并保证相关代理脚本已安装(副作用与原 defaultEnvironment 一致)。
     */
    fun defaultEnvironment(context: Context, source: String): Map<String, String>
}

/**
 * BrowserEnvironmentProvider 的全局注入点。
 * 由业务层 ContentProvider 在应用启动时 install,供 TerminalSessionController 等底层组件读取。
 */
object BrowserEnvironmentProviderHost {
    @Volatile
    private var provider: BrowserEnvironmentProvider? = null

    fun install(provider: BrowserEnvironmentProvider) {
        this.provider = provider
    }

    fun get(): BrowserEnvironmentProvider =
        provider ?: error("BrowserEnvironmentProvider 尚未注入;应在 KiteTaskContractInitializer 中 install。")
}
