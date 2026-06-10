package com.kftest.app.foundation.runtime

import android.content.Context
import android.system.Os
import com.kftest.app.foundation.logging.Logger
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 负责把 APK 内的运行时资源落到应用私有目录。
 *
 * 设计原则：
 * 1. proot 与基础镜像只初始化一次，后续直接复用。
 * 2. 所有运行时文件都放在 `files/runtime` 下，便于后续容器管理。
 * 3. rootfs 解包不再依赖系统 `tar`，避免 Android 沙箱对硬链接的限制。
 */
object AssetExtractor {

    private const val ASSET_PROOT = "proot/proot-arm64"
    private const val ASSET_PROOT_RUNTIME_DESCRIPTOR = "proot/proot-runtime.json"
    private const val ASSET_PROOT_LIBTALLOC = "proot/libtalloc.so.2"
    private const val ASSET_PROOT_LOADER = "proot/loader"
    private const val ASSET_PROOT_LOADER_32 = "proot/loader32"
    private const val RUNTIME_DIR = "runtime"
    private const val BIN_DIR = "bin"
    private const val LIB_DIR = "lib"
    private const val LIBEXEC_DIR = "libexec"
    private const val IMAGES_DIR = "images"
    private const val CONTAINERS_DIR = "containers"
    private const val SHARED_DIR = "shared"
    private const val TMP_DIR = "tmp"
    private const val LOGS_DIR = "logs"
    private const val ROOTFS_READY_MARKER = ".kf-rootfs-ready"
    private const val PERMISSION_MASK = 0x1FF
    private const val PROGRESS_EMIT_INTERVAL_MS = 500L

    enum class RootfsExtractionPhase {
        IDLE,
        PREPARING,
        EXTRACTING,
        VERIFYING,
        READY,
        FAILED
    }

    data class RootfsExtractionProgress(
        val phase: RootfsExtractionPhase = RootfsExtractionPhase.IDLE,
        val sourceLabel: String = "",
        val bytesRead: Long = 0L,
        val totalBytes: Long = 0L,
        val entriesExtracted: Int = 0,
        val currentEntry: String = "",
        val message: String = "",
        val errorMessage: String? = null,
        val startedAt: Long = 0L,
        val updatedAt: Long = System.currentTimeMillis()
    ) {
        val percent: Int?
            get() {
                if (totalBytes <= 0L) return null
                return ((bytesRead.coerceAtMost(totalBytes) * 100L) / totalBytes).toInt().coerceIn(0, 100)
            }

        val active: Boolean
            get() = phase == RootfsExtractionPhase.PREPARING ||
                phase == RootfsExtractionPhase.EXTRACTING ||
                phase == RootfsExtractionPhase.VERIFYING

        companion object {
            fun idle(): RootfsExtractionProgress = RootfsExtractionProgress()
        }
    }

    private val _rootfsProgress = MutableStateFlow(RootfsExtractionProgress.idle())
    val rootfsProgress: StateFlow<RootfsExtractionProgress> = _rootfsProgress

    data class RuntimeLayout(
        val profile: BaseImageProfile,
        val runtimeRoot: File,
        val prootFile: File,
        val prootLibDir: File,
        val prootLibtallocFile: File,
        val prootLibexecDir: File,
        val prootLoaderDir: File,
        val prootLoaderFile: File,
        val prootLoader32File: File,
        val prootRuntimeDescriptorFile: File,
        val baseImageDir: File,
        val containersDir: File,
        val sharedDir: File,
        val tmpDir: File,
        val logsDir: File,
        val registryFile: File
    )

    private data class ProotRuntimeAssetPaths(
        val executable: String,
        val libtalloc: String,
        val loader: String,
        val loader32: String
    )

    private data class DeferredHardLink(
        val destination: File,
        val targetName: String,
        val mode: Int
    )

