package com.kite.app.foundation.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HostNodeNativeCompatibilityContractTest {
    @Test
    fun `syscall tracer degrades openat2 with two-phase singlestep injection`() {
        val tracerSource = source("native/kite-glibc-host/kite-syscall-tracer.c").readText()
        // 1) PTRACE_O_TRACESECCOMP 必须在 options 里——不设它，SECCOMP_RET_TRACE
        //    命中时内核直接给 ENOSYS、不通知 tracer（4.19 真机实证）。
        assertTrue(tracerSource.contains("PTRACE_O_TRACESECCOMP"))
        // 2) 本机 4.19 OEM 内核不采纳 seccomp stop 中修改的 syscall 号，必须走
        //    PC 注入 + 两段 SINGLESTEP（而非只改号）。
        assertTrue(tracerSource.contains("PTRACE_SINGLESTEP"))
        assertTrue(tracerSource.contains("stage"))
        // 3) 内核完成缓存的 openat2(ENOSYS) 时 x0 会被写坏，第二段单步前必须
        //    恢复原始 dirfd（orig_x0）。
        assertTrue(tracerSource.contains("orig_x0"))
        // 4) seccomp 过滤器只拦 openat2，其余放行（零常态开销）。
        assertTrue(tracerSource.contains("__NR_openat2"))
        assertTrue(tracerSource.contains("SECCOMP_RET_TRACE"))
    }

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
