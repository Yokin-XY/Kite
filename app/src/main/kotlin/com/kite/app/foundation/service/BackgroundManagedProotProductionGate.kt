package com.kite.app.foundation.service

internal enum class BackgroundManagedProotGateState {
    ENABLED,
    DISABLED,
}

internal data class BackgroundManagedProotGateSnapshot(
    val schema: String,
    val state: BackgroundManagedProotGateState,
    val reason: String,
)

/** 后台通用 PRoot PROCESS 的单一生产开关；不识别资源、命令、应用或 runtime id。 */
internal object BackgroundManagedProotProductionGate {
    private const val SCHEMA = "background_managed_proot_gate_v1"
    private const val ENABLED = true
    private const val ENABLED_REASON = "rf950_matrix_passed"
    private const val DISABLED_REASON = "rf950_matrix_not_accepted"

    fun snapshot() = BackgroundManagedProotGateSnapshot(
        schema = SCHEMA,
        state = if (ENABLED) {
            BackgroundManagedProotGateState.ENABLED
        } else {
            BackgroundManagedProotGateState.DISABLED
        },
        reason = if (ENABLED) ENABLED_REASON else DISABLED_REASON,
    )

    fun requireEnabled() {
        val current = snapshot()
        check(current.state == BackgroundManagedProotGateState.ENABLED) {
            "background_managed_proot_gate_disabled:${current.reason}"
        }
    }

    fun toRuntimeHealthEnvText(): String = snapshot().let { current ->
        buildString {
            appendLine("proot_long_actual_production_gate_schema=${current.schema}")
            appendLine("proot_long_actual_production_gate_state=${current.state.name}")
            appendLine("proot_long_actual_production_gate_reason=${current.reason}")
        }
    }
}
