package com.kite.app.foundation.storage

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImmutableArtifactIntegrityTest {
    @Test
    fun `truncated staged file is rejected before immutable publication`() {
        val root = Files.createTempDirectory("kite-integrity-").toFile()
        val valid = File(root, "valid")
        val truncated = File(root, "truncated")
        try {
            val expectedFiles = linkedMapOf(
                "install.sh" to "#!/bin/sh\necho ready\n".toByteArray(),
                "packages/tool.tar" to "complete-payload".toByteArray(),
            )
            val integrityRaw = integrityManifest(expectedFiles)
            val integrity = ImmutableArtifactIntegrity.parse(integrityRaw)

            writeStage(valid, expectedFiles, integrityRaw)
            integrity.validateStageAndSeal(valid)
            assertTrue(integrity.isPublished(valid))
            assertTrue(File(valid, ImmutableArtifactIntegrity.RECEIPT_FILE).readText().contains(integrity.artifactKey))

            writeStage(truncated, expectedFiles, integrityRaw)
            File(truncated, "packages/tool.tar").writeText("short")
            val rejected = runCatching { integrity.validateStageAndSeal(truncated) }.isFailure

            assertTrue(rejected)
            assertFalse(File(truncated, ImmutableArtifactIntegrity.RECEIPT_FILE).exists())
        } finally {
            root.walkTopDown().forEach { path -> path.setWritable(true, true) }
            root.deleteRecursively()
        }
    }

    private fun writeStage(root: File, files: Map<String, ByteArray>, integrityRaw: String) {
        files.forEach { (path, bytes) ->
            File(root, path).apply {
                parentFile?.mkdirs()
                writeBytes(bytes)
            }
        }
        File(root, ImmutableArtifactIntegrity.INTEGRITY_FILE).writeText(integrityRaw)
    }

    private fun integrityManifest(files: Map<String, ByteArray>): String {
        val entries = files.entries.sortedBy(Map.Entry<String, ByteArray>::key).map { (path, bytes) ->
            JSONObject()
                .put("path", path)
                .put("size", bytes.size)
                .put("sha256", sha256(bytes))
        }
        val canonical = buildString {
            entries.forEach { entry ->
                append(entry.getString("path"))
                append('\u0000')
                append(entry.getLong("size"))
                append('\u0000')
                append(entry.getString("sha256"))
                append('\n')
            }
        }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("packageId", "test-pack")
            .put("version", 7)
            .put("contentDigest", sha256(canonical.toByteArray()))
            .put("files", JSONArray(entries))
            .toString() + "\n"
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
