package com.kite.app.ui

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.kite.app.R
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeComponentRecipes
import com.kite.app.theme.ThemeContainerRecipe
import com.kite.app.theme.ThemeEnvironment
import com.kite.app.theme.ThemeFoundations
import com.kite.app.theme.ThemeTokens

/**
 * 通用 UI 工具层(T7.0,ADR-016):把 MainActivity 的命令式 UI 工具收口到此处,
 * 供 Fragment 和 Activity 复用,避免每个 Fragment 复刻 dp/顶栏/按钮/配色。
 *
 * 用法:在 Fragment/Activity 里 `val ui = UiKit(requireContext(), environment)`,
 * 然后 `ui.topBar(...)`, `ui.dp(...)`, `ui.row { ... }`。
 *
 * 设计原则:
 * - 不持有 Activity 引用,只持有 Context(用 applicationContext 避免泄漏)。
 * - environment 由调用方传入(KiteTheme.resolve 的结果),UiKit 不耦合主题加载逻辑。
 * - 这是从 MainActivity 抽出的第一批公共 UI 工具,后续按需扩充。
 */
class UiKit private constructor(
    context: Context,
    val tokens: ThemeTokens,
    val foundations: ThemeFoundations,
    val components: ThemeComponentRecipes,
) {
    constructor(context: Context, environment: ThemeEnvironment) : this(
        context = context,
        tokens = environment.tokens,
        foundations = environment.foundations,
        components = environment.components,
    )

    /** 迁移期兼容入口；新页面应传入完整 ThemeEnvironment。 */
    @Deprecated("请传入完整 ThemeEnvironment")
    constructor(context: Context, tokens: ThemeTokens) : this(
        context = context,
        tokens = tokens,
        foundations = KiteTheme.foundations,
        components = KiteTheme.catalog.stylePacks.first().components,
    )

    private val density = context.resources.displayMetrics.density

    /** dp → px 转换。 */
    fun dp(value: Int): Int = (value * density).toInt()

    /** 水平居中的行容器(LinearLayout HORIZONTAL)。 */
    fun rowWith(context: Context, content: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            content()
        }

    /** 圆角矩形背景。 */
    fun roundedBox(
        fill: Int,
        stroke: Int,
        radius: Float,
        strokeWidth: Int = dp(1),
        dashWidth: Float = 0f,
        dashGap: Float = 0f
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            if (stroke != Color.TRANSPARENT) {
                if (dashWidth > 0f && dashGap > 0f) {
                    setStroke(strokeWidth, stroke, dashWidth, dashGap)
                } else {
                    setStroke(strokeWidth, stroke)
                }
            }
        }

    /** 按语义组件配方生成容器背景。 */
    fun containerBackground(
        fill: Int = tokens.cardBackground,
        stroke: Int = tokens.border,
        recipe: ThemeContainerRecipe = components.card,
    ): GradientDrawable = roundedBox(
        fill = fill,
        stroke = stroke,
        radius = dp(recipe.radius).toFloat(),
        strokeWidth = dp(recipe.strokeWidth),
    )

    /** 统一文字层级；文字内容和截断策略仍由页面负责。 */
    fun applyTextRole(view: TextView, role: UiTextRole): TextView = view.apply {
        val typography = foundations.typography
        textSize = when (role) {
            UiTextRole.PageTitle -> typography.pageTitle
            UiTextRole.SectionTitle -> typography.sectionTitle
            UiTextRole.CardTitle -> typography.cardTitle
            UiTextRole.Body -> typography.body
            UiTextRole.Supporting -> typography.supporting
            UiTextRole.Action -> typography.action
            UiTextRole.Badge -> typography.badge
        }
        typeface = when (role) {
            UiTextRole.PageTitle,
            UiTextRole.SectionTitle,
            UiTextRole.CardTitle,
            UiTextRole.Action,
            UiTextRole.Badge -> Typeface.DEFAULT_BOLD
            UiTextRole.Body,
            UiTextRole.Supporting -> Typeface.DEFAULT
        }
        setTextColor(
            when (role) {
                UiTextRole.Supporting -> tokens.textSecondary
                else -> tokens.textPrimary
            }
        )
    }

    /** 标准动作层级；页面只决定动作语义和布局宽高。 */
    fun applyActionRole(view: TextView, role: UiActionRole): TextView = view.apply {
        applyTextRole(this, UiTextRole.Action)
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(dp(16), 0, dp(16), 0)
        val (fill, stroke, textColor) = when (role) {
            UiActionRole.Primary -> Triple(tokens.primaryStrong, tokens.primaryStrong, tokens.buttonText)
            UiActionRole.Secondary -> Triple(tokens.surface, tokens.border, tokens.textPrimary)
            UiActionRole.Danger -> Triple(tokens.surface, tokens.dangerBorder, tokens.danger)
        }
        setTextColor(textColor)
        background = containerBackground(fill, stroke, components.control)
    }

    /** 图标/文字按钮。 */
    fun iconButton(
        context: Context,
        text: String,
        size: Int,
        fill: Int,
        textColor: Int,
        radius: Int,
        textSizeSp: Float = foundations.typography.pageTitle,
        contentDescription: String? = null,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        this.text = text
        textSize = textSizeSp
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setTextColor(textColor)
        this.contentDescription = contentDescription
        background = roundedBox(fill, fill, radius.toFloat())
        if (fill != Color.TRANSPARENT) elevation = dp(4).toFloat()
        layoutParams = LinearLayout.LayoutParams(size, size)
        setOnClickListener { onClick() }
    }

    /** 使用项目内矢量资源的标准图标按钮，不用文字字符模拟图标。 */
    fun imageButton(
        context: Context,
        @DrawableRes iconRes: Int,
        contentDescription: String,
        tint: Int = tokens.textPrimary,
        fill: Int = Color.TRANSPARENT,
        onClick: () -> Unit,
    ): ImageView = ImageView(context).apply {
        setImageDrawable(AppCompatResources.getDrawable(context, iconRes))
        imageTintList = ColorStateList.valueOf(tint)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(12), dp(12), dp(12), dp(12))
        this.contentDescription = contentDescription
        background = containerBackground(fill, Color.TRANSPARENT, components.control)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    /** 标准顶栏:返回按钮 + 居中标题 + 右侧占位。 */
    fun topBar(
        context: Context,
        title: String,
        onBack: () -> Unit,
        trailingAction: View? = null,
    ): View = rowWith(context) {
        setPadding(dp(18), dp(14), dp(18), dp(10))
        addView(imageButton(
            context = context,
            iconRes = R.drawable.ic_arrow_back_light,
            contentDescription = context.getString(R.string.common_back),
            onClick = onBack,
        ), LinearLayout.LayoutParams(
            dp(foundations.minimumTouchTarget),
            dp(foundations.minimumTouchTarget),
        ))
        addView(TextView(context).apply {
            text = title
            applyTextRole(this, UiTextRole.PageTitle)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(
            trailingAction ?: View(context),
            LinearLayout.LayoutParams(
                dp(foundations.minimumTouchTarget),
                dp(foundations.minimumTouchTarget),
            ),
        )
    }

    /**
     * 标准详情弹层：由 UiKit 统一主题容器、信息层级与动作排列，调用方只提供内容语义。
     */
    fun showDetailDialog(
        context: Context,
        title: String,
        fields: List<UiDialogField>,
        dismissLabel: String,
        primaryAction: UiDialogAction? = null,
    ): Dialog = showDialogCard(
        context = context,
        title = title,
        dismissLabel = dismissLabel,
        primaryAction = primaryAction,
    ) {
        fields.forEach { field ->
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = field.label
                    applyTextRole(this, UiTextRole.Supporting)
                })
                addView(TextView(context).apply {
                    text = field.value
                    applyTextRole(this, UiTextRole.Body)
                    setPadding(0, dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(14), 0, 0) })
        }
    }

    /** 标准确认弹层：说明文字与危险/主要动作均由主题语义控制。 */
    fun showConfirmDialog(
        context: Context,
        title: String,
        message: String,
        dismissLabel: String,
        primaryAction: UiDialogAction,
    ): Dialog = showDialogCard(
        context = context,
        title = title,
        dismissLabel = dismissLabel,
        primaryAction = primaryAction,
    ) {
        addView(TextView(context).apply {
            text = message
            applyTextRole(this, UiTextRole.Body)
            setPadding(0, dp(12), 0, 0)
        })
    }

    /** 标准单选弹层：选择后立即回调并关闭，不复制页面状态。 */
    fun showChoiceDialog(
        context: Context,
        title: String,
        options: List<String>,
        selectedIndex: Int,
        dismissLabel: String,
        onSelect: (Int) -> Unit,
    ): Dialog {
        lateinit var dialog: Dialog
        dialog = showDialogCard(
            context = context,
            title = title,
            dismissLabel = dismissLabel,
            primaryAction = null,
        ) {
            options.forEachIndexed { index, label ->
                addView(TextView(context).apply {
                    text = label
                    contentDescription = label
                    applyTextRole(this, UiTextRole.Action)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), 0, dp(16), 0)
                    isSelected = index == selectedIndex
                    setTextColor(if (isSelected) tokens.primaryText else tokens.textPrimary)
                    background = containerBackground(
                        fill = if (isSelected) tokens.primarySubtle else tokens.surface,
                        stroke = if (isSelected) tokens.primaryStrong else tokens.border,
                        recipe = components.control,
                    )
                    setOnClickListener {
                        onSelect(index)
                        dialog.dismiss()
                    }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(48),
                ).apply { setMargins(0, dp(10), 0, 0) })
            }
        }
        return dialog
    }

    /** 标准单行输入弹层：异步校验由调用方通过 handle 回写错误或关闭。 */
    fun showTextInputDialog(
        context: Context,
        title: String,
        hint: String,
        dismissLabel: String,
        confirmLabel: String,
        onSubmit: (String, UiTextInputDialogHandle) -> Unit,
    ): UiTextInputDialogHandle {
        val input = EditText(context).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            setPadding(dp(16), 0, dp(16), 0)
            background = containerBackground(tokens.inputBackground, tokens.border, components.control)
        }
        lateinit var handle: UiTextInputDialogHandle
        val dialog = showDialogCard(
            context = context,
            title = title,
            dismissLabel = dismissLabel,
            primaryAction = UiDialogAction(
                label = confirmLabel,
                role = UiActionRole.Primary,
                dismissOnClick = false,
            ) {
                onSubmit(input.text?.toString().orEmpty().trim(), handle)
            },
        ) {
            addView(input, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52),
            ).apply { setMargins(0, dp(14), 0, 0) })
        }
        handle = UiTextInputDialogHandle(dialog, input)
        return handle
    }

    private fun showDialogCard(
        context: Context,
        title: String,
        dismissLabel: String,
        primaryAction: UiDialogAction?,
        body: LinearLayout.() -> Unit,
    ): Dialog {
        val dialog = Dialog(context)
        val actionRow = rowWith(context) {
            val actions = buildList {
                add(UiDialogAction(dismissLabel, UiActionRole.Secondary) {})
                primaryAction?.let(::add)
            }
            actions.forEachIndexed { index, action ->
                addView(TextView(context).apply {
                    text = action.label
                    applyActionRole(this, action.role)
                    isEnabled = action.enabled
                    alpha = if (action.enabled) 1f else 0.62f
                    setOnClickListener {
                        if (!action.enabled) return@setOnClickListener
                        if (action.dismissOnClick) dialog.dismiss()
                        action.onClick()
                    }
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    if (index > 0) setMargins(dp(12), 0, 0, 0)
                })
            }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(18))
            background = containerBackground(
                fill = tokens.cardBackground,
                stroke = tokens.border,
                recipe = components.dialog,
            )
            elevation = dp(components.dialog.elevation).toFloat()
            addView(TextView(context).apply {
                text = title
                applyTextRole(this, UiTextRole.CardTitle)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })
            body()
            addView(actionRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(20), 0, 0) })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(Color.alpha(tokens.overlay) / 255f)
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        return dialog
    }
}

enum class UiTextRole {
    PageTitle,
    SectionTitle,
    CardTitle,
    Body,
    Supporting,
    Action,
    Badge,
}

enum class UiActionRole {
    Primary,
    Secondary,
    Danger,
}

data class UiDialogField(
    val label: String,
    val value: String,
)

data class UiDialogAction(
    val label: String,
    val role: UiActionRole,
    val enabled: Boolean = true,
    val dismissOnClick: Boolean = true,
    val onClick: () -> Unit,
)

class UiTextInputDialogHandle internal constructor(
    val dialog: Dialog,
    private val input: EditText,
) {
    val value: String get() = input.text?.toString().orEmpty()

    fun showError(message: String) {
        input.error = message
        input.requestFocus()
    }

    fun dismiss() = dialog.dismiss()
}
