package com.kite.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardRunIntentsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `文档任务 URI 包含代次且相同实例的新代不会复用旧任务`() {
        val oldIntent = CardRunIntents.launchIntent(
            context = context,
            recipeId = "recipe",
            instanceId = "instance",
            autoStart = false,
            generation = 11L,
        )
        val newIntent = CardRunIntents.launchIntent(
            context = context,
            recipeId = "recipe",
            instanceId = "instance",
            autoStart = false,
            generation = 12L,
        )

        assertNotEquals(oldIntent.data, newIntent.data)
        assertEquals(CardRunTaskIdentity("instance", 11L), CardRunIntents.taskIdentity(oldIntent))
        assertEquals(CardRunTaskIdentity("instance", 12L), CardRunIntents.taskIdentity(newIntent))
    }

    @Test
    fun `URI 与 extra 代次不一致时拒绝生成关闭身份`() {
        val intent = CardRunIntents.launchIntent(
            context = context,
            recipeId = "recipe",
            instanceId = "instance",
            generation = 21L,
        ).putExtra(CardRunIntents.EXTRA_GENERATION, 22L)

        assertNull(CardRunIntents.taskIdentity(intent))
    }

    @Test
    fun `没有真实代次的兼容入口不能触发任务移除停止`() {
        val intent = CardRunIntents.launchIntent(
            context = context,
            recipeId = "recipe",
            instanceId = "legacy",
            generation = null,
        )

        assertNull(CardRunIntents.taskIdentity(intent))
    }

    @Test
    fun `非运行窗口动作或组件不能伪装任务移除身份`() {
        val intent = CardRunIntents.launchIntent(
            context = context,
            recipeId = "recipe",
            instanceId = "instance",
            generation = 31L,
        )

        assertNull(CardRunIntents.taskIdentity(Intent(intent).setAction(Intent.ACTION_VIEW)))
        assertNull(CardRunIntents.taskIdentity(Intent(intent).setClass(context, MainActivity::class.java)))
    }
}
