package com.kite.app.resources

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class KiteResourceInstallSignal(
    val revision: Long = 0L,
    val reason: String = "initial",
    val resourceId: String? = null,
    val targetResourceId: String? = null
)

class KiteResourceInstallStore(context: Context) {
    private val registry = KiteResourceRegistry(context)
    private val installedSnapshots = KiteInstalledResourceSnapshotStore(context)
    private val pageCache = KiteResourcePageCacheStore(context)

    val signals: StateFlow<KiteResourceInstallSignal> = sharedSignals

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
        emitSignal("markInstalling", resourceId = resourceId)
    }

    fun markUninstalling(resourceId: String, runId: String? = null) {
        registry.markUninstalling(resourceId, runId)
        emitSignal("markUninstalling", resourceId = resourceId)
    }

    fun markInstalled(resourceId: String, version: String, runId: String?, summary: String?) {
        registry.markInstalled(resourceId, version, runId, summary)
        emitSignal("markInstalled", resourceId = resourceId)
    }

    fun markFailed(resourceId: String, operation: String, runId: String?, reason: String?) {
        registry.markFailed(resourceId, operation, runId, reason)
        emitSignal("markFailed", resourceId = resourceId)
    }

    fun clear(resourceId: String) {
        registry.clear(resourceId)
        installedSnapshots.clear(resourceId)
        emitSignal("clear", resourceId = resourceId)
    }

    fun beginPlan(targetResourceId: String, resourceIds: List<String>) {
        registry.beginPlan(targetResourceId, resourceIds)
        emitSignal("beginPlan", targetResourceId = targetResourceId)
    }

    fun pendingPlanResourceIds(): List<String> =
        registry.pendingPlanResourceIds()

    fun runningPlanResourceIds(): List<String> =
        registry.runningPlanResourceIds()

    fun planResourceIds(): List<String> =
        registry.planResourceIds()

    fun planStepStatus(resourceId: String): String =
        registry.planStepStatus(resourceId)

    fun markPlanStepRunning(resourceId: String): Boolean {
        val changed = registry.markPlanStepRunning(resourceId)
        if (changed) emitSignal("markPlanStepRunning", resourceId = resourceId)
        return changed
    }

    fun advancePlanAfter(resourceId: String): List<String> {
        val next = registry.advancePlanAfter(resourceId)
        emitSignal("advancePlanAfter", resourceId = resourceId)
        return next
    }

    fun failPlanAt(resourceId: String) {
        registry.failPlanAt(resourceId)
        emitSignal("failPlanAt", resourceId = resourceId)
    }

    fun resumePlanFrom(resourceId: String): Boolean {
        val changed = registry.resumePlanFrom(resourceId)
        if (changed) emitSignal("resumePlanFrom", resourceId = resourceId)
        return changed
    }

    fun clearPlan() {
        registry.clearPlan()
        emitSignal("clearPlan")
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

    private fun emitSignal(reason: String, resourceId: String? = null, targetResourceId: String? = null) {
        synchronized(signalLock) {
            revision += 1
            sharedSignals.value = KiteResourceInstallSignal(
                revision = revision,
                reason = reason,
                resourceId = resourceId,
                targetResourceId = targetResourceId
            )
        }
    }

    companion object {
        private val signalLock = Any()
        private val sharedSignals = MutableStateFlow(KiteResourceInstallSignal())
        private var revision = 0L

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
