package com.kite.app.platform.resources

import android.content.Context
import com.kite.app.resources.KiteResourceInstallStore
import com.kite.app.resources.KiteResourceManifest

internal data class AndroidPackageResourceFactsConvergence(
    val readyResourceIds: Set<String> = emptySet(),
    val restoredResourceIds: Set<String> = emptySet(),
    val missingResourceIds: Set<String> = emptySet(),
)

internal fun interface AndroidPackageStateProbe {
    fun installed(packageName: String): Boolean
}

@Suppress("DEPRECATION")
private fun packageManagerStateProbe(context: Context): AndroidPackageStateProbe =
    AndroidPackageStateProbe { packageName ->
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
    }

/** 把 Android PackageManager 的安装事实收敛回资源注册表，不让页面维护第二份状态。 */
internal class AndroidPackageResourceFactsReconciler(
    private val installStore: KiteResourceInstallStore,
    private val packageStateProbe: AndroidPackageStateProbe,
) {
    constructor(androidContext: Context, installStore: KiteResourceInstallStore) : this(
        installStore = installStore,
        packageStateProbe = packageManagerStateProbe(androidContext),
    )

    fun reconcile(
        manifests: Collection<KiteResourceManifest>,
        environmentId: String,
    ): AndroidPackageResourceFactsConvergence {
        val candidates = manifests
            .asSequence()
            .filter { it.sourceType == SOURCE_ANDROID_APK }
            .filter { it.source.packageName.isNotBlank() }
            .distinctBy(KiteResourceManifest::id)
            .toList()
        val ready = linkedSetOf<String>()
        val restored = linkedSetOf<String>()
        val missing = linkedSetOf<String>()
        candidates.forEach { manifest ->
            val packageInstalled = packageStateProbe.installed(manifest.source.packageName)
            val registered = installStore.isInstalled(manifest.id, environmentId)
            when {
                packageInstalled -> {
                    ready += manifest.id
                    if (!registered) {
                        installStore.markInstalled(
                            resourceId = manifest.id,
                            version = manifest.version,
                            runId = null,
                            summary = "Android 应用已通过包管理器校验，登记已恢复",
                            environmentId = environmentId,
                        )
                        restored += manifest.id
                    }
                }
                registered -> {
                    installStore.markRepairRequired(
                        resourceIds = listOf(manifest.id),
                        explanation = "Android 应用已被移除，需要重新获取",
                        environmentId = environmentId,
                    )
                    missing += manifest.id
                }
            }
        }
        return AndroidPackageResourceFactsConvergence(ready, restored, missing)
    }

    private companion object {
        const val SOURCE_ANDROID_APK = "android_apk"
    }
}
