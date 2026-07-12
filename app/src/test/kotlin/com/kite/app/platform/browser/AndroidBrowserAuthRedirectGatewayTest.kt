package com.kite.app.platform.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidBrowserAuthRedirectGatewayTest {
    @Test
    fun `进程重建恢复配方只保留投影身份不制造执行动作`() {
        val recipe = browserAuthRecoveryRecipe("temporary-recipe", "临时登录")

        assertEquals("temporary-recipe", recipe.id)
        assertEquals("临时登录", recipe.name)
        assertEquals("web", recipe.type)
        assertFalse(recipe.shortcut)
        assertTrue(recipe.steps.isEmpty())
    }
}
