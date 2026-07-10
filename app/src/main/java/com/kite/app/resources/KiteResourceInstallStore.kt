package com.kite.app.resources

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class KiteResourceInstallSignal(
    val revision: Long = 0L,
    val reason: String = "initial",
    val resourceId: String? = null,
    val targetResourceId: String? = null,
    val affectedResourceIds: List<String> = emptyList(),
    val status: String? = null,
    val operation: String = ""
)

class KiteResourceInstallStore(context: Context) {
    private val registry = KiteResourceRegistry(context)
    private val installedSnapshots = KiteInstalledResourceSnapshotStore(context)
    private val pageCache = KiteResourcePageCacheStore(context)

    init {
        initializeSharedSnapshots()
    }

    val signals: StateFlow<KiteResourceInstallSignal> = sharedSignals

    fun status(resourceId: String): String? =
        registryEntry(resourceId)?.status?.takeIf { it.isNotBlank() }

    fun isInstalled(resourceId: String): Boolean =
        registryEntry(resourceId)?.installed == true

    fun isFailed(resourceId: String): Boolean =
        registryEntry(resourceId)?.failed == true

    fun isInstalling(resourceId: String): Boolean =
        registryEntry(resourceId)?.installing == true

    fun isPreparing(resourceId: String): Boolean =
        registryEntry(resourceId)?.preparing == true

    fun isUninstalling(resourceId: String): Boolean =
        registryEntry(resourceId)?.uninstalling == true

    fun isBusy(resourceId: String): Boolean =
        registryEntry(resourceId)?.busy == true

    fun failedOperation(resourceId: String): String =
        registryEntry(resourceId)?.operation.orEmpty()

    fun registrySnapshot(resourceIds: Collection<String> = emptyList()): Map<String, KiteResourceRegistryEntry> =
        synchronized(signalLock) {
            if (resourceIds.isEmpty()) {
                sharedRegistrySnapshot.toMap()
            } else {
                resourceIds
                    .map { KiteResourceInstallRecipes.safeId(it) }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .mapNotNull { resourceId -> sharedRegistrySnapshot[resourceId]?.let { resourceId to it } }
                    .toMap()
            }
        }

    fun registryEntry(resourceId: String): KiteResourceRegistryEntry? =
        synchronized(signalLock) {
            sharedRegistrySnapshot[KiteResourceInstallRecipes.safeId(resourceId)]
        }

    fun planSnapshot(): KiteResourcePlanSnapshot =
        synchronized(signalLock) { sharedPlanSnapshot }

    fun markInstalling(resourceId: String, runId: String? = null) {
        registry.markInstalling(resourceId, runId)
        val entry = refreshRegistryEntry(resourceId)
        emitSignal(
            reason = "markInstalling",
            resourceId = resourceId,
            affectedResourceIds = listOf(resourceId),
            status = entry?.status ?: STATUS_INSTALLING,
            operation = entry?.operation ?: OP_INSTALL
        )
    }

    fun markPreparing(resourceId: String) {
        registry.markPreparing(resourceId)
        val entry = refreshRegistryEntry(resourceId)
        emitSignal(
            reason = "markPreparing",
            resourceId = resourceId,
            affectedResourceIds = listOf(resourceId),
            status = entry?.status ?: STATUS_PREPARING,
            operation = entry?.operation ?: OP_INSTALL
        )
    }

    fun markUninstalling(resourceId: String, runId: String? = null) {
        registry.markUninstalling(resourceId, runId)
        val entry = refreshRegistryEntry(resourceId)
        emitSignal(
            reason = "markUninstalling",
            resourceId = resourceId,
            affectedResourceIds = listOf(resourceId),
            status = entry?.status ?: STATUS_UNINSTALLING,
            operation = entry?.operation ?: OP_UNINSTALL
        )
    }

    fun markInstalled(resourceId: String, version: String, runId: String?, summary: String?) {
        registry.markInstalled(resourceId, version, runId, summary)
        val entry = refreshRegistryEntry(resourceId)
        emitSignal(
            reason = "markInstalled",
            resourceId = resourceId,
            affectedResourceIds = listOf(resourceId),
            status = entry?.status ?: STATUS_INSTALLED,
            operation = entry?.operation ?: OP_INSTALL
        )
    }

    fun markFailed(resourceId: String, operation: String, runId: String?, reason: String?) {
        registry.markFailed(resourceId, operation, runId, reason)
        val entry = refreshRegistryEntry(resourceId)
        emitSignal(
            reason = "markFailed",
            resourceId = resourceId,
            affectedResourceIds = listOf(resourceId),
            status = entry?.status ?: STATUS_FAILED,
            operation = entry?.operation ?: operation
        )
    }

    fun clear(resourceId: String) {
        registry.clear(resourceId)
        installedSnapshots.clear(resourceId)
        synchronized(signalLock) {
            sharedRegistrySnapshot.remove(KiteResourceInstallRecipes.safeId(resourceId))
        }
        emitSignal(
            reason = "clear",
            resourceId = resourceId,
            affectedResourceIds = listOf(resourceId),
            status = ""
        )
    }

