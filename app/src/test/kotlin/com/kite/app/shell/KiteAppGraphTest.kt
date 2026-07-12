package com.kite.app.shell

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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
    }

    @Test
    fun `页面可变加载器由组合根按使用方创建`() {
        val graph = KiteAppGraph.from(ApplicationProvider.getApplicationContext())

        assertNotSame(graph.createRecipeLoader(), graph.createRecipeLoader())
        assertNotSame(graph.createDropZoneManager(), graph.createDropZoneManager())
    }
}
