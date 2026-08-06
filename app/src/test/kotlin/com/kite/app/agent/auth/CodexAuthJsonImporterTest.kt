package com.kite.app.agent.auth

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CodexAuthJsonImporterTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `official auth json is written byte for byte and activates built in provider`() {
        val rootfs = temp.newFolder("rootfs")
        val codexDir = File(rootfs, "root/.codex").apply { mkdirs() }
        File(codexDir, "auth.json").writeText("""{"OPENAI_API_KEY":"old-key"}""")
        File(codexDir, "config.toml").writeText(
            """
            model_provider = "custom"
            model = "third-party-model"
            model_reasoning_effort = "high"

            [model_providers.custom]
            name = "custom"
            base_url = "https://example.test/v1"

            [projects."/workspace"]
            trust_level = "trusted"
            """.trimIndent() + "\n",
        )
        val payload = JSONObject()
            .put("auth_mode", "chatgpt")
            .put(
                "tokens",
                JSONObject()
                    .put("id_token", "opaque-id-token")
                    .put("access_token", "opaque-access-token")
                    .put("refresh_token", "opaque-refresh-token")
                    .put("account_id", "official-account"),
            )
            .put("last_refresh", "2026-07-26T12:00:00Z")
            .toString(2) + "\n"

        val result = CodexAuthJsonImporter.importIntoRootfs(rootfs, payload)

        assertTrue(result.success)
        assertTrue(result.authBackupCreated)
        assertTrue(result.configBackupCreated)
        assertEquals(payload, File(codexDir, "auth.json").readText())
        assertFalse(File(codexDir, "import-backups").exists())
        val config = File(codexDir, "config.toml").readText()
        assertFalse(config.contains("model_provider"))
        assertFalse(config.contains("third-party-model"))
        assertFalse(config.contains("[model_providers.custom]"))
        assertTrue(config.contains("model_reasoning_effort = \"high\""))
        assertTrue(config.contains("[projects.\"/workspace\"]"))
    }

    @Test
    fun `flat export is rejected in the official-only PR`() {
        val rootfs = temp.newFolder("flat-rootfs")
        val result = CodexAuthJsonImporter.importIntoRootfs(
            rootfs,
            JSONObject()
                .put("id_token", "opaque-id-token")
                .put("access_token", "opaque-access-token")
                .put("refresh_token", "opaque-refresh-token")
                .toString(),
        )

        assertFalse(result.success)
        assertEquals(CodexAuthImportError.INVALID_JSON, result.error)
        assertFalse(File(rootfs, "root/.codex/auth.json").exists())
    }

    @Test
    fun `missing refresh token is rejected without writing auth`() {
        val rootfs = temp.newFolder("invalid-rootfs")
        val payload = JSONObject()
            .put("auth_mode", "chatgpt")
            .put(
                "tokens",
                JSONObject()
                    .put("id_token", "opaque-id-token")
                    .put("access_token", "opaque-access-token"),
            )
            .toString()

        val result = CodexAuthJsonImporter.importIntoRootfs(rootfs, payload)

        assertFalse(result.success)
        assertEquals(CodexAuthImportError.MISSING_REFRESH_TOKEN, result.error)
        assertFalse(File(rootfs, "root/.codex/auth.json").exists())
    }
}
