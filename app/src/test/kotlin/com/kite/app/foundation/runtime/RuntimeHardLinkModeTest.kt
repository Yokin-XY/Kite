package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeHardLinkModeTest {
    @Test
    fun nativeModeOnlyRemovesLinkEmulationFlag() {
        val command = listOf("proot", "--link2symlink", "-0", "-r", "/rootfs", "traecli")

        assertEquals(command, RuntimeHardLinkMode.EMULATED.applyToProotCommand(command))
        assertEquals(
            listOf("proot", "-0", "-r", "/rootfs", "traecli"),
            RuntimeHardLinkMode.NATIVE.applyToProotCommand(command),
        )
    }
}
