package com.kite.app.feature.resources

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.util.LruCache
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.theme.kiteThemeEnvironment
import com.kite.app.ui.UiKit
import java.util.concurrent.Executors

internal object ResourceFeatureTheme {
    fun tokens(context: Context): ThemeTokens =
        context.kiteThemeEnvironment().tokens
}

internal data class ResourceItemViewBinding(
    val resourceId: String,
    val root: View,
    val stateView: TextView?,
    val actionButton: TextView,
    val compact: Boolean,
    var item: ResourceItemUiState
)

internal class ResourceFeatureViewFactory(
    private val context: Context,
    internal val tokens: ThemeTokens,
    private val onOpenDetail: (String) -> Unit,
    private val onPrimaryAction: (String) -> Unit
) {
    private val ui = UiKit(context, tokens)

    fun dp(value: Int): Int = ui.dp(value)

    fun roundedBox(fill: Int, stroke: Int, radius: Float) =
        ui.roundedBox(fill, stroke, radius)

    fun divider(): View = View(context).apply { setBackgroundColor(tokens.border) }

    fun stateBlock(title: String, detail: String, loading: Boolean = false, retry: (() -> Unit)? = null): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(34), dp(18), dp(30))
            if (loading) {
                addView(ProgressBar(context).apply { isIndeterminate = true })
            }
            addView(TextView(context).apply {
                text = title
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
                setPadding(0, if (loading) dp(14) else 0, 0, 0)
            })
            addView(TextView(context).apply {
                text = detail
                textSize = 12.5f
                gravity = Gravity.CENTER
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(7), 0, 0)
            })
            retry?.let { action ->
                addView(TextView(context).apply {
                    text = "重试"
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(tokens.primaryStrong)
                    background = roundedBox(tokens.primarySubtle, Color.TRANSPARENT, dp(16).toFloat())
                    setPadding(dp(22), dp(9), dp(22), dp(9))
                    setOnClickListener { action() }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, dp(16), 0, 0) }
                })
            }
        }

    fun sectionTitle(title: String): TextView = TextView(context).apply {
        text = title
        textSize = 19f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textPrimary)
    }

    fun heroPoster(item: ResourceItemUiState, imageAsset: String, contentDescriptionText: String): View {
        val width = (context.resources.displayMetrics.widthPixels - dp(44)).coerceAtLeast(dp(280))
        val height = (width * 780f / 1200f).toInt().coerceIn(dp(180), dp(240))
        return FrameLayout(context).apply {
            contentDescription = contentDescriptionText.ifBlank { "资源海报，点击查看资源详情" }
            isClickable = true
            isFocusable = true
            background = roundedBox(tokens.surface, tokens.border, dp(24).toFloat())
            clipToOutline = true
            elevation = dp(1).toFloat()
            setOnClickListener { onOpenDetail(item.resourceId) }
            layoutParams = LinearLayout.LayoutParams(width, height)
            val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            addView(image, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            ResourceFeatureBitmapRepository.load(context, imageAsset, maxOf(width, height)) { bitmap ->
                if (image.parent != null) image.setImageBitmap(bitmap)
            }
        }
    }

    fun listRow(item: ResourceItemUiState): ResourceItemViewBinding {
        val presentation = item.presentation()
        val stateView = TextView(context).apply {
            textSize = 11f
            setTextColor(tokens.textTertiary)
            setPadding(0, dp(3), 0, 0)
        }
        val action = TextView(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
            isClickable = true
            isFocusable = true
            contentDescription = "${presentation.name}，${presentation.stateLabel}"
            setOnClickListener { onOpenDetail(item.resourceId) }
            addView(icon(item, dp(56), dp(7), dp(14).toFloat(), 15f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(12), 0, dp(10), 0)
                }
                addView(TextView(context).apply {
                    text = presentation.name
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(context).apply {
                    text = presentation.description
                    textSize = 12.5f
                    setTextColor(tokens.textSecondary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(3), 0, 0)
                })
                addView(stateView)
            })
            addView(action, LinearLayout.LayoutParams(dp(60), dp(32)))
        }
        return ResourceItemViewBinding(item.resourceId, root, stateView, action, compact = true, item = item)
            .also(::bind)
    }

    fun shelfItem(item: ResourceItemUiState): ResourceItemViewBinding {
        val presentation = item.presentation()
        val action = TextView(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            contentDescription = "${presentation.name}，${presentation.stateLabel}"
            setOnClickListener { onOpenDetail(item.resourceId) }
            addView(icon(item, dp(58), dp(7), dp(16).toFloat(), 14f))
            addView(TextView(context).apply {
                text = presentation.name
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
                setPadding(0, dp(7), 0, 0)
                layoutParams = LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(action, LinearLayout.LayoutParams(dp(60), dp(32)).apply {
                setMargins(0, dp(7), 0, 0)
            })
        }
        return ResourceItemViewBinding(item.resourceId, root, null, action, compact = true, item = item)
            .also(::bind)
    }

    fun bind(binding: ResourceItemViewBinding, item: ResourceItemUiState = binding.item) {
        binding.item = item
        val presentation = item.presentation()
        binding.root.contentDescription = "${presentation.name}，${presentation.stateLabel}"
        binding.stateView?.text = listOf(
            presentation.version,
            presentation.sizeLabel,
            presentation.stateLabel
        ).filter(String::isNotBlank).joinToString(" · ")
        binding.actionButton.apply {
            text = presentation.actionLabel
            textSize = if (binding.compact) 12.2f else 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.primaryStrong)
            alpha = if (presentation.actionEnabled) 1f else 0.58f
            isEnabled = presentation.actionEnabled
            background = roundedBox(tokens.primarySubtle, Color.TRANSPARENT, dp(16).toFloat())
            setOnClickListener(null)
            if (presentation.actionEnabled) {
                setOnClickListener { onPrimaryAction(item.resourceId) }
            }
        }
    }

    fun acknowledge(binding: ResourceItemViewBinding?, label: String) {
        binding?.actionButton?.apply {
            text = label
            isEnabled = false
            alpha = 0.58f
            setOnClickListener(null)
        }
    }

    fun icon(
        item: ResourceItemUiState,
        size: Int,
        padding: Int,
        radius: Float,
        textSize: Float
    ): View = icon(
        textValue = item.presentation().iconText,
        accent = item.presentation().accent,
        assetPath = item.presentation().iconAsset,
        iconFit = item.presentation().iconFit,
        size = size,
        padding = padding,
        radius = radius,
        textSize = textSize
    )

    fun icon(
        textValue: String,
        accent: String,
        assetPath: String,
        iconFit: String,
        size: Int,
        padding: Int,
        radius: Float,
        textSize: Float
    ): View {
        val tone = KiteTheme.accent(accent, tokens)
        if (assetPath.isBlank()) {
            return TextView(context).apply {
                text = textValue
                this.textSize = textSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tone.strong)
                background = roundedBox(tokens.surface, tone.border, radius)
                layoutParams = LinearLayout.LayoutParams(size, size)
            }
        }
        val fullBleed = iconFit.equals("fullBleed", ignoreCase = true)
        return FrameLayout(context).apply {
            background = roundedBox(tokens.surface, tone.border, radius)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(size, size)
            val placeholder = TextView(context).apply {
                text = textValue
                this.textSize = textSize
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tone.strong)
            }
            addView(placeholder, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            ResourceFeatureBitmapRepository.load(context, assetPath, size) { bitmap ->
                if (parent == null) return@load
                removeAllViews()
                if (fullBleed) background = roundedBox(Color.TRANSPARENT, Color.TRANSPARENT, radius)
                addView(ImageView(context).apply {
                    scaleType = if (fullBleed) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_INSIDE
                    if (!fullBleed) setPadding(padding, padding, padding, padding)
                    setImageBitmap(bitmap)
                }, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
            }
        }
    }

    fun mediaBanner(
        item: ResourceItemUiState,
        assetPath: String,
        contentDescriptionText: String
    ): View {
        val presentation = item.presentation()
        val tone = KiteTheme.accent(presentation.accent, tokens)
        return FrameLayout(context).apply {
            background = roundedBox(tokens.surface, tone.border, dp(22).toFloat())
            clipToOutline = true
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(228)
            ).apply { setMargins(0, dp(20), 0, 0) }
            val image = ImageView(context).apply {
                contentDescription = contentDescriptionText.ifBlank { "${presentation.name} 视觉预览" }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            addView(image, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            ResourceFeatureBitmapRepository.load(
                context,
                assetPath,
                maxOf(context.resources.displayMetrics.widthPixels, dp(228))
            ) { bitmap ->
                if (image.parent != null) image.setImageBitmap(bitmap)
            }
        }
    }
}

private object ResourceFeatureBitmapRepository {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "KiteResourceBitmap").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val inFlight = linkedMapOf<String, MutableList<(Bitmap) -> Unit>>()

    fun load(context: Context, assetPath: String, maxDimension: Int, callback: (Bitmap) -> Unit) {
        val normalized = assetPath.trim().trimStart('/').takeIf { it.isNotBlank() && !it.contains("..") } ?: return
        val key = "$normalized@$maxDimension"
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
            val bitmap = decode(appContext, normalized, maxDimension)
            val waiters = synchronized(this) {
                if (bitmap != null) cache.put(key, bitmap)
                inFlight.remove(key).orEmpty()
            }
            if (bitmap != null) mainHandler.post { waiters.forEach { it(bitmap) } }
        }
    }

    private fun decode(context: Context, path: String, maxDimension: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    private fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= maxDimension && height / (sample * 2) >= maxDimension) {
            sample *= 2
        }
        return sample
    }
}