    fun beginPlan(targetResourceId: String, resourceIds: List<String>) {
        registry.beginPlan(targetResourceId, resourceIds)
        refreshPlanSnapshot()
        emitSignal(
            reason = "beginPlan",
            targetResourceId = targetResourceId,
            affectedResourceIds = resourceIds + targetResourceId
        )
    }

    fun pendingPlanResourceIds(): List<String> =
        planSnapshot().pendingResourceIds

    fun runningPlanResourceIds(): List<String> =
        planSnapshot().runningResourceIds

    fun planResourceIds(): List<String> =
        planSnapshot().resourceIds

    fun planStepStatus(resourceId: String): String =
        planSnapshot().stepStatus(KiteResourceInstallRecipes.safeId(resourceId))

    fun markPlanStepRunning(resourceId: String): Boolean {
        val changed = registry.markPlanStepRunning(resourceId)
        if (changed) {
            refreshPlanSnapshot()
            emitSignal("markPlanStepRunning", resourceId = resourceId, affectedResourceIds = listOf(resourceId))
        }
        return changed
    }

    fun advancePlanAfter(resourceId: String): List<String> {
        val next = registry.advancePlanAfter(resourceId)
        val plan = refreshPlanSnapshot()
        emitSignal(
            reason = "advancePlanAfter",
            resourceId = resourceId,
            targetResourceId = plan.targetResourceId,
            affectedResourceIds = plan.resourceIds + resourceId
        )
        return next
    }

    fun failPlanAt(resourceId: String) {
        registry.failPlanAt(resourceId)
        val plan = refreshPlanSnapshot()
        emitSignal(
            reason = "failPlanAt",
            resourceId = resourceId,
            targetResourceId = plan.targetResourceId,
            affectedResourceIds = plan.resourceIds + resourceId
        )
    }

    fun resumePlanFrom(resourceId: String): Boolean {
        val changed = registry.resumePlanFrom(resourceId)
        if (changed) {
            val plan = refreshPlanSnapshot()
            emitSignal(
                reason = "resumePlanFrom",
                resourceId = resourceId,
                targetResourceId = plan.targetResourceId,
                affectedResourceIds = plan.resourceIds + resourceId
            )
        }
        return changed
    }

    fun clearPlan() {
        val previous = planSnapshot()
        registry.clearPlan()
        refreshPlanSnapshot()
        emitSignal(
            reason = "clearPlan",
            targetResourceId = previous.targetResourceId,
            affectedResourceIds = previous.resourceIds + previous.targetResourceId
        )
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

    private fun initializeSharedSnapshots() {
        synchronized(signalLock) {
            if (snapshotsInitialized) return
            sharedRegistrySnapshot.clear()
            sharedRegistrySnapshot.putAll(registry.snapshot())
            sharedPlanSnapshot = registry.planSnapshot()
            snapshotsInitialized = true
        }
    }

    private fun refreshRegistryEntry(resourceId: String): KiteResourceRegistryEntry? {
        val cleanId = KiteResourceInstallRecipes.safeId(resourceId)
        val entry = registry.entry(cleanId)
        synchronized(signalLock) {
            if (entry == null) sharedRegistrySnapshot.remove(cleanId) else sharedRegistrySnapshot[cleanId] = entry
        }
        return entry
    }

    private fun refreshPlanSnapshot(): KiteResourcePlanSnapshot =
        registry.planSnapshot().also { snapshot ->
            synchronized(signalLock) { sharedPlanSnapshot = snapshot }
        }

    private fun emitSignal(
        reason: String,
        resourceId: String? = null,
        targetResourceId: String? = null,
        affectedResourceIds: List<String> = emptyList(),
        status: String? = null,
        operation: String = ""
    ) {
        synchronized(signalLock) {
            revision += 1
            sharedSignals.value = KiteResourceInstallSignal(
                revision = revision,
                reason = reason,
                resourceId = resourceId,
                targetResourceId = targetResourceId,
                affectedResourceIds = affectedResourceIds
                    .map { KiteResourceInstallRecipes.safeId(it) }
                    .filter { it.isNotBlank() }
                    .distinct(),
                status = status,
                operation = operation
            )
        }
    }

    companion object {
        private val signalLock = Any()
        private val sharedSignals = MutableStateFlow(KiteResourceInstallSignal())
        private val sharedRegistrySnapshot = linkedMapOf<String, KiteResourceRegistryEntry>()
        private var sharedPlanSnapshot = KiteResourcePlanSnapshot()
        private var snapshotsInitialized = false
        private var revision = 0L

        const val STATUS_INSTALLED = KiteResourceRegistry.STATUS_INSTALLED
        const val STATUS_FAILED = KiteResourceRegistry.STATUS_FAILED
        const val STATUS_PREPARING = KiteResourceRegistry.STATUS_PREPARING
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
