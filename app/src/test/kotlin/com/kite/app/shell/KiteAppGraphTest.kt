package com.kite.app.shell

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteAppGraphTest {
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
            setOf("opencode", "codex", "claude-code", "hermes", "openclaw", "kimi-code", "mimo-code"),
            graph.agentConfigAdapterRegistry.adapterIds()
        )
    }
}
