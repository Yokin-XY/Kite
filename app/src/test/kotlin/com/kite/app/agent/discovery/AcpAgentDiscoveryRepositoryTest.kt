package com.kite.app.agent.discovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AcpAgentDiscoveryRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val cacheDirectory = File(context.filesDir, "agent-discovery/acp-registry")

    @After
    fun tearDown() {
        cacheDirectory.deleteRecursively()
    }

    @Test
    fun `目录只接收受支持且安全的 Android 分发形式`() {
        val snapshot = AcpAgentCatalogParser.parse(CATALOG_WITH_ALL_DISTRIBUTIONS)

        assertEquals("2.1.0", snapshot.version)
        assertEquals(listOf("safe-agent"), snapshot.entries.map { it.id })
        val distributions = snapshot.entries.single().distributions
        assertEquals(
            listOf(
                AcpAgentDistributionKind.Npx,
                AcpAgentDistributionKind.Uvx,
                AcpAgentDistributionKind.LinuxArm64Binary,
            ),
            distributions.map { it.kind },
        )
        assertEquals("safe-agent@3.4.5", distributions[0].packageSpec)
        assertEquals("safe-agent-python@3.4.5", distributions[1].packageSpec)
        assertEquals("./bin/safe-agent", distributions[2].command)
        assertTrue(distributions[2].artifactVerified)
        assertEquals(AcpAgentCandidateTrust.MetadataOnly, snapshot.entries.single().trust)
    }

    @Test
    fun `不安全下载地址和越界命令不会进入候选目录`() {
        val snapshot = AcpAgentCatalogParser.parse(CATALOG_WITH_UNSAFE_BINARY)

        assertTrue(snapshot.entries.isEmpty())
    }

    @Test
    fun `在线刷新成功后网络失败复用最后一次成功缓存`() = runTest {
        val first = AcpAgentDiscoveryRepository(context) {
            AcpAgentCatalogFetchResult.Updated(CATALOG_WITH_ALL_DISTRIBUTIONS, "catalog-v2")
        }.refresh()

        assertEquals(AcpAgentCatalogSource.Live, first.source)
        assertEquals("safe-agent", first.entries.single().id)
        assertEquals("catalog-v2", File(cacheDirectory, "registry.etag").readText())

        val fallback = AcpAgentDiscoveryRepository(context) {
            throw IOException("offline")
        }.refresh()

        assertEquals(AcpAgentCatalogSource.Cache, fallback.source)
        assertEquals("safe-agent", fallback.entries.single().id)
        assertTrue(fallback.warning.orEmpty().contains("上次成功目录"))
    }

    @Test
    fun `服务器确认目录未变化时仍标记为在线最新`() = runTest {
        AcpAgentDiscoveryRepository(context) {
            AcpAgentCatalogFetchResult.Updated(CATALOG_WITH_ALL_DISTRIBUTIONS, "catalog-v2")
        }.refresh()

        val confirmed = AcpAgentDiscoveryRepository(context) {
            AcpAgentCatalogFetchResult.NotModified
        }.refresh()

        assertEquals(AcpAgentCatalogSource.Live, confirmed.source)
        assertNull(confirmed.warning)
    }

    @Test
    fun `损坏缓存不会阻断随包目录`() {
        cacheDirectory.mkdirs()
        File(cacheDirectory, "registry.json").writeText("{broken")
        File(cacheDirectory, "registry.etag").writeText("stale")

        val snapshot = AcpAgentDiscoveryRepository(context) {
            AcpAgentCatalogFetchResult.NotModified
        }.cachedSnapshot()

        assertEquals(AcpAgentCatalogSource.Bundled, snapshot.source)
        assertFalse(snapshot.entries.isEmpty())
        assertNotNull(snapshot.entries.singleOrNull { it.id == "codex-acp" })
        assertNull(snapshot.warning)
    }

    private companion object {
        val CATALOG_WITH_ALL_DISTRIBUTIONS = """
            {
              "version": "2.1.0",
              "agents": [
                {
                  "id": "safe-agent",
                  "name": "Safe Agent",
                  "version": "3.4.5",
                  "description": "test",
                  "repository": "https://example.com/safe-agent",
                  "distribution": {
                    "npx": {"package": "safe-agent@3.4.5", "args": ["--acp"]},
                    "uvx": {"package": "safe-agent-python@3.4.5"},
                    "binary": {
                      "linux-aarch64": {
                        "archive": "https://example.com/safe-agent.tar.gz",
                        "cmd": "./bin/safe-agent",
                        "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                      },
                      "windows-x86_64": {
                        "archive": "https://example.com/safe-agent.zip",
                        "cmd": "safe-agent.exe"
                      }
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val CATALOG_WITH_UNSAFE_BINARY = """
            {
              "version": "1.0.0",
              "agents": [
                {
                  "id": "unsafe-agent",
                  "name": "Unsafe Agent",
                  "version": "1.0.0",
                  "distribution": {
                    "binary": {
                      "linux-aarch64": {
                        "archive": "http://example.com/agent.tar.gz",
                        "cmd": "../agent"
                      }
                    }
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
