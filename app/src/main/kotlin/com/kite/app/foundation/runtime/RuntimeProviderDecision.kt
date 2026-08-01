package com.kite.app.foundation.runtime

internal enum class RuntimeProviderKind {
    ANDROID_NATIVE,
    MANAGED_RUNTIME,
    PROOT,
}

/** 不同 Provider 可以拥有不同准备上下文，但必须消费同一执行请求并返回同一结果合同。 */
internal interface RuntimeExecutionProvider<in C, out T> {
    val kind: RuntimeProviderKind

    fun prepare(
        context: C,
        request: RuntimeExecutionRequest,
    ): RuntimeProviderDecision<T>
}

/** Provider 只生成计划与证据，不创建进程、不写 Store。 */
internal sealed interface RuntimeProviderDecision<out T> {
    val provider: RuntimeProviderKind
    val reason: String

    data class Ready<T>(
        override val provider: RuntimeProviderKind,
        val plan: T,
        override val reason: String,
    ) : RuntimeProviderDecision<T> {
        init {
            require(reason.isNotBlank()) { "runtime_provider_ready_reason_missing" }
        }
    }

    /** 当前 Provider 无法完整兑现，但请求本身仍可交给后续 Provider。 */
    data class Unsupported(
        override val provider: RuntimeProviderKind,
        override val reason: String,
    ) : RuntimeProviderDecision<Nothing> {
        init {
            require(reason.isNotBlank()) { "runtime_provider_unsupported_reason_missing" }
        }
    }

    /** 请求、身份或安全合同失败；后续 Provider 不得掩盖该错误。 */
    data class Blocked(
        override val provider: RuntimeProviderKind,
        override val reason: String,
    ) : RuntimeProviderDecision<Nothing> {
        init {
            require(reason.isNotBlank()) { "runtime_provider_blocked_reason_missing" }
        }
    }
}

internal fun RuntimeFallbackPolicy.allowsProviderFallback(): Boolean =
    this == RuntimeFallbackPolicy.BEFORE_START_ONLY
