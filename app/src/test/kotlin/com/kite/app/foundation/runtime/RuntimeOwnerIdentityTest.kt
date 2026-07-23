package com.kite.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(55L, RuntimeOwnerIdentity.generation(owner.ownerId))
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

    @Test
    fun `根 owner 和步骤 owner 都能解析实例代次`() {
        val handle = RuntimeOwnerIdentity.step(
            RuntimeOwnerNamespace.Card,
            instanceId = "demo",
            generation = 1784086156860L,
            stepIndex = 0,
            stepId = "terminal",
            attemptId = 1L
        )

        assertEquals(1784086156860L, RuntimeOwnerIdentity.generation(handle.rootOwnerId))
        assertEquals(1784086156860L, RuntimeOwnerIdentity.generation(handle.ownerId))
        assertEquals(null, RuntimeOwnerIdentity.generation("terminal:legacy-session"))
    }

    @Test
    fun `后台运行项复用登记身份并通过统一 unit 通道分类`() {
        val handle = RuntimeOwnerIdentity.backgroundRuntime(
            runtimeId = "background-space-main-proot-capacity-worker-2",
            kind = "PROOT_CAPACITY_WORKER",
        )

        assertEquals("background-space-main-proot-capacity-worker-2", handle.rootOwnerId)
        assertEquals(handle.rootOwnerId, handle.ownerId)
        assertTrue(handle.unitId.startsWith("background:proot-capacity-worker:"))
        assertTrue(RuntimeOwnerIdentity.isBackgroundRuntime(handle.ownerId, handle.unitId))
        assertEquals(handle.ownerId, handle.environment()[RuntimeOwnerIdentity.RUNTIME_ID_ENV])
        assertEquals(handle.unitId, handle.environment()[RuntimeOwnerIdentity.UNIT_ID_ENV])
    }

    @Test
    fun `终端 owner 替代同一步骤同一次尝试的协议占位 owner`() {
        val provisional = RuntimeOwnerIdentity.step(
            namespace = RuntimeOwnerNamespace.Card,
            instanceId = "instance-a",
            generation = 100L,
            stepIndex = 0,
            stepId = "terminal-step",
            attemptId = 7L,
        )
        val actual = RuntimeOwnerIdentity.terminal(
            rootNamespace = RuntimeOwnerNamespace.Card,
            instanceId = "instance-a",
            generation = 100L,
            terminalSessionId = "terminal-a",
            stepIndex = 0,
            stepId = "terminal-step",
            attemptId = 7L,
        )

        assertTrue(RuntimeOwnerIdentity.supersedes(actual.ownerId, provisional.ownerId))
        assertTrue(RuntimeOwnerIdentity.belongsToRoot(actual.ownerId, provisional.rootOwnerId))
        assertTrue(RuntimeOwnerIdentity.belongsToRoot(provisional.ownerId, provisional.rootOwnerId))
        assertTrue(RuntimeOwnerIdentity.isRoot(provisional.rootOwnerId))
    }

    @Test
    fun `终端 owner 不会替代不同尝试或不同实例的步骤 owner`() {
        val actual = RuntimeOwnerIdentity.terminal(
            rootNamespace = RuntimeOwnerNamespace.Card,
            instanceId = "instance-a",
            generation = 100L,
            terminalSessionId = "terminal-a",
            stepIndex = 0,
            stepId = "terminal-step",
            attemptId = 7L,
        )
        val otherAttempt = RuntimeOwnerIdentity.step(
            namespace = RuntimeOwnerNamespace.Card,
            instanceId = "instance-a",
            generation = 100L,
            stepIndex = 0,
            stepId = "terminal-step",
            attemptId = 8L,
        )
        val otherInstance = RuntimeOwnerIdentity.step(
            namespace = RuntimeOwnerNamespace.Card,
            instanceId = "instance-b",
            generation = 100L,
            stepIndex = 0,
            stepId = "terminal-step",
            attemptId = 7L,
        )

        assertFalse(RuntimeOwnerIdentity.supersedes(actual.ownerId, otherAttempt.ownerId))
        assertFalse(RuntimeOwnerIdentity.supersedes(actual.ownerId, otherInstance.ownerId))
        assertFalse(RuntimeOwnerIdentity.belongsToRoot(actual.ownerId, otherInstance.rootOwnerId))
    }
}
