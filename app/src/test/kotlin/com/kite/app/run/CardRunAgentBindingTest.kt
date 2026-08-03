package com.kite.app.run

import androidx.test.core.app.ApplicationProvider
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.store.AgentConversationKey
import com.kite.app.agent.store.AgentConversationStore
import com.kite.app.feature.runsurface.RunSurfaceProjector
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CardRunAgentBindingTest {
    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }

    @Before
    fun setUp() {
        AgentConversationStore.resetForTest()
        CardRunStore.resetForTest()
        context.getSharedPreferences("kite_card_run_store", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        CardRunStore.initialize(context)
    }

    @After
    fun tearDown() {
        AgentConversationStore.resetForTest()
        CardRunStore.resetForTest()
        context.getSharedPreferences("kite_card_run_store", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `Agent 绑定只接受同一运行代次并保留轻量字段`() {
        val recipe = TestRecipes.serviceRecipe("agent-binding")
        val run = CardRunStore.start(recipe)

        val preparing = CardRunStore.updateAgentBinding(
            instanceId = run.instanceId,
            expectedGeneration = run.createdAt,
            providerId = "opencode",
            status = CardRunAgentConnectionStatus.Preparing,
            statusMessage = "正在连接"
        )
        val ready = CardRunStore.updateAgentBinding(
            instanceId = run.instanceId,
            expectedGeneration = run.createdAt,
            sessionId = "session-1",
            status = CardRunAgentConnectionStatus.Ready
        )
        val stale = CardRunStore.updateAgentBinding(
            instanceId = run.instanceId,
            expectedGeneration = run.createdAt - 1,
            sessionId = "stale-session",
            status = CardRunAgentConnectionStatus.Failed
        )

        assertEquals("opencode", preparing?.agentBinding?.providerId)
        assertEquals("session-1", ready?.agentBinding?.sessionId)
        assertEquals(CardRunAgentConnectionStatus.Ready, ready?.agentBinding?.status)
        assertNull(ready?.agentBinding?.statusMessage)
        assertNull(stale)
        assertEquals("session-1", CardRunStore.get(run.instanceId)?.agentBinding?.sessionId)
        assertTrue(CardRunStore.get(run.instanceId)?.hasRunBinding() == true)
    }

    @Test
    fun `进程恢复保留 Agent 身份但首页当前态回到中性停止`() {
        val recipe = TestRecipes.serviceRecipe("agent-restore")
        val run = CardRunStore.start(recipe, agentId = "opencode")
        CardRunStore.update(
            recipe = recipe,
            status = CardRunStatus.Running,
            runId = "run-1",
            pid = "123"
        )
        CardRunStore.updateAgentBinding(
            instanceId = run.instanceId,
            expectedGeneration = run.createdAt,
            providerId = "opencode",
            sessionId = "session-restore",
            status = CardRunAgentConnectionStatus.Ready
        )

        Thread.sleep(450)
        CardRunStore.resetForTest()
        CardRunStore.initialize(context)

        val restored = CardRunStore.get(run.instanceId)
        assertEquals(CardRunStatus.Stopped, restored?.status)
        assertEquals(CardRunAgentConnectionStatus.Disconnected, restored?.agentBinding?.status)
        assertEquals("opencode", restored?.agentId)
        assertEquals("opencode", restored?.agentBinding?.providerId)
        assertEquals("session-restore", restored?.agentBinding?.sessionId)
        assertNull(restored?.runId)
        assertNull(restored?.pid)
        assertNull(restored?.lastError)
        assertTrue(restored?.agentBinding?.statusMessage?.contains("重新连接") == true)
        assertEquals(KiteRunPrimaryAction.Start, KiteCardRunUiProjector.project(restored!!.status).primaryAction)
        assertFalse(KiteCardRunUiProjector.project(restored.status).problem)
        assertEquals(CardRunStatus.Failed, CardRunStore.historyForRecipe(recipe.id).first().status)
    }

    @Test
    fun `旧版本留下的 Agent 重启失败态在升级后回到中性停止`() {
        val recipe = TestRecipes.serviceRecipe("agent-restore-migration")
        val run = CardRunStore.start(recipe, agentId = "opencode")
        CardRunStore.updateAgentBinding(
            instanceId = run.instanceId,
            expectedGeneration = run.createdAt,
            providerId = "opencode",
            sessionId = "session-migration",
            status = CardRunAgentConnectionStatus.Disconnected,
            statusMessage = "Kite 重新启动，需要重新连接 Agent 会话"
        )
        CardRunStore.update(
            recipe = recipe,
            status = CardRunStatus.Failed,
            lastError = "Kite 重新启动，需要重新连接 Agent 会话"
        )

        Thread.sleep(450)
        CardRunStore.resetForTest()
        CardRunStore.initialize(context)

        val restored = CardRunStore.get(run.instanceId)
        assertEquals(CardRunStatus.Stopped, restored?.status)
        assertEquals("session-migration", restored?.agentBinding?.sessionId)
        assertNull(restored?.lastError)
    }

    @Test
    fun `同一 Agent 再次启动时把原生会话引用带入新运行代次`() {
        val recipe = TestRecipes.serviceRecipe("agent-reopen")
        val first = CardRunStore.start(recipe, agentId = "opencode")
        CardRunStore.updateAgentBinding(
            instanceId = first.instanceId,
            expectedGeneration = first.createdAt,
            providerId = "opencode",
            sessionId = "session-reopen",
            status = CardRunAgentConnectionStatus.Disconnected
        )
        CardRunStore.update(
            recipe = recipe,
            status = CardRunStatus.Failed,
            instanceId = first.instanceId,
            lastError = "等待重新连接"
        )

        val reopened = CardRunStore.start(recipe, agentId = "opencode")

        assertTrue(reopened.createdAt >= first.createdAt)
        assertEquals("opencode", reopened.agentId)
        assertEquals("opencode", reopened.agentBinding?.providerId)
        assertEquals("session-reopen", reopened.agentBinding?.sessionId)
        assertEquals(CardRunAgentConnectionStatus.Disconnected, reopened.agentBinding?.status)
    }

    @Test
    fun `清理 Agent 绑定不影响同一运行的其他事实`() {
        val recipe = TestRecipes.serviceRecipe("agent-clear")
        val run = CardRunStore.start(recipe, agentId = "opencode")
        CardRunStore.update(recipe, CardRunStatus.Running, lastMeaningfulOutput = "后台已启动")
        CardRunStore.updateAgentBinding(
            instanceId = run.instanceId,
            expectedGeneration = run.createdAt,
            providerId = "opencode",
            status = CardRunAgentConnectionStatus.Preparing
        )

        val cleared = CardRunStore.updateAgentBinding(
            instanceId = run.instanceId,
            expectedGeneration = run.createdAt,
            status = CardRunAgentConnectionStatus.Stopped,
            clear = true
        )

        assertNull(cleared?.agentBinding)
        assertEquals("opencode", cleared?.agentId)
        assertEquals(CardRunStatus.Running, cleared?.status)
        assertEquals("后台已启动", cleared?.lastMeaningfulOutput)
    }

    @Test
    fun `清理进程事实时保留本次明确写入的 Agent 失败原因`() {
        val recipe = TestRecipes.serviceRecipe("agent-failure")
        val run = CardRunStore.start(recipe, agentId = "example-agent")
        CardRunStore.update(
            recipe = recipe,
            status = CardRunStatus.Running,
            instanceId = run.instanceId,
            runtimeRootOwnerId = "card:${run.instanceId}@${run.createdAt}",
            runtimeOwnerId = "card:${run.instanceId}@${run.createdAt}/step/0-agent",
            runtimeUnitId = "agent",
            pid = "123"
        )

        val failed = CardRunStore.update(
            recipe = recipe,
            status = CardRunStatus.Failed,
            instanceId = run.instanceId,
            lastError = "请先登录 Example Agent",
            agentBinding = CardRunAgentBinding(
                providerId = "example-provider",
                status = CardRunAgentConnectionStatus.Failed,
                statusMessage = "请先登录 Example Agent"
            ),
            clearRunBinding = true
        )

        assertNull(failed.runtimeRootOwnerId)
        assertNull(failed.runtimeOwnerId)
        assertNull(failed.runtimeUnitId)
        assertNull(failed.pid)
        assertEquals(CardRunAgentConnectionStatus.Failed, failed.agentBinding?.status)
        assertEquals(
            "请先登录 Example Agent",
            failed.agentBinding?.statusMessage
        )
    }

    @Test
    fun `运行更新不能把已经固定的 Agent 身份改成其他 Agent`() {
        val recipe = TestRecipes.serviceRecipe("agent-identity")
        val run = CardRunStore.start(recipe, agentId = "opencode")

        val updated = CardRunStore.update(
            recipe = recipe,
            status = CardRunStatus.Failed,
            instanceId = run.instanceId,
            agentId = "other-agent",
            lastError = "错误的迟到回写"
        )

        assertEquals("opencode", updated.agentId)
    }

    @Test
    fun `高频会话更新不改 CardRun 事实或显示面结构身份`() {
        val recipe = TestRecipes.serviceRecipe("agent-pressure")
        val run = CardRunStore.start(recipe)
        val bound = CardRunStore.updateAgentBinding(
            instanceId = run.instanceId,
            expectedGeneration = run.createdAt,
            providerId = "opencode",
            sessionId = "session-pressure",
            status = CardRunAgentConnectionStatus.Ready
        )!!
        val key = AgentConversationKey("opencode", "session-pressure")
        AgentConversationStore.bind(run.instanceId, key, AgentSessionPhase.Prompting)
        val structureBefore = RunSurfaceProjector.project(recipe, bound).structureKey
        val runUpdatedAtBefore = bound.updatedAt

        repeat(1_000) {
            AgentConversationStore.applyEvent(
                key,
                AgentSessionEvent.MessageChunk(
                    role = AgentMessageRole.Assistant,
                    content = AgentContent.Text("流"),
                    messageId = "message-pressure"
                )
            )
        }
        AgentConversationStore.flushForTest()

        val unchangedRun = CardRunStore.get(run.instanceId)!!
        val structureAfter = RunSurfaceProjector.project(recipe, unchangedRun).structureKey
        assertEquals(runUpdatedAtBefore, unchangedRun.updatedAt)
        assertEquals(structureBefore, structureAfter)
        assertEquals(1_000L, AgentConversationStore.snapshot(key)?.revision)
    }
}
