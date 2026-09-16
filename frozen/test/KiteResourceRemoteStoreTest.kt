package com.kite.app.resources

import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KiteResourceRemoteStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `有效远程快照覆盖内置资源且篡改更新保留上一代缓存`() {
        val keyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val endpoint = KiteResourceStoreEndpoint(
            id = "test-store",
            snapshotUrl = "https://store.example/stable.json",
            signatureUrl = "https://store.example/stable.sig",
        )
        val bootstrap = KiteResourceStoreBootstrap(
            channel = "stable",
            minimumRevision = 1,
            maxSnapshotBytes = 256 * 1024,
            endpoints = listOf(endpoint),
            trustedKeys = mapOf(
                "test-key" to KiteResourceStoreTrustedKey(
                    id = "test-key",
                    algorithm = "SHA256withECDSA",
                    publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded),
                )
            ),
        )
        val payload = snapshotPayload(version = "remote-1")
        val responses = mutableMapOf(
            endpoint.snapshotUrl to payload,
            endpoint.signatureUrl to signatureDocument(payload, keyPair.private),
        )
        val store = KiteResourceRemoteStore(
            bootstrap = bootstrap,
            cacheDirectory = temporaryFolder.newFolder("resource-store"),
            fetcher = KiteResourceStoreFetcher { url, _ -> responses.getValue(url) },
            now = { 1000L },
        )
        val bundled = FixedSource(
            KiteResourceDefinitionSnapshot(
                revision = "asset-1",
                manifests = mapOf("kite.hermes.core" to manifest("bundled")),
                homeLayoutJson = homeLayout(),
            )
        )
        val composite = KiteResourceCompositeDefinitionSource(listOf(store, bundled))

        val published = store.refresh()
        val first = JSONObject(composite.snapshot().manifests.getValue("kite.hermes.core"))

        assertTrue(published is KiteResourceStoreRefreshResult.Published)
        assertEquals("remote-1", first.getJSONObject("base").getString("version"))

        responses[endpoint.snapshotUrl] = snapshotPayload(version = "tampered")
        val rejected = store.refresh()
        store.invalidate()
        val retained = JSONObject(composite.snapshot().manifests.getValue("kite.hermes.core"))

        assertTrue(rejected is KiteResourceStoreRefreshResult.Failed)
        assertEquals("remote-1", retained.getJSONObject("base").getString("version"))
        assertTrue(File(temporaryFolder.root, "resource-store/status.json").readText().contains("failed"))

        val root = projectRoot()
        val shippedBootstrapJson = JSONObject(
            File(root, "assets/resource-store/bootstrap.json").readText()
        )
        val shippedKeyJson = shippedBootstrapJson.getJSONArray("trustedKeys").getJSONObject(0)
        val shippedPayload = File(root, "store/v1/channels/stable.json").readBytes()
        val shippedSignature = File(root, "store/v1/channels/stable.sig").readBytes()
        val shippedStore = KiteResourceRemoteStore(
            bootstrap = KiteResourceStoreBootstrap(
                channel = "stable",
                minimumRevision = 1,
                maxSnapshotBytes = 1024 * 1024,
                endpoints = listOf(endpoint),
                trustedKeys = mapOf(
                    shippedKeyJson.getString("id") to KiteResourceStoreTrustedKey(
                        id = shippedKeyJson.getString("id"),
                        algorithm = shippedKeyJson.getString("algorithm"),
                        publicKeyBase64 = shippedKeyJson.getString("publicKey"),
                    )
                ),
            ),
            cacheDirectory = temporaryFolder.newFolder("shipped-resource-store"),
            fetcher = KiteResourceStoreFetcher { url, _ ->
                if (url == endpoint.snapshotUrl) shippedPayload else shippedSignature
            },
        )

        assertTrue(shippedStore.refresh() is KiteResourceStoreRefreshResult.Published)
        assertTrue(shippedStore.snapshot().manifests.size >= 20)
    }

    private fun snapshotPayload(version: String): ByteArray = JSONObject()
        .put("schemaVersion", 1)
        .put("channel", "stable")
        .put("revision", 1)
        .put("keyId", "test-key")
        .put("homeLayout", JSONObject(homeLayout()))
        .put(
            "manifests",
            JSONObject().put("kite.hermes.core", JSONObject(manifest(version)))
        )
        .toString()
        .toByteArray(Charsets.UTF_8)

    private fun manifest(version: String): String = """
        {
          "schemaVersion": 1,
          "id": "kite.hermes.core",
          "base": {"name":"Hermes","description":"","version":"$version"},
          "display": {"sections":["ai-community"]},
          "management": {"mode":"managed_extension"},
          "source": {"type":"git"}
        }
    """.trimIndent()

    private fun homeLayout(): String = """
        {
          "schemaVersion": 1,
          "sections": [{"id":"ai-community","title":"独立工具","style":"list"}],
          "tabs": [{"id":"all","label":"全部"}]
        }
    """.trimIndent()

    private fun signatureDocument(payload: ByteArray, privateKey: java.security.PrivateKey): ByteArray {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(payload)
        return JSONObject()
            .put("schemaVersion", 1)
            .put("keyId", "test-key")
            .put("algorithm", "SHA256withECDSA")
            .put("signature", Base64.getEncoder().encodeToString(signer.sign()))
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    private class FixedSource(
        private val value: KiteResourceDefinitionSnapshot
    ) : KiteResourceDefinitionSource {
        override fun snapshot(): KiteResourceDefinitionSnapshot = value

        override fun invalidate() = Unit
    }

    private fun projectRoot(): File = listOf(File("."), File(".."))
        .first { File(it, "store/v1/channels/stable.json").isFile }
}
