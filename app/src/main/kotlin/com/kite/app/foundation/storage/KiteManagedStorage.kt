package com.kite.app.foundation.storage

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Kite 自己拥有的数据目录。
 *
 * 卡片注册、待导入文件和诊断资料属于应用状态，不再与用户的安卓文件投递目录混在一起。
 * 用户文件则继续使用 Android 的真实共享存储路径。
 */
object KiteManagedStorage {
    private const val REGISTRY_ROOT = "registries"
    private const val HOME_CARDS_DIR = "home-cards"
    private const val CARD_IMPORTS_DIR = "card-imports"
    private const val CARD_LOGS_DIR = "card-logs"

    fun registryRootDir(context: Context): File =
        File(context.applicationContext.filesDir, REGISTRY_ROOT).apply {
            if (!exists() && !mkdirs()) error("managed_directory_failed:$absolutePath")
            if (!isDirectory) error("managed_path_not_directory:$absolutePath")
        }

    fun homeCardsDir(context: Context): File = managedDir(context, HOME_CARDS_DIR)

    fun cardImportsDir(context: Context): File = managedDir(context, CARD_IMPORTS_DIR)

    fun cardLogsDir(context: Context): File = managedDir(context, CARD_LOGS_DIR)

    @Suppress("DEPRECATION")
    fun publicDownloadsDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }

    fun rootfsImportCandidates(imageDirName: String): List<File> = listOf(
        "$imageDirName-rootfs.tgz",
        "$imageDirName-rootfs.tar.gz",
        "$imageDirName-rootfs.tar",
    ).map { File(publicDownloadsDir(), it) }

    private fun managedDir(context: Context, name: String): File =
        File(registryRootDir(context), name).apply {
            if (!exists() && !mkdirs()) error("managed_directory_failed:$absolutePath")
            if (!isDirectory) error("managed_path_not_directory:$absolutePath")
        }
}
