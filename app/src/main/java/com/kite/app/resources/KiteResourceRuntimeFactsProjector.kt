package com.kite.app.resources

data class KiteResourceRuntimeFacts(
    val installed: Boolean,
    val preparing: Boolean,
    val installing: Boolean,
    val uninstalling: Boolean,
    val failed: Boolean,
    val failedOperation: String,
    val idleStateLabel: String,
    val extraBusy: Boolean = false
)

object KiteResourceRuntimeFactsProjector {
    fun project(
        resourceId: String,
        registryEntry: KiteResourceRegistryEntry?,
        plan: KiteResourcePlanSnapshot,
        baselineInstalled: Boolean = false,
        idleStateLabel: String = "未获取",
        extraBusy: Boolean = false
    ): KiteResourceRuntimeFacts {
        val cleanId = KiteResourceInstallRecipes.safeId(resourceId)
        val inPlan = cleanId.isNotBlank() &&
            (cleanId == plan.targetResourceId || cleanId in plan.resourceIds)
        val planStatus = plan.stepStatus(cleanId)
        val planFailed = inPlan && (
            planStatus == KiteResourceInstallStore.PLAN_STEP_FAILED ||
                planStatus == KiteResourceInstallStore.PLAN_STEP_BLOCKED ||
                (cleanId == plan.targetResourceId && plan.resourceIds.any { id ->
                    val status = plan.stepStatus(id)
                    status == KiteResourceInstallStore.PLAN_STEP_FAILED ||
                        status == KiteResourceInstallStore.PLAN_STEP_BLOCKED
                })
            )
        val planBusy = inPlan && !planFailed && when {
            planStatus == KiteResourceInstallStore.PLAN_STEP_RUNNING -> true
            planStatus == KiteResourceInstallStore.PLAN_STEP_DONE -> false
            planStatus.isNotBlank() -> true
            cleanId == plan.targetResourceId ->
                plan.pendingResourceIds.isNotEmpty() || plan.runningResourceIds.isNotEmpty()
            else -> false
        }
        val failed = registryEntry?.failed == true || planFailed
        return KiteResourceRuntimeFacts(
            installed = registryEntry?.installed == true || baselineInstalled,
            preparing = registryEntry?.preparing == true,
            installing = registryEntry?.installing == true || planBusy,
            uninstalling = registryEntry?.uninstalling == true,
            failed = failed,
            failedOperation = registryEntry?.operation.orEmpty()
                .ifBlank { if (failed) KiteResourceInstallStore.OP_INSTALL else "" },
            idleStateLabel = idleStateLabel,
            extraBusy = extraBusy || registryEntry?.busy == true || planBusy
        )
    }
}
