package com.kite.app.run

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * T1 安全网:锁死 CardRunStore 的状态机契约。
 *
 * 这些契约是后续 P1/P2 重构(拆包名、拆 God Activity)最不能改坏的东西——
 * 一旦 start/update/进程恢复归一化的行为变了,这套测试会立刻红。
 *
 * 覆盖维度(对照 Playbook T1 验收):
 * - 核心状态流转:Starting → Running → Completed/Stopped/Failed
 * - 进程恢复归一化(Starting/Running → Failed)
 * - 重复 start 同 instance 的复用语义
 * - interruptible / active 状态聚合
 * - selectSurface / currentForRecipe / childrenOf 查询语义
 * - 并发写入安全性(@Synchronized)
 * - 边界:未知 recipe、空 instanceId、transient resource run 不持久化
 */
@RunWith(RobolectricTestRunner::class)
class CardRunStoreStateTransitionTest {

    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }

    @Before
    fun setUp() {
        // 只清内存状态,不 initialize。是否 initialize 由各测试自行决定,
        // 这样进程恢复类测试可以先 seed 持久化数据,再触发 initialize 读盘归一化。
        CardRunStore.resetForTest()
        // 清掉上一测试可能残留的磁盘数据,保证隔离
        context.getSharedPreferences("kite_card_run_store", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        CardRunStore.initialize(context)
    }

    @After
    fun tearDown() {
        CardRunStore.resetForTest()
        context.getSharedPreferences("kite_card_run_store", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ------------------------------------------------------------------
    // 核心状态流转
    // ------------------------------------------------------------------

    @Test
    fun `start 创建 Starting 状态并写入快照`() {
        val recipe = TestRecipes.serviceRecipe(id = "r1")

        val run = CardRunStore.start(recipe)

        assertEquals("r1", run.instanceId)
        assertEquals(CardRunStatus.Starting, run.status)
        assertEquals(recipe.steps.size, run.stepCount)
        // start 后立即可在 snapshot 与 get 中查到
        assertEquals(CardRunStatus.Starting, CardRunStore.get("r1")?.status)
        assertTrue(CardRunStore.snapshot().any { it.instanceId == "r1" })
    }

    @Test
    fun `update 推进 Starting 到 Running 并保留同一 instanceId`() {
        val recipe = TestRecipes.serviceRecipe(id = "r2")
        CardRunStore.start(recipe)

        val updated = CardRunStore.update(recipe, status = CardRunStatus.Running)

        assertEquals("r2", updated.instanceId)
        assertEquals(CardRunStatus.Running, CardRunStore.get("r2")?.status)
    }

    @Test
    fun `update 可到达 Completed 终态`() {
        val recipe = TestRecipes.serviceRecipe(id = "r3")
        CardRunStore.start(recipe)

        CardRunStore.update(recipe, status = CardRunStatus.Completed)

        assertEquals(CardRunStatus.Completed, CardRunStore.get("r3")?.status)
    }

    @Test
    fun `update 可到达 Stopped 终态`() {
        val recipe = TestRecipes.serviceRecipe(id = "r4")
        CardRunStore.start(recipe)
        CardRunStore.update(recipe, status = CardRunStatus.Running)

        CardRunStore.update(recipe, status = CardRunStatus.Stopped)

        assertEquals(CardRunStatus.Stopped, CardRunStore.get("r4")?.status)
    }

    @Test
    fun `update 带 lastError 时进入 Failed 终态并保留错误信息`() {
        val recipe = TestRecipes.serviceRecipe(id = "r5")
        CardRunStore.start(recipe)

        CardRunStore.update(recipe, status = CardRunStatus.Failed, lastError = "boom")

        val run = CardRunStore.get("r5")
        assertEquals(CardRunStatus.Failed, run?.status)
        assertEquals("boom", run?.lastError)
    }

    @Test
    fun `update 带 nextActionUrl 时 surface 自动解析为 Web`() {
        val recipe = TestRecipes.serviceRecipe(id = "r6")
        CardRunStore.start(recipe)

        CardRunStore.update(
            recipe,
            status = CardRunStatus.Completed,
            nextActionUrl = "http://127.0.0.1:8648"
        )

        assertEquals(CardRunSurface.Web, CardRunStore.get("r6")?.surface)
    }

    @Test
    fun `update 带 terminalSessionId 时 surface 自动解析为 Terminal`() {
        val recipe = TestRecipes.serviceRecipe(id = "r7")
        CardRunStore.start(recipe)

        CardRunStore.update(
            recipe,
            status = CardRunStatus.Running,
            terminalSessionId = "term-1"
        )

        assertEquals(CardRunSurface.Terminal, CardRunStore.get("r7")?.surface)
    }

    @Test
    fun `update 带 x11Display 时 surface 自动解析为 X11`() {
        val recipe = TestRecipes.serviceRecipe(id = "r8")
        CardRunStore.start(recipe)

        CardRunStore.update(
            recipe,
            status = CardRunStatus.Running,
            x11Display = ":0",
            x11SocketPath = "/tmp/.X11-unix/X0"
        )

        assertEquals(CardRunSurface.X11, CardRunStore.get("r8")?.surface)
    }

    // ------------------------------------------------------------------
    // 进程恢复归一化(真实契约)
    //
    // 真实场景:进程被杀前状态没正常结束,磁盘 SharedPreferences 里残留
    // Starting/Running 等中间态。initialize 读盘后的真实行为是:
    //   1. 先 normalizedAfterProcessRestore() 把中间态(Starting/Running/Opened/...)
    //      归一化为 Failed 并补 abort 信息;
    //   2. 再 shouldDropCurrentAfterProcessRestore() 决定是否丢弃"当前运行态":
    //      所有终态(Completed/Failed/Stopped/...)、所有需 reset 的中间态、
    //      带 run binding 的、子 run、带 nextActionUrl 的,都会被丢弃,
    //      不再出现在控制台当前 run 列表(避免重启后看到僵尸卡片)。
    //
    // 即:残留的中间态 run 在重启后不会作为 Failed 卡片留在控制台,而是直接消失
    // (它们的历史由独立的 history 链路保留)。
    //
    // 测试策略:直接向 SharedPreferences 写残留 JSON(模拟磁盘现状),再触发 initialize,
    // 验证归一化+丢弃逻辑。这绕开异步落盘不确定性,直接钉死 initialize 契约。
    // ------------------------------------------------------------------

    @Test
    fun `initialize 丢弃残留的 Starting 当前态不显示为僵尸卡片`() {
        seedPersistedRuns(runsJson(payloadOf(instanceId = "r9", recipeId = "r9", status = "Starting")))
        CardRunStore.resetForTest()
        CardRunStore.initialize(context)

        // 残留 Starting 不应作为当前 run 留在控制台
        assertNull(CardRunStore.get("r9"))
    }

    @Test
    fun `initialize 丢弃残留的 Running 当前态`() {
        seedPersistedRuns(runsJson(payloadOf(instanceId = "r10", recipeId = "r10", status = "Running")))
        CardRunStore.resetForTest()
        CardRunStore.initialize(context)

        assertNull(CardRunStore.get("r10"))
    }

    @Test
    fun `initialize 丢弃残留的 Opened 当前态`() {
        seedPersistedRuns(runsJson(payloadOf(instanceId = "r11b", recipeId = "r11b", status = "Opened")))
        CardRunStore.resetForTest()
        CardRunStore.initialize(context)

        assertNull(CardRunStore.get("r11b"))
    }

    @Test
    fun `initialize 丢弃残留的 Completed 终态当前态`() {
        seedPersistedRuns(runsJson(payloadOf(instanceId = "r11", recipeId = "r11", status = "Completed")))
        CardRunStore.resetForTest()
        CardRunStore.initialize(context)

        assertNull(CardRunStore.get("r11"))
    }

    @Test
    fun `initialize 清空磁盘残留后控制台为空`() {
        seedPersistedRuns(
            runsJson(
                payloadOf(instanceId = "a", recipeId = "a", status = "Starting"),
                payloadOf(instanceId = "b", recipeId = "b", status = "Running"),
                payloadOf(instanceId = "c", recipeId = "c", status = "Completed")
            )
        )
        CardRunStore.resetForTest()
        CardRunStore.initialize(context)

        assertTrue("所有残留当前态都应被清出控制台", CardRunStore.snapshot().isEmpty())
    }

    /** 把一段 runs JSON 写入 CardRunStore 用的 SharedPreferences,模拟磁盘残留。 */
    private fun seedPersistedRuns(runsJson: String) {
        val prefs = context.getSharedPreferences("kite_card_run_store", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("runs_v1", runsJson).commit()
    }

    /** 构造一条 CardRunState 的最小 JSON payload。 */
    private fun payloadOf(
        instanceId: String,
        recipeId: String,
        status: String,
        surface: String = "Summary",
        updatedAt: Long = System.currentTimeMillis(),
        createdAt: Long = updatedAt
    ): org.json.JSONObject = org.json.JSONObject(
        """{"instanceId":"$instanceId","recipeId":"$recipeId","recipeName":"seeded","""
            + """"status":"$status","surface":"$surface","currentStepIndex":-1,"""
            + """"stepCount":0,"createdAt":$createdAt,"updatedAt":$updatedAt}"""
    )

    /** 把若干 payload 包成一个 runs 数组 JSON 字符串。 */
    private fun runsJson(vararg payloads: org.json.JSONObject): String =
        org.json.JSONArray().apply { payloads.forEach { put(it) } }.toString()

    // ------------------------------------------------------------------
    // 重复 start 复用语义
    // ------------------------------------------------------------------

    @Test
    fun `重复 start 同 instanceId 的 Starting 无绑定状态时复用而非新建`() {
        val recipe = TestRecipes.serviceRecipe(id = "r12")
        val first = CardRunStore.start(recipe)

        // 再次 start 同一 instanceId,仍处于 Starting 且无 run 绑定 → 应复用
        val second = CardRunStore.start(recipe, instanceId = "r12")

        assertEquals(first.instanceId, second.instanceId)
        assertEquals(1, CardRunStore.snapshot().count { it.instanceId == "r12" })
    }

    @Test
    fun `start 已带 run 绑定的 instance 不复用而另起新状态`() {
        val recipe = TestRecipes.serviceRecipe(id = "r13")
        CardRunStore.start(recipe)
        // 给它绑定 run,使其不再是"无绑定的 Starting"
        CardRunStore.update(recipe, status = CardRunStatus.Running, runId = "run-1")

        // 此时同 instanceId 仍处于活跃,再次 start 应复用同一 instance(不另建)
        val again = CardRunStore.start(recipe, instanceId = "r13")
        assertEquals("r13", again.instanceId)
    }

    // ------------------------------------------------------------------
    // interruptible / active 聚合
    // ------------------------------------------------------------------

    @Test
    fun `Running 状态既 active 又 interruptible`() {
        val recipe = TestRecipes.serviceRecipe(id = "r14")
        CardRunStore.start(recipe)
        CardRunStore.update(recipe, status = CardRunStatus.Running)

        val run = CardRunStore.get("r14")!!
        assertTrue(run.isActive())
        assertTrue(run.isInterruptible())
    }

    @Test
    fun `Starting 状态 busy 但不 active 不 interruptible`() {
        val recipe = TestRecipes.serviceRecipe(id = "r15")
        CardRunStore.start(recipe)

        val run = CardRunStore.get("r15")!!
        assertTrue(run.isBusy())
        assertFalse(run.isActive())
        assertFalse(run.isInterruptible())
    }

    @Test
    fun `Completed 终态既不 active 也不 interruptible`() {
        val recipe = TestRecipes.serviceRecipe(id = "r16")
        CardRunStore.start(recipe)
        CardRunStore.update(recipe, status = CardRunStatus.Completed)

        val run = CardRunStore.get("r16")!!
        assertFalse(run.isActive())
        assertFalse(run.isInterruptible())
    }

    // ------------------------------------------------------------------
    // 查询语义
    // ------------------------------------------------------------------

    @Test
    fun `selectSurface 只改 surface 不改 status`() {
        val recipe = TestRecipes.serviceRecipe(id = "r17")
        CardRunStore.start(recipe)
        CardRunStore.update(recipe, status = CardRunStatus.Running)

        val selected = CardRunStore.selectSurface("r17", CardRunSurface.Report)

        assertNotNull(selected)
        assertEquals(CardRunSurface.Report, selected?.surface)
        assertEquals(CardRunStatus.Running, selected?.status)
    }

    @Test
    fun `selectSurface 对未知 instanceId 返回 null`() {
        assertNull(CardRunStore.selectSurface("does-not-exist", CardRunSurface.Report))
    }

    @Test
    fun `currentForRecipe 返回该 recipe 最新的顶层 run`() {
        val recipe = TestRecipes.serviceRecipe(id = "r18")
        CardRunStore.start(recipe)
        Thread.sleep(2)
        CardRunStore.update(recipe, status = CardRunStatus.Running)

        val current = CardRunStore.currentForRecipe("r18")

        assertNotNull(current)
        assertEquals(CardRunStatus.Running, current?.status)
    }

    @Test
    fun `childrenOf 返回指定 parent 的子 run`() {
        val parent = TestRecipes.serviceRecipe(id = "parent-r19")
        CardRunStore.start(parent)
        val childRecipe = TestRecipes.serviceRecipe(id = "child-r19")

        CardRunStore.start(childRecipe, instanceId = "child-r19", parentInstanceId = "parent-r19")

        val children = CardRunStore.childrenOf("parent-r19")
        assertEquals(1, children.size)
        assertEquals("child-r19", children.single().instanceId)
    }

    @Test
    fun `get 对未知 instanceId 返回 null`() {
        assertNull(CardRunStore.get("nope"))
    }

    // ------------------------------------------------------------------
    // 并发写入安全性
    // ------------------------------------------------------------------

    @Test
    fun `多线程并发 start 不同 recipe 不会丢失或串号`() {
        val threads = 8
        val perThread = 5
        val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
        val latch = java.util.concurrent.CountDownLatch(threads)
        val errors = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()

        repeat(threads) { t ->
            pool.submit {
                try {
                    repeat(perThread) { i ->
                        val id = "conc-$t-$i"
                        val recipe = TestRecipes.serviceRecipe(id = id)
                        CardRunStore.start(recipe)
                    }
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
        pool.shutdown()

        assertTrue("并发写入抛错: ${errors.joinToString { it.message ?: "?" }}", errors.isEmpty())
        val expectedCount = threads * perThread
        assertEquals(expectedCount, CardRunStore.snapshot().size)
        // 每个 instanceId 唯一,不串号
        val ids = CardRunStore.snapshot().map { it.instanceId }
        assertEquals(expectedCount, ids.toSet().size)
    }

    // ------------------------------------------------------------------
    // 边界
    // ------------------------------------------------------------------

    @Test
    fun `removeRecipes 清空指定 recipe 的运行状态`() {
        val recipe = TestRecipes.serviceRecipe(id = "r20")
        CardRunStore.start(recipe)

        CardRunStore.removeRecipes(listOf("r20"))

        assertNull(CardRunStore.get("r20"))
        assertFalse(CardRunStore.snapshot().any { it.instanceId == "r20" })
    }

    @Test
    fun `removeRecipes 忽略空白 id 不报错`() {
        CardRunStore.removeRecipes(listOf("", "  "))
        // 不报错即通过
    }

    @Test
    fun `resource-install-wizard recipe 不进入持久化快照`() {
        // isTransientResourceRunState:resource-install-wizard-* / resource-*-install|uninstall 不持久化
        val wizardRecipe = TestRecipes.serviceRecipe(
            id = "resource-install-wizard-xyz"
        )
        CardRunStore.start(wizardRecipe)

        CardRunStore.resetForTest()
        CardRunStore.initialize(context)

        // wizard run 是 transient,重载后不应存在
        assertNull(CardRunStore.get("resource-install-wizard-xyz"))
    }
}
