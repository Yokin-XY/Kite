package com.kite.app.resources

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class KiteResourceStoreEndpoint(
    val id: String,
    val snapshotUrl: String,
    val signatureUrl: String,
)

internal data class KiteResourceStoreTrustedKey(
    val id: String,
    val algorithm: String,
    val publicKeyBase64: String,
)

internal data class KiteResourceStoreBootstrap(
    val channel: String,
    val minimumRevision: Long,
    val maxSnapshotBytes: Int,
    val endpoints: List<KiteResourceStoreEndpoint>,
    val trustedKeys: Map<String, KiteResourceStoreTrustedKey>,
)

internal fun interface KiteResourceStoreFetcher {
    fun fetch(url: String, maxBytes: Int): ByteArray
}

internal sealed interface KiteResourceStoreRefreshResult {
    data class Published(val revision: Long, val endpointId: String) : KiteResourceStoreRefreshResult
    data class Unchanged(val revision: Long, val endpointId: String) : KiteResourceStoreRefreshResult
    data object Disabled : KiteResourceStoreRefreshResult
    data class Failed(val reason: String) : KiteResourceStoreRefreshResult
}

/**
 * 已签名远程资源目录的状态拥有者。
 *
 * 网络响应只有在完整验签、协议校验和代次校验后才会以单文件原子替换进入缓存。
 * 资源页面和 Loader 只读取这里已经发布的完整快照，绝不在页面绘制期间联网。
 */
