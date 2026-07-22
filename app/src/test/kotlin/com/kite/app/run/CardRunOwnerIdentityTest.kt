package com.kite.app.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRunOwnerIdentityTest {
    @Test
    fun `停止身份包含叶子当前和根 owner 且保持稳定顺序`() {
        val state = CardRunState(
            instanceId = "card-a",
            recipeId = "recipe-a",
            status = CardRunStatus.Running,
            runtimeRootOwnerId = "card:card-a@100",
            runtimeOwnerId = "terminal:session-a/instance/card-a@100/step/0/attempt/1",
            ownedRuntimeOwnerIds = listOf(
                "terminal:session-a/instance/card-a@100/step/0/attempt/1",
                "shell:card-a@100/step/1/attempt/1",
                " "
            )
        )

        assertEquals(
            listOf(
                "terminal:session-a/instance/card-a@100/step/0/attempt/1",
                "shell:card-a@100/step/1/attempt/1",
                "card:card-a@100"
            ),
            state.runtimeOwnerIdsForStop()
        )
        assertTrue(state.hasRuntimeOwnership())
    }

    @Test
    fun `只有根 owner 的实例仍属于运行时所有权范围`() {
        val state = CardRunState(
            instanceId = "card-root-only",
            recipeId = "recipe-root-only",
            status = CardRunStatus.CleanupPending,
            runtimeRootOwnerId = "card:card-root-only@200"
        )

        assertEquals(listOf("card:card-root-only@200"), state.runtimeOwnerIdsForStop())
        assertTrue(state.hasRuntimeOwnership())
    }
}
