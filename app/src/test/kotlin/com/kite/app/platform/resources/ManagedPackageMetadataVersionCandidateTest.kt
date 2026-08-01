package com.kite.app.platform.resources

import com.kite.app.foundation.runtime.RuntimeProviderDecision
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ManagedPackageMetadataVersionCandidateTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun regularMetadataReturnsNativeReadyWithoutProcess() {
        val root = temporary.newFolder("workspace")
        val metadata = root.resolve("software/tool/package.json")
        checkNotNull(metadata.parentFile).mkdirs()
        metadata.writeText("{\"version\":\"1.2.3-beta.1\"}")

        val decision = prepare(root, "/workspace/software/tool/package.json")

        assertTrue(decision is RuntimeProviderDecision.Ready)
        assertEquals("1.2.3-beta.1", (decision as RuntimeProviderDecision.Ready).plan.value)
    }

    @Test
    fun traversalIsBlockedAndCannotFallThrough() {
        val root = temporary.newFolder("workspace")

        val decision = prepare(root, "/workspace/software/../escape/package.json")

        assertTrue(decision is RuntimeProviderDecision.Blocked)
        assertEquals("managed_metadata_path_segment_invalid", decision.reason)
    }

    @Test
    fun incompleteMetadataFactsRemainUnsupported() {
        val root = temporary.newFolder("workspace")
        val malformed = root.resolve("malformed/package.json").also { file ->
            checkNotNull(file.parentFile).mkdirs()
            file.writeText("{\"version\":")
        }
        val nonString = root.resolve("non-string/package.json").also { file ->
            checkNotNull(file.parentFile).mkdirs()
            file.writeText("{\"version\":1620}")
        }
        val oversized = root.resolve("oversized/package.json").also { file ->
            checkNotNull(file.parentFile).mkdirs()
            file.writeText("{\"version\":\"1.0.0\",\"padding\":\"${"x".repeat(2048)}\"}")
        }

        assertUnsupported(prepare(root, containerPath(root, malformed)))
        assertUnsupported(prepare(root, containerPath(root, nonString)))
        assertUnsupported(prepare(root, containerPath(root, oversized), maximumBytes = 1024L))
        assertUnsupported(prepare(root, "/workspace/missing/package.json"))
    }

    @Test
    fun symbolicLinkRemainsUnsupportedWhenPlatformAllowsFixture() {
        val root = temporary.newFolder("workspace")
        val target = root.resolve("target/package.json").also { file ->
            checkNotNull(file.parentFile).mkdirs()
            file.writeText("{\"version\":\"1.0.0\"}")
        }
        val link = root.resolve("link/package.json")
        checkNotNull(link.parentFile).mkdirs()
        val created = runCatching { Files.createSymbolicLink(link.toPath(), target.toPath()) }.isSuccess
        assumeTrue("当前测试宿主不允许创建符号链接", created)

        assertUnsupported(prepare(root, containerPath(root, link)))
    }

    private fun prepare(
        root: java.io.File,
        path: String,
        maximumBytes: Long = 4096L,
    ) = ManagedPackageMetadataVersionCandidate.prepare(
        context = ManagedPackageMetadataContext(listOf(ManagedPackageMetadataRoot("/workspace", root))),
        request = ManagedPackageMetadataRequest(path, maximumBytes, "version"),
    )

    private fun containerPath(root: java.io.File, file: java.io.File): String =
        "/workspace/" + root.toPath().relativize(file.toPath()).joinToString("/")

    private fun assertUnsupported(decision: RuntimeProviderDecision<ManagedPackageMetadataPlan>) {
        assertTrue(decision is RuntimeProviderDecision.Unsupported)
    }
}
