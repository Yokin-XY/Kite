package com.kite.app.application.resources

import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceSourcePlanFactory

/**
 * 统一检查更新的稳定筛选规则。这里只读取资源合同和已安装事实，不读取页面、网络或磁盘版本。
 */
internal object ResourceUpdateBatchPolicy {
    fun isEligible(manifest: KiteResourceManifest, installed: Boolean): Boolean =
        installed &&
            manifest.management.userLifecycleEnabled &&
            KiteResourceSourcePlanFactory.plan(manifest).capabilities.checkUpdate
}
