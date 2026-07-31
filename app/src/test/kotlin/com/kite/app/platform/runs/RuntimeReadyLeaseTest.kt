package com.kite.app.platform.runs

import com.kite.app.application.runs.RecipeStepExecutionRequest
import com.kite.app.foundation.contracts.SpaceRecord
import com.kite.app.foundation.contracts.SpaceStatus
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.recipe.KiteExecution
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeStep
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeReadyLeaseTest {
    @Test
    fun `原请求可以消费已准备 Space`() {
        val request = request()
        val space = space()
        val lease = RuntimeReadyLease.create(request, container(), space, runtimeSnapshotRefreshed = true)

        assertTrue(lease.matches(request))
        assertEquals(space, lease.spaceFor(request))
        assertTrue(lease.runtimeSnapshotRefreshed)
    }

    @Test
    fun `显式 View 准备可以携带空 Space 但仍绑定原请求`() {
        val request = request()
        val lease = RuntimeReadyLease.create(request, container(), preparedSpace = null, runtimeSnapshotRefreshed = false)

        assertTrue(lease.matches(request))
        assertNull(lease.spaceFor(request))
        assertFalse(lease.runtimeSnapshotRefreshed)
    }

    @Test(expected = IllegalStateException::class)
    fun `不同重试不能消费旧凭证`() {
        val request = request(attemptId = 1L)
        val lease = RuntimeReadyLease.create(request, container(), space(), runtimeSnapshotRefreshed = true)

        lease.spaceFor(request(attemptId = 2L))
    }

    @Test(expected = IllegalStateException::class)
    fun `不同环境不能消费旧凭证`() {
        val request = request(environmentId = "default")
        val lease = RuntimeReadyLease.create(request, container(), space(), runtimeSnapshotRefreshed = true)

        lease.spaceFor(request(environmentId = "profile-2"))
    }

    private fun request(
        attemptId: Long = 1L,
        environmentId: String = "default",
    ): RecipeStepExecutionRequest {
        val step = KiteRecipeStep(id = "terminal", type = KiteRecipe.STEP_TERMINAL, text = "true")
        val recipe = KiteRecipe(
            id = "lease-test",
            name = "Lease Test",
            description = "",
            type = KiteRecipe.TYPE_START_SERVICE,
            defaultUrl = "",
            shortcut = false,
            execution = KiteExecution.steps(listOf(step)),
        )
        val state = CardRunState(
            instanceId = "lease-instance",
            recipeId = recipe.id,
            recipeName = recipe.name,
            status = CardRunStatus.Running,
            currentStepIndex = 0,
            createdAt = 100L,
            updatedAt = 100L,
            environmentId = environmentId,
        )
        return RecipeStepExecutionRequest(
            recipe = recipe,
            instanceId = state.instanceId,
            generation = state.createdAt,
            stepIndex = 0,
            step = step,
            previousState = state,
            attemptId = attemptId,
        )
    }

    private fun space(): SpaceRecord = SpaceRecord(
        id = "space-main",
        displayName = "默认空间",
        environmentId = "default",
        containerId = "ubuntu-main",
        workspacePath = "/workspace",
        createdAt = 10L,
        status = SpaceStatus.ACTIVE,
    )

    private fun container(): ContainerRecord = ContainerRecord(
        id = "ubuntu-main",
        displayName = "Ubuntu",
        imageName = "ubuntu-base-24.04-arm64",
        rootfsPath = "/runtime/rootfs",
        workspacePath = "/runtime/workspace",
        createdAt = 10L,
    )
}
