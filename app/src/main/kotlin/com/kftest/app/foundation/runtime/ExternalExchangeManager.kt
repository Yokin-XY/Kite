package com.kftest.app.foundation.runtime

import android.content.Context
import android.os.Environment
import com.kftest.app.foundation.logging.Logger
import java.io.File

object ExternalExchangeManager {

    private const val LOG_TAG = "ExternalExchange"
    private const val EXCHANGE_ROOT_NAME = "KF"
    private const val README_FILE_NAME = "README.txt"
    private const val FILES_DIR_NAME = "files"
    private const val DELIVERY_IN_DIR_NAME = "in"
    private const val DELIVERY_OUT_DIR_NAME = "out"
    private const val GROUPED_FILES_DIR_NAME = "files"
    private const val CARDS_DIR_NAME = "cards"
    private const val IMPORTS_DIR_NAME = "imports"
    const val CONTAINER_DELIVERY_PATH = "/chuan"
    const val CONTAINER_ALBUM_PATH = "/xiangce"
    const val CONTAINER_MOUNT_PATH = "/exchange"
    const val CONTAINER_CARDS_PATH = "/exchange/cards"

    @Synchronized
    fun ensureExchangeDir(context: Context): File {
        val appContext = context.applicationContext
        val exchangeDir = resolveHostExchangeDir(appContext)
        if (!exchangeDir.exists()) {
            val created = exchangeDir.mkdirs()
            Logger.i(LOG_TAG, "Create external delivery directory: path=${exchangeDir.absolutePath} created=$created")
        }
        ensureDeliverySubdirs(exchangeDir)
        ensureReadme(exchangeDir)
        return exchangeDir
    }

    @Synchronized
    fun ensureCardsDir(context: Context): File {
        return File(ensureExchangeDir(context), CARDS_DIR_NAME).apply {
            if (!exists()) {
                val created = mkdirs()
                Logger.i(LOG_TAG, "Prepare cards directory: path=$absolutePath created=$created")
            }
        }
    }

    @Synchronized
    fun ensureImportsDir(context: Context): File {
        return File(ensureExchangeDir(context), IMPORTS_DIR_NAME).apply {
            if (!exists()) {
                val created = mkdirs()
                Logger.i(LOG_TAG, "Prepare imports directory: path=$absolutePath created=$created")
            }
        }
    }

    @Synchronized
    fun ensureAlbumDir(): File {
        val picturesRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return try {
            if (!picturesRoot.exists()) {
                picturesRoot.mkdirs()
            }
            picturesRoot
        } catch (throwable: Throwable) {
            Logger.i(LOG_TAG, "Public Pictures directory is not available: ${throwable.message}")
            picturesRoot
        }
    }

    private fun resolveHostExchangeDir(context: Context): File {
        resolveDownloadRootIfWritable()?.let { return it }

        // Production and development builds share the same package-stable media directory.
        val sharedPackageName = "com.kftest.app"
        val mediaRoot = context.externalMediaDirs
            .asSequence()
            .filterNotNull()
            .map { normalizeMediaRoot(it, sharedPackageName) }
            .firstOrNull()
            ?: File("/sdcard/Android/media/${sharedPackageName}")

        return if (mediaRoot.name == EXCHANGE_ROOT_NAME) {
            mediaRoot
        } else {
            File(mediaRoot, EXCHANGE_ROOT_NAME)
        }
    }

    private fun resolveDownloadRootIfWritable(): File? {
        val downloadRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return try {
            if (!downloadRoot.exists() && !downloadRoot.mkdirs()) {
                Logger.i(LOG_TAG, "Public Download directory cannot be created; falling back to app media: ${downloadRoot.absolutePath}")
                return null
            }
            val probe = File(downloadRoot, ".kfshell_write_probe")
            probe.writeText("ok")
            probe.delete()
            File(downloadRoot, EXCHANGE_ROOT_NAME)
        } catch (throwable: Throwable) {
            Logger.i(LOG_TAG, "Public Download directory is not writable; falling back to app media: ${throwable.message}")
            null
        }
    }

    private fun normalizeMediaRoot(candidate: File, packageName: String): File {
        if (candidate.name == packageName) {
            return candidate
        }
        if (candidate.name == FILES_DIR_NAME && candidate.parentFile?.name == packageName) {
            return candidate.parentFile ?: candidate
        }
        return candidate
    }

    private fun ensureDeliverySubdirs(exchangeDir: File) {
        listOf(
            DELIVERY_IN_DIR_NAME,
            DELIVERY_OUT_DIR_NAME,
            CARDS_DIR_NAME,
            IMPORTS_DIR_NAME,
            "$GROUPED_FILES_DIR_NAME/$DELIVERY_IN_DIR_NAME",
            "$GROUPED_FILES_DIR_NAME/$DELIVERY_OUT_DIR_NAME"
        ).forEach { name ->
            val dir = File(exchangeDir, name)
            if (!dir.exists()) {
                val created = dir.mkdirs()
                Logger.i(LOG_TAG, "Prepare delivery subdirectory: path=${dir.absolutePath} created=$created")
            }
        }
    }

    private fun ensureReadme(exchangeDir: File) {
        val readme = File(exchangeDir, README_FILE_NAME)
        if (readme.exists()) {
            return
        }
        readme.writeText(
            """
            KFShell file delivery area.

            Container paths:
            - /chuan: preferred short transfer path.
            - /chuan/in: files delivered from Android into Linux.
            - /chuan/out: files exported from Linux back to Android.
            - /exchange/cards: shared Kite card JSON directory.
            - /exchange: compatibility alias for existing scripts.

            If Android allows public Download access, this directory is backed by Download/KF.
            If not, KFShell falls back to its app media directory.

            Keep shared app data here: cards, imports, delivery files, and export files.
            Move real projects into /workspace before building or editing heavily.
            """.trimIndent() + "\n"
        )
    }
}
