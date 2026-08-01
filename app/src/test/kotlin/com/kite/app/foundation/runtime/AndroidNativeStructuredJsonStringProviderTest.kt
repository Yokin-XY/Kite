package com.kite.app.foundation.runtime

import java.io.File
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
class AndroidNativeStructuredJsonStringProviderTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `普通文件只返回声明的顶层字符串字段`() {
        val root = temporary.newFolder("workspace")
        val metadata = root.resolve("software/tool/package.json")
        checkNotNull(metadata.parentFile).mkdirs()
        metadata.writeText("{\"version\":\"1.2.3-beta.1\",\"other\":\"ignored\"}")

        val decision = prepare(root, "/workspace/software/tool/package.json")

        assertTrue(decision is RuntimeProviderDecision.Ready)
        assertEquals("1.2.3-beta.1", (decision as RuntimeProviderDecision.Ready).plan.value)
        assertEquals(RuntimeProviderKind.ANDROID_NATIVE, decision.provider)
    }

    @Test
    fun `越权路径和非法结构化合同必须阻断而不是回退`() {
        val root = temporary.newFolder("workspace")

        assertBlocked(
            prepare(root, "/workspace/software/../escape/package.json"),
            "structured_json_path_segment_invalid",
        )
        assertBlocked(
            prepare(root, "/outside/package.json"),
            "structured_json_root_not_authorized",
        )
        assertBlocked(
            prepare(root, "/workspace/package.json", jsonField = "bad.field"),
            "structured_json_field_invalid",
        )
        assertBlocked(
            prepare(root, "/workspace/package.json", maximumBytes = 0L),
            "structured_json_maximum_bytes_invalid",
        )
    }

    @Test
    fun `缺失或不完整文件事实保持为可回退的 Unsupported`() {
        val root = temporary.newFolder("workspace")
        val malformed = fixture(root, "malformed/package.json", "{\"version\":")
        val trailing = fixture(root, "trailing/package.json", "{\"version\":\"1\"} trailing")
        val nonString = fixture(root, "non-string/package.json", "{\"version\":1630}")
        val missingField = fixture(root, "missing-field/package.json", "{\"name\":\"tool\"}")
        val oversized = fixture(
            root,
            "oversized/package.json",
            "{\"version\":\"1.0.0\",\"padding\":\"${"x".repeat(2048)}\"}",
        )

        listOf(malformed, trailing, nonString, missingField).forEach { file ->
            assertUnsupported(prepare(root, containerPath(root, file)))
        }
        assertUnsupported(prepare(root, containerPath(root, oversized), maximumBytes = 1024L))
        assertUnsupported(prepare(root, "/workspace/missing/package.json"))
    }

    @Test
    fun `符号链接在宿主允许建立夹具时保持 Unsupported`() {
        val root = temporary.newFolder("workspace")
        val target = fixture(root, "target/package.json", "{\"version\":\"1.0.0\"}")
        val link = root.resolve("link/package.json")
        checkNotNull(link.parentFile).mkdirs()
        val created = runCatching { Files.createSymbolicLink(link.toPath(), target.toPath()) }.isSuccess
        assumeTrue("当前测试宿主不允许创建符号链接", created)

        val decision = prepare(root, containerPath(root, link))

        assertUnsupported(decision)
        assertEquals("structured_json_symbolic_link", (decision as RuntimeProviderDecision.Unsupported).reason)
    }

    private fun fixture(root: File, path: String, content: String): File = root.resolve(path).also { file ->
        checkNotNull(file.parentFile).mkdirs()
        file.writeText(content)
    }

    private fun prepare(
        root: File,
        path: String,
        maximumBytes: Long = 4096L,
        jsonField: String = "version",
    ): RuntimeProviderDecision<StructuredJsonStringPlan> = AndroidNativeStructuredJsonStringProvider.prepare(
        context = StructuredJsonStringContext(listOf(StructuredJsonStringRoot("/workspace", root))),
        request = StructuredJsonStringRequest(path, maximumBytes, jsonField),
    )

    private fun containerPath(root: File, file: File): String =
        "/workspace/" + root.toPath().relativize(file.toPath()).joinToString("/")

    private fun assertUnsupported(decision: RuntimeProviderDecision<StructuredJsonStringPlan>) {
        assertTrue(decision is RuntimeProviderDecision.Unsupported)
    }

    private fun assertBlocked(
        decision: RuntimeProviderDecision<StructuredJsonStringPlan>,
        reason: String,
    ) {
        assertTrue(decision is RuntimeProviderDecision.Blocked)
        assertEquals(reason, (decision as RuntimeProviderDecision.Blocked).reason)
    }
}
