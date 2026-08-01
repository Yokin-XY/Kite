package com.kite.app.foundation.runtime

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.kite.app.foundation.contracts.ContainerRecord
import com.kite.app.foundation.workspace.WorkspaceBuildSupport
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/** 完整可变修复完成后的跨进程物理收据；任何不一致都只返回 false。 */
internal object DefaultContainerColdReuseReceipt {
    private const val SCHEMA = 1
    private const val FILE_NAME = ".kf-container-cold-reuse-receipt.json"
    private const val PERMISSION_MASK = 0xFFF

    fun isCurrent(
        context: Context,
        layout: AssetExtractor.RuntimeLayout,
        container: ContainerRecord,
        hostTimeZoneId: String,
    ): Boolean = runCatching {
        val receipt = JSONObject(receiptFile(container).readText())
        val appInstall = appInstallStamp(context)
        receipt.optInt("schema", -1) == SCHEMA &&
            receipt.optLong("appVersionCode", -1L) == appInstall.versionCode &&
            receipt.optLong("appLastUpdateTime", -1L) == appInstall.lastUpdateTime &&
            receipt.optString("profile") == layout.profile.codename &&
            receipt.optString("containerId") == container.id &&
            receipt.optLong("containerCreatedAt", -1L) == container.createdAt &&
            receipt.optString("hostTimeZoneId") == hostTimeZoneId &&
            receipt.optString("proofDigest") == proofDigest(layout, container)
    }.getOrDefault(false)

    fun write(
        context: Context,
        layout: AssetExtractor.RuntimeLayout,
        container: ContainerRecord,
        hostTimeZoneId: String,
    ) {
        val appInstall = appInstallStamp(context)
        val content = JSONObject()
            .put("schema", SCHEMA)
            .put("appVersionCode", appInstall.versionCode)
            .put("appLastUpdateTime", appInstall.lastUpdateTime)
            .put("profile", layout.profile.codename)
            .put("containerId", container.id)
            .put("containerCreatedAt", container.createdAt)
            .put("hostTimeZoneId", hostTimeZoneId)
            .put("proofDigest", proofDigest(layout, container))
            .toString(2) + "\n"
        val receipt = receiptFile(container)
        if (!receipt.exists() || receipt.readText() != content) {
            receipt.parentFile?.mkdirs()
            receipt.writeText(content)
        }
    }

    private fun receiptFile(container: ContainerRecord): File = File(container.rootfsPath, FILE_NAME)

    private fun proofDigest(
        layout: AssetExtractor.RuntimeLayout,
        container: ContainerRecord,
    ): String {
        val rootfs = File(container.rootfsPath)
        val workspace = File(container.workspacePath)
        val workspaceProof = WorkspaceBuildSupport.coldReuseProofPaths(workspace)
        val runtimeFiles = listOf(
            layout.prootFile,
            layout.prootLibtallocFile,
            layout.prootLoaderFile,
            layout.prootLoader32File,
            layout.prootRuntimeDescriptorFile,
        )
        val rootfsDirectories = listOf(
            "tmp",
            "var/tmp",
            "run",
            "var/log",
            "var/log/supervisor",
            "run/supervisor",
        ).map { relative -> File(rootfs, relative) }
        val rootfsFiles = buildList {
            add(File(rootfs, "etc/apt/sources.list"))
            add(File(rootfs, "etc/nsswitch.conf"))
            add(File(rootfs, "etc/group"))
            add(File(rootfs, "etc/timezone"))
            add(File(rootfs, "etc/localtime"))
            add(File(rootfs, ".kf-container-bootstrap-ready"))
            add(File(rootfs, ".kf-container-rootfs-ready"))
            addAll(
                listOf(
                    "bin/sh",
                    "bin/bash",
                    "usr/bin/env",
                    "var/lib/dpkg/status",
                ).map { relative -> File(rootfs, relative) },
            )
        }
        val lines = buildList {
            runtimeFiles.forEach { file -> add(fileLine("runtime", layout.runtimeRoot, file)) }
            rootfsDirectories.forEach { file -> add(directoryLine("rootfs", rootfs, file)) }
            rootfsFiles.forEach { file -> add(fileLine("rootfs", rootfs, file)) }
            workspaceProof.directories.forEach { file -> add(directoryLine("workspace", workspace, file)) }
            workspaceProof.staticFiles.forEach { file -> add(fileLine("workspace", workspace, file)) }
            workspaceProof.requiredPaths.forEach { file ->
                add("required|workspace/${relativePath(workspace, file)}|exists=${file.exists()}")
            }
            add("profile|${layout.profile.codename}")
        }.sorted().joinToString("\n")
        return sha256(lines.toByteArray())
    }

    private fun directoryLine(scope: String, root: File, file: File): String =
        stat(file).let { value ->
            "dir|$scope/${relativePath(root, file)}|exists=${value?.isDirectory == true}|" +
                "mode=${value?.mode ?: -1}"
        }

    private fun fileLine(scope: String, root: File, file: File): String {
        val value = stat(file)
        val digest = if (value?.isSymbolicLink == true) {
            runCatching { "link:${Os.readlink(file.absolutePath)}" }.getOrDefault("link:unreadable")
        } else {
            "metadata"
        }
        return "file|$scope/${relativePath(root, file)}|kind=${value?.kind ?: "missing"}|" +
            "length=${value?.length ?: 0L}|modified=${value?.modified ?: 0L}|" +
            "mode=${value?.mode ?: -1}|digest=$digest"
    }

    private fun relativePath(root: File, file: File): String = runCatching {
        file.absoluteFile.normalize().relativeTo(root.absoluteFile.normalize()).invariantSeparatorsPath
    }.getOrDefault(file.name)

    private data class PhysicalStat(
        val kind: String,
        val length: Long,
        val modified: Long,
        val mode: Int,
        val isDirectory: Boolean,
        val isRegular: Boolean,
        val isSymbolicLink: Boolean,
    )

    private fun stat(file: File): PhysicalStat? = runCatching {
        val value = Os.lstat(file.absolutePath)
        val isDirectory = OsConstants.S_ISDIR(value.st_mode)
        val isRegular = OsConstants.S_ISREG(value.st_mode)
        val isSymbolicLink = OsConstants.S_ISLNK(value.st_mode)
        PhysicalStat(
            kind = when {
                isDirectory -> "directory"
                isRegular -> "file"
                isSymbolicLink -> "symlink"
                else -> "other"
            },
            length = value.st_size,
            modified = value.st_mtime,
            mode = value.st_mode and PERMISSION_MASK,
            isDirectory = isDirectory,
            isRegular = isRegular,
            isSymbolicLink = isSymbolicLink,
        )
    }.getOrNull()

    private data class AppInstallStamp(
        val versionCode: Long,
        val lastUpdateTime: Long,
    )

    private fun appInstallStamp(context: Context): AppInstallStamp = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).let { info ->
            AppInstallStamp(
                versionCode = info.longVersionCode,
                lastUpdateTime = info.lastUpdateTime,
            )
        }
    }.getOrDefault(AppInstallStamp(versionCode = -1L, lastUpdateTime = -1L))

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
