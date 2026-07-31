package com.kite.app.foundation.runtime

import com.kite.app.foundation.service.BackgroundRuntimeKind
import com.kite.app.foundation.service.RuntimeRetentionClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeProcessExpectedStopPriorityTest {
    @Test
    fun `persisted expected stop wins over core automatic recovery`() {
        val decision = RuntimeProcessStopReconciliation.evaluate(
            root = RuntimeRootSnapshot(
                ownerKind = RuntimeRootOwnerKind.BACKGROUND_RUNTIME,
                ownerId = "core-runtime",
                title = "core",
                statusLabel = "运行中",
                retentionClass = RuntimeRetentionClass.CRITICAL_CORE,
                runtimeKind = BackgroundRuntimeKind.CONTAINER_SUPERVISOR,
                reality = RuntimeRootReality.STALE_RECORD,
                stopReconciliationState = RuntimeProcessUnitObservationState.STOPPED_EXPECTED,
                stopReconciliationReason = "expected_stop:manual-stop",
                stopReconciliationAutoRecoverySuppressed = true,
            )
        )

        assertTrue(decision.expectedStop)
        assertTrue(decision.suppressAutoRecovery)
        assertFalse(decision.autoRecoveryAllowed)
        assertFalse(decision.coreRecoveryRequired)
    }
}
