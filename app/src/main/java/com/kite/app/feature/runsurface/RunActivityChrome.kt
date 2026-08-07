package com.kite.app.feature.runsurface

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import com.kite.app.run.CardRunSurface
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class RunActivityChromeActions(
    val onCloseInstance: () -> Unit,
    val onSelectWindow: (String, CardRunSurface) -> Unit,
    val onRestartWindow: (String) -> Unit,
    val onCloseWindow: (String) -> Unit,
    val onOpenWeb: () -> Unit,
    val onOpenTerminal: () -> Unit,
    val onToggleSurfaceToolbar: () -> Unit
)

/** 实例外壳只拥有竖条和窗口总览；各显示面的操作栏由显示面自己管理。 */
internal class RunActivityChrome(
    private val context: Context,
    private val tokens: ThemeTokens,
    private val actions: RunActivityChromeActions
) {
    private val ui = UiKit(context, tokens)
    private val handle: View
    private var overview: RunWindowOverviewScreen? = null
    private var latestState: RunSurfaceUiState? = null

    val root: FrameLayout = FrameLayout(context).apply {
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false
    }

    init {
        handle = sideHandle(
            onSingleTap = actions.onToggleSurfaceToolbar,
            onDoubleTap = ::showOverviewFromDoubleTap
        )
        root.addView(
            handle,
            FrameLayout.LayoutParams(ui.dp(20), ui.dp(64), Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply {
                setMargins(0, 0, ui.dp(4), 0)
            }
        )

    }

    fun render(state: RunSurfaceUiState) {
        latestState = state
        handle.visibility = View.VISIBLE
        overview?.render(state)
    }

    fun setChromeVisible(visible: Boolean) {
        root.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun handleBack(): Boolean = overview?.handleBack() == true

    fun dispose() {
        overview?.dispose()
        overview = null
        latestState = null
        root.removeAllViews()
    }

    internal fun overviewVisibleForTesting(): Boolean = overview?.root?.visibility == View.VISIBLE

    internal fun overviewCreatedForTesting(): Boolean = overview != null

    internal fun handleForTesting(): View = handle

    internal fun showOverview() {
        val nextOverview = overview ?: createOverview().also { created ->
            overview = created
            root.addView(
                created.root,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        latestState?.let(nextOverview::render)
        nextOverview.show()
    }

    private fun showOverviewFromDoubleTap() {
        // 第一次抬手已经即时执行单击；双击成立时再切一次，恢复双击前的工具栏状态。
        actions.onToggleSurfaceToolbar()
        showOverview()
    }

    /** 窗口总览是用户显式打开的浮层，首个显示面只保留最新投影，不预建其 View 树。 */
    private fun createOverview(): RunWindowOverviewScreen = RunWindowOverviewScreen(
        context = context,
        tokens = tokens,
        onSelectWindow = actions.onSelectWindow,
        onRestartWindow = actions.onRestartWindow,
        onCloseWindow = actions.onCloseWindow,
        onOpenWeb = actions.onOpenWeb,
        onOpenTerminal = actions.onOpenTerminal,
        onCloseInstance = actions.onCloseInstance
    )

    private fun sideHandle(
        onSingleTap: () -> Unit,
        onDoubleTap: () -> Unit
    ): View = object : View(root.context) {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(164, 255, 255, 255)
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(56, 123, 137, 156)
            style = Paint.Style.STROKE
            strokeWidth = resources.displayMetrics.density
        }
        private val rect = RectF()
        private var dragProgress = 0f
        private var dragAnimator: ValueAnimator? = null

        fun animateDrag(active: Boolean) {
            animate()
                .scaleX(if (active) 1.22f else 1f)
                .scaleY(if (active) 1.08f else 1f)
                .setDuration(130L)
                .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f))
                .start()
            dragAnimator?.cancel()
            dragAnimator = ValueAnimator.ofFloat(dragProgress, if (active) 1f else 0f).apply {
                duration = 130L
                interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
                addUpdateListener {
                    dragProgress = it.animatedValue as Float
                    postInvalidateOnAnimation()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val visualWidth = ui.dp(4) + ((ui.dp(14) - ui.dp(4)) * dragProgress)
            val visualHeight = ui.dp(54) + (ui.dp(4) * dragProgress)
            val cx = width / 2f
            val cy = height / 2f
            rect.set(
                cx - visualWidth / 2f,
                cy - visualHeight / 2f,
                cx + visualWidth / 2f,
                cy + visualHeight / 2f
            )
            canvas.drawRoundRect(rect, visualWidth / 2f, visualWidth / 2f, fillPaint)
            canvas.drawRoundRect(rect, visualWidth / 2f, visualWidth / 2f, strokePaint)
        }
    }.apply handle@{
        setBackgroundColor(Color.TRANSPARENT)
        elevation = ui.dp(7).toFloat()
        isClickable = true
        isFocusable = true
        contentDescription = "运行窗口控制"
        setOnClickListener { onSingleTap() }

        var pressed = false
        var dragging = false
        var moved = false
        var downRawY = 0f
        var downTop = 0
        var dragStarter: Runnable? = null
        var lastTapUpAt = 0L
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

        fun enterDragMode(view: View) {
            if (dragging || !pressed || !view.isAttachedToWindow) return
            dragging = true
            lastTapUpAt = 0L
            (view.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            this@handle.animateDrag(true)
        }

        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressed = true
                    dragging = false
                    moved = false
                    downRawY = event.rawY
                    downTop = view.top
                    val starter = Runnable { enterDragMode(view) }
                    dragStarter = starter
                    view.postDelayed(starter, longPressTimeout)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downRawY
                    if (abs(dy) > ui.dp(4)) moved = true
                    if (!dragging && event.eventTime - event.downTime >= longPressTimeout) {
                        enterDragMode(view)
                    }
                    if (dragging) {
                        val parent = view.parent as? ViewGroup
                        val params = view.layoutParams as? FrameLayout.LayoutParams
                        if (parent != null && params != null && parent.height > view.height) {
                            params.gravity = Gravity.RIGHT or Gravity.TOP
                            params.topMargin = (downTop + dy.roundToInt()).coerceIn(
                                ui.dp(12),
                                parent.height - view.height - ui.dp(12)
                            )
                            params.rightMargin = ui.dp(4)
                            view.layoutParams = params
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    pressed = false
                    dragStarter?.let(view::removeCallbacks)
                    val wasDragging = dragging
                    dragging = false
                    this@handle.animateDrag(false)
                    if (!wasDragging && !moved) {
                        val now = event.eventTime
                        if (lastTapUpAt > 0L && now - lastTapUpAt <= doubleTapTimeout) {
                            lastTapUpAt = 0L
                            onDoubleTap()
                        } else {
                            lastTapUpAt = now
                            view.performClick()
                        }
                    } else {
                        lastTapUpAt = 0L
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    pressed = false
                    dragging = false
                    moved = false
                    lastTapUpAt = 0L
                    dragStarter?.let(view::removeCallbacks)
                    this@handle.animateDrag(false)
                    true
                }
                else -> false
            }
        }
    }
}
