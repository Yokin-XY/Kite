package com.kite.app.feature.runsurface

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import com.kite.app.R
import com.kite.app.foundation.workspace.KiteStorageContract
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiActionRole
import com.kite.app.ui.UiKit
import com.kite.app.ui.UiTextRole

/** 新建 Agent 项目的两项资料：Kite 显示名称和系统文件选择器返回的真实工作目录。 */
internal class AgentProjectEditorDialog(
    private val context: Context,
    tokens: ThemeTokens,
    private val onChooseDirectory: () -> Unit,
    private val onSubmit: (name: String, cwd: String, handle: AgentProjectEditorDialog) -> Unit,
) {
    private val ui = UiKit(context, tokens)
    private val tokens = tokens
    private val dialog = Dialog(context)
    private val nameInput = EditText(context)
    private val directoryPath = TextView(context)
    private val errorText = TextView(context)
    private val confirm = TextView(context)
    private var selectedCwd: String? = null

    fun show() {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(18))
            background = ui.containerBackground(
                fill = tokens.cardBackground,
                stroke = tokens.border,
                recipe = ui.components.dialog,
            )
            elevation = ui.dp(ui.components.dialog.elevation).toFloat()

            addView(TextView(context).apply {
                text = "新建项目"
                ui.applyTextRole(this, UiTextRole.CardTitle)
            })

            addView(TextView(context).apply {
                text = "项目名称"
                ui.applyTextRole(this, UiTextRole.Supporting)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, ui.dp(16), 0, ui.dp(7)) })

            addView(nameInput.apply {
                hint = "例如：Kite"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                maxLines = 1
                isSingleLine = true
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textTertiary)
                setPadding(ui.dp(16), 0, ui.dp(16), 0)
                background = ui.containerBackground(
                    tokens.inputBackground,
                    tokens.border,
                    ui.components.control,
                )
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(52),
            ))

            addView(TextView(context).apply {
                text = "工作目录"
                ui.applyTextRole(this, UiTextRole.Supporting)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, ui.dp(16), 0, ui.dp(7)) })

            addView(buildDirectoryRow(), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(64),
            ))

            addView(errorText.apply {
                textSize = 12.5f
                setTextColor(tokens.danger)
                visibility = TextView.GONE
                setPadding(ui.dp(2), ui.dp(8), ui.dp(2), 0)
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(context).apply {
                    text = "取消"
                    ui.applyActionRole(this, UiActionRole.Secondary)
                    setOnClickListener { dialog.dismiss() }
                }, LinearLayout.LayoutParams(0, ui.dp(48), 1f))
                addView(confirm.apply {
                    text = "新建"
                    ui.applyActionRole(this, UiActionRole.Primary)
                    setOnClickListener {
                        val cwd = selectedCwd
                        when {
                            nameInput.text.isNullOrBlank() -> showError("请输入项目名称")
                            cwd == null -> showError("请选择工作目录")
                            else -> onSubmit(nameInput.text.toString().trim(), cwd, this@AgentProjectEditorDialog)
                        }
                    }
                }, LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply {
                    setMargins(ui.dp(12), 0, 0, 0)
                })
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, ui.dp(20), 0, 0) })
        }

        dialog.setContentView(content)
        dialog.setOnDismissListener { selectedCwd = null }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(android.graphics.Color.alpha(tokens.overlay) / 255f)
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    fun updateDirectory(cwd: String) {
        val normalized = KiteStorageContract.normalizeWorkspacePath(cwd)
            ?.takeIf(KiteStorageContract::isSelectableProjectPath)
            ?: return
        selectedCwd = normalized
        directoryPath.text = normalized
        directoryPath.setTextColor(tokens.textPrimary)
        errorText.visibility = TextView.GONE
    }

    fun showError(message: String) {
        errorText.text = message
        errorText.visibility = TextView.VISIBLE
    }

    fun dismiss() = dialog.dismiss()

    fun isShowing(): Boolean = dialog.isShowing

    fun currentDirectory(): String? = selectedCwd

    private fun buildDirectoryRow(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        contentDescription = "选择工作目录"
        setPadding(ui.dp(16), 0, ui.dp(12), 0)
        background = ui.containerBackground(
            tokens.inputBackground,
            tokens.border,
            ui.components.control,
        )
        addView(directoryPath.apply {
            text = "选择 Kite Ubuntu 中的文件夹"
            ui.applyTextRole(this, UiTextRole.Body)
            setTextColor(tokens.textTertiary)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(ImageView(context).apply {
            setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_chevron_right_light))
            imageTintList = android.content.res.ColorStateList.valueOf(tokens.textSecondary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)).apply {
            setMargins(ui.dp(12), 0, 0, 0)
        })
        setOnClickListener {
            errorText.visibility = TextView.GONE
            onChooseDirectory()
        }
    }
}
