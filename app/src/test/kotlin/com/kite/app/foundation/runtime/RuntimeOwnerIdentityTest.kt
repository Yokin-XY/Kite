package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeOwnerIdentityTest {
    @Test
    fun `同一实例不同代次不会复用 owner`() {
        val first = RuntimeOwnerIdentity.step(
            RuntimeOwnerNamespace.Card,
            instanceId = "demo",
            generation = 100L,
            stepIndex = 0,
            stepId = "start",
            attemptId = 1L
        )
        val second = RuntimeOwnerIdentity.step(
            RuntimeOwnerNamespace.Card,
            instanceId = "demo",
            generation = 101L,
            stepIndex = 0,
            stepId = "start",
            attemptId = 1L
        )

        assertNotEquals(first.rootOwnerId, second.rootOwnerId)
        assertNotEquals(first.ownerId, second.ownerId)
    }

    @Test
    fun `同一代次重新执行步骤会生成不同叶子`() {
        val first = RuntimeOwnerIdentity.step(
            RuntimeOwnerNamespace.Resource,
            "resource-install",
            100L,
            2,
            "download",
            7L
        )
        val retried = RuntimeOwnerIdentity.step(
            RuntimeOwnerNamespace.Resource,
            "resource-install",
            100L,
            2,
            "download",
            8L
        )

        assertEquals(first.rootOwnerId, retried.rootOwnerId)
        assertNotEquals(first.ownerId, retried.ownerId)
        assertTrue(first.ownerId.startsWith("resource:"))
    }

    @Test
    fun `终端 owner 保留可解析会话并携带实例代次`() {
        val owner = RuntimeOwnerIdentity.terminal(
            RuntimeOwnerNamespace.Card,
            instanceId = "instance-a",
            generation = 55L,
            terminalSessionId = "terminal-123",
            stepIndex = 1,
            stepId = "terminal",
            attemptId = 9L
        )

        assertEquals("terminal-123", RuntimeOwnerIdentity.terminalSessionId(owner.ownerId))
        assertTrue(owner.ownerId.contains("instance/instance-a@55"))
        assertEquals(owner.ownerId, owner.environment()[RuntimeOwnerIdentity.RUNTIME_ID_ENV])
        assertEquals(owner.unitId, owner.environment()[RuntimeOwnerIdentity.UNIT_ID_ENV])
    }

    @Test
    fun `超长和含空格身份会稳定收敛为安全 token`() {
        val raw = "中文 实例/" + "x".repeat(120)
        val first = RuntimeOwnerIdentity.root(RuntimeOwnerNamespace.Card, raw, 1L)
        val second = RuntimeOwnerIdentity.root(RuntimeOwnerNamespace.Card, raw, 1L)

        assertEquals(first, second)
        assertTrue(first.none(Char::isWhitespace))
        assertTrue(first.length < 100)
    }
}
