package com.kite.app.application.resources

import com.kite.app.resources.KiteResourcePlanSnapshot

/** 失败项属于原获取队列时续接队列；独立失败时只重新获取该资源。 */
internal object FailedResourceRecoveryPolicy {
    fun continuation(
        resourceId: String,
        plan: KiteResourcePlanSnapshot,
    ): ResourceRunContinuation =
        if (plan.targetResourceId.isNotBlank() && resourceId in plan.resourceIds) {
            ResourceRunContinuation.ResumeInstallWizard
        } else {
            ResourceRunContinuation.Reinstall
        }
}
