package com.kite.app.agent.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentProjectStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: AgentProjectStore

    @Before
    fun setUp() {
        store = AgentProjectStore(context)
        store.resetForTest()
    }

    @After
    fun tearDown() {
        store.resetForTest()
    }

    @Test
    fun `project name and work directory are persisted separately per Agent`() {
        val saved = store.save("opencode", "微信项目", "/workspace/client/wechat")

        assertTrue(saved is AgentProjectSaveResult.Success)
        assertEquals(
            listOf("微信项目" to "/workspace/client/wechat"),
            store.projects("opencode").map { it.name to it.cwd },
        )
        assertTrue(store.projects("codex").isEmpty())
    }

    @Test
    fun `same directory updates display name without duplicating project`() {
        store.save("opencode", "旧名称", "/workspace/Kite")
        store.save("opencode", "Kite", "/workspace/Kite/")

        assertEquals(listOf("Kite"), store.projects("opencode").map(AgentProject::name))
    }

    @Test
    fun `archived project leaves active list and can be restored`() {
        store.save("opencode", "Kite", "/workspace/Kite")

        assertTrue(store.archive("opencode", "Kite", "/workspace/Kite", nowMillis = 12L))
        assertTrue(store.projects("opencode").isEmpty())
        assertEquals(
            listOf("Kite" to 12L),
            store.archivedProjects("opencode").map { it.name to it.archivedAtMillis },
        )

        assertTrue(store.restore("opencode", "/workspace/Kite"))
        assertEquals(listOf("Kite"), store.projects("opencode").map(AgentProject::name))
        assertTrue(store.archivedProjects("opencode").isEmpty())
    }

    @Test
    fun `session derived project can create an archive marker and saving it restores the project`() {
        assertTrue(store.archive("opencode", "临时项目", "/workspace/derived", nowMillis = 24L))
        assertEquals(
            listOf("/workspace/derived"),
            store.archivedProjects("opencode").map(AgentProject::cwd),
        )

        store.save("opencode", "正式项目", "/workspace/derived")

        assertEquals(listOf("正式项目"), store.projects("opencode").map(AgentProject::name))
        assertTrue(store.archivedProjects("opencode").isEmpty())
    }

    @Test
    fun `project rejects Android directories workspace root and duplicate names`() {
        assertTrue(
            store.save("opencode", "手机文件", "/storage/emulated/0/Documents")
                is AgentProjectSaveResult.Failure,
        )
        assertTrue(
            store.save("opencode", "默认", "/workspace")
                is AgentProjectSaveResult.Failure,
        )
        store.save("opencode", "Kite", "/workspace/Kite")
        assertTrue(
            store.save("opencode", "kite", "/workspace/Other")
                is AgentProjectSaveResult.Failure,
        )
    }
}