internal class KiteResourceRemoteStore(
    private val bootstrap: KiteResourceStoreBootstrap,
    private val cacheDirectory: File,
    private val fetcher: KiteResourceStoreFetcher = HttpKiteResourceStoreFetcher(),
    private val now: () -> Long = System::currentTimeMillis,
) : KiteResourceDefinitionSource {
    private val snapshotLock = Any()
    private val refreshLock = Any()
    private var cachedSnapshot: KiteResourceDefinitionSnapshot? = null

    init {
        cacheDirectory.mkdirs()
        cleanupInterruptedWrites()
    }

    override fun snapshot(): KiteResourceDefinitionSnapshot = synchronized(snapshotLock) {
        cachedSnapshot?.let { return@synchronized it }
        loadCurrent()?.snapshot?.also { cachedSnapshot = it }
            ?: KiteResourceDefinitionSnapshot(
                revision = "remote-empty",
                manifests = emptyMap(),
                homeLayoutJson = null,
            )
    }

    override fun invalidate() {
        synchronized(snapshotLock) {
            cachedSnapshot = null
        }
    }

    fun refresh(): KiteResourceStoreRefreshResult = synchronized(refreshLock) {
        if (bootstrap.endpoints.isEmpty()) {
            writeStatus(outcome = "disabled", reason = "未配置远程资源目录")
            return@synchronized KiteResourceStoreRefreshResult.Disabled
        }
        val current = loadCurrent()
        val failures = mutableListOf<String>()
        bootstrap.endpoints.forEach { endpoint ->
            val result = runCatching {
                val payload = fetcher.fetch(endpoint.snapshotUrl, bootstrap.maxSnapshotBytes)
                val detachedSignature = fetcher.fetch(endpoint.signatureUrl, MAX_SIGNATURE_DOCUMENT_BYTES)
                val verified = verify(payload, detachedSignature)
                when {
                    current != null && verified.revision < current.revision -> {
                        error("拒绝目录降级：${verified.revision} < ${current.revision}")
                    }
                    current != null && verified.revision == current.revision &&
                        verified.payloadSha256 != current.payloadSha256 -> {
                        error("同一目录代次出现不同内容")
                    }
                    current != null && verified.revision == current.revision -> {
                        writeStatus(
                            outcome = "unchanged",
                            endpointId = endpoint.id,
                            revision = verified.revision,
                        )
                        KiteResourceStoreRefreshResult.Unchanged(verified.revision, endpoint.id)
                    }
                    else -> {
                        publish(verified, detachedSignature, endpoint.id)
                        invalidate()
                        writeStatus(
                            outcome = "published",
                            endpointId = endpoint.id,
                            revision = verified.revision,
                        )
                        KiteResourceStoreRefreshResult.Published(verified.revision, endpoint.id)
                    }
                }
            }
            result.getOrNull()?.let { return@synchronized it }
            val reason = result.exceptionOrNull()?.message.orEmpty().ifBlank { "未知错误" }
            failures += "${endpoint.id}: ${reason.take(MAX_STATUS_REASON_CHARS)}"
        }
        val reason = failures.joinToString("；").ifBlank { "所有远程目录均不可用" }
        writeStatus(outcome = "failed", reason = reason, revision = current?.revision)
        Log.w(TAG, "Resource store refresh failed: $reason")
        KiteResourceStoreRefreshResult.Failed(reason)
    }

    fun statusFile(): File = File(cacheDirectory, STATUS_FILE)

    private fun loadCurrent(): VerifiedSnapshot? {
        val file = File(cacheDirectory, CURRENT_FILE)
        if (!file.isFile || file.length() !in 1..MAX_CACHE_ENVELOPE_BYTES.toLong()) return null
        return runCatching {
            val envelope = JSONObject(file.readText(Charsets.UTF_8))
            check(envelope.optInt("schemaVersion") == CACHE_SCHEMA_VERSION) {
                "不支持的资源缓存版本"
            }
            val payload = Base64.getDecoder().decode(envelope.getString("payload"))
            val signature = Base64.getDecoder().decode(envelope.getString("signature"))
            verify(payload, signature)
        }.onFailure { error ->
            Log.w(TAG, "Ignored invalid resource store cache", error)
        }.getOrNull()
    }

    private fun verify(payload: ByteArray, detachedSignature: ByteArray): VerifiedSnapshot {
        check(payload.isNotEmpty() && payload.size <= bootstrap.maxSnapshotBytes) {
            "资源目录大小不合法"
        }
        check(detachedSignature.isNotEmpty() && detachedSignature.size <= MAX_SIGNATURE_DOCUMENT_BYTES) {
            "资源目录签名大小不合法"
        }
        val signatureDocument = JSONObject(detachedSignature.toString(Charsets.UTF_8))
        check(signatureDocument.optInt("schemaVersion") == SIGNATURE_SCHEMA_VERSION) {
            "不支持的签名文档版本"
        }
        val keyId = signatureDocument.optString("keyId").trim()
        val trustedKey = bootstrap.trustedKeys[keyId] ?: error("签名密钥不受信任")
        val algorithm = signatureDocument.optString("algorithm").trim()
        check(algorithm == trustedKey.algorithm && algorithm == SIGNATURE_ALGORITHM) {
            "签名算法不受支持"
        }
        val signatureBytes = Base64.getDecoder().decode(signatureDocument.getString("signature"))
        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(trustedKey.publicKeyBase64))
        )
        val verifier = Signature.getInstance(algorithm)
        verifier.initVerify(publicKey)
        verifier.update(payload)
        check(verifier.verify(signatureBytes)) { "资源目录签名无效" }

        val payloadJson = JSONObject(payload.toString(Charsets.UTF_8))
        check(payloadJson.optInt("schemaVersion") == SNAPSHOT_SCHEMA_VERSION) {
            "不支持的资源目录协议版本"
        }
        check(payloadJson.optString("channel") == bootstrap.channel) { "资源目录频道不匹配" }
        check(payloadJson.optString("keyId") == keyId) { "资源目录密钥标识不匹配" }
        val revision = payloadJson.optLong("revision", -1L)
        check(revision >= bootstrap.minimumRevision) {
            "资源目录代次低于应用安全下限"
        }
        val manifestsJson = payloadJson.optJSONObject("manifests") ?: error("资源目录缺少 manifests")
        check(manifestsJson.length() in 1..MAX_MANIFEST_COUNT) { "资源卡数量不合法" }
        val manifests = linkedMapOf<String, String>()
        val keys = manifestsJson.keys().asSequence().toList().sorted()
        keys.forEach { resourceId ->
            check(RESOURCE_ID.matches(resourceId)) { "资源 id 不合法：$resourceId" }
            val manifest = manifestsJson.optJSONObject(resourceId) ?: error("资源卡不是对象：$resourceId")
            check(manifest.optInt("schemaVersion") == MANIFEST_SCHEMA_VERSION) {
                "资源卡协议版本不受支持：$resourceId"
            }
            check(manifest.optString("id") == resourceId) { "资源卡 id 不匹配：$resourceId" }
            manifests[resourceId] = manifest.toString()
        }
        val homeLayout = payloadJson.optJSONObject("homeLayout") ?: error("资源目录缺少 homeLayout")
        check(homeLayout.optInt("schemaVersion") == HOME_LAYOUT_SCHEMA_VERSION) {
            "首页目录协议版本不受支持"
        }
        val digest = payload.sha256()
        return VerifiedSnapshot(
            revision = revision,
            payload = payload,
            payloadSha256 = digest,
            snapshot = KiteResourceDefinitionSnapshot(
                revision = "remote-$revision-${digest.take(12)}",
                manifests = manifests,
                homeLayoutJson = homeLayout.toString(),
            ),
        )
    }

    private fun publish(
        verified: VerifiedSnapshot,
        detachedSignature: ByteArray,
        endpointId: String,
    ) {
        val envelope = JSONObject()
            .put("schemaVersion", CACHE_SCHEMA_VERSION)
            .put("revision", verified.revision)
            .put("endpointId", endpointId)
            .put("cachedAt", now())
            .put("payloadSha256", verified.payloadSha256)
            .put("payload", Base64.getEncoder().encodeToString(verified.payload))
            .put("signature", Base64.getEncoder().encodeToString(detachedSignature))
            .toString()
            .toByteArray(Charsets.UTF_8)
        check(envelope.size <= MAX_CACHE_ENVELOPE_BYTES) { "资源目录缓存过大" }
        atomicReplace(File(cacheDirectory, CURRENT_FILE), envelope)
    }

    private fun writeStatus(
        outcome: String,
        endpointId: String = "",
        revision: Long? = null,
        reason: String = "",
    ) {
        runCatching {
            val status = JSONObject()
                .put("schemaVersion", STATUS_SCHEMA_VERSION)
                .put("channel", bootstrap.channel)
                .put("outcome", outcome)
                .put("endpointId", endpointId)
                .put("revision", revision ?: JSONObject.NULL)
                .put("reason", reason.take(MAX_STATUS_REASON_CHARS))
                .put("updatedAt", now())
                .toString(2)
                .toByteArray(Charsets.UTF_8)
            atomicReplace(statusFile(), status)
        }.onFailure { error ->
            Log.w(TAG, "Failed to write resource store status", error)
        }
    }

    private fun atomicReplace(target: File, bytes: ByteArray) {
        check(cacheDirectory.mkdirs() || cacheDirectory.isDirectory) {
            "无法创建资源目录缓存"
        }
        val stage = File(cacheDirectory, "$PENDING_PREFIX${UUID.randomUUID()}")
        try {
            FileOutputStream(stage).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    stage.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(stage.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            stage.delete()
        }
    }

    private fun cleanupInterruptedWrites() {
        val cutoff = now() - PENDING_MAX_AGE_MS
        cacheDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(PENDING_PREFIX) && it.lastModified() < cutoff }
            .forEach(File::delete)
    }

    private data class VerifiedSnapshot(
        val revision: Long,
        val payload: ByteArray,
        val payloadSha256: String,
        val snapshot: KiteResourceDefinitionSnapshot,
    )

    companion object {
        fun create(context: Context): KiteResourceRemoteStore {
            val appContext = context.applicationContext
            val bootstrapJson = appContext.assets.open(BOOTSTRAP_ASSET).bufferedReader().use { it.readText() }
            return KiteResourceRemoteStore(
                bootstrap = parseBootstrap(JSONObject(bootstrapJson)),
                cacheDirectory = File(appContext.filesDir, CACHE_DIRECTORY),
            )
        }

        private fun parseBootstrap(json: JSONObject): KiteResourceStoreBootstrap {
            check(json.optInt("schemaVersion") == BOOTSTRAP_SCHEMA_VERSION) {
                "不支持的资源目录引导版本"
            }
            val endpoints = json.optJSONArray("endpoints").toEndpoints()
            val keys = json.optJSONArray("trustedKeys").toTrustedKeys()
            if (endpoints.isNotEmpty()) check(keys.isNotEmpty()) { "远程资源目录缺少可信密钥" }
            return KiteResourceStoreBootstrap(
                channel = json.optString("channel").trim().ifBlank { "stable" },
                minimumRevision = json.optLong("minimumRevision", 1L).coerceAtLeast(1L),
                maxSnapshotBytes = json.optInt("maxSnapshotBytes", DEFAULT_MAX_SNAPSHOT_BYTES)
                    .coerceIn(MIN_MAX_SNAPSHOT_BYTES, MAX_MAX_SNAPSHOT_BYTES),
                endpoints = endpoints,
                trustedKeys = keys.associateBy(KiteResourceStoreTrustedKey::id),
            )
        }

        private fun JSONArray?.toEndpoints(): List<KiteResourceStoreEndpoint> = buildList {
            val array = this@toEndpoints ?: return@buildList
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val snapshotUrl = item.optString("snapshotUrl").trim()
                val signatureUrl = item.optString("signatureUrl").trim()
                if (id.isNotBlank() && snapshotUrl.isNotBlank() && signatureUrl.isNotBlank()) {
                    add(KiteResourceStoreEndpoint(id, snapshotUrl, signatureUrl))
                }
            }
        }

        private fun JSONArray?.toTrustedKeys(): List<KiteResourceStoreTrustedKey> = buildList {
            val array = this@toTrustedKeys ?: return@buildList
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val algorithm = item.optString("algorithm").trim()
                val publicKey = item.optString("publicKey").trim()
                if (id.isNotBlank() && algorithm.isNotBlank() && publicKey.isNotBlank()) {
                    add(KiteResourceStoreTrustedKey(id, algorithm, publicKey))
                }
            }
        }

        private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

        private const val TAG = "KiteResourceStore"
        private const val BOOTSTRAP_ASSET = "resource-store/bootstrap.json"
        private const val CACHE_DIRECTORY = "resource-store/stable"
        private const val CURRENT_FILE = "current.json"
        private const val STATUS_FILE = "status.json"
        private const val PENDING_PREFIX = ".pending-"
        private const val BOOTSTRAP_SCHEMA_VERSION = 1
        private const val SNAPSHOT_SCHEMA_VERSION = 1
        private const val SIGNATURE_SCHEMA_VERSION = 1
        private const val CACHE_SCHEMA_VERSION = 1
        private const val STATUS_SCHEMA_VERSION = 1
        private const val MANIFEST_SCHEMA_VERSION = 1
        private const val HOME_LAYOUT_SCHEMA_VERSION = 1
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val DEFAULT_MAX_SNAPSHOT_BYTES = 1024 * 1024
        private const val MIN_MAX_SNAPSHOT_BYTES = 64 * 1024
        private const val MAX_MAX_SNAPSHOT_BYTES = 4 * 1024 * 1024
        private const val MAX_SIGNATURE_DOCUMENT_BYTES = 16 * 1024
        private const val MAX_CACHE_ENVELOPE_BYTES = 6 * 1024 * 1024
        private const val MAX_MANIFEST_COUNT = 512
        private const val MAX_STATUS_REASON_CHARS = 2048
        private const val PENDING_MAX_AGE_MS = 24 * 60 * 60 * 1000L
        private val RESOURCE_ID = Regex("kite\\.[a-z0-9][a-z0-9._-]{1,127}")
    }
}

internal class HttpKiteResourceStoreFetcher : KiteResourceStoreFetcher {
    override fun fetch(url: String, maxBytes: Int): ByteArray = fetch(URL(url), maxBytes, redirectCount = 0)

    private fun fetch(url: URL, maxBytes: Int, redirectCount: Int): ByteArray {
        check(url.protocol.equals("https", ignoreCase = true)) { "资源目录只允许 HTTPS" }
        check(redirectCount <= MAX_REDIRECTS) { "资源目录重定向过多" }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json, text/plain;q=0.9")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            val code = connection.responseCode
            if (code in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location").orEmpty()
                check(location.isNotBlank()) { "资源目录重定向缺少地址" }
                return fetch(URL(url, location), maxBytes, redirectCount + 1)
            }
            check(code in 200..299) { "资源目录 HTTP $code" }
            val declaredLength = connection.contentLengthLong
            check(declaredLength < 0L || declaredLength <= maxBytes.toLong()) { "资源目录响应过大" }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= maxBytes) { "资源目录响应过大" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_REDIRECTS = 3
        const val USER_AGENT = "Kite-Resource-Store/1"
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
