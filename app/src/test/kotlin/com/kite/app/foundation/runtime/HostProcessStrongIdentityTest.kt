package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostProcessStrongIdentityTest {
    @Test
    fun `proc stat parser reads start ticks after command containing right parenthesis`() {
        val raw = "321 (worker ) name) S 1 200 300 0 0 0 0 0 0 0 11 13 0 0 0 0 1 0 987654"

        val parsed = parseHostProcStat(raw)

        assertEquals(200, parsed?.processGroupId)
        assertEquals(300, parsed?.sessionId)
        assertEquals(24L, parsed?.cpuTimeTicks)
        assertEquals(987654L, parsed?.processStartTicks)
    }

    @Test
    fun `invalid or incomplete proc stat cannot manufacture start identity`() {
        assertNull(parseHostProcStat("321 worker S 1 2"))
        assertNull(parseHostProcStat("321 (worker) S 1 2"))
        assertNull(
            parseHostProcStat(
                "321 (worker) S 1 200 300 0 0 0 0 0 0 0 11 13 0 0 0 0 1 0 0"
            )?.processStartTicks
        )
    }

    @Test
    fun `boot id is canonicalized and malformed values fail closed`() {
        assertEquals(BOOT_ID, normalizeHostBootId("  ${BOOT_ID.uppercase()}\n"))
        assertNull(normalizeHostBootId(""))
        assertNull(normalizeHostBootId("not-a-boot-id"))
    }

    @Test
    fun `snapshot yields identity only for app process with boot and start ticks`() {
        val process = HostProcessRecord(
            user = "u0_a123",
            pid = 321,
            parentPid = 1,
            rawState = "S",
            command = "proot",
            commandLine = "proot /bin/bash",
            processStartTicks = 987654L,
        )
        val snapshot = HostProcessSnapshot(
            allProcesses = listOf(process),
            appUser = "u0_a123",
            bootId = BOOT_ID,
        )

        assertEquals(
            HostProcessIdentityObservation(BOOT_ID, 321, 987654L),
            snapshot.strongIdentity(321),
        )
        assertNull(snapshot.strongIdentity(999))
        assertNull(
            HostProcessSnapshot(listOf(process), "u0_a123", bootId = null)
                .strongIdentity(321)
        )
        assertNull(
            HostProcessSnapshot(
                listOf(process.copy(processStartTicks = null)),
                "u0_a123",
                bootId = BOOT_ID,
            ).strongIdentity(321)
        )
    }

    companion object {
        private const val BOOT_ID = "12345678-1234-4abc-8def-1234567890ab"
    }
}