    fun getRuntimeLayout(context: Context, profile: BaseImageProfile = BaseImageProfile.DEFAULT): RuntimeLayout {
        val runtimeRoot = File(context.filesDir, RUNTIME_DIR)
        return RuntimeLayout(
            profile = profile,
            runtimeRoot = runtimeRoot,
            prootFile = File(File(runtimeRoot, BIN_DIR), "proot"),
            prootLibDir = File(runtimeRoot, LIB_DIR),
            prootLibtallocFile = File(File(runtimeRoot, LIB_DIR), "libtalloc.so.2"),
            prootLibexecDir = File(runtimeRoot, LIBEXEC_DIR),
            prootLoaderDir = File(File(runtimeRoot, LIBEXEC_DIR), "proot"),
            prootLoaderFile = File(File(File(runtimeRoot, LIBEXEC_DIR), "proot"), "loader"),
            prootLoader32File = File(File(File(runtimeRoot, LIBEXEC_DIR), "proot"), "loader32"),
            prootRuntimeDescriptorFile = File(runtimeRoot, "proot-runtime.json"),
            baseImageDir = File(File(runtimeRoot, IMAGES_DIR), profile.imageDirName),
            containersDir = File(runtimeRoot, CONTAINERS_DIR),
            sharedDir = File(runtimeRoot, SHARED_DIR),
            tmpDir = File(runtimeRoot, TMP_DIR),
            logsDir = File(runtimeRoot, LOGS_DIR),
            registryFile = File(runtimeRoot, "containers.json")
        )
    }

    @Synchronized
    fun prepareRuntime(context: Context, profile: BaseImageProfile = BaseImageProfile.DEFAULT): RuntimeLayout {
        val layout = getRuntimeLayout(context, profile)
        val packagedProotDescriptor = RuntimeMigrationEngine.resolveProotDescriptor(
            context = context,
            layout = layout,
            packagedDescriptor = readPackagedProotRuntimeDescriptor(context)
        )
        val packagedProotAssets = resolvePackagedProotRuntimeAssets(context, packagedProotDescriptor)
        val shouldRefreshProotAssets =
            shouldRefreshProotRuntimeAssets(layout, packagedProotDescriptor, packagedProotAssets)
        ensureRuntimeDirectories(layout)
        extractProotIfNeeded(context, layout.prootFile, shouldRefreshProotAssets, packagedProotAssets.executable)
        extractProotLibrariesIfNeeded(context, layout, shouldRefreshProotAssets, packagedProotAssets)
        writeProotRuntimeDescriptor(layout, packagedProotDescriptor, packagedProotAssets)
        extractBaseRootfsIfNeeded(context, layout.baseImageDir, profile)
        return layout
    }

    fun isBaseImageReady(context: Context, profile: BaseImageProfile = BaseImageProfile.DEFAULT): Boolean {
        val layout = getRuntimeLayout(context, profile)
        return File(layout.baseImageDir, "bin/bash").exists() &&
            File(layout.baseImageDir, ROOTFS_READY_MARKER).exists()
    }

    private fun ensureRuntimeDirectories(layout: RuntimeLayout) {
        listOf(
            layout.runtimeRoot,
            layout.prootFile.parentFile,
            layout.prootLibDir,
            layout.prootLibexecDir,
            layout.prootLoaderDir,
            layout.baseImageDir.parentFile,
            layout.containersDir,
            layout.sharedDir,
            layout.tmpDir,
            layout.logsDir
        ).forEach { directory ->
            if (directory != null && !directory.exists()) {
                directory.mkdirs()
            }
        }
    }

    private fun extractProotIfNeeded(
        context: Context,
        destination: File,
        forceRefresh: Boolean,
        assetPath: String
    ) {
        if (!forceRefresh && destination.exists() && destination.canExecute()) {
            return
        }

        Logger.i("AssetExtractor", "准备 proot 可执行文件: $assetPath -> ${destination.absolutePath}")
        copyAsset(context, assetPath, destination)
        Os.chmod(destination.absolutePath, 0b111101101)
    }

    private fun extractProotLibrariesIfNeeded(
        context: Context,
        layout: RuntimeLayout,
        forceRefresh: Boolean,
        assetPaths: ProotRuntimeAssetPaths
    ) {
        if (forceRefresh || !layout.prootLibtallocFile.exists()) {
            Logger.i("AssetExtractor", "准备 proot 依赖库: ${assetPaths.libtalloc} -> ${layout.prootLibtallocFile.absolutePath}")
            copyAsset(context, assetPaths.libtalloc, layout.prootLibtallocFile)
        }

        if (forceRefresh || !layout.prootLoaderFile.exists()) {
            Logger.i("AssetExtractor", "准备 proot loader: ${assetPaths.loader} -> ${layout.prootLoaderFile.absolutePath}")
            copyAsset(context, assetPaths.loader, layout.prootLoaderFile)
            Os.chmod(layout.prootLoaderFile.absolutePath, 0b111101101)
        }

        if (forceRefresh || !layout.prootLoader32File.exists()) {
            Logger.i("AssetExtractor", "准备 proot loader32: ${assetPaths.loader32} -> ${layout.prootLoader32File.absolutePath}")
            copyAsset(context, assetPaths.loader32, layout.prootLoader32File)
            Os.chmod(layout.prootLoader32File.absolutePath, 0b111101101)
        }
    }

