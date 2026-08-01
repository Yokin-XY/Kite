package com.kite.app.foundation.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HostNodeNativeCompatibilityContractTest {
    @Test
    fun `syscall forwarding preserves caller registers in arm64 assembly`() {
        val cSource = source("native/kite-glibc-host/kite-glibc-compat.c").readText()
        val assembly = source("native/kite-glibc-host/kite-glibc-syscall-arm64.S").readText()

        assertFalse(cSource.contains("va_arg("))
        assertFalse(cSource.contains("long syscall("))
        assertTrue(assembly.contains(".global syscall"))
        assertTrue(assembly.contains("br x9"))
        assertTrue(assembly.contains("kite_real_syscall"))
        assertTrue(assembly.contains("kite_syscall_enosys"))
    }

    @Test
    fun `robust mutex requests fail explicitly instead of inheriting fake support`() {
        val cSource = source("native/kite-glibc-host/kite-glibc-compat.c").readText()

        assertTrue(cSource.contains("pthread_mutexattr_setrobust"))
        assertTrue(cSource.contains("PTHREAD_MUTEX_ROBUST"))
        assertTrue(cSource.contains("return ENOTSUP"))
    }

    @Test
    fun `generic launcher accepts one explicit target and no shell`() {
        val source = source("native/kite-glibc-host/kite-glibc-host-launcher.c").readText()

        assertTrue(source.contains("KITE_GLIBC_HOST_TARGET"))
        assertTrue(source.contains("execv(loader, loader_argv)"))
        assertFalse(source.contains("system("))
        assertFalse(source.contains("/bin/sh"))
    }

    private fun source(path: String): File = File(path).takeIf(File::isFile)
        ?: File("../$path").takeIf(File::isFile)
        ?: error("missing source fixture: $path")
}
