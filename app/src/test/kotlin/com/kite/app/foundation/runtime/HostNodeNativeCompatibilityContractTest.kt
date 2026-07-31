package com.kite.app.foundation.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HostNodeNativeCompatibilityContractTest {
    @Test
    fun `syscall forwarding preserves caller registers in arm64 assembly`() {
        val cSource = source("native/kite-node-host/kite-node-glibc-compat.c").readText()
        val assembly = source("native/kite-node-host/kite-node-glibc-syscall-arm64.S").readText()

        assertFalse(cSource.contains("va_arg("))
        assertFalse(cSource.contains("long syscall("))
        assertTrue(assembly.contains(".global syscall"))
        assertTrue(assembly.contains("br x9"))
        assertTrue(assembly.contains("kite_real_syscall"))
        assertTrue(assembly.contains("kite_syscall_enosys"))
    }

    @Test
    fun `robust mutex requests fail explicitly instead of inheriting fake support`() {
        val cSource = source("native/kite-node-host/kite-node-glibc-compat.c").readText()

        assertTrue(cSource.contains("pthread_mutexattr_setrobust"))
        assertTrue(cSource.contains("PTHREAD_MUTEX_ROBUST"))
        assertTrue(cSource.contains("return ENOTSUP"))
    }

    private fun source(path: String): File = File(path).takeIf(File::isFile)
        ?: File("../$path").takeIf(File::isFile)
        ?: error("missing source fixture: $path")
}
