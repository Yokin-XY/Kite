package com.kite.app.platform.runs

import android.app.Application
import com.kite.app.application.runs.RunCommandResult
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteLaunchConfig
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunStore
import com.kite.app.run.CardRunSurface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidRunNotificationCoordinatorTest {
    private lateinit var application: Application
    private lateinit var coordinatorJob: Job
    private lateinit var coordinatorScope: CoroutineScope

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("kite_card_run_store", Application.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        CardRunStore.resetForTest()
        CardRunStore.initialize(application)
        coordinatorJob = SupervisorJob()
        coordinatorScope = CoroutineScope(coordinatorJob + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        runBlocking { coordinatorJob.cancelAndJoin() }
        CardRunStore.resetForTest()
    }

    @Test
    fun `关闭只接受当前代次并进入正式停止入口`() {
        val recipe = recipe(keepFinishedNotification = false)
        val waiting = waitingTerminal(recipe)
        val closeCalls = AtomicInteger(0)
        val closeTaskCalls = AtomicInteger(0)
        val coordinator = coordinator(
            runtimeRecipe = recipe,
            latestRecipe = recipe,
            closeRun = { _, _ ->
                closeCalls.incrementAndGet()
                RunCommandResult.Accepted(waiting.instanceId)
            },
            closeTask = { _, _ -> closeTaskCalls.incrementAndGet() }
        )

        val staleFinished = CountDownLatch(1)
        coordinator.handleClose(waiting.instanceId, waiting.createdAt - 1, staleFinished::countDown)
        assertTrue(staleFinished.await(2, TimeUnit.SECONDS))
        assertEquals(0, closeCalls.get())

        val acceptedFinished = CountDownLatch(1)
        coordinator.handleClose(waiting.instanceId, waiting.createdAt, acceptedFinished::countDown)
        assertTrue(acceptedFinished.await(2, TimeUnit.SECONDS))
        assertEquals(1, closeCalls.get())
        assertEquals(1, closeTaskCalls.get())
    }

    @Test
    fun `结果通知再次运行使用最新卡片并创建新启动请求`() {
        val runtimeRecipe = recipe(name = "旧名称", keepFinishedNotification = true)
        val latestRecipe = recipe(name = "新名称", keepFinishedNotification = true)
        val terminal = CardRunStore.update(
            recipe = runtimeRecipe,
            status = CardRunStatus.Stopped,
            instanceId = waitingTerminal(runtimeRecipe).instanceId,
            surface = CardRunSurface.Report,
            lastMeaningfulOutput = "已停止"
        )
        var restartedWith: KiteRecipe? = null
        val coordinator = coordinator(
            runtimeRecipe = runtimeRecipe,
            latestRecipe = latestRecipe,
            restartRun = { recipe, _ ->
                restartedWith = recipe
                RunCommandResult.Accepted(terminal.instanceId)
            }
        )

        val finished = CountDownLatch(1)
        coordinator.handleRestart(
            latestRecipe.id,
            terminal.instanceId,
            terminal.createdAt,
            finished::countDown
        )

        assertTrue(finished.await(2, TimeUnit.SECONDS))
        assertEquals("新名称", restartedWith?.name)
    }

    @Test
    fun `进程任务取消并完成后不再投影运行状态`() = runTest {
        val recipe = recipe(
            keepFinishedNotification = false,
            steps = listOf(
                KiteRecipeStep(id = "terminal", type = KiteRecipe.STEP_TERMINAL),
                KiteRecipeStep(id = "report", type = KiteRecipe.STEP_SHELL),
            ),
        )
        val waiting = waitingTerminal(recipe)
        val processJob = SupervisorJob()
        val processScope = CoroutineScope(processJob + StandardTestDispatcher(testScheduler))
        val coordinator = coordinator(recipe, recipe, scope = processScope)

        coordinator.start()
        runCurrent()
        val publishedRequirement = coordinator.requirement.value
        assertNotNull(publishedRequirement)

        processJob.cancelAndJoin()
        CardRunStore.removeRun(waiting.instanceId, waiting.createdAt)
        runCurrent()

        assertEquals(publishedRequirement, coordinator.requirement.value)
    }

    @Test
    fun `动作尚未调度即取消仍只结束一次异步广播`() = runTest {
        val recipe = recipe(keepFinishedNotification = false)
        val waiting = waitingTerminal(recipe)
        val processJob = SupervisorJob()
        val processScope = CoroutineScope(processJob + StandardTestDispatcher(testScheduler))
        val finished = AtomicInteger(0)
        val coordinator = coordinator(recipe, recipe, scope = processScope)

        coordinator.handleClose(waiting.instanceId, waiting.createdAt) { finished.incrementAndGet() }
        processJob.cancelAndJoin()
        runCurrent()

        assertEquals(1, finished.get())
    }

    @Test
    fun `动作执行中取消仍只结束一次异步广播`() = runTest {
        val recipe = recipe(keepFinishedNotification = false)
        val waiting = waitingTerminal(recipe)
        val processJob = SupervisorJob()
        val processScope = CoroutineScope(processJob + StandardTestDispatcher(testScheduler))
        val entered = AtomicInteger(0)
        val finished = AtomicInteger(0)
        val coordinator = coordinator(
            runtimeRecipe = recipe,
            latestRecipe = recipe,
            closeRun = { _, _ ->
                entered.incrementAndGet()
                processJob.cancel()
                RunCommandResult.Accepted(waiting.instanceId)
            },
            scope = processScope,
        )

        coordinator.handleClose(waiting.instanceId, waiting.createdAt) { finished.incrementAndGet() }
        runCurrent()
        processJob.join()

        assertEquals(1, entered.get())
        assertEquals(1, finished.get())
    }

    private fun waitingTerminal(recipe: KiteRecipe): CardRunState {
        val started = CardRunStore.start(recipe, INSTANCE_ID)
        return CardRunStore.update(
            recipe = recipe,
            status = CardRunStatus.WaitingTerminal,
            instanceId = started.instanceId,
            surface = CardRunSurface.Terminal,
            currentStepIndex = 0,
            terminalSessionId = "terminal-test",
            lastMeaningfulOutput = "等待终端完成"
        )
    }

    private fun coordinator(
        runtimeRecipe: KiteRecipe,
        latestRecipe: KiteRecipe,
        closeRun: (KiteRecipe, CardRunState) -> RunCommandResult = { _, _ ->
            RunCommandResult.Ignored("not_used")
        },
        restartRun: (KiteRecipe, CardRunState) -> RunCommandResult = { _, _ ->
            RunCommandResult.Ignored("not_used")
        },
        closeTask: (String, Long) -> Unit = { _, _ -> },
        scope: CoroutineScope = coordinatorScope,
    ): AndroidRunNotificationCoordinator = AndroidRunNotificationCoordinator(
        context = application,
        scope = scope,
        recipeResolver = { recipeId -> runtimeRecipe.takeIf { it.id == recipeId } },
        restartRecipeResolver = { recipeId -> latestRecipe.takeIf { it.id == recipeId } },
        completeStep = { RunCommandResult.Ignored("not_used") },
        closeRun = closeRun,
        restartRun = restartRun,
        closeRunTask = closeTask,
        viewBinder = RunNotificationViewBinder { _, _, _ -> }
    )

    private fun recipe(
        name: String = "通知测试",
        keepFinishedNotification: Boolean,
        steps: List<KiteRecipeStep> = listOf(
            KiteRecipeStep(id = "terminal", type = KiteRecipe.STEP_TERMINAL),
        ),
    ): KiteRecipe = KiteRecipe(
        id = "notification-recipe",
        name = name,
        description = "",
        type = KiteRecipe.TYPE_START_SERVICE,
        defaultUrl = "",
        shortcut = false,
        launch = KiteLaunchConfig(keepFinishedNotification = keepFinishedNotification),
        execution = KiteExecution.steps(steps)
    )

    private companion object {
        const val INSTANCE_ID = "notification-instance"
    }
}
