package com.kite.app

import com.kite.app.bridge.KiteBrowserProxyInstaller
import com.kite.app.foundation.service.KiteTaskContract
import com.kite.app.foundation.service.KiteTaskContractHost
import com.kite.app.foundation.terminal.BrowserEnvironmentProvider
import com.kite.app.foundation.terminal.BrowserEnvironmentProviderHost
import com.kite.app.foundation.toolchain.ToolchainResourcePort
import com.kite.app.foundation.toolchain.ToolchainResourcePortHost
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.shell.KiteAppGraph

/**
 * 业务层对 foundation 层依赖反转契约的实现与注入。
 *
 * 用 ContentProvider 做零侵入初始化:Android 在 Application.onCreate 之后、
 * 任何 Activity/Service 启动之前会自动创建已注册的 ContentProvider,
 * 因此这里 install() 的时机一定早于 KFShellService / TerminalSessionController /
 * ToolchainPackInstaller 首次读取契约。
 *
 * 这样 foundation 层通过接口反向依赖业务层,而业务层通过本 Provider 注入实现,
 * 双向都不需要 hardcode 对方,斩断 foundation → com.kite.app 的反向依赖(T5)。
 */
class KiteTaskContractInitializer : android.content.ContentProvider() {

    override fun onCreate(): Boolean {
        KiteTaskContractHost.install(object : KiteTaskContract {
            override val mainActivityClass: Class<*> = MainActivity::class.java
            override val cardRunActivityClassName: String = CardRunActivity::class.java.name
        })
        BrowserEnvironmentProviderHost.install(object : BrowserEnvironmentProvider {
            override fun defaultEnvironment(context: android.content.Context, source: String): Map<String, String> =
                KiteBrowserProxyInstaller.defaultEnvironment(context, source)
        })
        ToolchainResourcePortHost.install(object : ToolchainResourcePort {
            override fun currentEnvironmentId(context: android.content.Context): String =
                KiteAppGraph.from(context.applicationContext).resourceInstallStore.currentEnvironmentId()

            override fun statusOf(
                context: android.content.Context,
                resourceId: String,
                environmentId: String
            ): String =
                KiteAppGraph.from(context.applicationContext).resourceInstallStore
                    .registryEntry(resourceId, environmentId)?.status.orEmpty()

            override fun versionOf(
                context: android.content.Context,
                resourceId: String,
                environmentId: String
            ): String =
                KiteAppGraph.from(context.applicationContext).resourceInstallStore
                    .registryEntry(resourceId, environmentId)?.version.orEmpty()

            override fun markInstalling(
                context: android.content.Context,
                resourceId: String,
                runId: String?,
                environmentId: String
            ) {
                KiteAppGraph.from(context.applicationContext).resourceInstallStore
                    .markInstalling(resourceId, runId, environmentId = environmentId)
            }

            override fun markInstalled(
                context: android.content.Context,
                resourceId: String,
                version: String?,
                runId: String?,
                summary: String?,
                environmentId: String
            ) {
                KiteAppGraph.from(context.applicationContext).resourceInstallStore
                    .markInstalled(resourceId, version ?: "", runId, summary, environmentId)
            }

            override fun markFailed(
                context: android.content.Context,
                resourceId: String,
                runId: String?,
                reason: String?,
                environmentId: String
            ) {
                KiteAppGraph.from(context.applicationContext).resourceInstallStore
                    .markFailed(resourceId, KiteResourceInstallStore.OP_INSTALL, runId, reason, environmentId)
            }

        })
        return true
    }

    // 以下方法本 Provider 不实际提供内容,仅为满足 ContentProvider 契约。
    override fun query(uri: android.net.Uri, p: Array<out String>?, s: String?, args: Array<out String>?, sort: String?): android.database.Cursor? = null
    override fun getType(uri: android.net.Uri): String? = null
    override fun insert(uri: android.net.Uri, values: android.content.ContentValues?) = null
    override fun delete(uri: android.net.Uri, s: String?, args: Array<String>?) = 0
    override fun update(uri: android.net.Uri, values: android.content.ContentValues?, s: String?, args: Array<String>?) = 0
}


