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

        // syscall() 转发必须留在汇编层（C 变参包装会读不存在的参数，UB）。
        assertFalse(cSource.contains("long syscall("))
        assertTrue(assembly.contains(".global syscall"))
        assertTrue(assembly.contains("br x9"))
        assertTrue(assembly.contains("kite_real_syscall"))
        assertTrue(assembly.contains("kite_syscall_enosys"))
        // 路径型 syscall 进 C 重写层（Rust/静态二进制 inline svc 之外的调用面），
        // C 侧按号精确转发。
        assertTrue(assembly.contains("kite_syscall_path"))
        assertTrue(cSource.contains("long kite_syscall_path(long number, long a1, long a2, long a3, long a4, long a5)"))
        assertTrue(cSource.contains("SYS_mkdirat"))
        assertTrue(cSource.contains("SYS_openat2"))
    }

    @Test
    fun `variadic open family extracts mode only behind O_CREAT gate`() {
        val cSource = source("native/kite-glibc-host/kite-glibc-compat.c").readText()

        // open/open64/openat/openat64 的 va_list 仅在 O_CREAT 门控内提取，且按 int
        // 提升读回后 cast 到 mode_t（窄类型直接 va_arg 是 UB）。
        val gated = Regex("if \\(flags & O_CREA\\w*\\) \\{\\s*va_list").findAll(cSource).count()
        assertTrue("O_CREAT gated va_list expected at least 4, got $gated", gated >= 4)
        assertTrue(cSource.contains("va_arg(arguments, int)"))
        assertFalse(cSource.contains("va_arg(arguments, mode_t)"))
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
