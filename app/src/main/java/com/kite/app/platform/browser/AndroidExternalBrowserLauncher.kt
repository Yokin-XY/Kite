package com.kite.app.platform.browser

import android.content.Context
import android.content.Intent
import android.net.Uri

/** 供非 Activity Feature 使用的系统浏览器入口。 */
internal object AndroidExternalBrowserLauncher {
    fun open(context: Context, url: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
}
