package com.kite.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.util.concurrent.Executors

/** 首页和运行管理共享的卡片图标加载器；解码固定在后台线程，UI 只接收缓存结果。 */
internal object RecipeIconBitmapRepository {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KiteRecipeIcon").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val inFlight = linkedMapOf<String, MutableList<(Bitmap) -> Unit>>()

    fun load(context: Context, source: String, size: Int, callback: (Bitmap) -> Unit) {
        val value = source.trim().takeIf { it.isNotBlank() && !it.contains("..") } ?: return
        val key = "$value@$size"
        synchronized(this) {
            cache.get(key)?.let { bitmap ->
                mainHandler.post { callback(bitmap) }
                return
            }
            val waiters = inFlight.getOrPut(key) { mutableListOf() }
            waiters += callback
            if (waiters.size > 1) return
        }
        val appContext = context.applicationContext
        executor.execute {
            val bitmap = decode(appContext, value)
            val waiters = synchronized(this) {
                if (bitmap != null) cache.put(key, bitmap)
                inFlight.remove(key).orEmpty()
            }
            if (bitmap != null) mainHandler.post { waiters.forEach { it(bitmap) } }
        }
    }

    private fun decode(context: Context, source: String): Bitmap? {
        val file = if (source.startsWith("/")) File(source) else File(context.filesDir, source)
        if (file.isFile) return BitmapFactory.decodeFile(file.absolutePath)
        val asset = source.trimStart('/')
        return runCatching { context.assets.open(asset).use(BitmapFactory::decodeStream) }.getOrNull()
    }
}
