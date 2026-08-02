package com.kite.app.feature.runsurface

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.kite.app.foundation.workspace.ContainerVisibleFileResolver
import java.io.File
import java.net.URI

/** Markdown 链接的统一产品路由；TextView 不直接解释第三方 URL 或容器路径。 */
internal object AgentMarkdownLinkRouter {
    fun open(context: Context, rawTarget: String) {
        val target = rawTarget.trim()
        val intent = runCatching { intent(context, target) }.getOrNull()
        if (intent == null) {
            Toast.makeText(context, "无法打开这个链接", Toast.LENGTH_LONG).show()
            return
        }
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, "没有可打开此内容的应用", Toast.LENGTH_LONG).show()
        }
    }

    internal fun resolveLocalFile(context: Context, rawTarget: String): File? {
        val withoutAnchor = rawTarget.substringBefore('#').substringBefore('?')
        val direct = ContainerVisibleFileResolver.resolve(context, withoutAnchor)
        if (direct?.isFile == true) return direct
        val withoutLine = withoutAnchor.replace(FILE_LINE_SUFFIX, "")
        return ContainerVisibleFileResolver.resolve(context, withoutLine)?.takeIf(File::isFile)
    }

    private fun intent(context: Context, target: String): Intent? {
        if (target.isBlank() || target.startsWith('#')) return null
        val scheme = runCatching { URI(target).scheme?.lowercase() }.getOrNull()
        if (scheme in REMOTE_SCHEMES) {
            return Intent(Intent.ACTION_VIEW, Uri.parse(target))
        }
        val file = resolveLocalFile(context, target) ?: return null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val extension = file.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private val FILE_LINE_SUFFIX = Regex(":\\d+(?::\\d+)?$")
    private val REMOTE_SCHEMES = setOf("http", "https", "mailto", "content")
}
