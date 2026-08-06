package com.kite.app.agent.auth

import org.json.JSONArray
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
import java.time.Instant
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class CodexAuthJsonImporterTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `official and CPA credentials activate built in Codex provider`() {
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
        val idToken = jwt("""{"sub":"official-user","email":"official@example.test"}""")
        val payload = JSONObject()
            .put("id_token", idToken)
            .put("access_token", "access")
            .put("refresh_token", "refresh")
            .put("account_id", "official-account")
            .put("last_refresh", "2026-07-26T12:00:00Z")
            .toString()

        val result = CodexAuthJsonImporter.importIntoRootfs(
            rootfs,
            payload,
            Instant.parse("2026-07-26T13:00:00Z"),
        )

        assertTrue(result.success)
        assertEquals(1, result.importedAccountCount)
        assertTrue(result.authBackupCreated)
        assertTrue(result.configBackupCreated)
        val auth = JSONObject(File(codexDir, "auth.json").readText())
        assertEquals("chatgpt", auth.getString("auth_mode"))
        assertEquals("official-account", auth.getJSONObject("tokens").getString("account_id"))
        val config = File(codexDir, "config.toml").readText()
        assertFalse(config.contains("model_provider"))
        assertFalse(config.contains("third-party-model"))
        assertFalse(config.contains("[model_providers.custom]"))
        assertTrue(config.contains("model_reasoning_effort = \"high\""))
        assertTrue(config.contains("[projects.\"/workspace\"]"))
    }

    @Test
    fun `Sub2API accounts export stores every account and supports switching`() {
        val rootfs = temp.newFolder("sub2api-rootfs")
        val firstIdToken = jwt("""{"sub":"sub2api-user-1","email":"first@example.test"}""")
        val secondIdToken = jwt("""{"sub":"sub2api-user-2","email":"second@example.test"}""")
        val payload = JSONObject()
            .put("exported_at", "2026-08-05T00:00:00Z")
            .put("proxies", JSONArray())
            .put(
                "accounts",
                JSONArray()
                    .put(
                        JSONObject().put(
                            "credentials",
                            JSONObject()
                                .put("access_token", "access-1")
                                .put("refresh_token", "refresh-1")
                                .put("id_token", firstIdToken)
                                .put("email", "first@example.test")
                                .put("chatgpt_account_id", "sub2api-account-1"),
                        ),
                    )
                    .put(
                        JSONObject().put(
                            "credentials",
                            JSONObject()
                                .put("access_token", "access-2")
                                .put("refresh_token", "refresh-2")
                                .put("id_token", secondIdToken)
                                .put("email", "second@example.test")
                                .put("chatgpt_account_id", "sub2api-account-2"),
                        ),
                    ),
            )
            .put("type", "sub2api")
            .put("version", 1)
            .toString()

        val result = CodexAuthJsonImporter.importIntoRootfs(rootfs, payload)

        assertTrue(result.success)
        assertEquals(2, result.importedAccountCount)
        assertEquals("first@example.test", result.accountLabel)
        assertEquals(
            "sub2api-account-1",
            JSONObject(File(rootfs, "root/.codex/auth.json").readText())
                .getJSONObject("tokens")
                .getString("account_id"),
        )

        val snapshot = CodexAuthJsonImporter.snapshotRootfs(rootfs)
        assertEquals(2, snapshot.accounts.size)
        val second = snapshot.accounts.single { it.label == "second@example.test" }
        val switch = CodexAuthJsonImporter.activateAccountRootfs(rootfs, second.id)

        assertTrue(switch.success)
        assertEquals(second.id, switch.snapshot.activeAccountId)
        assertEquals(
            "sub2api-account-2",
            JSONObject(File(rootfs, "root/.codex/auth.json").readText())
                .getJSONObject("tokens")
                .getString("account_id"),
        )
    }

    @Test
    fun `wrapped session and CPA array are accepted`() {
        val idToken = jwt("""{"sub":"array-user"}""")
        val nested = JSONObject()
            .put("tokens", JSONObject()
                .put("id_token", idToken)
                .put("access_token", "access")
                .put("refresh_token", "refresh")
                .put("account_id", "array-account"))
        val wrapped = JSONObject().put("session_json", nested.toString()).toString()
        assertEquals("array-account", CodexAuthJsonImporter.parseCredentials(wrapped)?.accountId)

        val array = JSONArray().put(
            JSONObject()
                .put("id_token", idToken)
                .put("access_token", "access")
                .put("refresh_token", "refresh")
                .put("account_id", "array-account"),
        ).toString()
        assertEquals("array-account", CodexAuthJsonImporter.parseCredentials(array)?.accountId)
    }

    @Test
    fun `missing refresh token is rejected without writing auth`() {
        val rootfs = temp.newFolder("invalid-rootfs")
        val payload = JSONObject()
            .put("id_token", jwt("""{"sub":"user"}"""))
            .put("access_token", "access")
            .toString()

        val result = CodexAuthJsonImporter.importIntoRootfs(rootfs, payload)

        assertFalse(result.success)
        assertEquals(CodexAuthImportError.MISSING_REFRESH_TOKEN, result.error)
        assertFalse(File(rootfs, "root/.codex/auth.json").exists())
    }

    private fun jwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "$header.$body.signature"
    }
}
