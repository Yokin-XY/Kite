package com.kite.app.foundation.runtime

import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLaunchPreparationCacheTest {
    @Test
    fun `同一启动身份复用已构建准备`() {
        val cache = RuntimeLaunchPreparationCache<String>()
        val builds = AtomicInteger(0)

        val first = cache.getOrBuild(identity(), "first_start") {
            "prepared-${builds.incrementAndGet()}"
        }
        val second = cache.getOrBuild(identity(), "second_start") {
            "prepared-${builds.incrementAndGet()}"
        }

        assertEquals("prepared-1", first)
        assertEquals(first, second)
        assertEquals(1, builds.get())
        assertEquals(1L, cache.snapshot().hitCount)
        assertEquals(1L, cache.snapshot().rebuildCount)
    }

    @Test
    fun `启动身份变化后重建`() {
        val cache = RuntimeLaunchPreparationCache<String>()
        val builds = AtomicInteger(0)

        cache.getOrBuild(identity(), "default_container") { "prepared-${builds.incrementAndGet()}" }
        val changed = cache.getOrBuild(
            identity(workspacePath = "/runtime/shared/other"),
            "workspace_changed",
        ) { "prepared-${builds.incrementAndGet()}" }

        assertEquals("prepared-2", changed)
        assertEquals(2, builds.get())
        assertEquals(2L, cache.snapshot().rebuildCount)
    }

    @Test
    fun `同一路径重建容器后也重建准备`() {
        val cache = RuntimeLaunchPreparationCache<String>()
        val builds = AtomicInteger(0)

        cache.getOrBuild(identity(), "first_container") { "prepared-${builds.incrementAndGet()}" }
        val rebuilt = cache.getOrBuild(
            identity(containerCreatedAtMs = 24L),
            "recreated_container",
        ) { "prepared-${builds.incrementAndGet()}" }

        assertEquals("prepared-2", rebuilt)
        assertEquals(2, builds.get())
    }

    @Test
    fun `只有默认原生环境允许复用准备`() {
        assertTrue(RuntimeLaunchPreparationPolicy.isCacheEligible(null, null))
        assertTrue(RuntimeLaunchPreparationPolicy.isCacheEligible(" ", ""))
        assertFalse(RuntimeLaunchPreparationPolicy.isCacheEligible("view-a", null))
        assertFalse(RuntimeLaunchPreparationPolicy.isCacheEligible(null, "env-a"))
    }

    @Test
    fun `容器入口接入快照且重建路径显式失效`() {
        val source = listOf(
            File("src/main/kotlin/com/kite/app/foundation/runtime/KFContainerManager.kt"),
            File("app/src/main/kotlin/com/kite/app/foundation/runtime/KFContainerManager.kt"),
        ).first(File::exists).readText()

        assertTrue(source.contains("private val ordinaryLaunchPreparationCache"))
        assertTrue(source.contains("private var preparedDefaultContainerIdentity"))
        assertTrue(source.contains("RuntimeLaunchPreparationPolicy.isCacheEligible("))
        assertTrue(source.contains("reason=explicit_view_or_environment"))
        assertTrue(source.contains("ordinaryLaunchPreparationCache.invalidate(\"reset_default_container\")"))
        assertTrue(source.contains("preparedDefaultContainerIdentity = null"))
        assertTrue(source.contains("默认容器准备快照命中"))
        assertTrue(source.contains("launchLifecycleLock.read"))
        assertTrue(source.contains("launchLifecycleLock.write"))
    }

    @Test
    fun `显式失效后同一身份也重建`() {
        val cache = RuntimeLaunchPreparationCache<String>()
        val builds = AtomicInteger(0)

        cache.getOrBuild(identity(), "first_start") { "prepared-${builds.incrementAndGet()}" }
        cache.invalidate("container_reset")
        val rebuilt = cache.getOrBuild(identity(), "after_reset") { "prepared-${builds.incrementAndGet()}" }

        assertEquals("prepared-2", rebuilt)
        assertEquals(1L, cache.snapshot().invalidationCount)
        assertEquals("container_reset", cache.snapshot().lastInvalidationReason)
        assertTrue(cache.snapshot().hasEntry)
    }

    @Test
    fun `并发首次请求只构建一次`() {
        val cache = RuntimeLaunchPreparationCache<String>()
        val builds = AtomicInteger(0)
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<String>())
        val executor = Executors.newFixedThreadPool(8)

        repeat(8) {
            executor.execute {
                ready.countDown()
                start.await()
                results += cache.getOrBuild(identity(), "concurrent_start") {
                    Thread.sleep(40L)
                    "prepared-${builds.incrementAndGet()}"
                }
            }
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS))
        start.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

        assertEquals(1, builds.get())
        assertEquals(setOf("prepared-1"), results.toSet())
        assertEquals(7L, cache.snapshot().hitCount)
        assertFalse(results.isEmpty())
    }

    private fun identity(
        workspacePath: String = "/runtime/shared/default",
        containerCreatedAtMs: Long = 23L,
    ) = RuntimeLaunchPreparationIdentity(
        runtimeRootPath = "/runtime",
        runtimeDescriptorStamp = 23L,
        containerId = "ubuntu-main",
        containerCreatedAtMs = containerCreatedAtMs,
        rootfsPath = "/runtime/containers/ubuntu-main/rootfs",
        workspacePath = workspacePath,
        networkMode = "HOST",
    )
}
