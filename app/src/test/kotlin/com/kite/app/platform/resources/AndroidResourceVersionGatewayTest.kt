package com.kite.app.platform.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kite.app.bridge.BridgeResult
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.foundation.runtime.StructuredJsonStringContext
import com.kite.app.foundation.runtime.StructuredJsonStringRoot
import com.kite.app.resources.KiteResourceMetadataVersionProbeSpec
import com.kite.app.resources.KiteResourceVersionProbeSpec
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AndroidResourceVersionGatewayTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }

    @Test
    fun `Ready 直接返回原生值且不创建 PRoot recipe`() = runBlocking {
        val root = temporary.newFolder("workspace")
        fixture(root, "software/tool/package.json", "{\"version\":\"2.4.0\"}")
        val harness = harness(root)

        val result = harness.gateway.readInstalledVersion("any-resource", probe(), "any-environment")

        assertEquals("2.4.0", result.getOrThrow())
        assertEquals(0, harness.recipeRuns)
        assertEquals(listOf(InstalledVersionRoute.ANDROID_NATIVE), harness.routes.map { it.route })
    }

    @Test
    fun `Unsupported 在进程前只回退一次原命令`() = runBlocking {
        val root = temporary.newFolder("workspace")
        val harness = harness(root)

        val result = harness.gateway.readInstalledVersion("any-resource", probe(), "any-environment")

        assertEquals("9.8.7", result.getOrThrow())
        assertEquals(1, harness.recipeRuns)
        assertEquals("node-compatible-fallback", harness.lastCommand)
        assertEquals(listOf(InstalledVersionRoute.PROOT_FALLBACK), harness.routes.map { it.route })
        assertEquals("structured_json_path_missing", harness.routes.single().reason)
    }

    @Test
    fun `Blocked 不得静默执行兼容命令`() = runBlocking {
        val root = temporary.newFolder("workspace")
        val harness = harness(root)
        val blocked = probe("/workspace/software/../escape/package.json")

        val result = harness.gateway.readInstalledVersion("any-resource", blocked, "any-environment")

        assertTrue(result.isFailure)
        assertEquals(0, harness.recipeRuns)
        assertEquals(listOf(InstalledVersionRoute.BLOCKED), harness.routes.map { it.route })
        assertEquals("structured_json_path_segment_invalid", harness.routes.single().reason)
    }

    @Test
    fun `旧显式命令没有结构化事实时保持单次 PRoot 兼容`() = runBlocking {
        val root = temporary.newFolder("workspace")
        val harness = harness(root)
        val custom = KiteResourceVersionProbeSpec(command = "tool --version")

        val result = harness.gateway.readInstalledVersion("any-resource", custom, "any-environment")

        assertEquals("9.8.7", result.getOrThrow())
        assertEquals(1, harness.recipeRuns)
        assertEquals("tool --version", harness.lastCommand)
        assertEquals("structured_metadata_absent", harness.routes.single().reason)
    }

    private fun probe(
        path: String = "/workspace/software/tool/package.json",
    ) = KiteResourceVersionProbeSpec(
        command = "node-compatible-fallback",
        group = 0,
        structuredMetadata = KiteResourceMetadataVersionProbeSpec(
            containerPath = path,
            maximumBytes = 4096L,
            jsonField = "version",
        ),
    )

    private fun harness(root: File): GatewayHarness {
        lateinit var harness: GatewayHarness
        val routes = mutableListOf<InstalledVersionRouteEvent>()
        val gateway = AndroidResourceVersionGateway(
            bridgeClient = KiteBridgeClient(KiteDiagnostics(context)),
            metadataContextProvider = {
                StructuredJsonStringContext(listOf(StructuredJsonStringRoot("/workspace", root)))
            },
            routeObserver = routes::add,
            recipeRunner = { recipe, _, callback ->
                harness.recipeRuns += 1
                harness.lastCommand = recipe.steps.single().cmd.orEmpty()
                callback(
                    BridgeResult(
                        ok = true,
                        accepted = true,
                        status = "finished",
                        message = "9.8.7",
                    )
                )
            },
        )
        return GatewayHarness(gateway, routes).also { harness = it }
    }

    private fun fixture(root: File, path: String, content: String): File = root.resolve(path).also { file ->
        checkNotNull(file.parentFile).mkdirs()
        file.writeText(content)
    }

    private data class GatewayHarness(
        val gateway: AndroidResourceVersionGateway,
        val routes: MutableList<InstalledVersionRouteEvent>,
        var recipeRuns: Int = 0,
        var lastCommand: String = "",
    )
}
