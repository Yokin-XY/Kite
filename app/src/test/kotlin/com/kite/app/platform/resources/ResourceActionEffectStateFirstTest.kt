package com.kite.app.platform.resources

import com.kite.app.application.resources.ResourceActionEffect
import com.kite.app.application.resources.ResourceActionMessagePresentation
import com.kite.app.application.resources.ResourceRunLaunchResult
import com.kite.app.application.runs.RunCommandResult
import com.kite.app.application.runs.RunStartRequest
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceActionEffectStateFirstTest {
    @Test
    fun `持有计划生命周期门的失败重装不递归获取同一把锁`() {
        val source = File(
            "src/main/java/com/kite/app/platform/resources/AndroidResourceActionGateway.kt"
        ).readText()

        assertTrue(source.contains("restartInstall = { installLocked(target.id, environmentId) }"))
        assertTrue(source.contains("restartInstall?.invoke() ?: install(target.id)"))
    }

    @Test
    fun `恢复计划会返回安装向导且计划冲突不会只发可过滤消息`() {
        val source = File(
            "src/main/java/com/kite/app/platform/resources/AndroidResourceActionGateway.kt"
        ).readText()

        assertTrue(source.contains("val visibleEffects = if (parentInstanceId == null) listOf(wizard)"))
        assertTrue(source.contains("return listOf(effect) + conflictNotice"))
        assertTrue(source.contains("explicitResult(\"\${planTarget.name} 仍有未完成的获取任务"))
        assertFalse(source.contains("return message(\"\$activeName 正在获取，请先完成或取消当前任务\")"))
    }

    @Test
    fun `需要运行窗口时先启动再从同一实例读取代次`() {
        val request = request("visible-resource", "visible-instance")
        val generation = 71L
        val events = mutableListOf<String>()
        var root: CardRunState? = null
        val starter = ResourceOpenRunStarter(
            startRun = { accepted ->
                events += "start"
                root = state(accepted, generation)
                RunCommandResult.Accepted(accepted.instanceId)
            },
            stateFor = { instanceId, environmentId ->
                events += "state"
                root?.takeIf { it.instanceId == instanceId && it.environmentId == environmentId }
            },
        )

        val effects = starter.start(request, "Visible", opensRunSurface = true)

        assertEquals(listOf("start", "state"), events)
        val open = effects.filterIsInstance<ResourceActionEffect.OpenRun>().single()
        assertEquals(request.recipe.id, open.recipeId)
        assertEquals(request.instanceId, open.instanceId)
        assertEquals(generation, open.generation)
        assertFalse(open.autoStart)
    }

    @Test
    fun `编排器拒绝时不打开半成品页面`() {
        var stateRead = false
        val starter = ResourceOpenRunStarter(
            startRun = { RunCommandResult.Ignored("runtime_not_ready") },
            stateFor = { _, _ ->
                stateRead = true
                null
            },
        )

        val effects = starter.start(
            request("rejected-resource", "rejected-instance"),
            "Rejected",
            opensRunSurface = true,
        )

        assertFalse(stateRead)
        assertTrue(effects.none { it is ResourceActionEffect.OpenRun })
        assertTrue(effects.single() is ResourceActionEffect.Message)
    }

    @Test
    fun `接受后缺少同一实例事实仍不打开页面`() {
        val starter = ResourceOpenRunStarter(
            startRun = { RunCommandResult.Accepted(it.instanceId) },
            stateFor = { _, _ -> null },
        )

        val effects = starter.start(
            request("missing-state-resource", "missing-state-instance"),
            "Missing state",
            opensRunSurface = true,
        )

        assertTrue(effects.none { it is ResourceActionEffect.OpenRun })
        assertTrue(effects.single() is ResourceActionEffect.Message)
    }

    @Test
    fun `无需运行窗口的配方保持后台启动且不读取页面状态`() {
        var starts = 0
        var stateReads = 0
        val starter = ResourceOpenRunStarter(
            startRun = {
                starts += 1
                RunCommandResult.Accepted(it.instanceId)
            },
            stateFor = { _, _ ->
                stateReads += 1
                null
            },
        )

        val effects = starter.start(
            request("silent-resource", "silent-instance"),
            "Silent",
            opensRunSurface = false,
        )

        assertEquals(1, starts)
        assertEquals(0, stateReads)
        assertTrue(effects.none { it is ResourceActionEffect.OpenRun })
        assertTrue(effects.single() is ResourceActionEffect.Message)
    }

    @Test
    fun `复用实例与安装向导 effect 都传播 root 代次`() {
        val reused = CardRunState(
            instanceId = "reused-instance",
            recipeId = "reused-recipe",
            status = CardRunStatus.Running,
            createdAt = 81L,
            updatedAt = 82L,
        )
        val wizard = CardRunState(
            instanceId = "wizard-instance",
            recipeId = "wizard-recipe",
            ownerKind = CardRunState.OWNER_KIND_INSTALL_WIZARD,
            stepId = "target-resource",
            status = CardRunStatus.Opened,
            surface = CardRunSurface.InstallWizard,
            createdAt = 91L,
            updatedAt = 92L,
        )

        val open = resourceOpenEffect(reused)
        val install = installWizardOpenEffect(
            root = wizard,
            targetResourceId = "target-resource",
            planResourceIds = listOf("dependency", "target-resource"),
        )

        assertEquals(reused.createdAt, open.generation)
        assertFalse(open.autoStart)
        assertEquals(wizard.createdAt, install.generation)
        assertEquals(wizard.instanceId, install.instanceId)
        assertEquals(listOf("dependency", "target-resource"), install.planResourceIds)
    }

    @Test
    fun `维护任务接受后立即打开同一运行报告而不是留下死按钮`() {
        val run = state(request("repair-recipe", "repair-instance"), generation = 101L)

        val effects = managedOperationStartEffects(
            result = ResourceRunLaunchResult.Accepted(run),
            resourceName = "Hermes",
            operationLabel = "修复",
        )

        val open = effects.filterIsInstance<ResourceActionEffect.OpenRun>().single()
        assertEquals(run.recipeId, open.recipeId)
        assertEquals(run.instanceId, open.instanceId)
        assertEquals(run.createdAt, open.generation)
        assertFalse(open.autoStart)
    }

    @Test
    fun `维护任务启动被拒绝时返回不可过滤的明确结果`() {
        val effects = managedOperationStartEffects(
            result = ResourceRunLaunchResult.Rejected("resource_write_conflict"),
            resourceName = "Hermes",
            operationLabel = "修复",
        )

        val message = effects.filterIsInstance<ResourceActionEffect.Message>().single()
        assertEquals(ResourceActionMessagePresentation.ExplicitResult, message.presentation)
        assertTrue(message.text.contains("resource_write_conflict"))
        assertTrue(effects.none { it is ResourceActionEffect.OpenRun })
    }

    private fun request(recipeId: String, instanceId: String): RunStartRequest = RunStartRequest(
        recipe = KiteRecipe(
            id = recipeId,
            name = recipeId,
            description = "",
            type = KiteRecipe.TYPE_START_SERVICE,
            defaultUrl = "",
            shortcut = false,
            execution = KiteExecution.steps(
                listOf(
                    KiteRecipeStep(
                        id = "open",
                        type = KiteRecipe.STEP_SHELL,
                        cmd = "echo open",
                    )
                )
            ),
        ),
        instanceId = instanceId,
        ownerKind = CardRunState.OWNER_KIND_RESOURCE,
        stepId = recipeId,
        environmentId = "test-environment",
    )

    private fun state(request: RunStartRequest, generation: Long): CardRunState = CardRunState(
        instanceId = request.instanceId,
        recipeId = request.recipe.id,
        recipeName = request.recipe.name,
        ownerKind = request.ownerKind,
        stepId = request.stepId,
        status = CardRunStatus.Starting,
        surface = CardRunSurface.Report,
        createdAt = generation,
        updatedAt = generation,
        environmentId = request.environmentId,
    )
}