    private fun writeProotRuntimeDescriptor(
        layout: RuntimeLayout,
        packagedDescriptor: JSONObject,
        assetPaths: ProotRuntimeAssetPaths
    ) {
        val descriptor = JSONObject(packagedDescriptor.toString())
        descriptor.put(
            "installed",
            JSONObject()
                .put("executablePath", layout.prootFile.absolutePath)
                .put("resolvedExecutableAssetPath", assetPaths.executable)
                .put("loaderMode", packagedDescriptor.optString("loaderMode", "external"))
                .put("libDir", layout.prootLibDir.absolutePath)
                .put("libtallocPath", layout.prootLibtallocFile.absolutePath)
                .put("resolvedLibtallocAssetPath", assetPaths.libtalloc)
                .put("loaderPath", layout.prootLoaderFile.absolutePath)
                .put("resolvedLoaderAssetPath", assetPaths.loader)
                .put("loader32Path", layout.prootLoader32File.absolutePath)
                .put("resolvedLoader32AssetPath", assetPaths.loader32)
                .put("descriptorPath", layout.prootRuntimeDescriptorFile.absolutePath)
        )

        val rendered = descriptor.toString(2) + "\n"
        if (!layout.prootRuntimeDescriptorFile.exists() ||
            layout.prootRuntimeDescriptorFile.readText() != rendered
        ) {
            layout.prootRuntimeDescriptorFile.writeText(rendered)
        }
    }

    private fun readPackagedProotRuntimeDescriptor(context: Context): JSONObject {
        val rawDescriptor = readAssetTextIfExists(context, ASSET_PROOT_RUNTIME_DESCRIPTOR)
            ?: buildFallbackProotRuntimeDescriptor()
        return runCatching { JSONObject(rawDescriptor) }
            .getOrElse {
                Logger.e("AssetExtractor", "PRoot runtime descriptor is invalid: ${it.message}")
                JSONObject(buildFallbackProotRuntimeDescriptor())
            }
    }

    private fun shouldRefreshProotRuntimeAssets(
        layout: RuntimeLayout,
        packagedDescriptor: JSONObject,
        packagedAssetPaths: ProotRuntimeAssetPaths
    ): Boolean {
        if (!layout.prootFile.exists() ||
            !layout.prootLibtallocFile.exists() ||
            !layout.prootLoaderFile.exists() ||
            !layout.prootLoader32File.exists()
        ) {
            return true
        }

        if (!layout.prootRuntimeDescriptorFile.exists()) {
            return true
        }

        val installedDescriptor = runCatching {
            JSONObject(layout.prootRuntimeDescriptorFile.readText())
        }.getOrElse {
            Logger.e("AssetExtractor", "Installed PRoot runtime descriptor is invalid: ${it.message}")
            return true
        }
        val installedAssetId = installedDescriptor.optString("assetId")
        val packagedAssetId = packagedDescriptor.optString("assetId")
        if (installedAssetId.isNotBlank() &&
            packagedAssetId.isNotBlank() &&
            installedAssetId != packagedAssetId
        ) {
            return true
        }

        val installed = installedDescriptor.optJSONObject("installed")
        return installed == null ||
            installed.optString("loaderMode") != packagedDescriptor.optString("loaderMode", "external") ||
            installed.optString("resolvedExecutableAssetPath") != packagedAssetPaths.executable ||
            installed.optString("resolvedLibtallocAssetPath") != packagedAssetPaths.libtalloc ||
            installed.optString("resolvedLoaderAssetPath") != packagedAssetPaths.loader ||
            installed.optString("resolvedLoader32AssetPath") != packagedAssetPaths.loader32
    }

