package com.kite.app.platform.runs

import com.kite.app.application.runs.RecipeStepExecutionRequest
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.contracts.SpaceRecord

/**
 * 一次 Recipe step 启动链的就绪凭证。只允许原请求消费，不持久化，也不跨步骤或重试复用。
 */
internal data class RuntimeReadyLease(
    val instanceId: String,
    val generation: Long,
    val stepIndex: Int,
    val stepId: String,
    val attemptId: Long,
    val environmentId: String,
    val container: ContainerRecord,
    val preparedSpace: SpaceRecord?,
    val runtimeSnapshotRefreshed: Boolean,
) {
    fun spaceFor(request: RecipeStepExecutionRequest): SpaceRecord? {
        check(matches(request)) { "runtime_ready_lease_mismatch" }
        return preparedSpace
    }

    fun containerFor(request: RecipeStepExecutionRequest): ContainerRecord {
        check(matches(request)) { "runtime_ready_lease_mismatch" }
        return container
    }

    fun matches(request: RecipeStepExecutionRequest): Boolean =
        instanceId == request.instanceId &&
            generation == request.generation &&
            stepIndex == request.stepIndex &&
            stepId == request.step.id &&
            attemptId == request.attemptId &&
            environmentId == request.previousState.environmentId

    companion object {
        fun create(
            request: RecipeStepExecutionRequest,
            container: ContainerRecord,
            preparedSpace: SpaceRecord?,
            runtimeSnapshotRefreshed: Boolean,
        ): RuntimeReadyLease = RuntimeReadyLease(
            instanceId = request.instanceId,
            generation = request.generation,
            stepIndex = request.stepIndex,
            stepId = request.step.id,
            attemptId = request.attemptId,
            environmentId = request.previousState.environmentId,
            container = container,
            preparedSpace = preparedSpace,
            runtimeSnapshotRefreshed = runtimeSnapshotRefreshed,
        )
    }
}
