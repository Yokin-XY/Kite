package com.kite.app.application.resources

import android.app.Application
import com.kite.app.resources.KiteResourceManagementMode
import com.kite.app.resources.KiteResourceManagementSpec
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceLatestVersionProbe
import com.kite.app.resources.KiteResourceMetadataVersionProbeSpec
import com.kite.app.resources.KiteResourceRemoteVersionProbe
import com.kite.app.resources.KiteResourceSourceSpec
import com.kite.app.resources.KiteResourceVersionProbeSpec
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ResourceVersionCoordinatorTest {
    @Test
    fun `版本检查按实际安装版本和远端版本返回更新事实`() = runTest {
        val manifest = manifest()
        val coordinator = ResourceVersionCoordinator(
            FakeGateway(installed = "example 1.2.3", latest = "{\"version\":\"1.3.0\"}")
        )

        val result = coordinator.check(manifest)

        assertTrue(result is ResourceVersionCheckResult.UpdateAvailable)
        result as ResourceVersionCheckResult.UpdateAvailable
        assertEquals("1.2.3", result.installedVersion)
        assertEquals("1.3.0", result.latestVersion)
    }

    @Test
    fun `相同版本和本地更高版本都不会误报可更新`() = runTest {
        val current = ResourceVersionCoordinator(
            FakeGateway("example 1.2.3", "{\"version\":\"1.2.3\"}")
        ).check(manifest())
        val ahead = ResourceVersionCoordinator(
            FakeGateway("example 2.0.0", "{\"version\":\"1.9.0\"}")
        ).check(manifest())

        assertTrue(current is ResourceVersionCheckResult.Current)
        assertTrue(ahead is ResourceVersionCheckResult.Current)
        assertFalse((current as ResourceVersionCheckResult.Current).locallyAhead)
        assertTrue((ahead as ResourceVersionCheckResult.Current).locallyAhead)
    }

    @Test
    fun `网络失败和无法识别版本保持失败而不是假装最新版`() = runTest {
        val networkFailure = ResourceVersionCoordinator(
            FakeGateway("example 1.0.0", "", latestFailure = IllegalStateException("offline"))
        ).check(manifest())
        val parseFailure = ResourceVersionCoordinator(
            FakeGateway("unknown", "{\"version\":\"1.0.0\"}")
        ).check(manifest())

        assertEquals("latest", (networkFailure as ResourceVersionCheckResult.Failed).stage)
        assertEquals("installed", (parseFailure as ResourceVersionCheckResult.Failed).stage)
    }

    @Test
    fun `预发布版本比较遵守稳定版高于同核心预发布版`() {
        assertEquals(ResourceVersionOrder.OLDER, ResourceVersionComparator.compare("1.0.0-beta.2", "1.0.0"))
        assertEquals(ResourceVersionOrder.NEWER, ResourceVersionComparator.compare("1.0.0", "1.0.0-rc.1"))
        assertEquals(ResourceVersionOrder.OLDER, ResourceVersionComparator.compare("1.0.0-beta.2", "1.0.0-beta.10"))
    }

    @Test
    fun `GitHub Release 版本同时接受 API 结果和官方重定向结果`() {
        val probe = KiteResourceRemoteVersionProbe(
            url = "https://api.github.com/repos/example/example/releases/latest",
            jsonField = "tag_name",
            format = "github_release",
            fallbackUrl = "https://github.com/example/example/releases/latest"
        )

        assertEquals("v1.2.3", ResourceVersionParser.latest("{\"tag_name\":\"v1.2.3\"}", probe))
        assertEquals(
            "v1.2.4",
            ResourceVersionParser.latest("https://github.com/example/example/releases/tag/v1.2.4", probe)
        )
    }

    @Test
    fun `本地最新版本命令与已安装版本使用同一解析规则`() = runTest {
        val base = manifest()
        val localManifest = base.copy(
            management = base.management.copy(
                latestVersionProbe = KiteResourceVersionProbeSpec(
                    command = "example latest-version",
                    pattern = "version=([0-9.]+)"
                )
            ),
            source = KiteResourceSourceSpec(type = "bundled"),
            sourceType = "bundled"
        )
        val coordinator = ResourceVersionCoordinator(
            FakeGateway(installed = "example 1.0.0", latest = "version=2.0.0")
        )

        val result = coordinator.check(localManifest)

        assertTrue(result is ResourceVersionCheckResult.UpdateAvailable)
        result as ResourceVersionCheckResult.UpdateAvailable
        assertEquals("1.0.0", result.installedVersion)
        assertEquals("2.0.0", result.latestVersion)
    }

    @Test
    fun `版本探测始终携带发起检查时的环境身份`() = runTest {
        val gateway = FakeGateway("example 1.0.0", "{\"version\":\"1.0.0\"}")

        ResourceVersionCoordinator(gateway).check(manifest(), "profile-2")

        assertEquals(listOf("profile-2", "profile-2"), gateway.observedEnvironmentIds)
    }

    @Test
    fun `统一检查只选择已安装可管理且具备版本合同的资源`() {
        val managed = manifest()
        val system = managed.copy(
            management = managed.management.copy(mode = KiteResourceManagementMode.SYSTEM_COMPONENT)
        )
        val withoutInstalledProbe = managed.copy(
            management = managed.management.copy(versionProbe = null),
            source = KiteResourceSourceSpec(type = "custom"),
            sourceType = "custom"
        )

        assertTrue(ResourceUpdateBatchPolicy.isEligible(managed, installed = true))
        assertFalse(ResourceUpdateBatchPolicy.isEligible(managed, installed = false))
        assertFalse(ResourceUpdateBatchPolicy.isEligible(system, installed = true))
        assertFalse(ResourceUpdateBatchPolicy.isEligible(withoutInstalledProbe, installed = true))
    }

    @Test
    fun `批量预检 Ready 后复用原生值且只读取远端版本`() = runTest {
        val gateway = FakeGateway(
            installed = "should-not-run",
            latest = "{\"version\":\"1.3.0\"}",
            preparation = ResourceVersionInstalledPreparation.Ready("1.2.3", "test_ready"),
        )
        val coordinator = ResourceVersionCoordinator(gateway)

        val prepared = coordinator.prepareBatchCheck(structuredManifest(), "profile-ready")
        val result = coordinator.check(prepared)

        assertTrue(prepared is PreparedResourceVersionCheck.StructuredNativeRemote)
        assertEquals(ResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE, prepared.lane)
        assertTrue(result is ResourceVersionCheckResult.UpdateAvailable)
        assertEquals(1, gateway.preparationCalls)
        assertEquals(0, gateway.installedReadCount)
        assertEquals(1, gateway.latestReadCount)
        assertEquals(listOf("profile-ready"), gateway.observedEnvironmentIds)
    }

    @Test
    fun `批量预检 Unsupported 会把整项交给兼容车道`() = runTest {
        val gateway = FakeGateway(
            installed = "1.2.3",
            latest = "{\"version\":\"1.2.3\"}",
            preparation = ResourceVersionInstalledPreparation.Unsupported("metadata_missing"),
        )
        val coordinator = ResourceVersionCoordinator(gateway)

        val prepared = coordinator.prepareBatchCheck(structuredManifest())
        val result = coordinator.check(prepared)

        assertTrue(prepared is PreparedResourceVersionCheck.ProotCompatibility)
        assertEquals(ResourceVersionBatchLane.PROOT_COMPATIBILITY, prepared.lane)
        assertTrue(result is ResourceVersionCheckResult.Current)
        assertEquals(1, gateway.installedReadCount)
        assertEquals(1, gateway.latestReadCount)
    }

    @Test
    fun `批量预检 Blocked 直接失败且不启动任何版本读取`() = runTest {
        val gateway = FakeGateway(
            installed = "should-not-run",
            latest = "should-not-run",
            preparation = ResourceVersionInstalledPreparation.Blocked("path_escape"),
        )
        val coordinator = ResourceVersionCoordinator(gateway)

        val prepared = coordinator.prepareBatchCheck(structuredManifest())
        val result = coordinator.check(prepared)

        assertTrue(prepared is PreparedResourceVersionCheck.Completed)
        assertEquals(ResourceVersionBatchLane.STRUCTURED_NATIVE_REMOTE, prepared.lane)
        assertTrue(result is ResourceVersionCheckResult.Failed)
        assertEquals("installed_version_blocked:path_escape", (result as ResourceVersionCheckResult.Failed).reason)
        assertEquals(0, gateway.installedReadCount)
        assertEquals(0, gateway.latestReadCount)
    }

    private fun manifest() = KiteResourceManifest(
        id = "kite.example",
        name = "Example",
        description = "",
        version = "",
        iconText = "",
        iconAsset = "",
        displayCategory = "",
        displayAccent = "",
        displaySizeLabel = "",
        displayLongDescription = "",
        displayBadge = null,
        displayMedia = null,
        displayPreviewCards = emptyList(),
        displayRequirementRows = emptyList(),
        displayRecommendations = emptyList(),
        sections = listOf("test"),
        tags = emptyList(),
        provides = emptyList(),
        baseRequirements = emptyList(),
        defaultRequirements = emptyList(),
        extensions = emptyList(),
        management = KiteResourceManagementSpec(
            mode = KiteResourceManagementMode.MANAGED_EXTENSION,
            managedCommands = listOf("example"),
            versionProbe = KiteResourceVersionProbeSpec(
                command = "example --version",
                pattern = "example ([0-9.]+)"
            )
        ),
        source = KiteResourceSourceSpec(type = "npm", packageName = "example"),
        sourceType = "npm",
        installActions = emptyList(),
        updateActions = emptyList(),
        uninstallActions = emptyList(),
        openRecipe = null,
        homeCards = emptyList(),
        rawJson = JSONObject()
    )

    private fun structuredManifest(): KiteResourceManifest {
        val base = manifest()
        return base.copy(
            management = base.management.copy(
                versionProbe = KiteResourceVersionProbeSpec(
                    command = "node-compatible-fallback",
                    structuredMetadata = KiteResourceMetadataVersionProbeSpec(
                        containerPath = "/workspace/software/example/package.json",
                        maximumBytes = 4096L,
                        jsonField = "version",
                    ),
                ),
            ),
        )
    }

    private class FakeGateway(
        private val installed: String,
        private val latest: String,
        private val installedFailure: Throwable? = null,
        private val latestFailure: Throwable? = null,
        private val preparation: ResourceVersionInstalledPreparation =
            ResourceVersionInstalledPreparation.Unsupported("test_preparation_unavailable"),
    ) : ResourceVersionGateway, ResourceVersionBatchPreparationGateway {
        val observedEnvironmentIds = mutableListOf<String>()
        var preparationCalls = 0
        var installedReadCount = 0
        var latestReadCount = 0

        override suspend fun prepareInstalledVersion(
            probe: KiteResourceVersionProbeSpec,
        ): ResourceVersionInstalledPreparation {
            preparationCalls += 1
            return preparation
        }

        override suspend fun readInstalledVersion(
            resourceId: String,
            probe: KiteResourceVersionProbeSpec,
            environmentId: String
        ): Result<String> {
            installedReadCount += 1
            observedEnvironmentIds += environmentId
            return installedFailure?.let(Result.Companion::failure) ?: Result.success(installed)
        }

        override suspend fun readLatestVersion(
            resourceId: String,
            probe: KiteResourceLatestVersionProbe,
            environmentId: String
        ): Result<String> {
            latestReadCount += 1
            observedEnvironmentIds += environmentId
            return latestFailure?.let(Result.Companion::failure) ?: Result.success(latest)
        }
    }
}
