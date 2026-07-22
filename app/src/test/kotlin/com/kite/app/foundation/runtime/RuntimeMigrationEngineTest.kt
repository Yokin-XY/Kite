package com.kite.app.foundation.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RuntimeMigrationEngineTest {
    @Test
    fun selectsActiveRuntimeByStableCapabilityInsteadOfTelemetryModeName() {
        val descriptor = descriptor(
            activeRuntimeId = "lifecycle-v9",
            runtimes = listOf(
                runtime(
                    runtimeId = "lifecycle-v9",
                    assetId = "lifecycle-v9-asset",
                    executable = "proot/proot-v9",
                    telemetryMode = "future_protocol_name_that_selector_does_not_know",
                    capabilities = listOf("process_lifecycle_events", "active_process_registry")
                ),
                stockRuntime()
            )
        )

        val selected = RuntimeMigrationEngine.resolvePackagedProotDescriptorForTesting(descriptor)

        assertEquals("lifecycle-v9", selected.getString("activeRuntimeId"))
        assertEquals("lifecycle-v9-asset", selected.getString("assetId"))
        assertEquals("telemetry_capable_runtime_selected", selected.getString("telemetrySubstrateReason"))
        assertEquals(
            "process_lifecycle_events",
            selected.getJSONArray("capabilities").getString(0)
        )
    }

    @Test
    fun keepsLegacyLifecycleV0DescriptorCompatible() {
        val descriptor = descriptor(
            activeRuntimeId = "legacy-lifecycle",
            runtimes = listOf(
                runtime(
                    runtimeId = "legacy-lifecycle",
                    assetId = "legacy-lifecycle-asset",
                    executable = "proot/proot-legacy",
                    telemetryMode = "debug_jsonl_lifecycle_v0"
                ),
                stockRuntime()
            )
        )

        val selected = RuntimeMigrationEngine.resolvePackagedProotDescriptorForTesting(descriptor)

        assertEquals("legacy-lifecycle", selected.getString("activeRuntimeId"))
    }

    @Test
    fun fallsBackToStockWhenCapableRuntimeIsQuarantined() {
        val descriptor = descriptor(
            activeRuntimeId = "broken-lifecycle",
            runtimes = listOf(
                runtime(
                    runtimeId = "broken-lifecycle",
                    assetId = "broken-lifecycle-asset",
                    executable = "proot/proot-broken",
                    telemetryMode = "lifecycle_v99",
                    capabilities = listOf("process_lifecycle_events"),
                    validationState = "quarantined_after_device_failure"
                ),
                stockRuntime()
            )
        )

        val selected = RuntimeMigrationEngine.resolvePackagedProotDescriptorForTesting(descriptor)

        assertEquals("stock-preexisting", selected.getString("activeRuntimeId"))
        assertEquals("stock-proot-arm64", selected.getString("assetId"))
    }

    private fun descriptor(activeRuntimeId: String, runtimes: List<JSONObject>): JSONObject {
        return JSONObject()
            .put("assetId", runtimes.first().getString("assetId"))
            .put("activeRuntimeId", activeRuntimeId)
            .put("loaderMode", "embedded")
            .put("telemetryMode", runtimes.first().getString("telemetryMode"))
            .put("availableRuntimes", JSONArray(runtimes))
    }

    private fun runtime(
        runtimeId: String,
        assetId: String,
        executable: String,
        telemetryMode: String,
        capabilities: List<String> = emptyList(),
        validationState: String = "validated"
    ): JSONObject {
        return JSONObject()
            .put("runtimeId", runtimeId)
            .put("assetId", assetId)
            .put("provider", "termux-proot-fork")
            .put("sourceKind", "test")
            .put("executableAssetPath", executable)
            .put("loaderMode", "embedded")
            .put("telemetryMode", telemetryMode)
            .put("capabilities", JSONArray(capabilities))
            .put("validationState", validationState)
    }

    private fun stockRuntime(): JSONObject {
        return runtime(
            runtimeId = "stock-preexisting",
            assetId = "stock-proot-arm64",
            executable = "proot/proot-arm64",
            telemetryMode = "none_current"
        )
    }
}