    private fun resolvePackagedProotRuntimeAssets(
        context: Context,
        descriptor: JSONObject
    ): ProotRuntimeAssetPaths {
        return ProotRuntimeAssetPaths(
            executable = resolvePackagedAssetPath(context, descriptor, "executableAssetPath", ASSET_PROOT),
            libtalloc = resolvePackagedAssetPath(context, descriptor, "libtallocAssetPath", ASSET_PROOT_LIBTALLOC),
            loader = resolvePackagedAssetPath(context, descriptor, "loaderAssetPath", ASSET_PROOT_LOADER),
            loader32 = resolvePackagedAssetPath(context, descriptor, "loader32AssetPath", ASSET_PROOT_LOADER_32)
        )
    }

    private fun resolvePackagedAssetPath(
        context: Context,
        descriptor: JSONObject,
        key: String,
        fallbackAssetPath: String
    ): String {
        val requestedPath = descriptor.optString(key).takeIf { it.isNotBlank() }
        if (requestedPath != null && assetExists(context, requestedPath)) {
            return requestedPath
        }
        if (requestedPath != null) {
            Logger.e(
                "AssetExtractor",
                "PRoot runtime descriptor references missing asset $key=$requestedPath, fallback=$fallbackAssetPath"
            )
        }
        return fallbackAssetPath
    }

    private fun extractBaseRootfsIfNeeded(context: Context, destinationDir: File, profile: BaseImageProfile) {
        val readyMarker = File(destinationDir, ROOTFS_READY_MARKER)
        if (File(destinationDir, "bin/bash").exists() && readyMarker.exists()) {
            Logger.i("AssetExtractor", "基础 rootfs 已就绪，跳过解压 (${profile.label})")
            publishRootfsProgress(
                phase = RootfsExtractionPhase.READY,
                sourceLabel = profile.label,
                message = "基础 rootfs 已就绪"
            )
            return
        }

        Logger.i("AssetExtractor", "开始准备基础 rootfs: ${profile.label} -> ${destinationDir.absolutePath}")
        val startedAt = System.currentTimeMillis()
        publishRootfsProgress(
            phase = RootfsExtractionPhase.PREPARING,
            sourceLabel = profile.label,
            message = "正在准备系统镜像",
            startedAt = startedAt
        )

        val rootfsAsset = findFirstExistingAsset(context, profile.rootfsAssetCandidates)

        if (rootfsAsset == null) {
            Logger.i("AssetExtractor", "APK 中未找到 ${profile.label} 的 rootfs 资源，尝试从 exchange 导入")
            val exchangeSource = resolveExchangeRootfsSource(context, profile)
            if (exchangeSource != null) {
                importRootfsFromExchange(exchangeSource, destinationDir, readyMarker, startedAt)
                return
            }
            val message = "APK 中未找到 ${profile.label} 的 rootfs 资源，且 exchange 中无可用源"
            publishRootfsProgress(
                phase = RootfsExtractionPhase.FAILED,
                sourceLabel = profile.label,
                message = "系统镜像缺失",
                errorMessage = message,
                startedAt = startedAt
            )
            throw IllegalStateException(message)
        }

        deleteRecursively(destinationDir)
        destinationDir.mkdirs()

        try {
            extractTarAsset(context, rootfsAsset, destinationDir, startedAt)
        } catch (throwable: Throwable) {
            Logger.e("AssetExtractor", "rootfs 解压失败: ${throwable.message}")
            deleteRecursively(destinationDir)
            publishRootfsProgress(
                phase = RootfsExtractionPhase.FAILED,
                sourceLabel = rootfsAsset,
                message = "基础镜像解压失败，下次会清理后重新解压",
                errorMessage = throwable.message ?: throwable.javaClass.simpleName,
                startedAt = startedAt
            )
            throw IllegalStateException("基础镜像解压失败", throwable)
        }

        publishRootfsProgress(
            phase = RootfsExtractionPhase.VERIFYING,
            sourceLabel = rootfsAsset,
            message = "正在校验系统镜像",
            startedAt = startedAt
        )
        if (!File(destinationDir, "bin/bash").exists()) {
            deleteRecursively(destinationDir)
            val message = "基础镜像不完整，缺少 bin/bash"
            publishRootfsProgress(
                phase = RootfsExtractionPhase.FAILED,
                sourceLabel = rootfsAsset,
                message = "系统镜像校验失败，下次会重新解压",
                errorMessage = message,
                startedAt = startedAt
            )
            throw IllegalStateException(message)
        }

        readyMarker.writeText("ready\n")
        publishRootfsProgress(
            phase = RootfsExtractionPhase.READY,
            sourceLabel = rootfsAsset,
            message = "基础 rootfs 解压完成",
            bytesRead = _rootfsProgress.value.bytesRead,
            totalBytes = _rootfsProgress.value.totalBytes,
            entriesExtracted = _rootfsProgress.value.entriesExtracted,
            startedAt = startedAt
        )
        Logger.i("AssetExtractor", "基础 rootfs 解压完成")
    }

