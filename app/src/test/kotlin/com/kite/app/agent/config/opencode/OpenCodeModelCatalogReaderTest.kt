package com.kite.app.agent.config.opencode

import com.kite.app.agent.config.AgentConfigCommandExecutionResult
import com.kite.app.agent.config.AgentConfigCommandExecutor
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenCodeModelCatalogReaderTest {
    @Test
    fun parsesVerboseCatalogByStableValueInsteadOfFreeNameSuffix() {
        val parsed = parseOpenCodeVerboseModelCatalog(
            verboseCatalog(
                model("big-pickle", "Big Pickle"),
                model("deepseek-v4-flash-free", "DeepSeek V4 Flash Free (New)"),
            )
        ) as OpenCodeModelCatalogParseResult.Ready

        assertEquals(
            listOf("opencode/big-pickle", "opencode/deepseek-v4-flash-free"),
            parsed.models.map(OpenCodeCatalogModel::nativeValue),
        )
        assertEquals(listOf("Big Pickle", "DeepSeek V4 Flash Free (New)"), parsed.models.map { it.displayName })
    }

    @Test
    fun rejectsTruncatedOrMismatchedVerboseCatalog() {
        assertTrue(
            parseOpenCodeVerboseModelCatalog(
                listOf("opencode/big-pickle", "{", "  \"id\": \"big-pickle\"")
            ) is OpenCodeModelCatalogParseResult.Malformed
        )
        assertTrue(
            parseOpenCodeVerboseModelCatalog(
                verboseCatalog(model("different", "Wrong")).toMutableList().also {
                    it[0] = "opencode/big-pickle"
                }
            ) is OpenCodeModelCatalogParseResult.Malformed
        )
    }

    @Test
    fun readsInIsolatedCredentialFreeEnvironmentAndReusesBoundedCache() = runTest {
        var now = 1_000L
        var calls = 0
        var next: AgentConfigCommandExecutionResult = AgentConfigCommandExecutionResult.Completed.of(
            0,
            verboseCatalog(model("big-pickle", "Big Pickle")),
        )
        val reader = OpenCodeModelCatalogReader(
            commandExecutor = AgentConfigCommandExecutor { argv, cwd ->
                calls += 1
                assertEquals("/workspace", cwd)
                assertEquals("env", argv.first())
                assertTrue(argv.containsAll(listOf(
                    "-u",
                    "OPENCODE_API_KEY",
                    "HOME=/tmp/kite-test-public-models",
                    "XDG_DATA_HOME=/tmp/kite-test-public-models/data",
                    "XDG_CONFIG_HOME=/tmp/kite-test-public-models/config",
                    "XDG_CACHE_HOME=/tmp/kite-test-public-models/cache",
                    "opencode",
                    "--pure",
                    "models",
                    "--verbose",
                )))
                next
            },
            clockMillis = { now },
            isolatedHome = "/tmp/kite-test-public-models",
        )

        val first = reader.read() as OpenCodeModelCatalogReadResult.Ready
        assertEquals(listOf("opencode/big-pickle"), first.models.map(OpenCodeCatalogModel::nativeValue))
        assertEquals(setOf("opencode/big-pickle"), reader.cachedNativeValues())
        assertTrue(reader.read() is OpenCodeModelCatalogReadResult.Ready)
        assertEquals(1, calls)

        now += 10 * 60 * 1_000L
        next = AgentConfigCommandExecutionResult.Failed("offline")
        val stale = reader.read() as OpenCodeModelCatalogReadResult.Ready
        assertEquals("OpenCode 免费模型目录暂未刷新", stale.warning)
        assertEquals(listOf("opencode/big-pickle"), stale.models.map(OpenCodeCatalogModel::nativeValue))
        assertEquals(2, calls)
    }

    @Test
    fun convertsUnexpectedCommandFailureIntoAReadFailure() = runTest {
        val reader = OpenCodeModelCatalogReader(
            commandExecutor = AgentConfigCommandExecutor { _, _ -> error("process unavailable") },
            isolatedHome = "/tmp/kite-test-public-models-failure",
        )

        val result = reader.read() as OpenCodeModelCatalogReadResult.Failed

        assertEquals("无法读取 OpenCode 免费模型目录", result.message)
    }

    private fun model(id: String, name: String): JSONObject = JSONObject()
        .put("id", id)
        .put("providerID", "opencode")
        .put("name", name)
        .put("cost", JSONObject().put("input", 0).put("output", 0))

    private fun verboseCatalog(vararg models: JSONObject): List<String> = buildList {
        models.forEach { model ->
            add("opencode/${model.getString("id")}")
            addAll(model.toString(2).lineSequence().toList())
        }
    }
}
