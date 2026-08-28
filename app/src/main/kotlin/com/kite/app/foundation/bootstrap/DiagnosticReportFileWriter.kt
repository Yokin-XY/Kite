package com.kite.app.foundation.bootstrap

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.io.Writer

data class GeneratedDiagnosticReport(
    val displayName: String,
    val displayPath: String,
    val uri: Uri,
)

/** 把诊断原文按需写到用户可见的下载目录，不在页面或剪贴板中展开。 */
object DiagnosticReportFileWriter {
    private const val REPORT_DIRECTORY = "Kite"

    fun write(
        context: Context,
        displayName: String,
        writeContent: (Writer) -> Unit,
    ): GeneratedDiagnosticReport {
        require(displayName.endsWith(".txt", ignoreCase = true)) { "report_name_must_be_txt" }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeWithMediaStore(context.applicationContext, displayName, writeContent)
        } else {
            writeLegacy(displayName, writeContent)
        }
    }

    private fun writeWithMediaStore(
        context: Context,
        displayName: String,
        writeContent: (Writer) -> Unit,
    ): GeneratedDiagnosticReport {
        val resolver = context.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$REPORT_DIRECTORY"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
            "report_media_store_insert_failed"
        }
        try {
            checkNotNull(resolver.openOutputStream(uri, "w")) { "report_output_stream_unavailable" }
                .use { output ->
                    BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use(writeContent)
                }
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
        return GeneratedDiagnosticReport(
            displayName = displayName,
            displayPath = "下载/$REPORT_DIRECTORY/$displayName",
            uri = uri,
        )
    }

    @Suppress("DEPRECATION")
    private fun writeLegacy(
        displayName: String,
        writeContent: (Writer) -> Unit,
    ): GeneratedDiagnosticReport {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            REPORT_DIRECTORY,
        )
        check(directory.exists() || directory.mkdirs()) { "report_directory_create_failed:${directory.absolutePath}" }
        val file = File(directory, displayName)
        file.bufferedWriter(Charsets.UTF_8).use(writeContent)
        return GeneratedDiagnosticReport(
            displayName = displayName,
            displayPath = file.absolutePath,
            uri = Uri.fromFile(file),
        )
    }
}
