package com.kite.app.feature.recipeeditor

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeConfig
import com.kite.app.theme.ThemeTokens
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 编辑模块自有的头像裁剪面，不向 Activity 暴露图片或手势状态。 */
internal fun showRecipeEditorIconCropDialog(
    context: Context,
    sourceBitmap: Bitmap,
    onCropped: (Bitmap) -> Unit
) {
    val tokens = editorCropTokens(context)
    val dialog = Dialog(context)
    val cropView = RecipeEditorIconCropView(context, sourceBitmap).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            context.dp(320)
        ).apply {
            setMargins(0, context.dp(14), 0, context.dp(18))
        }
    }
    val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(18), context.dp(18), context.dp(18), context.dp(14))
        background = cropRounded(tokens.surfaceElevated, Color.TRANSPARENT, context.dp(20).toFloat())
        addView(TextView(context).apply {
            text = "裁剪头像"
            textSize = 18f
            setTextColor(tokens.textPrimary)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addView(TextView(context).apply {
            text = "拖动图片调整位置，双指缩放"
            textSize = 12f
            setTextColor(tokens.textSecondary)
            setPadding(0, context.dp(4), 0, 0)
        })
        addView(cropView)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(cropAction(context, tokens, "取消", primary = false) {
                dialog.dismiss()
            }, LinearLayout.LayoutParams(context.dp(86), context.dp(40)).apply {
                setMargins(0, 0, context.dp(10), 0)
            })
            addView(cropAction(context, tokens, "保存", primary = true) {
                onCropped(cropView.cropBitmap(512))
                dialog.dismiss()
            }, LinearLayout.LayoutParams(context.dp(96), context.dp(40)))
        })
    }
    dialog.setContentView(panel)
    dialog.setOnDismissListener {
        if (!sourceBitmap.isRecycled) sourceBitmap.recycle()
    }
    dialog.show()
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

private fun cropAction(
    context: Context,
    tokens: ThemeTokens,
    label: String,
    primary: Boolean,
    action: () -> Unit
): TextView = TextView(context).apply {
    text = label
    textSize = 14f
    gravity = Gravity.CENTER
    setTextColor(if (primary) Color.WHITE else tokens.textSecondary)
    background = cropRounded(
        if (primary) tokens.primaryStrong else tokens.surface,
        if (primary) tokens.primaryStrong else tokens.border,
        context.dp(12).toFloat()
    )
    isClickable = true
    isFocusable = true
    setOnClickListener { action() }
}

private class RecipeEditorIconCropView(
    context: Context,
    private val sourceBitmap: Bitmap
) : View(context) {
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x77000000 }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2).toFloat()
    }
    private val imageMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val cropRect = RectF()
    private var scale = 1f
    private var minScale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var initialPinchDistance = 0f
    private var initialPinchScale = 1f
    private var gestureMode = GESTURE_NONE

    init {
        setBackgroundColor(Color.rgb(18, 24, 38))
        contentDescription = "头像裁剪区域"
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val cropSize = min(width, height) * 0.82f
        cropRect.set(
            (width - cropSize) / 2f,
            (height - cropSize) / 2f,
            (width + cropSize) / 2f,
            (height + cropSize) / 2f
        )
        resetToCoverCrop()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateImageMatrix()
        canvas.drawBitmap(sourceBitmap, imageMatrix, imagePaint)
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, overlayPaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), overlayPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, overlayPaint)
        canvas.drawRoundRect(cropRect, context.dp(18).toFloat(), context.dp(18).toFloat(), borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureMode = GESTURE_DRAG
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2) {
                gestureMode = GESTURE_PINCH
                initialPinchDistance = pointerDistance(event)
                initialPinchScale = scale
            }
            MotionEvent.ACTION_MOVE -> when {
                gestureMode == GESTURE_PINCH && event.pointerCount >= 2 -> {
                    val distance = pointerDistance(event)
                    if (initialPinchDistance > 0f) {
                        scale = (initialPinchScale * distance / initialPinchDistance)
                            .coerceIn(minScale, minScale * 5f)
                        clampOffset()
                        invalidate()
                    }
                }
                gestureMode == GESTURE_DRAG -> {
                    offsetX += event.x - lastX
                    offsetY += event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    clampOffset()
                    invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                gestureMode = GESTURE_DRAG
                val remainingIndex = if (event.actionIndex == 0 && event.pointerCount > 1) 1 else 0
                lastX = event.getX(remainingIndex)
                lastY = event.getY(remainingIndex)
            }
            MotionEvent.ACTION_UP -> {
                gestureMode = GESTURE_NONE
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> gestureMode = GESTURE_NONE
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun cropBitmap(outputSize: Int): Bitmap {
        updateImageMatrix()
        imageMatrix.invert(inverseMatrix)
        val points = floatArrayOf(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom)
        inverseMatrix.mapPoints(points)
        val left = points[0].toInt().coerceIn(0, sourceBitmap.width - 1)
        val top = points[1].toInt().coerceIn(0, sourceBitmap.height - 1)
        val right = points[2].toInt().coerceIn(left + 1, sourceBitmap.width)
        val bottom = points[3].toInt().coerceIn(top + 1, sourceBitmap.height)
        val cropped = Bitmap.createBitmap(sourceBitmap, left, top, right - left, bottom - top)
        return Bitmap.createScaledBitmap(cropped, outputSize, outputSize, true).also { scaled ->
            if (cropped !== scaled) cropped.recycle()
        }
    }

    private fun resetToCoverCrop() {
        if (cropRect.isEmpty) return
        minScale = max(cropRect.width() / sourceBitmap.width, cropRect.height() / sourceBitmap.height)
        scale = minScale
        offsetX = cropRect.centerX() - sourceBitmap.width * scale / 2f
        offsetY = cropRect.centerY() - sourceBitmap.height * scale / 2f
        clampOffset()
    }

    private fun updateImageMatrix() {
        imageMatrix.reset()
        imageMatrix.postScale(scale, scale)
        imageMatrix.postTranslate(offsetX, offsetY)
    }

    private fun clampOffset() {
        val scaledWidth = sourceBitmap.width * scale
        val scaledHeight = sourceBitmap.height * scale
        offsetX = when {
            scaledWidth <= cropRect.width() -> cropRect.centerX() - scaledWidth / 2f
            offsetX > cropRect.left -> cropRect.left
            offsetX + scaledWidth < cropRect.right -> cropRect.right - scaledWidth
            else -> offsetX
        }
        offsetY = when {
            scaledHeight <= cropRect.height() -> cropRect.centerY() - scaledHeight / 2f
            offsetY > cropRect.top -> cropRect.top
            offsetY + scaledHeight < cropRect.bottom -> cropRect.bottom - scaledHeight
            else -> offsetY
        }
    }

    private fun pointerDistance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val GESTURE_NONE = 0
        const val GESTURE_DRAG = 1
        const val GESTURE_PINCH = 2
    }
}

private fun cropRounded(fill: Int, stroke: Int, radius: Float): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(fill)
        if (Color.alpha(stroke) > 0) setStroke(1, stroke)
    }

private fun editorCropTokens(context: Context): ThemeTokens {
    val settings = context.getSharedPreferences("kite_theme", Context.MODE_PRIVATE)
    return KiteTheme.resolve(
        ThemeConfig(
            themeColor = settings.getInt("theme_color", KiteTheme.defaultThemeColor),
            backgroundColor = settings.getInt("background_color", KiteTheme.defaultBackgroundColor)
        )
    )
}

private fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density + 0.5f).toInt()
