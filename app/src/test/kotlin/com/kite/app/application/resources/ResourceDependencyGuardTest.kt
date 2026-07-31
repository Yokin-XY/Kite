package com.kite.app.application.resources

import com.kite.app.resources.KiteResourceManagementMode
import com.kite.app.resources.KiteResourceManagementSpec
import com.kite.app.resources.KiteResourceManifest
import com.kite.app.resources.KiteResourceSourceSpec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceDependencyGuardTest {
    @Test
    fun `唯一依赖提供者不能被卸载`() {
        val manifests = listOf(
            manifest("runtime", provides = listOf("runtime.node>=26")),
            manifest("agent", name = "Agent", base = listOf("runtime.node>=26"))
        )
        val guard = ResourceDependencyGuard { requirement ->
            manifests.filter { requirement in it.provides }.map(KiteResourceManifest::id)
        }

        val blockers = guard.blockers("runtime", manifests, setOf("runtime", "agent"))

        assertEquals(1, blockers.size)
        assertEquals("Agent", blockers.single().resourceName)
        assertEquals("runtime.node>=26", blockers.single().requirement)
    }

    @Test
    fun `存在其他已安装提供者时允许卸载`() {
        val manifests = listOf(
            manifest("runtime-a", provides = listOf("runtime.node>=26")),
            manifest("runtime-b", provides = listOf("runtime.node>=26")),
            manifest("agent", base = listOf("runtime.node>=26"))
        )
        val guard = ResourceDependencyGuard { listOf("runtime-a", "runtime-b") }

        assertTrue(
            guard.blockers(
                "runtime-a",
                manifests,
                setOf("runtime-a", "runtime-b", "agent")
            ).isEmpty()
        )
    }

    @Test
    fun `未安装依赖者不阻挡卸载`() {
        val manifests = listOf(
            manifest("runtime", provides = listOf("runtime.node>=26")),
            manifest("agent", base = listOf("runtime.node>=26"))
        )
        val guard = ResourceDependencyGuard { listOf("runtime") }

        assertTrue(guard.blockers("runtime", manifests, setOf("runtime")).isEmpty())
    }

    private fun manifest(
        id: String,
        name: String = id,
        provides: List<String> = emptyList(),
        base: List<String> = emptyList()
    ) = KiteResourceManifest(
        id = id,
        name = name,
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
        provides = provides,
        baseRequirements = base,
        defaultRequirements = emptyList(),
        extensions = emptyList(),
        management = KiteResourceManagementSpec(KiteResourceManagementMode.MANAGED_EXTENSION),
        source = KiteResourceSourceSpec(type = "test"),
        sourceType = "",
        installActions = emptyList(),
        updateActions = emptyList(),
        uninstallActions = emptyList(),
        openRecipe = null,
        homeCards = emptyList(),
        rawJson = JSONObject()
    )
}
