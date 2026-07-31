package com.kite.app.foundation.service

import com.kite.app.foundation.runtime.HostProcessIdentityObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackgroundRuntimeStrongIdentityPersistenceTest {
    @Test
    fun `strong identity round trips and legacy json remains identity unavailable`() {
        val record = record().copy(
            status = BackgroundRuntimeStatus.RUNNING,
            pid = 321,
            processBootId = BOOT_ID,
            processStartTicks = 987654L,
        )

        val restored = BackgroundRuntimeRecord.fromJson(record.toJson())
        assertEquals(321, restored.pid)
        assertEquals(BOOT_ID, restored.processBootId)
        assertEquals(987654L, restored.processStartTicks)
        assertEquals(
            HostProcessIdentityObservation(BOOT_ID, 321, 987654L),
            restored.persistedProcessIdentityOrNull(),
        )

        val legacy = record.toJson().apply {
            remove("processBootId")
            remove("processStartTicks")
        }
        val restoredLegacy = BackgroundRuntimeRecord.fromJson(legacy)
        assertEquals(321, restoredLegacy.pid)
        assertNull(restoredLegacy.processBootId)
        assertNull(restoredLegacy.processStartTicks)
        assertNull(restoredLegacy.persistedProcessIdentityOrNull())
    }

    @Test
    fun `same pid preserves identity while changed or cleared pid clears both fields`() {
        val record = record().copy(
            status = BackgroundRuntimeStatus.RUNNING,
            pid = 321,
            processBootId = BOOT_ID,
            processStartTicks = 987654L,
        )

        assertEquals(BOOT_ID, record.withProcessPid(321).processBootId)
        assertEquals(987654L, record.withProcessPid(321).processStartTicks)

        val changed = record.withProcessPid(654)
        assertEquals(654, changed.pid)
        assertNull(changed.processBootId)
        assertNull(changed.processStartTicks)

        val cleared = record.withProcessPid(null)
        assertNull(cleared.pid)
        assertNull(cleared.processBootId)
        assertNull(cleared.processStartTicks)
    }

    @Test
    fun `identity can attach only to active record with exact pid`() {
        val identity = HostProcessIdentityObservation(BOOT_ID, 321, 987654L)
        val running = record().copy(status = BackgroundRuntimeStatus.RUNNING, pid = 321)
        val attached = running.withObservedProcessIdentity(identity)
        assertEquals(BOOT_ID, attached?.processBootId)
        assertEquals(987654L, attached?.processStartTicks)

        assertNull(running.withObservedProcessIdentity(identity.copy(hostPid = 654)))
        assertNull(
            running.copy(status = BackgroundRuntimeStatus.STOPPED)
                .withObservedProcessIdentity(identity)
        )
    }

    @Test
    fun `partial or malformed persisted identity fails closed`() {
        val base = record().copy(status = BackgroundRuntimeStatus.RUNNING, pid = 321)

        assertNull(base.copy(processBootId = BOOT_ID).persistedProcessIdentityOrNull())
        assertNull(base.copy(processStartTicks = 987654L).persistedProcessIdentityOrNull())
        assertNull(
            base.copy(
                processBootId = "not-a-boot-id",
                processStartTicks = 987654L,
            ).persistedProcessIdentityOrNull()
        )
    }

    private fun record() = BackgroundRuntimeRecord(
        id = "background-test",
        spaceId = "space-test",
        kind = BackgroundRuntimeKind.CUSTOM,
        mode = BackgroundRuntimeMode.PROCESS,
        title = "test",
        workingDirectory = "/workspace",
        startCommand = "exec /bin/sleep 30",
        logPath = "/tmp/test.log",
        createdAt = 1L,
    )

    companion object {
        private const val BOOT_ID = "12345678-1234-4abc-8def-1234567890ab"
    }
}
