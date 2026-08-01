package com.kite.app.foundation.runtime

/**
 * 默认 Ubuntu 容器在新进程中的只读复用事实。
 *
 * 这里只描述 Android 已经能确认的物理事实；不启动 PRoot、不修复文件，也不持有容器状态。
 */
internal data class DefaultContainerColdReuseFacts(
    val ordinaryRequest: Boolean,
    val runtimeAssetsCurrent: Boolean,
    val baseImageReady: Boolean,
    val containerRecordCurrent: Boolean,
    val containerRootfsReady: Boolean,
    val workspaceReady: Boolean,
    val mutableRepairCurrent: Boolean,
    val identity: RuntimeLaunchPreparationIdentity?,
)

internal sealed interface DefaultContainerColdReuseDecision {
    data class Ready(
        val identity: RuntimeLaunchPreparationIdentity,
    ) : DefaultContainerColdReuseDecision

    data class Unsupported(
        val reason: String,
    ) : DefaultContainerColdReuseDecision

    data class Blocked(
        val reason: String,
    ) : DefaultContainerColdReuseDecision
}

/** 副作用前的肯定式 Provider；不能完整证明时由调用方继续原完整准备。 */
internal object DefaultContainerColdReuseProvider {
    fun evaluate(facts: DefaultContainerColdReuseFacts): DefaultContainerColdReuseDecision {
        if (!facts.ordinaryRequest) {
            return DefaultContainerColdReuseDecision.Unsupported("explicit_view_or_environment")
        }
        val identity = facts.identity
            ?: return DefaultContainerColdReuseDecision.Unsupported("default_container_identity_missing")
        if (!validIdentity(identity)) {
            return DefaultContainerColdReuseDecision.Blocked("default_container_identity_invalid")
        }

        val missing = buildList {
            if (!facts.runtimeAssetsCurrent) add("runtime_assets")
            if (!facts.baseImageReady) add("base_image")
            if (!facts.containerRecordCurrent) add("container_record")
            if (!facts.containerRootfsReady) add("container_rootfs")
            if (!facts.workspaceReady) add("workspace")
            if (!facts.mutableRepairCurrent) add("mutable_repair")
        }
        if (missing.isNotEmpty()) {
            return DefaultContainerColdReuseDecision.Unsupported(
                "reuse_facts_unready:${missing.joinToString(",")}",
            )
        }
        return DefaultContainerColdReuseDecision.Ready(identity)
    }

    private fun validIdentity(identity: RuntimeLaunchPreparationIdentity): Boolean =
        identity.runtimeRootPath.isNotBlank() &&
            identity.runtimeDescriptorStamp > 0L &&
            identity.containerId.isNotBlank() &&
            identity.containerCreatedAtMs > 0L &&
            identity.rootfsPath.isNotBlank() &&
            identity.workspacePath.isNotBlank() &&
            identity.networkMode.isNotBlank()
}
