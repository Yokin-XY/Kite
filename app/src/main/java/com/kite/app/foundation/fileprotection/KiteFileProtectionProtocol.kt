package com.kite.app.foundation.fileprotection

import com.kite.app.application.fileprotection.FileProtectionBackendId
import java.nio.charset.StandardCharsets

internal const val KITE_FILE_PROTECTION_CONTROL_SCHEMA = "kf_file_protection_v1"
internal const val KITE_FILE_PROTECTION_ENTRY_SCHEMA = "kf_file_protection_entry_v1"
internal const val KITE_FILE_PROTECTION_LEGACY_CONTROL_SCHEMA = "kf_resource_txn_v1"
internal const val KITE_FILE_PROTECTION_LEGACY_ENTRY_SCHEMA = "kf_resource_txn_entry_v1"

internal data class KiteFileProtectionControl(
    val generation: Long,
    val operationId: String,
    val rootHostPath: String,
    val journalHostPath: String,
    val maxJournalBytes: Long,
    val backendId: FileProtectionBackendId
)

internal enum class KiteFileProtectionBeforeKind(val wireValue: String) {
    Absent("absent"),
    File("file"),
    Directory("directory"),
    Symlink("symlink");

    companion object {
        fun parse(value: String): KiteFileProtectionBeforeKind? = entries.firstOrNull {
            it.wireValue == value.trim()
        }
    }
}

internal enum class KiteFileProtectionStorageMode(val wireValue: String) {
    WholeObject("whole_object"),
    RangeUndo("range_undo");

    companion object {
        fun parse(value: String): KiteFileProtectionStorageMode? = entries.firstOrNull {
            it.wireValue == value.trim()
        }
    }
}

internal data class KiteFileProtectionEntry(
    val relativePath: String,
    val beforeKind: KiteFileProtectionBeforeKind,
    val mode: Int,
    val linkTarget: String = "",
    val storageMode: KiteFileProtectionStorageMode = KiteFileProtectionStorageMode.WholeObject,
    val originalSize: Long = -1L
) {
    val depth: Int
        get() = if (relativePath == ".") 0 else relativePath.count { it == '/' } + 1
}

/**
 * Android 与 PRoot 之间的业务无关控制协议。
 *
 * 当前 PRoot 同时识别通用 schema 和旧资源事务 schema；新操作发布通用格式，
 * decode 保留双读以恢复升级前已经落盘的操作。
 */
internal object KiteFileProtectionProtocol {
    enum class WireVersion { LegacyV1, FileProtectionV1 }

    private val safeIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

    fun encodeControl(
        control: KiteFileProtectionControl,
        wireVersion: WireVersion = WireVersion.FileProtectionV1
    ): String {
        validate(control)
        return buildString {
            when (wireVersion) {
                WireVersion.LegacyV1 -> {
                    appendLine("schema=$KITE_FILE_PROTECTION_LEGACY_CONTROL_SCHEMA")
                    appendLine("generation=${control.generation}")
                    appendLine("transaction_id=${control.operationId}")
                    appendLine("root_path=${control.rootHostPath}")
                    appendLine("entries_path=${control.journalHostPath}")
                    appendLine("max_backup_bytes=${control.maxJournalBytes}")
                    appendLine("backend=${control.backendId.wireValue}")
                }
                WireVersion.FileProtectionV1 -> {
                    appendLine("schema=$KITE_FILE_PROTECTION_CONTROL_SCHEMA")
                    appendLine("generation=${control.generation}")
                    appendLine("operation_id=${control.operationId}")
                    appendLine("root_path=${control.rootHostPath}")
                    appendLine("journal_path=${control.journalHostPath}")
                    appendLine("max_journal_bytes=${control.maxJournalBytes}")
                    appendLine("backend=${control.backendId.wireValue}")
                }
            }
        }
    }

    fun decodeControl(raw: String): KiteFileProtectionControl? {
        val fields = parseFields(raw) ?: return null
        return runCatching {
            val legacy = fields["schema"] == KITE_FILE_PROTECTION_LEGACY_CONTROL_SCHEMA
            if (!legacy && fields["schema"] != KITE_FILE_PROTECTION_CONTROL_SCHEMA) error("unknown schema")
            KiteFileProtectionControl(
                generation = fields.getValue("generation").toLong(),
                operationId = fields.getValue(if (legacy) "transaction_id" else "operation_id"),
                rootHostPath = fields.getValue("root_path"),
                journalHostPath = fields.getValue(if (legacy) "entries_path" else "journal_path"),
                maxJournalBytes = fields.getValue(if (legacy) "max_backup_bytes" else "max_journal_bytes").toLong(),
                backendId = fields["backend"]
                    ?.let { wire -> FileProtectionBackendId.entries.firstOrNull { it.wireValue == wire } }
                    ?: FileProtectionBackendId.WholeObjectPreimage
            ).also(::validate)
        }.getOrNull()
    }

