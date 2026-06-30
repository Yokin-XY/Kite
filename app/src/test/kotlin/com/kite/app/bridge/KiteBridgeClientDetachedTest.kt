package com.kite.app.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T2:detached 启动接受判定的契约测试。
 *
 * detachedStartAccepted 是 bridge 对"后台已拉起进程"的判定:必须有 pid,
 * 且要么正常退出(exitCode 0),要么还在运行(timedOut 表示 bridge 因超时返回但进程已起)。
 * 这是 detached 模式的核心安全契约——没拿到 pid 绝不能假装成功。
 */
class KiteBridgeClientDetachedTest {

    @Test
    fun `detached 超时但有 pid 应判为 accepted`() {
        assertTrue(detachedStartAccepted(timedOut = true, exitCode = -1, pid = "10265"))
    }

    @Test
    fun `detached 超时但无 pid 不应判为 accepted`() {
        assertFalse(detachedStartAccepted(timedOut = true, exitCode = -1, pid = null))
    }

    @Test
    fun `detached 正常退出 exitCode 0 且有 pid 应判为 accepted`() {
        assertTrue(detachedStartAccepted(timedOut = false, exitCode = 0, pid = "10265"))
    }

    @Test
    fun `detached 正常退出 exitCode 0 但无 pid 不应判为 accepted`() {
        // 即使退出码是 0,没拿到 pid 也无法后续管理进程,不算 accepted
        assertFalse(detachedStartAccepted(timedOut = false, exitCode = 0, pid = null))
    }

    @Test
    fun `detached 非零退出且未超时不应判为 accepted`() {
        assertFalse(detachedStartAccepted(timedOut = false, exitCode = 1, pid = "10265"))
    }

    @Test
    fun `detached 空白 pid 视同无 pid`() {
        assertFalse(detachedStartAccepted(timedOut = true, exitCode = -1, pid = ""))
        assertFalse(detachedStartAccepted(timedOut = true, exitCode = -1, pid = "   "))
    }
}
