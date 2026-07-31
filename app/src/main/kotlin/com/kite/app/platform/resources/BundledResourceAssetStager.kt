package com.kite.app.platform.resources

import android.content.Context
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
        val pending = File(resourceCache, "$BUNDLE_DIR.pending")
        val previous = File(resourceCache, "$BUNDLE_DIR.previous")

        pending.deleteRecursively()
        previous.deleteRecursively()
        copyAssetTree(context.applicationContext, cleanAssetRoot, pending)
        check(pending.walkTopDown().any(File::isFile)) {
            "Bundled resource asset is empty: $cleanAssetRoot"
        }

        if (destination.exists()) {
            check(destination.renameTo(previous)) {
                "Unable to preserve previous bundled resource asset: ${destination.absolutePath}"
            }
        }
        if (!pending.renameTo(destination)) {
            previous.renameTo(destination)
            error("Unable to publish bundled resource asset: ${destination.absolutePath}")
        }
        previous.deleteRecursively()
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

    private const val BUNDLE_DIR = "bundle"
}
