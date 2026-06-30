package com.kite.app.foundation.runtime

enum class RuntimeNetworkTopologyKind(val label: String) {
    SHARED_HOST_STACK("共享宿主网络栈"),
    DISABLED("网络关闭")
}

enum class RuntimeLoopbackKind(val label: String) {
    SHARED_WITH_ANDROID("127.0.0.1 与 Android 宿主共用"),
    UNAVAILABLE("loopback 不可用")
}

enum class RuntimePortPolicyKind(val label: String) {
    HOST_SHARED_UNPRIVILEGED("共享宿主端口空间，优先使用 1024 以上端口"),
    NONE("不提供端口绑定")
}

enum class RuntimeControlBoundaryKind(val label: String) {
    ANDROID_HOST_OWNED("Android 特有控制面留在 APK 层"),
    CONTAINER_ONLY("仅容器内能力")
}

enum class RuntimeExposureScope(val label: String) {
    LOOPBACK_ONLY("仅本机回环"),
    SHARED_LAN("局域网共享"),
    HOST_LOCAL_ONLY("宿主本地"),
    UNKNOWN("未知")
}

data class RuntimeNetworkSemantics(
    val topology: RuntimeNetworkTopologyKind,
    val loopback: RuntimeLoopbackKind,
    val portPolicy: RuntimePortPolicyKind,
    val controlBoundary: RuntimeControlBoundaryKind,
    val notes: List<String> = emptyList()
) {
    fun compactSummary(): String {
        return "${topology.label} / ${loopback.label} / ${portPolicy.label}"
    }

    fun statusLines(): List<String> {
        return buildList {
            add("network=${topology.label}")
            add("loopback=${loopback.label}")
            add("ports=${portPolicy.label}")
            add("control=${controlBoundary.label}")
            notes.forEach { note -> add("note=$note") }
        }
    }
}

fun NetworkMode.toRuntimeNetworkSemantics(): RuntimeNetworkSemantics {
    return when (this) {
        NetworkMode.HOST -> RuntimeNetworkSemantics(
            topology = RuntimeNetworkTopologyKind.SHARED_HOST_STACK,
            loopback = RuntimeLoopbackKind.SHARED_WITH_ANDROID,
            portPolicy = RuntimePortPolicyKind.HOST_SHARED_UNPRIVILEGED,
            controlBoundary = RuntimeControlBoundaryKind.ANDROID_HOST_OWNED,
            notes = listOf(
                "容器和 Android 宿主共用同一网络命名空间",
                "局域网访问直接依赖宿主网络，不做 NAT 或端口映射",
                "容器内 Web 服务默认建议绑定 127.0.0.1，避免意外暴露",
                "ADB/系统控制能力优先通过 APK 层或 bridge 暴露给容器"
            )
        )
        NetworkMode.NONE -> RuntimeNetworkSemantics(
            topology = RuntimeNetworkTopologyKind.DISABLED,
            loopback = RuntimeLoopbackKind.UNAVAILABLE,
            portPolicy = RuntimePortPolicyKind.NONE,
            controlBoundary = RuntimeControlBoundaryKind.CONTAINER_ONLY,
            notes = listOf("当前容器网络已关闭")
        )
    }
}
