package com.kite.app.shell

import android.content.ContextWrapper
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
class KiteAppGraphTest {
    @After
    fun tearDown() {
        runBlocking {
            KiteAppGraph.release(ApplicationProvider.getApplicationContext())?.join()
        }
    }

    @Test
    fun `同一进程复用组合根和长期依赖`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = KiteAppGraph.from(context)
        val second = KiteAppGraph.from(context)

        assertSame(first, second)
        assertSame(first.diagnostics, second.diagnostics)
        assertSame(first.bridgeClient, second.bridgeClient)
        assertSame(first.browserAuthSessions, second.browserAuthSessions)
        assertSame(first.browserAutomationSessions, second.browserAutomationSessions)
        assertSame(first.resourceInstallStore, second.resourceInstallStore)
        assertSame(first.resourceManifestLoader, second.resourceManifestLoader)
        assertSame(first.recipeLoader, second.recipeLoader)
        assertSame(first.cardGroupStore, second.cardGroupStore)
        assertSame(first.recipeFeatureGateway, second.recipeFeatureGateway)
    }

    @Test
    fun `配方事实复用进程依赖而页面工具按使用方创建`() {
        val graph = KiteAppGraph.from(ApplicationProvider.getApplicationContext())

        assertSame(graph.recipeLoader, graph.createRecipeLoader())
        assertSame(graph.createRecipeLoader(), graph.createRecipeLoader())
        assertNotSame(graph.createDropZoneManager(), graph.createDropZoneManager())
    }

    @Test
    fun `默认组合根登记所有内置 Agent 配置适配器`() {
        val graph = KiteAppGraph.from(ApplicationProvider.getApplicationContext())

        assertEquals(
            setOf(
                "opencode",
                "codex",
                "claude-code",
                "kimi-code",
                "hermes",
                "gemini-cli",
                "pi-coding-agent",
                "openclaw",
                "mimo-code",
                "qwen-code",
                "reasonix",
            ),
            graph.agentConfigAdapterRegistry.adapterIds()
        )
    }

    @Test
    fun `进程预载会把 Agent 统一能力目录放入内存缓存`() = runBlocking {
        val graph = KiteAppGraph.from(ApplicationProvider.getApplicationContext())

        graph.preloadAgentConversationCatalogs().join()

        assertNotNull(graph.agentProviderCatalogStore.cachedSnapshot("codex"))
    }

    @Test
    fun `同一 Application 并发请求仍只创建一个组合根`() {
        val context = ApplicationIdentityContext(ApplicationProvider.getApplicationContext())
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val results = (1..32).map {
                executor.submit<KiteAppGraph> {
                    start.await()
                    KiteAppGraph.from(context)
                }
            }
            start.countDown()
            val first = results.first().get()
            results.drop(1).forEach { assertSame(first, it.get()) }
        } finally {
            executor.shutdownNow()
            runBlocking { KiteAppGraph.release(context)?.join() }
        }
    }

    @Test
    fun `释放同一 Application 幂等且返回已完成的进程任务`() = runBlocking {
        val context = ApplicationIdentityContext(ApplicationProvider.getApplicationContext())
        KiteAppGraph.from(context)

        val released = KiteAppGraph.release(context)
        released?.join()

        assertNotNull(released)
        assertTrue(released!!.isCompleted)
        assertNull(KiteAppGraph.release(context))
    }

    @Test
    fun `新 Application 获得新组合根且旧 Application 迟到释放不会关闭它`() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val oldApplication = ApplicationIdentityContext(base)
        val newApplication = ApplicationIdentityContext(base)
        val oldGraph = KiteAppGraph.from(oldApplication)
        val newGraph = KiteAppGraph.from(newApplication)

        assertNotSame(oldGraph, newGraph)
        assertNull(KiteAppGraph.release(oldApplication))
        assertSame(newGraph, KiteAppGraph.from(newApplication))

        val newProcess = KiteAppGraph.release(newApplication)
        newProcess?.join()
        assertNotNull(newProcess)
        assertTrue(newProcess!!.isCompleted)
    }

    private class ApplicationIdentityContext(base: Context) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
    }
}