    private fun extractTarAsset(context: Context, assetPath: String, destinationDir: File, startedAt: Long) {
        val isCompressedTar = assetPath.endsWith(".gz")

        context.assets.open(assetPath).use { assetInput ->
            val totalBytes = runCatching { assetInput.available().toLong() }.getOrDefault(0L)
            val countingInput = CountingInputStream(BufferedInputStream(assetInput))
            val archiveInput: InputStream = if (isCompressedTar) {
                GZIPInputStream(countingInput)
            } else {
                countingInput
            }

            extractTarStream(
                archiveInput = archiveInput,
                destinationDir = destinationDir,
                sourceLabel = assetPath,
                totalBytes = totalBytes,
                startedAt = startedAt,
                bytesRead = { countingInput.bytesRead }
            )
        }
    }

    private fun extractTarStream(
        archiveInput: InputStream,
        destinationDir: File,
        sourceLabel: String,
        totalBytes: Long,
        startedAt: Long,
        bytesRead: () -> Long
    ) {
        val deferredHardLinks = mutableListOf<DeferredHardLink>()
        var entriesExtracted = 0
        var currentEntry = ""
        var lastEmitAt = 0L

        fun emitProgress(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastEmitAt < PROGRESS_EMIT_INTERVAL_MS) return
            lastEmitAt = now
            publishRootfsProgress(
                phase = RootfsExtractionPhase.EXTRACTING,
                sourceLabel = sourceLabel,
                bytesRead = bytesRead(),
                totalBytes = totalBytes,
                entriesExtracted = entriesExtracted,
                currentEntry = currentEntry,
                message = if (currentEntry.isBlank()) "正在解压系统镜像" else "正在解压 $currentEntry",
                startedAt = startedAt
            )
        }

        TarArchiveInputStream(archiveInput).use { tarInput ->
            emitProgress(force = true)
            while (true) {
                val entry = tarInput.nextTarEntry ?: break
                currentEntry = entry.name
                entriesExtracted += 1
                emitProgress(force = true)
                extractEntry(tarInput, destinationDir, entry, deferredHardLinks) {
                    emitProgress(force = false)
                }
            }
        }

