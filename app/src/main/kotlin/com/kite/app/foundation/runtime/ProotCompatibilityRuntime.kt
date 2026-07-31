package com.kite.app.foundation.runtime

/** PRoot 是最终兼容 Provider；选择原因由上游 Planner 显式传入，不从命令名反推。 */
internal data class ProotCompatibilityProviderContext(
    val selectionReason: String,
) {
    init {
        require(selectionReason.isNotBlank()) { "proot_selection_reason_missing" }
    }
}

/**
 * 入口无关的 PRoot 逻辑计划。物理 argv 仍由既有 KFContainerManager 在真正启动前生成，
 * 因而不会复制 rootfs、bind、网络、View 或遥测规则。
 */
internal data class ProotCompatibilityPlan(
    val payload: RuntimeExecutionPayload,
    val workingDirectory: String,
    val environment: Map<String, String>,
    val interactivePty: Boolean,
    val requestedProotViewId: String?,
    val requestedProotEnvironmentId: String?,
)

internal object ProotCompatibilityRuntimeProvider :
    RuntimeExecutionProvider<ProotCompatibilityProviderContext, ProotCompatibilityPlan> {
    override val kind: RuntimeProviderKind = RuntimeProviderKind.PROOT

    override fun prepare(
        context: ProotCompatibilityProviderContext,
        request: RuntimeExecutionRequest,
    ): RuntimeProviderDecision<ProotCompatibilityPlan> {
        if (
            request.payload is RuntimeExecutionPayload.NativeCapability ||
            RuntimeExecutionRequirement.ANDROID_NATIVE in request.requirements
        ) {
            return RuntimeProviderDecision.Blocked(
                provider = kind,
                reason = "proot_cannot_execute_android_native_capability",
            )
        }
        return RuntimeProviderDecision.Ready(
            provider = kind,
            plan = ProotCompatibilityPlan(
                payload = request.payload,
                workingDirectory = request.workingDirectory?.trim().orEmpty().ifBlank { "/workspace" },
                environment = request.environment.toMap(),
                interactivePty = RuntimeExecutionRequirement.INTERACTIVE_PTY in request.requirements,
                requestedProotViewId = request.environment[ProotViewBinding.ENV_VIEW_ID]
                    ?.trim()
                    ?.takeIf(String::isNotBlank),
                requestedProotEnvironmentId = request.environment[ProotViewBinding.ENV_ENVIRONMENT_ID]
                    ?.trim()
                    ?.takeIf(String::isNotBlank),
            ),
            reason = context.selectionReason,
        )
    }
}
