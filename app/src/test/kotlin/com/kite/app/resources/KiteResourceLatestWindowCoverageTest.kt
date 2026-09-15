package com.kite.app.resources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteResourceLatestWindowCoverageTest {
    @Test
    fun `28张资源卡中所有远程安装入口都受最近三版窗口约束`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resourceRoot = sequenceOf(File("../assets/resources"), File("assets/resources"))
            .first(File::isDirectory)
        val manifests = resourceRoot.listFiles()
            .orEmpty()
            .mapNotNull { directory ->
                File(directory, "manifest.json")
                    .takeIf(File::isFile)
                    ?.readText()
                    ?.let { KiteResourceManifestLoader(context).parseManifestJson(it) }
            }

        assertEquals(28, manifests.size)
        val systemComponents = manifests.filter {
            it.management.mode == KiteResourceManagementMode.SYSTEM_COMPONENT
        }
        assertEquals(
            setOf("kite.curl", "kite.git", "kite.nodejs", "kite.python", "kite.tool.env", "kite.uv"),
            systemComponents.mapTo(linkedSetOf()) { it.id },
        )

        val managedResources = manifests - systemComponents.toSet()
        // official_command 把新鲜度交给官方安装器（命令自取 @latest），
        // 不在我们的网络获取层，也不受签名窗口约束。
        val windowlessResources = managedResources.filter {
            it.source.type == "official_command"
        }
        assertEquals(21, managedResources.size - windowlessResources.size)
        (managedResources - windowlessResources.toSet()).forEach { manifest ->
            val networkSteps = KiteResourceSourcePlanFactory.plan(manifest)
                .installActions
                .flatMap { it.installSteps }
                .filter { it.type in NETWORK_STEP_TYPES }
            assertTrue("${manifest.id} must declare a remote acquisition step", networkSteps.isNotEmpty())
            networkSteps.forEach { step ->
                val groups = when (step.type) {
                    KiteResourceInstallPlanCompiler.STEP_NPM,
                    KiteResourceInstallPlanCompiler.STEP_PYPI,
                    -> step.latestVersionWindow.groupBy { it.artifact }
                    else -> mapOf(step.id to step.latestVersionWindow)
                }
                assertTrue("${manifest.id}/${step.id} has no signed latest window", groups.isNotEmpty())
                groups.forEach { (artifact, candidates) ->
                    assertTrue(
                        "${manifest.id}/${step.id}/$artifact must keep at most three recent versions",
                        candidates.size in 1..3,
                    )
                }
            }
        }
    }

    private companion object {
        val NETWORK_STEP_TYPES = setOf(
            KiteResourceInstallPlanCompiler.STEP_GIT,
            KiteResourceInstallPlanCompiler.STEP_NPM,
            KiteResourceInstallPlanCompiler.STEP_PYPI,
            KiteResourceInstallPlanCompiler.STEP_LATEST_DOWNLOAD,
        )
    }
}
