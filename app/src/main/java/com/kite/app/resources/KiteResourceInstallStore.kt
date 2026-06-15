package com.kite.app.resources

import android.content.Context

class KiteResourceInstallStore(context: Context) {
    private val registry = KiteResourceRegistry(context)
    private val installedSnapshots = KiteInstalledResourceSnapshotStore(context)
    private val pageCache = KiteResourcePageCacheStore(context)

    fun status(resourceId: String): String? =
        registry.status(resourceId)

    fun isInstalled(resourceId: String): Boolean =
        registry.isInstalled(resourceId)

    fun isFailed(resourceId: String): Boolean =
        registry.isFailed(resourceId)

    fun isInstalling(resourceId: String): Boolean =
        registry.isInstalling(resourceId)

    fun isUninstalling(resourceId: String): Boolean =
        registry.isUninstalling(resourceId)

    fun isBusy(resourceId: String): Boolean =
        registry.isBusy(resourceId)

    fun failedOperation(resourceId: String): String =
        registry.failedOperation(resourceId)

    fun registrySnapshot(resourceIds: Collection<String> = emptyList()): Map<String, KiteResourceRegistryEntry> =
        registry.snapshot(resourceIds)

    fun registryEntry(resourceId: String): KiteResourceRegistryEntry? =
        registry.entry(resourceId)

    fun planSnapshot(): KiteResourcePlanSnapshot =
        registry.planSnapshot()

    fun markInstalling(resourceId: String, runId: String? = null) {
        registry.markInstalling(resourceId, runId)
    }

    fun markUninstalling(resourceId: String, runId: String? = null) {
        registry.markUninstalling(resourceId, runId)
    }

    fun markInstalled(resourceId: String, version: String, runId: String?, summary: String?) {
        registry.markInstalled(resourceId, version, runId, summary)
    }

    fun markFailed(resourceId: String, operation: String, runId: String?, reason: String?) {
        registry.markFailed(resourceId, operation, runId, reason)
    }

    fun clear(resourceId: String) {
        registry.clear(resourceId)
        installedSnapshots.clear(resourceId)
    }

    fun beginPlan(targetResourceId: String, resourceIds: List<String>) {
        registry.beginPlan(targetResourceId, resourceIds)
    }

    fun pendingPlanResourceIds(): List<String> =
        registry.pendingPlanResourceIds()

    fun runningPlanResourceIds(): List<String> =
        registry.runningPlanResourceIds()

    fun planResourceIds(): List<String> =
        registry.planResourceIds()

    fun planStepStatus(resourceId: String): String =
        registry.planStepStatus(resourceId)

    fun markPlanStepRunning(resourceId: String): Boolean =
        registry.markPlanStepRunning(resourceId)

    fun advancePlanAfter(resourceId: String): List<String> =
        registry.advancePlanAfter(resourceId)

    fun failPlanAt(resourceId: String) {
        registry.failPlanAt(resourceId)
    }

    fun clearPlan() {
        registry.clearPlan()
    }

    fun saveInstalledSnapshot(
        resourceId: String,
        name: String,
        iconJson: String,
        version: String,
        manifestJson: String
    ) {
        installedSnapshots.save(resourceId, name, iconJson, version, manifestJson)
    }

    fun installedSnapshotManifestJson(resourceId: String): String? =
        installedSnapshots.manifestJson(resourceId)

    fun putPageCache(cacheKey: String, payloadJson: String, maxAgeMs: Long) {
        pageCache.putPage(cacheKey, payloadJson, maxAgeMs)
    }

    fun pageCache(cacheKey: String): String? =
        pageCache.page(cacheKey)

    fun clearExpiredPageCache() {
        pageCache.clearExpired()
    }

    companion object {
        const val STATUS_INSTALLED = KiteResourceRegistry.STATUS_INSTALLED
        const val STATUS_FAILED = KiteResourceRegistry.STATUS_FAILED
        const val STATUS_INSTALLING = KiteResourceRegistry.STATUS_INSTALLING
        const val STATUS_UNINSTALLING = KiteResourceRegistry.STATUS_UNINSTALLING
        const val OP_INSTALL = KiteResourceRegistry.OP_INSTALL
        const val OP_UNINSTALL = KiteResourceRegistry.OP_UNINSTALL
        const val PLAN_STEP_DONE = KiteResourceRegistry.PLAN_STEP_DONE
        const val PLAN_STEP_RUNNING = KiteResourceRegistry.PLAN_STEP_RUNNING
        const val PLAN_STEP_FAILED = KiteResourceRegistry.PLAN_STEP_FAILED
        const val PLAN_STEP_BLOCKED = KiteResourceRegistry.PLAN_STEP_BLOCKED
    }
}