        materializeDeferredHardLinks(destinationDir, deferredHardLinks)
        emitProgress(force = true)
    }

    private fun extractEntry(
        tarInput: TarArchiveInputStream,
        destinationDir: File,
        entry: TarArchiveEntry,
        deferredHardLinks: MutableList<DeferredHardLink>,
        onProgress: () -> Unit
    ) {
        val outputFile = resolveEntryFile(destinationDir, entry.name)

        when {
            entry.isDirectory -> {
                outputFile.mkdirs()
                applyModeIfPossible(outputFile, entry.mode)
            }

            entry.isSymbolicLink -> {
                createSymbolicLink(outputFile, entry.linkName)
            }

            entry.isLink -> {
                val linked = materializeHardLink(
                    destinationDir = destinationDir,
                    outputFile = outputFile,
                    targetName = entry.linkName,
                    mode = entry.mode
                )
                if (!linked) {
                    deferredHardLinks += DeferredHardLink(
                        destination = outputFile,
                        targetName = entry.linkName,
                        mode = entry.mode
                    )
                }
            }

            entry.isFile -> {
                writeRegularFile(tarInput, outputFile, entry.mode, onProgress)
            }

            else -> {
                Logger.d("AssetExtractor", "跳过特殊条目: ${entry.name}")
            }
        }
    }

    private fun writeRegularFile(
        tarInput: TarArchiveInputStream,
        outputFile: File,
        mode: Int,
        onProgress: () -> Unit
    ) {
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = tarInput.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                onProgress()
            }
        }
        applyModeIfPossible(outputFile, mode)
    }

    private fun createSymbolicLink(outputFile: File, linkTarget: String) {
        outputFile.parentFile?.mkdirs()
        deleteRecursively(outputFile)
        Os.symlink(linkTarget, outputFile.absolutePath)
    }

    private fun materializeHardLink(
        destinationDir: File,
        outputFile: File,
        targetName: String,
        mode: Int
    ): Boolean {
        val targetFile = resolveEntryFile(destinationDir, targetName)
        if (!targetFile.exists() || !targetFile.isFile) {
            return false
        }

        outputFile.parentFile?.mkdirs()
        targetFile.inputStream().use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }
        applyModeIfPossible(outputFile, mode)
        return true
    }

    private fun materializeDeferredHardLinks(
        destinationDir: File,
        deferredHardLinks: List<DeferredHardLink>
    ) {
        val unresolved = deferredHardLinks.filterNot { link ->
            materializeHardLink(
                destinationDir = destinationDir,
                outputFile = link.destination,
                targetName = link.targetName,
                mode = link.mode
            )
        }

        if (unresolved.isNotEmpty()) {
            val preview = unresolved.joinToString(limit = 5) {
                "${it.destination.relativeTo(destinationDir).path} -> ${it.targetName}"
            }
            throw IllegalStateException("存在未解析的硬链接: $preview")
        }
    }

    private fun resolveEntryFile(destinationDir: File, entryName: String): File {
        val normalizedName = entryName.replace('\\', '/')
        val outputFile = File(destinationDir, normalizedName)

        val destinationPath = destinationDir.canonicalFile.toPath()
        val outputPath = outputFile.canonicalFile.toPath()
        if (!outputPath.startsWith(destinationPath)) {
            throw IllegalArgumentException("检测到越界条目: $entryName")
        }

        return outputFile
    }

    private fun applyModeIfPossible(file: File, mode: Int) {
        if (!file.exists()) {
            return
        }

        val sanitizedMode = mode and PERMISSION_MASK
        if (sanitizedMode == 0) {
            return
        }

        runCatching {
            Os.chmod(file.absolutePath, sanitizedMode)
        }.onFailure { throwable ->
            Logger.d("AssetExtractor", "设置权限失败: ${file.absolutePath}, ${throwable.message}")
        }
    }

    private fun copyAsset(context: Context, assetPath: String, destination: File) {
        destination.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun readAssetTextIfExists(context: Context, assetPath: String): String? {
        return runCatching {
            context.assets.open(assetPath).bufferedReader().use { reader -> reader.readText() }
        }.getOrNull()
    }

    private fun assetExists(context: Context, assetPath: String): Boolean {
        return runCatching {
            context.assets.open(assetPath).close()
            true
        }.getOrDefault(false)
    }

    private fun buildFallbackProotRuntimeDescriptor(): String {
        return """
            {
              "schemaVersion": 1,
              "component": "proot",
              "assetId": "unknown-proot-arm64",
              "provider": "kf-packaged-stock",
              "sourceKind": "missing_descriptor_fallback",
              "sourceRepository": "unknown",
              "sourceCommit": "unknown",
              "sourceTag": "unknown",
              "licenseFamily": "GPL-family",
              "binaryRole": "active_runtime",
              "executableAssetPath": "proot/proot-arm64",
              "loaderMode": "external",
              "libtallocAssetPath": "proot/libtalloc.so.2",
              "loaderAssetPath": "proot/loader",
              "loader32AssetPath": "proot/loader32",
              "telemetryMode": "none_current",
              "policyOwnership": "android_control_plane",
              "ubuntuAuthority": "advisory_only"
            }
        """.trimIndent()
    }

    private fun resolveExchangeRootfsSource(context: Context, profile: BaseImageProfile): File? {
        val exchangeDir = ExternalExchangeManager.ensureExchangeDir(context)
        val candidate = File(exchangeDir, "${profile.imageDirName}-rootfs.tar.gz")
        if (candidate.exists() && candidate.length() > 0) return candidate
        val candidateTar = File(exchangeDir, "${profile.imageDirName}-rootfs.tar")
        if (candidateTar.exists() && candidateTar.length() > 0) return candidateTar
        return null
    }

    private fun importRootfsFromExchange(source: File, destinationDir: File, readyMarker: File, startedAt: Long) {
        Logger.i("AssetExtractor", "从 exchange 导入 rootfs: ${source.absolutePath} -> ${destinationDir.absolutePath}")
        deleteRecursively(destinationDir)
        destinationDir.mkdirs()
        try {
            val isCompressed = source.name.endsWith(".gz")
            val countingInput = CountingInputStream(BufferedInputStream(source.inputStream()))
            val archiveInput: InputStream = if (isCompressed) {
                GZIPInputStream(countingInput)
            } else {
                countingInput
            }
            extractTarStream(
                archiveInput = archiveInput,
                destinationDir = destinationDir,
                sourceLabel = source.name,
                totalBytes = source.length(),
                startedAt = startedAt,
                bytesRead = { countingInput.bytesRead }
            )
        } catch (throwable: Throwable) {
            Logger.e("AssetExtractor", "exchange rootfs 导入失败: ${throwable.message}")
            deleteRecursively(destinationDir)
            publishRootfsProgress(
                phase = RootfsExtractionPhase.FAILED,
                sourceLabel = source.name,
                message = "exchange rootfs 导入失败，下次会重新导入",
                errorMessage = throwable.message ?: throwable.javaClass.simpleName,
                startedAt = startedAt
            )
            throw IllegalStateException("exchange rootfs 导入失败", throwable)
        }
        publishRootfsProgress(
            phase = RootfsExtractionPhase.VERIFYING,
            sourceLabel = source.name,
            message = "正在校验 exchange rootfs",
            bytesRead = _rootfsProgress.value.bytesRead,
            totalBytes = _rootfsProgress.value.totalBytes,
            entriesExtracted = _rootfsProgress.value.entriesExtracted,
            startedAt = startedAt
        )
        if (!File(destinationDir, "bin/bash").exists()) {
            deleteRecursively(destinationDir)
            val message = "exchange rootfs 不完整，缺少 bin/bash"
            publishRootfsProgress(
                phase = RootfsExtractionPhase.FAILED,
                sourceLabel = source.name,
                message = "exchange rootfs 校验失败，下次会重新导入",
                errorMessage = message,
                startedAt = startedAt
            )
            throw IllegalStateException(message)
        }
        readyMarker.writeText("ready\n")
        publishRootfsProgress(
            phase = RootfsExtractionPhase.READY,
            sourceLabel = source.name,
            message = "exchange rootfs 导入完成",
            bytesRead = _rootfsProgress.value.bytesRead,
            totalBytes = _rootfsProgress.value.totalBytes,
            entriesExtracted = _rootfsProgress.value.entriesExtracted,
            startedAt = startedAt
        )
        Logger.i("AssetExtractor", "exchange rootfs 导入完成")
    }

    private fun findFirstExistingAsset(context: Context, candidates: List<String>): String? {
        return candidates.firstOrNull { assetPath ->
            runCatching {
                context.assets.open(assetPath).close()
                true
            }.getOrDefault(false)
        }
    }

    private fun deleteRecursively(target: File) {
        if (!target.exists() && !target.isSymlink()) {
            return
        }

        if (target.isSymlink() || target.isFile) {
            if (!target.delete()) {
                Logger.d("AssetExtractor", "跳过无法删除的文件: ${target.absolutePath}")
            }
            return
        }

        target.walkBottomUp().forEach { file ->
            if (!file.delete()) {
                Logger.d("AssetExtractor", "跳过无法删除的文件: ${file.absolutePath}")
            }
        }
    }

    private fun File.isSymlink(): Boolean {
        return runCatching {
            canonicalPath != absolutePath
        }.getOrDefault(false)
    }

    private fun publishRootfsProgress(
        phase: RootfsExtractionPhase,
        sourceLabel: String = "",
        bytesRead: Long = 0L,
        totalBytes: Long = 0L,
        entriesExtracted: Int = 0,
        currentEntry: String = "",
        message: String = "",
        errorMessage: String? = null,
        startedAt: Long = _rootfsProgress.value.startedAt
    ) {
        _rootfsProgress.value = RootfsExtractionProgress(
            phase = phase,
            sourceLabel = sourceLabel,
            bytesRead = bytesRead,
            totalBytes = totalBytes,
            entriesExtracted = entriesExtracted,
            currentEntry = currentEntry,
            message = message,
            errorMessage = errorMessage,
            startedAt = startedAt,
            updatedAt = System.currentTimeMillis()
        )
    }

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            val result = super.read()
            if (result >= 0) bytesRead += 1
            return result
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) bytesRead += read.toLong()
            return read
        }
    }
}
