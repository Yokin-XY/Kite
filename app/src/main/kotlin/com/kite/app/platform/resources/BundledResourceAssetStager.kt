package com.kite.app.platform.resources

import android.content.Context
import com.kite.app.foundation.storage.AtomicDirectoryPublisher
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import com.kite.app.resources.KiteResourceInstallRecipes
import java.io.File

/** 把资源声明的 APK asset 发布到 Ubuntu 可读取、但独立于安装目录的缓存位置。 */
internal object BundledResourceAssetStager {
    fun stage(context: Context, resourceId: String, assetRoot: String): File {
        val cleanAssetRoot = requireSafeAssetRoot(assetRoot)
        val cleanId = KiteResourceInstallRecipes.safeId(resourceId)
        val container = WorkSurfaceRuntimeBridge.ensureDefaultContainer(context.applicationContext)
        val workspaceRoot = File(container.workspacePath)
        val resourceCache = File(workspaceRoot, ".kf/cache/resources/$cleanId").also(File::mkdirs)
        val destination = File(resourceCache, BUNDLE_DIR)
        val revision = bundledRevision(context.applicationContext, cleanAssetRoot)
        AtomicDirectoryPublisher.publish(
            destination = destination,
            isComplete = { candidate -> bundledDirectoryIsComplete(candidate, revision) },
        ) { pending ->
            copyAssetTree(context.applicationContext, cleanAssetRoot, pending)
            check(pending.walkTopDown().any { file -> file.isFile }) {
                "Bundled resource asset is empty: $cleanAssetRoot"
            }
            File(pending, REVISION_FILE).writeText(revision)
        }
        return destination
    }

    fun workspacePath(resourceId: String): String =
        "/workspace/.kf/cache/resources/${KiteResourceInstallRecipes.safeId(resourceId)}/$BUNDLE_DIR"

    private fun requireSafeAssetRoot(value: String): String {
        val clean = value.trim().trim('/')
        require(clean.isNotBlank()) { "Bundled resource asset path is empty" }
        require('\\' !in clean && ".." !in clean.split('/')) {
            "Unsafe bundled resource asset path: $value"
        }
        return clean
    }

    private fun copyAssetTree(context: Context, assetPath: String, destination: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                destination.outputStream().use(input::copyTo)
            }
            return
        }
        destination.mkdirs()
        children.forEach { child ->
            copyAssetTree(context, "$assetPath/$child", File(destination, child))
        }
    }

    private fun bundledRevision(context: Context, assetRoot: String): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return "${packageInfo.lastUpdateTime}:$assetRoot"
    }

    private fun bundledDirectoryIsComplete(directory: File, revision: String): Boolean =
        directory.isDirectory &&
            File(directory, REVISION_FILE).takeIf(File::isFile)?.readText() == revision &&
            directory.walkTopDown().any { file -> file.isFile && file.name != REVISION_FILE }

    private const val BUNDLE_DIR = "bundle"
    private const val REVISION_FILE = ".kite-bundle-revision"
}
