package com.kite.app.foundation.storage

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Build-authored integrity contract for a directory artifact.
 *
 * Full file hashing happens only while staging. Published artifacts use the immutable receipt,
 * exact file set, and declared lengths as the fast reuse check on ordinary launches.
 */
internal class ImmutableArtifactIntegrity private constructor(
    val packageId: String,
    val version: Int,
    val contentDigest: String,
    private val integrityManifestSha256: String,
    private val files: List<Entry>,
) {
    val artifactKey: String = "$packageId:$version:$contentDigest"

    fun validateStageAndSeal(root: File) {
        check(root.isDirectory) { "Artifact stage is not a directory: ${root.absolutePath}" }
        check(integrityManifestMatches(root)) {
            "Artifact integrity manifest does not match the build manifest"
        }
        check(actualFilePaths(root) - RECEIPT_FILE == expectedFilePaths()) {
            "Artifact file set does not match the build manifest"
        }
        files.forEach { entry ->
            val file = File(root, entry.path)
            check(file.isFile && file.length() == entry.size) {
                "Artifact file size mismatch: ${entry.path}"
            }
            check(sha256(file) == entry.sha256) {
                "Artifact file digest mismatch: ${entry.path}"
            }
        }
        writeReceipt(root)
        sealReadOnly(root)
    }

    fun isPublished(root: File): Boolean = runCatching {
        if (!root.isDirectory || !integrityManifestMatches(root)) return@runCatching false
        if (!isSealedReadOnly(root)) return@runCatching false
        if (actualFilePaths(root) != expectedFilePaths() + RECEIPT_FILE) return@runCatching false
        if (files.any { entry ->
                File(root, entry.path).let { file -> !file.isFile || file.length() != entry.size }
            }
        ) {
            return@runCatching false
        }
        val receipt = JSONObject(File(root, RECEIPT_FILE).readText())
        receipt.optInt("schemaVersion") == RECEIPT_SCHEMA_VERSION &&
            receipt.optString("artifactKey") == artifactKey &&
            receipt.optString("integrityManifestSha256") == integrityManifestSha256 &&
            receipt.optInt("fileCount") == files.size &&
            receipt.optLong("totalBytes") == files.sumOf(Entry::size)
    }.getOrDefault(false)

    private fun integrityManifestMatches(root: File): Boolean {
        val manifest = File(root, INTEGRITY_FILE)
        return manifest.isFile && sha256(manifest) == integrityManifestSha256
    }

    private fun actualFilePaths(root: File): Set<String> = root.walkTopDown()
        .filter(File::isFile)
        .map { file -> file.relativeTo(root).invariantSeparatorsPath }
        .toSet()

    private fun expectedFilePaths(): Set<String> = files.mapTo(linkedSetOf(), Entry::path) + INTEGRITY_FILE

    private fun writeReceipt(root: File) {
        val receipt = File(root, RECEIPT_FILE)
        receipt.setWritable(true, true)
        receipt.writeText(
            JSONObject()
                .put("schemaVersion", RECEIPT_SCHEMA_VERSION)
                .put("artifactKey", artifactKey)
                .put("integrityManifestSha256", integrityManifestSha256)
                .put("fileCount", files.size)
                .put("totalBytes", files.sumOf(Entry::size))
                .toString() + "\n",
            Charsets.UTF_8,
        )
    }

    private fun sealReadOnly(root: File) {
        val posix = supportsPosixPermissions()
        root.walkBottomUp().forEach { path ->
            if (posix) {
                val permissions = Files.getPosixFilePermissions(path.toPath()).toMutableSet().apply {
                    remove(PosixFilePermission.OWNER_WRITE)
                    remove(PosixFilePermission.GROUP_WRITE)
                    remove(PosixFilePermission.OTHERS_WRITE)
                }
                Files.setPosixFilePermissions(path.toPath(), permissions)
            } else {
                path.setWritable(false, false)
            }
        }
    }

    private fun isSealedReadOnly(root: File): Boolean {
        if (!supportsPosixPermissions()) return true
        return root.walkTopDown().none { path ->
            Files.getPosixFilePermissions(path.toPath()).any { permission ->
                permission == PosixFilePermission.OWNER_WRITE ||
                    permission == PosixFilePermission.GROUP_WRITE ||
                    permission == PosixFilePermission.OTHERS_WRITE
            }
        }
    }

    private fun supportsPosixPermissions(): Boolean =
        FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    private data class Entry(
        val path: String,
        val size: Long,
        val sha256: String,
    )

    companion object {
        const val INTEGRITY_FILE = "integrity.json"
        const val RECEIPT_FILE = ".kite-artifact-receipt.json"
        private const val SCHEMA_VERSION = 1
        private const val RECEIPT_SCHEMA_VERSION = 1
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")

        fun parse(rawManifest: String): ImmutableArtifactIntegrity {
            val json = JSONObject(rawManifest)
            require(json.optInt("schemaVersion") == SCHEMA_VERSION) {
                "Unsupported artifact integrity schema"
            }
            val packageId = json.getString("packageId").trim()
            val version = json.getInt("version")
            val contentDigest = json.getString("contentDigest").trim().lowercase()
            require(packageId.isNotBlank() && version > 0 && SHA256_PATTERN.matches(contentDigest)) {
                "Invalid artifact identity"
            }
            val jsonFiles = json.getJSONArray("files")
            val entries = buildList {
                repeat(jsonFiles.length()) { index ->
                    val item = jsonFiles.getJSONObject(index)
                    val path = requireSafeRelativePath(item.getString("path"))
                    val size = item.getLong("size")
                    val sha256 = item.getString("sha256").trim().lowercase()
                    require(size >= 0L && SHA256_PATTERN.matches(sha256)) {
                        "Invalid artifact entry: $path"
                    }
                    add(Entry(path = path, size = size, sha256 = sha256))
                }
            }.sortedBy(Entry::path)
            require(entries.isNotEmpty() && entries.map(Entry::path).distinct().size == entries.size) {
                "Artifact integrity manifest has no files or duplicate paths"
            }
            require(computeContentDigest(entries) == contentDigest) {
                "Artifact content digest does not match its entries"
            }
            return ImmutableArtifactIntegrity(
                packageId = packageId,
                version = version,
                contentDigest = contentDigest,
                integrityManifestSha256 = sha256(rawManifest.toByteArray(Charsets.UTF_8)),
                files = entries,
            )
        }

        private fun requireSafeRelativePath(value: String): String {
            val path = value.trim()
            require(
                path.isNotBlank() &&
                    '\\' !in path &&
                    !path.startsWith('/') &&
                    path.split('/').none { it.isBlank() || it == "." || it == ".." } &&
                    path != INTEGRITY_FILE &&
                    path != RECEIPT_FILE,
            ) { "Unsafe artifact path: $value" }
            return path
        }

        private fun computeContentDigest(entries: List<Entry>): String {
            val canonical = buildString {
                entries.forEach { entry ->
                    append(entry.path)
                    append('\u0000')
                    append(entry.size)
                    append('\u0000')
                    append(entry.sha256)
                    append('\n')
                }
            }
            return sha256(canonical.toByteArray(Charsets.UTF_8))
        }

        private fun sha256(file: File): String = file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
            digest.digest().toHex()
        }

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .toHex()

        private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
    }
}
