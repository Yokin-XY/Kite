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
    val operation: String = "",
    val environmentId: String = KiteResourceRegistry.DEFAULT_ENVIRONMENT_ID
)

class KiteResourceInstallStore(
    context: Context,
    initialEnvironmentId: String = KiteResourceRegistry.DEFAULT_ENVIRONMENT_ID
) {
    private val registry = KiteResourceRegistry(context)
    private val installedSnapshots = KiteInstalledResourceSnapshotStore(context)
    private val pageCache = KiteResourcePageCacheStore(context)

    @Volatile
    private var activeEnvironmentId = normalizeEnvironmentId(initialEnvironmentId)

    init {
        if (ensureEnvironmentSnapshot(activeEnvironmentId)) {
            reconcileInterruptedPlan(activeEnvironmentId)
            reconcileOrphanedPreparingState(activeEnvironmentId)
        }
    }

    val signals: StateFlow<KiteResourceInstallSignal> = sharedSignals

    fun currentEnvironmentId(): String = activeEnvironmentId

    fun activateEnvironment(environmentId: String) {
        val next = normalizeEnvironmentId(environmentId)
        val previous = activeEnvironmentId
        if (previous == next) return
        if (ensureEnvironmentSnapshot(next)) {
            reconcileInterruptedPlan(next)
            reconcileOrphanedPreparingState(next)
        }
        val affected = synchronized(signalLock) {
            (snapshotForLocked(previous).keys + snapshotForLocked(next).keys).distinct()
        }
        activeEnvironmentId = next
        emitSignal(
            reason = "activeEnvironmentChanged",
            affectedResourceIds = affected,
            environmentId = next
        )
    }

    fun status(resourceId: String, environmentId: String = currentEnvironmentId()): String? =
        registryEntry(resourceId, environmentId)?.status?.takeIf { it.isNotBlank() }

    fun isInstalled(resourceId: String, environmentId: String = currentEnvironmentId()): Boolean =
        registryEntry(resourceId, environmentId)?.installed == true

    fun isFailed(resourceId: String, environmentId: String = currentEnvironmentId()): Boolean =
        registryEntry(resourceId, environmentId)?.failed == true

    fun isInstalling(resourceId: String, environmentId: String = currentEnvironmentId()): Boolean =
        registryEntry(resourceId, environmentId)?.installing == true

    fun isPreparing(resourceId: String, environmentId: String = currentEnvironmentId()): Boolean =
        registryEntry(resourceId, environmentId)?.preparing == true

    fun isUninstalling(resourceId: String, environmentId: String = currentEnvironmentId()): Boolean =
        registryEntry(resourceId, environmentId)?.uninstalling == true

    fun isBusy(resourceId: String, environmentId: String = currentEnvironmentId()): Boolean =
        registryEntry(resourceId, environmentId)?.busy == true

    fun failedOperation(resourceId: String, environmentId: String = currentEnvironmentId()): String =
        registryEntry(resourceId, environmentId)?.operation.orEmpty()

    fun registrySnapshot(
        resourceIds: Collection<String> = emptyList(),
        environmentId: String = currentEnvironmentId()
    ): Map<String, KiteResourceRegistryEntry> =
        synchronized(signalLock) {
            val snapshot = snapshotForLocked(normalizeEnvironmentId(environmentId))
            if (resourceIds.isEmpty()) {
                snapshot.toMap()
            } else {
                resourceIds
                    .map { KiteResourceInstallRecipes.safeId(it) }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .mapNotNull { resourceId -> snapshot[resourceId]?.let { resourceId to it } }
                    .toMap()
            }
        }

    fun registryEntry(
        resourceId: String,
        environmentId: String = currentEnvironmentId()
    ): KiteResourceRegistryEntry? =
        synchronized(signalLock) {
            snapshotForLocked(normalizeEnvironmentId(environmentId))[KiteResourceInstallRecipes.safeId(resourceId)]
        }

    fun planSnapshot(environmentId: String = currentEnvironmentId()): KiteResourcePlanSnapshot =
        synchronized(signalLock) {
            sharedPlanSnapshots[normalizeEnvironmentId(environmentId)] ?: KiteResourcePlanSnapshot()
        }

    fun markInstalling(
        resourceId: String,
        runId: String? = null,
        operation: String = OP_INSTALL,
        environmentId: String = currentEnvironmentId()
    ) {
        registry.markInstalling(resourceId, runId, operation, environmentId)
        val entry = refreshRegistryEntry(resourceId, environmentId)
        emitSignal(
            reason = "markInstalling",
            resourceId = resourceId,
            affectedResourceIds = listOf(resourceId),
            status = entry?.status ?: STATUS_INSTALLING,
            operation = entry?.operation ?: operation,
            environmentId = environmentId
        )
    }

    fun markPreparing(resourceId: String, environmentId: String = currentEnvironmentId()) {
        registry.markPreparing(resourceId, environmentId)
        val entry = refreshRegistryEntry(resourceId, environmentId)
        emitSignal(
            reason = "markPreparing",
            resourceId = resourceId,
            affectedResourceIds = listOf(resourceId),
            status = entry?.status ?: STATUS_PREPARING,
            operation = entry?.operation ?: OP_INSTALL,
            environmentId = environmentId
        )
    }

    fun markUninstalling(
        resourceId: String,
        runId: String? = null,
        environmentId: String = currentEnvironmentId()
    ) {
        registry.markUninstalling(resourceId, runId, environmentId)
        val entry = refreshRegistryEntry(resourceId, environmentId)
        emitSignal(
            reason = "markUninstalling",
            resourceId = resourceId,
            affectedResourceIds = listOf(resourceId),
            status = entry?.status ?: STATUS_UNINSTALLING,
            operation = entry?.operation ?: OP_UNINSTALL,
            environmentId = environmentId
        )
    }

    fun markInstalled(
        resourceId: String,
        version: String,
        runId: String?,
        summary: String?,
        environmentId: String = currentEnvironmentId()
    ) {
        registry.markInstalled(resourceId, version, runId, summary, environmentId)
        val entry = refreshRegistryEntry(resourceId, environmentId)
        emitSignal(
            reason = "markInstalled",
            resourceId = resourceId,
            affectedResourceIds = listOf(resourceId),
            status = entry?.status ?: STATUS_INSTALLED,
            operation = entry?.operation ?: OP_INSTALL,
            environmentId = environmentId
        )
    }

    fun markFailed(
        resourceId: String,
        operation: String,
        runId: String?,
        reason: String?,
        environmentId: String = currentEnvironmentId()
    ) {
        registry.markFailed(resourceId, operation, runId, reason, environmentId)
        val entry = refreshRegistryEntry(resourceId, environmentId)
        emitSignal(
            reason = "markFailed",
            resourceId = resourceId,
            affectedResourceIds = listOf(resourceId),
            status = entry?.status ?: STATUS_FAILED,
            operation = entry?.operation ?: operation,
            environmentId = environmentId
        )
    }

    fun markUpdateChecking(resourceId: String, environmentId: String = currentEnvironmentId()) {
        markVersionCheck(resourceId, UPDATE_STATUS_CHECKING, reason = "markUpdateChecking", environmentId = environmentId)
    }

    fun markUpdateAvailable(
        resourceId: String,
        installedVersion: String,
        latestVersion: String,
        environmentId: String = currentEnvironmentId()
    ) {
        markVersionCheck(
            resourceId,
            UPDATE_STATUS_AVAILABLE,
            installedVersion = installedVersion,
            latestVersion = latestVersion,
            reason = "markUpdateAvailable",
            environmentId = environmentId
        )
    }

    fun markUpdateCurrent(
        resourceId: String,
        installedVersion: String,
        latestVersion: String,
        environmentId: String = currentEnvironmentId()
    ) {
        markVersionCheck(
            resourceId,
            UPDATE_STATUS_CURRENT,
            installedVersion = installedVersion,
            latestVersion = latestVersion,
            reason = "markUpdateCurrent",
            environmentId = environmentId
        )
    }

    fun markUpdateUnsupported(
        resourceId: String,
        explanation: String,
        environmentId: String = currentEnvironmentId()
    ) {
        markVersionCheck(
            resourceId,
            UPDATE_STATUS_UNSUPPORTED,
            summary = explanation,
            reason = "markUpdateUnsupported",
            environmentId = environmentId
        )
    }

    fun markUpdateCheckFailed(
        resourceId: String,
        explanation: String,
        environmentId: String = currentEnvironmentId()
    ) {
        markVersionCheck(
            resourceId,
            UPDATE_STATUS_FAILED,
            summary = explanation,
            reason = "markUpdateCheckFailed",
            environmentId = environmentId
        )
    }

    fun markMaintenanceFailed(
        resourceId: String,
        operation: String,
        explanation: String,
        environmentId: String = currentEnvironmentId()
    ) {
        markVersionCheck(
            resourceId,
            UPDATE_STATUS_FAILED,
            summary = explanation,
            operation = operation,
            registryStatus = STATUS_INSTALLED,
            reason = "markMaintenanceFailed",
            environmentId = environmentId
        )
    }

    fun clear(resourceId: String, environmentId: String = currentEnvironmentId()) {
        registry.clear(resourceId, environmentId)
        installedSnapshots.clear(resourceId, environmentId)
        synchronized(signalLock) {
            snapshotForLocked(normalizeEnvironmentId(environmentId))
                .remove(KiteResourceInstallRecipes.safeId(resourceId))
        }
        emitSignal(
            reason = "clear",
            resourceId = resourceId,
            affectedResourceIds = listOf(resourceId),
            status = "",
            environmentId = environmentId
        )
    }

    fun invalidateMissingInstallations(
        resourceIds: Collection<String>,
        environmentId: String = currentEnvironmentId()
    ) {
        clearResourceFacts(
            resourceIds = resourceIds,
            reason = "invalidateMissingInstallations",
            environmentId = environmentId
        )
    }

    private fun reconcileOrphanedPreparingState(environmentId: String) {
        val plan = planSnapshot(environmentId)
        val planResourceIds = (plan.resourceIds + plan.targetResourceId)
            .filter(String::isNotBlank)
            .toSet()
        val orphaned = registrySnapshot(environmentId = environmentId).values
            .filter { entry -> entry.preparing && entry.resourceId !in planResourceIds }
            .map(KiteResourceRegistryEntry::resourceId)
        clearResourceFacts(
            resourceIds = orphaned,
            reason = "reconcileOrphanedPreparingState",
            environmentId = environmentId
        )
    }

    private fun clearResourceFacts(
        resourceIds: Collection<String>,
        reason: String,
        environmentId: String
    ) {
        val normalizedEnvironmentId = normalizeEnvironmentId(environmentId)
        val normalizedIds = resourceIds
            .map(KiteResourceInstallRecipes::safeId)
            .filter(String::isNotBlank)
            .distinct()
        if (normalizedIds.isEmpty()) return
        registry.clear(normalizedIds, normalizedEnvironmentId)
        installedSnapshots.clear(normalizedIds, normalizedEnvironmentId)
        synchronized(signalLock) {
            val snapshot = snapshotForLocked(normalizedEnvironmentId)
            normalizedIds.forEach(snapshot::remove)
        }
        emitSignal(
            reason = reason,
            affectedResourceIds = normalizedIds,
            status = "",
            environmentId = normalizedEnvironmentId
        )
    }

    fun beginPlan(
        targetResourceId: String,
        resourceIds: List<String>,
        environmentId: String = currentEnvironmentId()
    ) {
        registry.beginPlan(targetResourceId, resourceIds, environmentId)
        refreshPlanSnapshot(environmentId)
        emitSignal(
            reason = "beginPlan",
            targetResourceId = targetResourceId,
            affectedResourceIds = resourceIds + targetResourceId,
            environmentId = environmentId
        )
    }

    private fun reconcileInterruptedPlan(environmentId: String) {
        val plan = registry.planSnapshot(environmentId)
        if (plan.targetResourceId.isBlank()) return
        val interrupted = plan.runningResourceIds.distinct()
        interrupted.forEach { resourceId ->
            val previous = registry.entry(resourceId, environmentId)
            registry.markFailed(
                resourceId = resourceId,
                operation = OP_INSTALL,
                runId = previous?.runId,
                reason = "上次获取被中断，请清理后重试",
                environmentId = environmentId,
            )
        }
        val affected = (plan.resourceIds + plan.targetResourceId)
            .filter(String::isNotBlank)
            .distinct()
        val transient = affected.filter { resourceId ->
            resourceId !in interrupted &&
                registry.entry(resourceId, environmentId)?.let { entry ->
                    entry.busy && !entry.installed && !entry.failed
                } == true
        }
        registry.clear(transient, environmentId)
        installedSnapshots.clear(transient, environmentId)
        registry.clearPlan(environmentId)
        synchronized(signalLock) {
            val snapshot = snapshotForLocked(normalizeEnvironmentId(environmentId))
            transient.forEach(snapshot::remove)
            interrupted.forEach { resourceId ->
                registry.entry(resourceId, environmentId)?.let { snapshot[resourceId] = it }
            }
        }
        refreshPlanSnapshot(environmentId)
        emitSignal(
            reason = "reconcileInterruptedPlan",
            targetResourceId = plan.targetResourceId,
            affectedResourceIds = affected,
            status = if (interrupted.isNotEmpty()) STATUS_FAILED else "",
            operation = if (interrupted.isNotEmpty()) OP_INSTALL else "",
            environmentId = environmentId,
        )
    }

    fun invalidateChangedInstallations(
        resourceIds: Collection<String>,
        environmentId: String = currentEnvironmentId()
    ) {
        clearResourceFacts(
            resourceIds = resourceIds,
            reason = "invalidateChangedInstallations",
            environmentId = environmentId
        )
    }

    fun beginPreparingPlan(
        targetResourceId: String,
        environmentId: String = currentEnvironmentId()
    ): Boolean {
        val previous = registry.planSnapshot(environmentId)
        val accepted = registry.beginPreparingPlan(targetResourceId, environmentId)
        if (!accepted) return false
        val plan = refreshPlanSnapshot(environmentId)
        if (plan != previous) {
            emitSignal(
                reason = "beginPreparingPlan",
                targetResourceId = plan.targetResourceId,
                affectedResourceIds = listOf(plan.targetResourceId),
                environmentId = environmentId
            )
        }
        return true
    }

    fun activatePreparedPlan(
        targetResourceId: String,
        resourceIds: List<String>,
        environmentId: String = currentEnvironmentId()
    ): Boolean {
        val activated = registry.activatePreparedPlan(targetResourceId, resourceIds, environmentId)
        if (!activated) return false
        val plan = refreshPlanSnapshot(environmentId)
        emitSignal(
            reason = "activatePreparedPlan",
            targetResourceId = plan.targetResourceId,
            affectedResourceIds = plan.resourceIds + plan.targetResourceId,
            environmentId = environmentId
        )
        return true
    }

    fun pendingPlanResourceIds(environmentId: String = currentEnvironmentId()): List<String> =
        planSnapshot(environmentId).pendingResourceIds

    fun runningPlanResourceIds(environmentId: String = currentEnvironmentId()): List<String> =
        planSnapshot(environmentId).runningResourceIds

    fun planResourceIds(environmentId: String = currentEnvironmentId()): List<String> =
        planSnapshot(environmentId).resourceIds

    fun planStepStatus(resourceId: String, environmentId: String = currentEnvironmentId()): String =
        planSnapshot(environmentId).stepStatus(KiteResourceInstallRecipes.safeId(resourceId))

    fun markPlanStepRunning(resourceId: String, environmentId: String = currentEnvironmentId()): Boolean {
        val changed = registry.markPlanStepRunning(resourceId, environmentId)
        if (changed) {
            refreshPlanSnapshot(environmentId)
            emitSignal(
                "markPlanStepRunning",
                resourceId = resourceId,
                affectedResourceIds = listOf(resourceId),
                environmentId = environmentId
            )
        }
        return changed
    }

    fun advancePlanAfter(resourceId: String, environmentId: String = currentEnvironmentId()): List<String> {
        val next = registry.advancePlanAfter(resourceId, environmentId)
        val plan = refreshPlanSnapshot(environmentId)
        emitSignal(
            reason = "advancePlanAfter",
            resourceId = resourceId,
            targetResourceId = plan.targetResourceId,
            affectedResourceIds = plan.resourceIds + resourceId,
            environmentId = environmentId
        )
        return next
    }

    fun failPlanAt(resourceId: String, environmentId: String = currentEnvironmentId()) {
        val previous = planSnapshot(environmentId)
        registry.failPlanAt(resourceId, environmentId)
        refreshPlanSnapshot(environmentId)
        val transient = (previous.resourceIds + previous.targetResourceId)
            .filter(String::isNotBlank)
            .distinct()
            .filter { candidate ->
                candidate != KiteResourceInstallRecipes.safeId(resourceId) &&
                    registry.entry(candidate, environmentId)?.let { entry ->
                        entry.busy && !entry.installed && !entry.failed
                    } == true
            }
        if (transient.isNotEmpty()) {
            registry.clear(transient, environmentId)
            installedSnapshots.clear(transient, environmentId)
            synchronized(signalLock) {
                val snapshot = snapshotForLocked(normalizeEnvironmentId(environmentId))
                transient.forEach(snapshot::remove)
            }
        }
        emitSignal(
            reason = "failPlanAt",
            resourceId = resourceId,
            targetResourceId = previous.targetResourceId,
            affectedResourceIds = previous.resourceIds + previous.targetResourceId + resourceId,
            environmentId = environmentId
        )
    }

    fun resumePlanFrom(resourceId: String, environmentId: String = currentEnvironmentId()): Boolean {
        val changed = registry.resumePlanFrom(resourceId, environmentId)
        if (changed) {
            val plan = refreshPlanSnapshot(environmentId)
            emitSignal(
                reason = "resumePlanFrom",
                resourceId = resourceId,
                targetResourceId = plan.targetResourceId,
                affectedResourceIds = plan.resourceIds + resourceId,
                environmentId = environmentId
            )
        }
        return changed
    }

    fun clearPlan(environmentId: String = currentEnvironmentId()) {
        val previous = planSnapshot(environmentId)
        registry.clearPlan(environmentId)
        refreshPlanSnapshot(environmentId)
        emitSignal(
            reason = "clearPlan",
            targetResourceId = previous.targetResourceId,
            affectedResourceIds = previous.resourceIds + previous.targetResourceId,
            environmentId = environmentId
        )
    }

    fun saveInstalledSnapshot(
        resourceId: String,
        name: String,
        iconJson: String,
        version: String,
        manifestJson: String,
        environmentId: String = currentEnvironmentId()
    ) {
        installedSnapshots.save(resourceId, name, iconJson, version, manifestJson, environmentId)
    }

    fun installedSnapshotManifestJson(
        resourceId: String,
        environmentId: String = currentEnvironmentId()
    ): String? = installedSnapshots.manifestJson(resourceId, environmentId)

    fun putPageCache(cacheKey: String, payloadJson: String, maxAgeMs: Long) {
        pageCache.putPage(cacheKey, payloadJson, maxAgeMs)
    }

    fun pageCache(cacheKey: String): String? =
        pageCache.page(cacheKey)

    fun clearExpiredPageCache() {
        pageCache.clearExpired()
    }

    private fun ensureEnvironmentSnapshot(environmentId: String): Boolean {
        val normalizedEnvironmentId = normalizeEnvironmentId(environmentId)
        return synchronized(signalLock) {
            if (normalizedEnvironmentId in initializedEnvironments) return@synchronized false
            sharedRegistrySnapshots[normalizedEnvironmentId] =
                registry.snapshot(environmentId = normalizedEnvironmentId).toMutableMap()
            sharedPlanSnapshots[normalizedEnvironmentId] = registry.planSnapshot(normalizedEnvironmentId)
            initializedEnvironments += normalizedEnvironmentId
            true
        }
    }

    private fun markVersionCheck(
        resourceId: String,
        updateStatus: String,
        installedVersion: String = "",
        latestVersion: String = "",
        summary: String? = null,
        operation: String? = null,
        registryStatus: String? = null,
        reason: String,
        environmentId: String = currentEnvironmentId()
    ) {
        registry.markVersionCheck(
            resourceId = resourceId,
            updateStatus = updateStatus,
            installedVersion = installedVersion,
            latestVersion = latestVersion,
            summary = summary,
            operation = operation,
            status = registryStatus,
            environmentId = environmentId
        )
        val entry = refreshRegistryEntry(resourceId, environmentId)
        emitSignal(
            reason = reason,
            resourceId = resourceId,
            affectedResourceIds = listOf(resourceId),
            status = entry?.status,
            operation = entry?.operation.orEmpty(),
            environmentId = environmentId
        )
    }

    private fun refreshRegistryEntry(resourceId: String, environmentId: String): KiteResourceRegistryEntry? {
        val normalizedEnvironmentId = normalizeEnvironmentId(environmentId)
        val cleanId = KiteResourceInstallRecipes.safeId(resourceId)
        val entry = registry.entry(cleanId, normalizedEnvironmentId)
        synchronized(signalLock) {
            val snapshot = snapshotForLocked(normalizedEnvironmentId)
            if (entry == null) snapshot.remove(cleanId) else snapshot[cleanId] = entry
        }
        return entry
    }

    private fun refreshPlanSnapshot(environmentId: String): KiteResourcePlanSnapshot =
        registry.planSnapshot(normalizeEnvironmentId(environmentId)).also { snapshot ->
            synchronized(signalLock) {
                sharedPlanSnapshots[normalizeEnvironmentId(environmentId)] = snapshot
            }
        }

    private fun emitSignal(
        reason: String,
        resourceId: String? = null,
        targetResourceId: String? = null,
        affectedResourceIds: List<String> = emptyList(),
        status: String? = null,
        operation: String = "",
        environmentId: String = currentEnvironmentId()
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
                operation = operation,
                environmentId = normalizeEnvironmentId(environmentId)
            )
        }
    }

    private fun normalizeEnvironmentId(environmentId: String): String =
        environmentId.trim().ifBlank { KiteResourceRegistry.DEFAULT_ENVIRONMENT_ID }

    private fun snapshotForLocked(environmentId: String): MutableMap<String, KiteResourceRegistryEntry> =
        sharedRegistrySnapshots.getOrPut(environmentId) { linkedMapOf() }

    companion object {
        private val signalLock = Any()
        private val sharedSignals = MutableStateFlow(KiteResourceInstallSignal())
        private val sharedRegistrySnapshots = linkedMapOf<String, MutableMap<String, KiteResourceRegistryEntry>>()
        private val sharedPlanSnapshots = linkedMapOf<String, KiteResourcePlanSnapshot>()
        private val initializedEnvironments = linkedSetOf<String>()
        private var revision = 0L

        const val STATUS_INSTALLED = KiteResourceRegistry.STATUS_INSTALLED
        const val STATUS_FAILED = KiteResourceRegistry.STATUS_FAILED
        const val STATUS_PREPARING = KiteResourceRegistry.STATUS_PREPARING
        const val STATUS_INSTALLING = KiteResourceRegistry.STATUS_INSTALLING
        const val STATUS_UNINSTALLING = KiteResourceRegistry.STATUS_UNINSTALLING
        const val OP_INSTALL = KiteResourceRegistry.OP_INSTALL
        const val OP_UNINSTALL = KiteResourceRegistry.OP_UNINSTALL
        const val UPDATE_STATUS_CHECKING = "checking"
        const val UPDATE_STATUS_AVAILABLE = "available"
        const val UPDATE_STATUS_CURRENT = "current"
        const val UPDATE_STATUS_UNSUPPORTED = "unsupported"
        const val UPDATE_STATUS_FAILED = "failed"
        const val PLAN_STEP_DONE = KiteResourceRegistry.PLAN_STEP_DONE
        const val PLAN_STEP_PENDING = KiteResourceRegistry.PLAN_STEP_PENDING
        const val PLAN_STEP_RUNNING = KiteResourceRegistry.PLAN_STEP_RUNNING
        const val PLAN_STEP_FAILED = KiteResourceRegistry.PLAN_STEP_FAILED
        const val PLAN_STEP_BLOCKED = KiteResourceRegistry.PLAN_STEP_BLOCKED
        const val PLAN_STATUS_PREPARING = KiteResourceRegistry.PLAN_STATUS_PREPARING
        const val PLAN_STATUS_ACTIVE = KiteResourceRegistry.PLAN_STATUS_ACTIVE
    }
}
