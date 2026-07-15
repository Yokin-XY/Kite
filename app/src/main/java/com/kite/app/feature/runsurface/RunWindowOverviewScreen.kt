package com.kite.app.feature.runsurface

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kite.app.R
import com.kite.app.run.CardRunSurface
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit

/** 当前实例内的显示面总览。窗口事实来自 RunSurfaceUiState，本视图不保存运行状态。 */
internal class RunWindowOverviewScreen(
    private val context: Context,
    private val tokens: ThemeTokens,
    private val onSelectWindow: (String, CardRunSurface) -> Unit,
    private val onRestartWindow: (String) -> Unit,
    private val onCloseWindow: (String) -> Unit,
    private val onOpenWeb: () -> Unit,
    private val onOpenTerminal: () -> Unit,
    private val onStop: () -> Unit
) {
    private val ui = UiKit(context, tokens)
    private val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val cards = linkedMapOf<String, LinearLayout>()
    private val stopButton: View
    private var structureSignature = ""
    private var canStop = false

    val root: FrameLayout = FrameLayout(context).apply {
        visibility = View.GONE
        isClickable = true
        isFocusable = true
        setBackgroundColor(tokens.pageBackground)

        addView(
            ScrollView(context).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(ui.dp(24), ui.dp(36), ui.dp(24), ui.dp(108))
                        addView(TextView(context).apply {
                            text = "实例窗口"
                            textSize = 22f
                            typeface = Typeface.DEFAULT_BOLD
                            includeFontPadding = false
                            setTextColor(tokens.textPrimary)
                        })
                        addView(TextView(context).apply {
                            text = "管理当前实例中的前端窗口"
                            textSize = 13f
                            typeface = Typeface.DEFAULT_BOLD
                            includeFontPadding = false
                            setTextColor(tokens.textSecondary)
                            setPadding(0, ui.dp(8), 0, 0)
                        })
                        addView(
                            grid,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { setMargins(0, ui.dp(24), 0, 0) }
                        )
                    },
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val dock = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(ui.dp(16), ui.dp(7), ui.dp(16), ui.dp(9))
            background = ui.roundedBox(
                Color.argb(248, 255, 255, 255),
                Color.rgb(226, 231, 239),
                0f,
                ui.dp(1)
            )
        }
        stopButton = dockButton(
            icon = R.drawable.card_run_window_dock_trash,
            label = "停止任务"
        ) { confirmStop() }
        dock.addView(stopButton, dockParams())
        dock.addView(
            dockButton(
                icon = R.drawable.card_run_window_dock_add,
                label = "新建"
            ) { showCreateBubble() },
            dockParams()
        )
        dock.addView(
            dockButton(
                icon = R.drawable.card_run_window_dock_back,
                label = "返回"
            ) { hide() },
            dockParams()
        )
        addView(
            dock,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(88),
                Gravity.BOTTOM
            )
        )
    }

    fun render(state: RunSurfaceUiState) {
        canStop = state.canStop
        stopButton.isEnabled = canStop
        stopButton.alpha = if (canStop) 1f else 0.38f
        val nextSignature = state.windows.joinToString("|") {
            "${it.windowId}:${it.surface.name}:${it.kind.name}:${it.title}:${it.subtitle}:${it.canRestart}:${it.canClose}"
        }
        if (nextSignature != structureSignature) {
            structureSignature = nextSignature
            rebuildGrid(state.windows)
        }
        state.windows.forEach { window ->
            cards[window.windowId]?.applyWindowSelection(window.selected)
        }
    }

    fun show() {
        if (root.visibility == View.VISIBLE) return
        root.visibility = View.VISIBLE
        root.alpha = 0f
        root.scaleX = 0.98f
        root.scaleY = 0.98f
        root.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180L)
            .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
            .start()
    }

    fun hide() {
        if (root.visibility != View.VISIBLE) return
        root.animate()
            .alpha(0f)
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(130L)
            .withEndAction {
                root.visibility = View.GONE
                root.alpha = 1f
                root.scaleX = 1f
                root.scaleY = 1f
            }
            .start()
    }

    fun handleBack(): Boolean {
        if (root.visibility != View.VISIBLE) return false
        hide()
        return true
    }

    fun dispose() {
        root.animate().cancel()
        root.removeAllViews()
        cards.clear()
    }

    private fun rebuildGrid(windows: List<RunSurfaceWindowUiState>) {
        cards.clear()
        grid.removeAllViews()
        windows.chunked(2).forEachIndexed { rowIndex, rowWindows ->
            val row = LinearLayout(root.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            rowWindows.forEachIndexed { index, window ->
                val card = windowCard(window)
                cards[window.windowId] = card
                row.addView(
                    card,
                    LinearLayout.LayoutParams(0, ui.dp(218), 1f).apply {
                        setMargins(
                            if (index == 0) 0 else ui.dp(6),
                            0,
                            if (index == 0) ui.dp(6) else 0,
                            0
                        )
                    }
                )
            }
            if (rowWindows.size == 1) {
                row.addView(
                    View(root.context),
                    LinearLayout.LayoutParams(0, ui.dp(218), 1f).apply {
                        setMargins(ui.dp(6), 0, 0, 0)
                    }
                )
            }
            grid.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, if (rowIndex == 0) 0 else ui.dp(14), 0, 0) }
            )
        }
    }

    private fun windowCard(window: RunSurfaceWindowUiState): LinearLayout =
        LinearLayout(root.context).apply card@{
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = "打开${window.title}"
            elevation = ui.dp(if (window.selected) 5 else 2).toFloat()
            setPadding(ui.dp(9), ui.dp(8), ui.dp(9), ui.dp(9))

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        windowHeaderAction(
                            icon = R.drawable.ic_refresh_light,
                            description = "重新执行${window.title}",
                            visible = window.canRestart
                        ) { onRestartWindow(window.windowId) },
                        LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)).apply {
                            setMargins(0, 0, ui.dp(5), 0)
                        }
                    )
                    addView(ImageView(context).apply {
                        setImageResource(window.kind.iconRes())
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)).apply {
                        setMargins(0, 0, ui.dp(8), 0)
                    })
                    addView(TextView(context).apply {
                        text = window.title
                        textSize = 14f
                        typeface = Typeface.DEFAULT_BOLD
                        includeFontPadding = false
                        maxLines = 1
                        setTextColor(tokens.textPrimary)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(
                        windowHeaderAction(
                            icon = R.drawable.ic_close_light,
                            description = "关闭${window.title}",
                            visible = window.canClose
                        ) { onCloseWindow(window.windowId) },
                        LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)).apply {
                            setMargins(ui.dp(5), 0, 0, 0)
                        }
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ui.dp(34)
                )
            )
            addView(
                ImageView(context).apply {
                    setImageResource(window.kind.previewRes())
                    scaleType = ImageView.ScaleType.FIT_XY
                    adjustViewBounds = false
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply { setMargins(0, ui.dp(4), 0, ui.dp(7)) }
            )
            addView(TextView(context).apply {
                text = window.subtitle
                textSize = 11f
                includeFontPadding = false
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(tokens.textSecondary)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            applyWindowSelection(window.selected)
            setOnClickListener { pressed(this) {
                hide()
                onSelectWindow(window.windowId, window.surface)
            } }
        }

    private fun windowHeaderAction(
        icon: Int,
        description: String,
        visible: Boolean,
        action: () -> Unit
    ): View = FrameLayout(root.context).apply {
        contentDescription = if (visible) description else null
        isClickable = visible
        isFocusable = visible
        visibility = if (visible) View.VISIBLE else View.INVISIBLE
        background = ui.roundedBox(
            if (visible) Color.argb(20, 31, 35, 41) else Color.TRANSPARENT,
            Color.TRANSPARENT,
            ui.dp(14).toFloat()
        )
        addView(ImageView(context).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(tokens.textSecondary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, FrameLayout.LayoutParams(ui.dp(18), ui.dp(18), Gravity.CENTER))
        if (visible) setOnClickListener { pressed(this, action) }
    }

    private fun LinearLayout.applyWindowSelection(selected: Boolean) {
        background = ui.roundedBox(
            tokens.surface,
            if (selected) tokens.primaryStrong else tokens.border,
            ui.dp(8).toFloat(),
            ui.dp(if (selected) 3 else 1)
        )
        elevation = ui.dp(if (selected) 5 else 2).toFloat()
        isSelected = selected
    }

    private fun dockButton(icon: Int, label: String, action: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = label
            addView(ImageView(context).apply {
                setImageResource(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            addView(TextView(context).apply {
                text = label
                textSize = 10.5f
                includeFontPadding = false
                gravity = Gravity.CENTER
                setTextColor(tokens.textSecondary)
            })
            setOnClickListener { pressed(this, action) }
        }

    private fun dockParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)

    private fun confirmStop() {
        if (!canStop) return
        AlertDialog.Builder(root.context)
            .setTitle("停止当前任务？")
            .setMessage("窗口会保留到后台确认停止结果。")
            .setNegativeButton("取消", null)
            .setPositiveButton("停止任务") { _, _ -> onStop() }
            .show()
    }

    private fun showCreateBubble() {
        val bubble = Dialog(root.context)
        val row = LinearLayout(root.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { }
            alpha = 0f
            scaleX = 0.72f
            scaleY = 0.72f
            translationY = ui.dp(22).toFloat()
            addView(createBubbleButton(terminal = true) {
                bubble.dismiss()
                hide()
                onOpenTerminal()
            }, LinearLayout.LayoutParams(ui.dp(62), ui.dp(62)).apply {
                setMargins(0, 0, ui.dp(16), 0)
            })
            addView(createBubbleButton(terminal = false) {
                bubble.dismiss()
                hide()
                onOpenWeb()
            }, LinearLayout.LayoutParams(ui.dp(62), ui.dp(62)))
        }
        val overlay = FrameLayout(root.context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            setOnClickListener { bubble.dismiss() }
            addView(
                row,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ).apply { setMargins(0, 0, 0, ui.dp(112)) }
            )
        }
        bubble.setContentView(overlay)
        bubble.setOnShowListener {
            row.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(180L)
                .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
                .start()
        }
        bubble.show()
        bubble.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0f)
            decorView.setPadding(0, 0, 0, 0)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    private fun createBubbleButton(terminal: Boolean, action: () -> Unit): View =
        FrameLayout(root.context).apply {
            contentDescription = if (terminal) "新建终端" else "新建网页"
            tooltipText = contentDescription
            background = ui.roundedBox(
                if (terminal) Color.rgb(34, 184, 98) else Color.rgb(59, 130, 246),
                Color.TRANSPARENT,
                ui.dp(31).toFloat(),
                0
            )
            elevation = ui.dp(6).toFloat()
            addView(
                createBubbleGlyph(terminal),
                FrameLayout.LayoutParams(ui.dp(34), ui.dp(34), Gravity.CENTER)
            )
            setOnClickListener { pressed(this, action) }
        }

    private fun createBubbleGlyph(terminal: Boolean): View = object : View(root.context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 2.5f * resources.displayMetrics.density
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (terminal) {
                canvas.drawLine(w * 0.24f, h * 0.34f, w * 0.43f, h * 0.50f, paint)
                canvas.drawLine(w * 0.24f, h * 0.66f, w * 0.43f, h * 0.50f, paint)
                canvas.drawLine(w * 0.56f, h * 0.68f, w * 0.78f, h * 0.68f, paint)
            } else {
                canvas.drawRoundRect(
                    RectF(w * 0.18f, h * 0.24f, w * 0.82f, h * 0.64f),
                    ui.dp(3).toFloat(),
                    ui.dp(3).toFloat(),
                    paint
                )
                canvas.drawLine(w * 0.50f, h * 0.64f, w * 0.50f, h * 0.78f, paint)
                canvas.drawLine(w * 0.34f, h * 0.80f, w * 0.66f, h * 0.80f, paint)
            }
        }
    }

    private fun pressed(view: View, action: () -> Unit) {
        view.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(70L)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100L)
                    .withEndAction(action)
                    .start()
            }
            .start()
    }

    private fun RunSurfaceWindowKind.iconRes(): Int = when (this) {
        RunSurfaceWindowKind.Terminal -> R.drawable.card_run_window_icon_terminal
        RunSurfaceWindowKind.Web -> R.drawable.card_run_window_icon_web
        RunSurfaceWindowKind.Report,
        RunSurfaceWindowKind.X11,
        RunSurfaceWindowKind.InstallWizard -> R.drawable.card_run_window_icon_shell
    }

    private fun RunSurfaceWindowKind.previewRes(): Int = when (this) {
        RunSurfaceWindowKind.Terminal -> R.drawable.card_run_window_preview_terminal
        RunSurfaceWindowKind.Web,
        RunSurfaceWindowKind.X11 -> R.drawable.card_run_window_preview_web
        RunSurfaceWindowKind.Report,
        RunSurfaceWindowKind.InstallWizard -> R.drawable.card_run_window_preview_shell
    }

}
