package com.kite.app.feature.runsurface

import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunAgentBinding
import com.kite.app.run.CardRunAgentConnectionStatus
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import com.kite.app.theme.KiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunSurfaceHostTest {
    @Test
    fun `Agent 绑定状态和会话 id 变化只更新既有显示绑定`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val host = RunSurfaceHost(context, KiteTheme.resolve(KiteTheme.defaultSelection, false).tokens)
        val recipe = KiteRecipe(
            id = "agent-recipe",
            name = "OpenCode",
            description = "",
            type = KiteRecipe.TYPE_START_SERVICE,
            defaultUrl = "",
            shortcut = false,
            execution = KiteExecution.steps(
                listOf(KiteRecipeStep("agent", KiteRecipe.STEP_AGENT, providerId = "opencode"))
            )
        )
        val preparing = CardRunState(
            instanceId = "agent-instance",
            recipeId = recipe.id,
            status = CardRunStatus.Running,
            surface = CardRunSurface.Agent,
            currentStepIndex = 0,
            stepCount = 1,
            agentBinding = CardRunAgentBinding("opencode", status = CardRunAgentConnectionStatus.Preparing),
            createdAt = 10L,
            updatedAt = 10L
        )
        val ready = preparing.copy(
            agentBinding = preparing.agentBinding?.copy(
                sessionId = "session-1",
                status = CardRunAgentConnectionStatus.Ready
            ),
            updatedAt = 11L
        )
        var factoryCalls = 0
        val binding = RecordingBinding(View(context))

        assertTrue(host.render(RunSurfaceProjector.project(recipe, preparing)) {
            factoryCalls++
            binding
        })
        val rootBefore = binding.root
        assertFalse(host.render(RunSurfaceProjector.project(recipe, ready)) {
            factoryCalls++
            RecordingBinding(View(context))
        })

        assertEquals(1, factoryCalls)
        assertSame(rootBefore, binding.root)
        assertEquals(2, binding.renderCount)
    }

    private class RecordingBinding(override val root: View) : RunSurfaceBinding {
        var renderCount: Int = 0
        override fun render(state: RunSurfaceUiState) {
            renderCount++
        }
    }
}
