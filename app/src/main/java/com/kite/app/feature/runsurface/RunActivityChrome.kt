package com.kite.app.feature.runsurface

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import com.kite.app.R
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class RunActivityChromeActions(
    val onComplete: () -> Unit,
    val onStop: () -> Unit,
    val onCloseWindow: () -> Unit,
    val onSelectSurface: (CardRunSurface) -> Unit,
    val onOpenWeb: () -> Unit,
    val onWebBack: () -> Unit,
    val onWebForward: () -> Unit,
    val onWebReload: () -> Unit,
    val onWebStopLoading: () -> Unit,
    val onSubmitWebUrl: (String) -> Unit
)

/** 运行窗口的操作外壳。它提交用户意图，不复制或宣布运行结果。 */
internal class RunActivityChrome(
    context: Context,
    private val tokens: ThemeTokens,
    private val actions: RunActivityChromeActions
) {
    private val ui = UiKit(context, tokens)
    private val capsule = FrameLayout(context)
    private val controls = FrameLayout(context)
    private val standardControls: LinearLayout
    private val webControls: LinearLayout
    private val completeButton: TextView
    private val webCompleteButton: TextView
    private val webBackButton: TextView
    private val webForwardButton: TextView
    private val webReloadButton: TextView
    private val webAddress: EditText
    private val overview: RunWindowOverviewScreen
    private var state: RunSurfaceUiState? = null
    private var webNavigation = RunWebNavigationUiState()
    private var expanded = false
    private var widthAnimator: ValueAnimator? = null
    private var autoOpenedKey = ""
    private var pendingCompleteUpdatedAt: Long? = null
    private var unconfirmedCompleteUpdatedAt: Long? = null

    val root: FrameLayout = FrameLayout(context).apply {
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false
    }

    init {
        completeButton = textAction("完成并继续", "完成当前步骤并继续") {
            requestComplete()
        }
        webCompleteButton = iconAction("✓", "完成当前步骤并继续") {
            requestComplete()
        }
        webBackButton = iconAction("‹", "后退") { actions.onWebBack() }
        webForwardButton = iconAction("›", "前进") { actions.onWebForward() }
        webReloadButton = iconAction("↻", "刷新网页") {
            if (webNavigation.loading) actions.onWebStopLoading() else actions.onWebReload()
        }
        webAddress = EditText(context).apply {
            hint = "输入网址"
            textSize = 13f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            includeFontPadding = false
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textSecondary)
            background = ColorDrawable(Color.TRANSPARENT)
            setPadding(ui.dp(11), 0, ui.dp(6), 0)
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                    submitAddress()
                    true
                } else {
                    false
                }
            }
        }

        standardControls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                completeButton,
                LinearLayout.LayoutParams(ui.dp(122), ui.dp(36)).apply {
                    setMargins(0, 0, ui.dp(10), 0)
                }
            )
            addView(
                controlGroup(
                    windowButton(),
                    moreButton()
                ),
                LinearLayout.LayoutParams(ui.dp(82), ui.dp(38))
            )
        }

        webControls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(3), 0, ui.dp(3), 0)
            background = glassBackground(ui.dp(19))
            elevation = ui.dp(5).toFloat()
            addView(webBackButton, iconParams())
            addView(webForwardButton, iconParams())
            addView(webReloadButton, iconParams())
            addView(
                FrameLayout(context).apply {
                    background = ui.roundedBox(
                        Color.argb(184, 255, 255, 255),
                        Color.argb(44, 123, 137, 156),
                        ui.dp(16).toFloat(),
                        ui.dp(1)
                    )
                    addView(
                        webAddress,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ).apply { setMargins(0, 0, ui.dp(34), 0) }
                    )
                    addView(
                        iconAction("⌕", "打开网址") { submitAddress() },
                        FrameLayout.LayoutParams(ui.dp(34), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT)
                    )
                },
                LinearLayout.LayoutParams(0, ui.dp(32), 1f).apply {
                    setMargins(ui.dp(2), 0, ui.dp(4), 0)
                }
            )
            addView(webCompleteButton, iconParams())
            addView(windowButton(), iconParams())
            addView(moreButton(), iconParams())
        }

        controls.addView(
            standardControls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.RIGHT or Gravity.CENTER_VERTICAL
            )
        )
        controls.addView(
            webControls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        capsule.apply {
            setBackgroundColor(Color.TRANSPARENT)
            alpha = 0f
            clipChildren = false
            clipToPadding = false
            addView(
                controls,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        root.addView(
            capsule,
            FrameLayout.LayoutParams(0, ui.dp(42), Gravity.TOP or Gravity.RIGHT).apply {
                setMargins(0, ui.dp(12), ui.dp(12), 0)
            }
        )
        root.addView(
            sideHandle { toggle() },
            FrameLayout.LayoutParams(ui.dp(20), ui.dp(64), Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply {
                setMargins(0, 0, ui.dp(4), 0)
            }
        )

        overview = RunWindowOverviewScreen(
            context = context,
            tokens = tokens,
            onSelectSurface = actions.onSelectSurface,
            onOpenWeb = actions.onOpenWeb,
            onStop = actions.onStop
        )
        root.addView(
            overview.root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun render(next: RunSurfaceUiState) {
        val previousUpdatedAt = state?.updatedAt
        val previousContent = state?.content
        state = next
        if ((pendingCompleteUpdatedAt != null || unconfirmedCompleteUpdatedAt != null) &&
            previousUpdatedAt != next.updatedAt
        ) {
            pendingCompleteUpdatedAt = null
            unconfirmedCompleteUpdatedAt = null
        }
        val web = next.content as? RunSurfaceContent.Web
        standardControls.visibility = if (web == null) View.VISIBLE else View.GONE
        webControls.visibility = if (web != null) View.VISIBLE else View.GONE
        updateCapsulePosition(web != null)
        completeButton.visibility = if (next.canCompleteCurrentStep) View.VISIBLE else View.GONE
        webCompleteButton.visibility = if (next.canCompleteCurrentStep) View.VISIBLE else View.GONE
        updateCompletionAppearance()
        overview.render(next)
        if (web != null && previousContent !is RunSurfaceContent.Web) {
            updateWebNavigation(RunWebNavigationUiState(url = web.url.orEmpty()))
        } else if (web != null && webNavigation.url.isBlank() && !web.url.isNullOrBlank()) {
            updateWebNavigation(webNavigation.copy(url = web.url))
        } else {
            updateWebControls()
        }
        if (expanded) animateWidth(expandedWidth(), animate = true)

        val key = "${next.target.instanceId}:${next.currentStepIndex}:${next.canCompleteCurrentStep}"
        if (next.canCompleteCurrentStep && autoOpenedKey != key) {
            autoOpenedKey = key
            capsule.postDelayed({
                if (root.isAttachedToWindow && state?.target?.instanceId == next.target.instanceId) {
                    setExpanded(true)
                }
            }, 500L)
        }
    }

    fun updateWebNavigation(next: RunWebNavigationUiState) {
        webNavigation = next
        updateWebControls()
    }

    fun setChromeVisible(visible: Boolean) {
        root.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) setExpanded(false, animate = false)
    }

    fun handleBack(): Boolean {
        if (overview.handleBack()) return true
        if (!expanded) return false
        setExpanded(false)
        return true
    }

    fun dispose() {
        widthAnimator?.cancel()
        overview.dispose()
        root.removeAllViews()
    }

    internal fun expandedForTesting(): Boolean = expanded

    internal fun overviewVisibleForTesting(): Boolean = overview.root.visibility == View.VISIBLE

    internal fun webAddressForTesting(): EditText = webAddress

    private fun requestComplete() {
        val current = state ?: return
        if (!current.canCompleteCurrentStep || pendingCompleteUpdatedAt != null) return
        pendingCompleteUpdatedAt = current.updatedAt
        unconfirmedCompleteUpdatedAt = null
        updateCompletionAppearance()
        actions.onComplete()
        completeButton.postDelayed({
            if (pendingCompleteUpdatedAt == current.updatedAt) {
                pendingCompleteUpdatedAt = null
                unconfirmedCompleteUpdatedAt = current.updatedAt
                updateCompletionAppearance()
            }
        }, 1800L)
    }

    private fun updateCompletionAppearance() {
        val enabled = state?.canCompleteCurrentStep == true && pendingCompleteUpdatedAt == null
        completeButton.isEnabled = enabled
        webCompleteButton.isEnabled = enabled
        completeButton.alpha = if (enabled) 1f else 0.48f
        webCompleteButton.alpha = if (enabled) 1f else 0.48f
        completeButton.text = when {
            pendingCompleteUpdatedAt != null -> "处理中"
            unconfirmedCompleteUpdatedAt != null -> "请重试"
            else -> "完成并继续"
        }
        webCompleteButton.contentDescription = completeButton.text
    }

    private fun updateWebControls() {
        webBackButton.isEnabled = webNavigation.canGoBack
        webBackButton.alpha = if (webNavigation.canGoBack) 1f else 0.32f
        webForwardButton.isEnabled = webNavigation.canGoForward
        webForwardButton.alpha = if (webNavigation.canGoForward) 1f else 0.32f
        webReloadButton.text = if (webNavigation.loading) "×" else "↻"
        webReloadButton.contentDescription = if (webNavigation.loading) "停止加载" else "刷新网页"
        if (!webAddress.hasFocus() && webAddress.text?.toString() != webNavigation.url) {
            webAddress.setText(webNavigation.url)
        }
    }

    private fun submitAddress() {
        actions.onSubmitWebUrl(webAddress.text?.toString().orEmpty())
    }

    private fun toggle() {
        setExpanded(!expanded)
    }

    private fun setExpanded(open: Boolean, animate: Boolean = true) {
        if (expanded == open && (open || capsule.layoutParams.width == 0)) return
        expanded = open
        animateWidth(if (open) expandedWidth() else 0, animate)
    }

    private fun expandedWidth(): Int {
        val current = state
        return if (current?.content is RunSurfaceContent.Web) {
            (root.resources.displayMetrics.widthPixels - ui.dp(24)).coerceAtLeast(ui.dp(280))
        } else {
            if (current?.canCompleteCurrentStep == true) ui.dp(214) else ui.dp(82)
        }
    }

    private fun updateCapsulePosition(web: Boolean) {
        val params = capsule.layoutParams as? FrameLayout.LayoutParams ?: return
        if (web) {
            params.gravity = Gravity.TOP or Gravity.RIGHT
            params.topMargin = ui.dp(12)
            params.rightMargin = ui.dp(12)
        } else {
            params.gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            params.topMargin = 0
            params.rightMargin = ui.dp(28)
        }
        capsule.layoutParams = params
    }

    private fun animateWidth(targetWidth: Int, animate: Boolean) {
        widthAnimator?.cancel()
        val startWidth = capsule.layoutParams?.width?.coerceAtLeast(0) ?: 0
        if (!animate || startWidth == targetWidth) {
            capsule.layoutParams = capsule.layoutParams.apply { width = targetWidth }
            capsule.alpha = if (targetWidth == 0) 0f else 1f
            return
        }
        widthAnimator = ValueAnimator.ofInt(startWidth, targetWidth).apply {
            duration = 220L
            interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
            addUpdateListener { animator ->
                val width = animator.animatedValue as Int
                capsule.layoutParams = capsule.layoutParams.apply { this.width = width }
                capsule.alpha = if (targetWidth > startWidth) {
                    animator.animatedFraction
                } else {
                    1f - animator.animatedFraction
                }
            }
            start()
        }
    }

    private fun showOverview() {
        setExpanded(false)
        overview.show()
    }

    private fun showMore(anchor: View) {
        val current = state ?: return
        PopupMenu(root.context, anchor).apply {
            if (current.canCompleteCurrentStep) menu.add(0, MENU_COMPLETE, 0, "完成并继续")
            menu.add(0, MENU_COLLAPSE, 1, "收起控制条")
            menu.add(0, MENU_CLOSE_WINDOW, 2, "收起窗口")
            val stop = menu.add(
                0,
                MENU_STOP,
                3,
                if (current.status == CardRunStatus.Stopping) "停止中" else "停止任务"
            )
            stop.isEnabled = current.canStop
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_COMPLETE -> requestComplete()
                    MENU_COLLAPSE -> setExpanded(false)
                    MENU_CLOSE_WINDOW -> actions.onCloseWindow()
                    MENU_STOP -> confirmStop()
                }
                true
            }
            show()
        }
    }

    private fun confirmStop() {
        if (state?.canStop != true) return
        AlertDialog.Builder(root.context)
            .setTitle("停止当前任务？")
            .setMessage("窗口会保留到后台确认停止结果。")
            .setNegativeButton("取消", null)
            .setPositiveButton("停止任务") { _, _ -> actions.onStop() }
            .show()
    }

    private fun controlGroup(vararg children: View): LinearLayout =
        LinearLayout(root.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(ui.dp(3), 0, ui.dp(3), 0)
            background = glassBackground(ui.dp(19))
            elevation = ui.dp(5).toFloat()
            children.forEach { addView(it, iconParams()) }
        }

    private fun windowButton(): View = ImageView(root.context).apply {
        setImageResource(R.drawable.card_run_window_switch_grid)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "实例窗口"
        isClickable = true
        isFocusable = true
        setPadding(ui.dp(5), ui.dp(5), ui.dp(5), ui.dp(5))
        setOnClickListener { showOverview() }
    }

    private fun moreButton(): View = iconAction("⋮", "更多操作") { clicked ->
        showMore(clicked)
    }

    private fun textAction(label: String, description: String, action: () -> Unit): TextView =
        TextView(root.context).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            contentDescription = description
            isClickable = true
            isFocusable = true
            setTextColor(tokens.textPrimary)
            background = glassBackground(ui.dp(18))
            elevation = ui.dp(5).toFloat()
            setOnClickListener { action() }
        }

    private fun iconAction(label: String, description: String, action: (View) -> Unit): TextView =
        TextView(root.context).apply {
            text = label
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            contentDescription = description
            isClickable = true
            isFocusable = true
            setTextColor(tokens.textPrimary)
            background = ui.roundedBox(Color.TRANSPARENT, Color.TRANSPARENT, ui.dp(16).toFloat())
            setOnClickListener { action(this) }
        }

    private fun glassBackground(radius: Int) = ui.roundedBox(
        Color.argb(150, 255, 255, 255),
        Color.argb(48, 123, 137, 156),
        radius.toFloat(),
        ui.dp(1)
    )

    private fun iconParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ui.dp(34), ViewGroup.LayoutParams.MATCH_PARENT)

    private fun sideHandle(onClick: () -> Unit): View =
        object : View(root.context) {
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
            setOnClickListener { onClick() }
            var pressed = false
            var dragging = false
            var moved = false
            var downRawY = 0f
            var downTop = 0
            var dragStarter: Runnable? = null
            val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

            fun enterDragMode(view: View) {
                if (dragging || !pressed || !view.isAttachedToWindow) return
                dragging = true
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
                            view.performClick()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        pressed = false
                        dragging = false
                        dragStarter?.let(view::removeCallbacks)
                        this@handle.animateDrag(false)
                        true
                    }
                    else -> false
                }
            }
        }

    private companion object {
        const val MENU_COMPLETE = 1
        const val MENU_COLLAPSE = 2
        const val MENU_CLOSE_WINDOW = 3
        const val MENU_STOP = 4
    }
}