    fun encodeEntry(entry: KiteFileProtectionEntry): String {
        val path = normalizeRelativePath(entry.relativePath)
            ?: throw IllegalArgumentException("unsafe relative path")
        require(entry.mode in 0..0xFFFF) { "mode outside supported range" }
        require(entry.beforeKind == KiteFileProtectionBeforeKind.Symlink || entry.linkTarget.isEmpty()) {
            "link target is only valid for symlinks"
        }
        require(entry.storageMode != KiteFileProtectionStorageMode.RangeUndo || entry.beforeKind == KiteFileProtectionBeforeKind.File) {
            "range undo is only valid for an existing file"
        }
        require(entry.storageMode != KiteFileProtectionStorageMode.RangeUndo || entry.originalSize >= 0L) {
            "range undo requires original size"
        }
        return buildString {
            appendLine("schema=$KITE_FILE_PROTECTION_ENTRY_SCHEMA")
            appendLine("capture_state=complete")
            appendLine("relative_path_hex=${path.toHex()}")
            appendLine("before_kind=${entry.beforeKind.wireValue}")
            appendLine("mode=${entry.mode}")
            appendLine("link_target_hex=${entry.linkTarget.toHex()}")
            appendLine("storage_mode=${entry.storageMode.wireValue}")
            appendLine("original_size=${entry.originalSize}")
        }
    }

    fun decodeEntry(raw: String): KiteFileProtectionEntry? {
        val fields = parseFields(raw) ?: return null
        if (fields["schema"] !in setOf(KITE_FILE_PROTECTION_ENTRY_SCHEMA, KITE_FILE_PROTECTION_LEGACY_ENTRY_SCHEMA)) {
            return null
        }
        if (fields["capture_state"] != "complete") return null
        return runCatching {
            val relativePath = fields.getValue("relative_path_hex").hexToString()
            val kind = KiteFileProtectionBeforeKind.parse(fields.getValue("before_kind"))
                ?: error("unknown before kind")
            KiteFileProtectionEntry(
                relativePath = normalizeRelativePath(relativePath) ?: error("unsafe relative path"),
                beforeKind = kind,
                mode = fields.getValue("mode").toInt(),
                linkTarget = fields.getValue("link_target_hex").hexToString(),
                storageMode = fields["storage_mode"]
                    ?.let(KiteFileProtectionStorageMode::parse)
                    ?: KiteFileProtectionStorageMode.WholeObject,
                originalSize = fields["original_size"]?.toLongOrNull() ?: -1L
            ).also(::encodeEntry)
        }.getOrNull()
    }

    fun normalizeRelativePath(value: String): String? {
        val clean = value.trim().replace('\\', '/')
        if (clean.startsWith('/')) return null
        if ('\u0000' in clean || '\n' in clean || '\r' in clean) return null
        val segments = clean.split('/').filterNot { it.isBlank() || it == "." }
        if (segments.any { it == ".." }) return null
        return segments.joinToString("/").ifBlank { "." }
    }

    private fun validate(control: KiteFileProtectionControl) {
        require(control.generation > 0L) { "protection generation must be positive" }
        require(safeIdPattern.matches(control.operationId)) { "unsafe operation id" }
        requireAbsoluteHostPath(control.rootHostPath, "root")
        requireAbsoluteHostPath(control.journalHostPath, "journal")
        require(control.journalHostPath != control.rootHostPath) { "journal path must stay outside root" }
        require(control.maxJournalBytes > 0L) { "max journal bytes must be positive" }
    }

    private fun parseFields(raw: String): Map<String, String>? {
        val result = linkedMapOf<String, String>()
        for (line in raw.lineSequence()) {
            if (line.isBlank()) continue
            val separator = line.indexOf('=')
            if (separator <= 0) return null
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            if (!Regex("[a-z][a-z0-9_]*").matches(key) || key in result) return null
            result[key] = value
        }
        return result
    }

    private fun requireAbsoluteHostPath(value: String, label: String) {
        require(value.startsWith('/')) { "$label path must be absolute" }
        require('\u0000' !in value && '\n' !in value && '\r' !in value) { "$label path contains control data" }
    }

    private fun String.toHex(): String = toByteArray(StandardCharsets.UTF_8).joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }

    private fun String.hexToString(): String {
        require(length % 2 == 0 && all { it.digitToIntOrNull(16) != null }) { "invalid hex" }
        val bytes = ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        return bytes.toString(StandardCharsets.UTF_8)
    }
}
