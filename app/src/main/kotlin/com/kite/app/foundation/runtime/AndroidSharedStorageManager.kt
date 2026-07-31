package com.kite.app.foundation.runtime

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import com.kite.app.foundation.workspace.AndroidSharedStorageVolume
import com.kite.app.foundation.workspace.AndroidSharedStorageVolumePlan
import java.io.File

data class AndroidSharedStorageSnapshot(
    val accessGranted: Boolean,
    val volumes: List<AndroidSharedStorageVolume>,
    val unavailableReason: String? = null
)

/**
 * 安卓共享存储的事实拥有者。
 *
 * 这里只发现 Android 已授权给 Kite 的真实卷根，并生成同路径 PRoot bind；
 * 不负责文件复制、目录同步或项目工作区登记。
 */
object AndroidSharedStorageManager {

    fun snapshot(context: Context): AndroidSharedStorageSnapshot {
        val appContext = context.applicationContext
        if (!hasBroadFileAccess(appContext)) {
            return AndroidSharedStorageSnapshot(
                accessGranted = false,
                volumes = emptyList(),
                unavailableReason = "尚未获得手机文件管理权限"
            )
        }

        val roots = buildList {
            @Suppress("DEPRECATION")
            add(Environment.getExternalStorageDirectory().absolutePath to "手机存储")

            val storageManager = appContext.getSystemService(StorageManager::class.java)
            storageManager?.storageVolumes.orEmpty().forEach { volume ->
                val directory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    volume.directory
                } else {
                    resolveLegacyVolumeDirectory(appContext, volume.uuid)
                } ?: return@forEach
                val label = runCatching { volume.getDescription(appContext) }
                    .getOrNull()
                    .orEmpty()
                    .ifBlank { directory.name }
                add(directory.absolutePath to label)
            }
        }
        val volumes = AndroidSharedStorageVolumePlan.fromRoots(roots)
            .filter { File(it.hostPath).isDirectory }
        return AndroidSharedStorageSnapshot(
            accessGranted = true,
            volumes = volumes,
            unavailableReason = if (volumes.isEmpty()) "没有发现可直接访问的安卓存储卷" else null
        )
    }

    fun bindMounts(snapshot: AndroidSharedStorageSnapshot): List<ProotBindMount> {
        if (!snapshot.accessGranted) return emptyList()
        return snapshot.volumes.mapIndexed { index, volume ->
            ProotBindMount(
                sourcePath = volume.hostPath,
                targetPath = volume.containerPath,
                role = "android_shared_storage_$index"
            )
        }
    }

    fun containerRoots(context: Context): List<String> = snapshot(context).volumes.map { it.containerPath }

    fun hasBroadFileAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            val writeGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            readGranted && writeGranted
        }
    }

    private fun resolveLegacyVolumeDirectory(context: Context, uuid: String?): File? {
        val packageSuffix = "/Android/data/${context.packageName}/files"
        return context.getExternalFilesDirs(null)
            .asSequence()
            .filterNotNull()
            .map { it.absolutePath }
            .filter { it.endsWith(packageSuffix) }
            .map { File(it.removeSuffix(packageSuffix)) }
            .firstOrNull { candidate ->
                if (uuid.isNullOrBlank()) {
                    candidate.absolutePath == Environment.getExternalStorageDirectory().absolutePath
                } else {
                    candidate.name.equals(uuid, ignoreCase = true)
                }
            }
    }
}
