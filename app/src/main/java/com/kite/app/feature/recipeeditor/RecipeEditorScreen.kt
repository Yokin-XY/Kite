package com.kite.app.feature.recipeeditor

import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.kite.app.action.KiteRecipeActionIntent
import com.kite.app.recipe.KiteCardGroup
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.run.CardRunStatus
import com.kite.app.run.KiteRunPrimaryAction
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeScope
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.theme.kiteThemeEnvironment
import com.kite.app.ui.UiKit

internal interface RecipeEditorScreenActions {
    fun onBack()
    fun onSave()
    fun onDelete()
    fun onNameChanged(value: String)
    fun onDescriptionChanged(value: String)
    fun onSelectBuiltinIcon(name: String)
    fun onSelectImageIcon(source: String)
    fun onPickImage()
    fun onSelectGroup(groupId: String)
    fun onCreateGroup(name: String)
    fun onSetLaunchOpenInstance(enabled: Boolean)
    fun onSetKeepFinishedNotification(enabled: Boolean)
    fun onSetShortcutRequested(requested: Boolean)
    fun onPutStep(index: Int?, step: RecipeEditorStepDraft)
    fun onRemoveStep(index: Int)
    fun onMoveStep(from: Int, to: Int)
    fun onApplyTemplate(type: String)
    fun onOpenRawJson(recipeId: String)
    fun onOpenRunHistory(recipeId: String)
    fun onRun(intent: KiteRecipeActionIntent)
}

/** 配方编辑页面的真实 View 所有者，只绑定 RecipeEditorUiState。 */
internal class RecipeEditorScreen(
    private val context: Context,
    private val actions: RecipeEditorScreenActions,
    private val iconSources: () -> List<String>,
    private val iconBytes: (String) -> ByteArray?
) {
    private val tokens = editorTokens(context)
    private val ui = UiKit(context, tokens)
    private val titleView = TextView(context)
    private val rightAction = TextView(context)
    private val nameInput = EditText(context)
    private val descriptionInput = EditText(context)
    private val iconHost = FrameLayout(context)
    private val statusHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val stepsHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val groupDetail = TextView(context)
    private val existingOnlyHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val runActionHost = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val scroll = ScrollView(context)
    private var latestState = RecipeEditorUiState()
    private var rendering = false
    private var iconSignature = ""
    private var stepsSignature = ""
    private var runSignature = ""

    val root: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(tokens.pageBackground)
        addView(header())
        addView(statusHost)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(148))
            addView(identityPanel())
            addView(descriptionPanel())
            addView(divider(dp(24)))
            addView(sectionTitle("动作流程"))
            addView(templateRow())
            addView(stepsHost)
            addView(addStepButton())
            addView(existingOnlyHost)
            addView(divider(dp(20)))
            addView(groupRow())
        }
        scroll.addView(body)
        val frame = FrameLayout(context).apply {
            addView(scroll, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(runActionHost, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46),
                Gravity.BOTTOM
            ).apply {
                setMargins(dp(24), 0, dp(24), dp(24))
            })
            scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                val hide = scrollY > maxOf(dp(180), scroll.height / 3)
                runActionHost.animate()
                    .translationY(if (hide) dp(82).toFloat() else 0f)
                    .alpha(if (hide) 0f else 1f)
                    .setDuration(180L)
                    .start()
            }
        }
        addView(frame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
    }

    init {
        nameInput.addTextChangedListener(watcher { if (!rendering) actions.onNameChanged(it) })
        descriptionInput.addTextChangedListener(watcher { if (!rendering) actions.onDescriptionChanged(it) })
    }

    fun render(state: RecipeEditorUiState) {
        latestState = state
        rendering = true
        titleView.text = if (state.isNew) "新建配置" else "编辑配置"
        rightAction.text = if (state.isNew) "保存" else "..."
        rightAction.setTextColor(if (state.isNew) tokens.primaryStrong else tokens.textPrimary)
        setTextIfChanged(nameInput, state.draft.name)
        setTextIfChanged(descriptionInput, state.draft.description)
        groupDetail.text = groupLabel(state)
        rendering = false

        renderStatus(state)
        renderIcon(state)
        renderSteps(state)
        renderExistingRows(state)
        renderRunActions(state)
    }

    fun acknowledgeRun() {
        runActionHost.childrenTextViews().forEach { button ->
            button.isEnabled = false
            button.alpha = 0.58f
            if (button.text == "启动" || button.text == "重新启动") button.text = "启动中"
            if (button.text == "停止") button.text = "停止中"
        }
    }

    fun showUnsavedDialog(
        creatingNew: Boolean,
        onDiscard: () -> Unit,
        onSave: () -> Unit
    ) {
        val dialog = Dialog(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            background = rounded(tokens.cardBackground, tokens.border, dp(20).toFloat())
            addView(TextView(context).apply {
                text = if (creatingNew) "取消新建配置？" else "保存这次修改？"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
            })
            addView(row().apply {
                setPadding(0, dp(20), 0, 0)
                addView(dialogAction(
                    if (creatingNew) "取消" else "不保存",
                    tokens.danger,
                    soft = true
                ) {
                    dialog.dismiss()
                    onDiscard()
                }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(0, 0, dp(8), 0)
                })
                addView(dialogAction(
                    if (creatingNew) "继续编辑" else "保存",
                    if (creatingNew) tokens.textPrimary else Color.WHITE,
                    soft = creatingNew
                ) {
                    dialog.dismiss()
                    if (!creatingNew) onSave()
                }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(dp(8), 0, 0, 0)
                })
            })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
            setLayout((context.resources.displayMetrics.widthPixels * 0.78f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    fun showDeleteConfirm() {
        val recipe = latestState.originalRecipe ?: return
        val dialog = Dialog(context)
        dialog.setContentView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            background = rounded(tokens.cardBackground, tokens.border, dp(18).toFloat())
            addView(TextView(context).apply {
                text = "删除 ${recipe.name}？"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "卡片配置会从共享目录移除。"
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
            addView(row().apply {
                setPadding(0, dp(18), 0, 0)
                addView(dialogAction("取消", tokens.textPrimary, soft = true) { dialog.dismiss() },
                    LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, 0, dp(8), 0) })
                addView(dialogAction("删除", Color.WHITE, soft = false, danger = true) {
                    dialog.dismiss()
                    actions.onDelete()
                }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(8), 0, 0, 0) })
            })
        })
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
            setLayout((context.resources.displayMetrics.widthPixels * 0.8f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    fun showGroupDialog() {
        val dialog = Dialog(context)
        val input = editorInput("新建分组")
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = rounded(tokens.cardBackground, tokens.border, dp(22).toFloat())
            addView(TextView(context).apply {
                text = "选择分组"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
            })
            addView(ScrollView(context).apply {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    if (latestState.groups.isEmpty()) {
                        addView(stateText("还没有分组"))
                    } else {
                        latestState.groups.forEach { group ->
                            addView(groupChoice(group, group.id == latestState.draft.groupId) {
                                actions.onSelectGroup(group.id)
                                dialog.dismiss()
                            })
                        }
                    }
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)).apply {
                setMargins(0, dp(14), 0, dp(14))
            })
            addView(row().apply {
                addView(input, LinearLayout.LayoutParams(0, dp(44), 1f))
                addView(dialogAction("新建", Color.WHITE, soft = false) {
                    val name = input.text?.toString().orEmpty().trim()
                    if (name.isBlank()) input.error = "请输入分组名" else {
                        actions.onCreateGroup(name)
                        dialog.dismiss()
                    }
                }, LinearLayout.LayoutParams(dp(72), dp(44)).apply { setMargins(dp(10), 0, 0, 0) })
            })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
            setLayout((context.resources.displayMetrics.widthPixels * 0.86f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    fun showIconDialog() {
        val dialog = Dialog(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = rounded(tokens.surfaceElevated, Color.TRANSPARENT, dp(24).toFloat())
            addView(sectionTitle("选择头像"))
            addView(TextView(context).apply {
                text = "从头像集选择，或添加一张新图片"
                textSize = 12f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(4), 0, dp(12))
            })
            addView(iconSectionTitle("头像集"))
            addView(iconGrid().apply {
                iconSources().forEach { source ->
                    addView(imageIconChoice(source) {
                        actions.onSelectImageIcon(source)
                        dialog.dismiss()
                    })
                }
                addView(iconChoiceFrame(false) {
                    dialog.dismiss()
                    actions.onPickImage()
                }.apply {
                    addView(iconChoiceGlyph("+", "添加"))
                })
            })
            addView(iconSectionTitle("预置图标").apply { setPadding(0, dp(14), 0, dp(8)) })
            addView(iconGrid().apply {
                presetIcons.forEach { name ->
                    addView(iconChoiceFrame(
                        latestState.draft.selectedIconType == KiteRecipeIcon.TYPE_BUILTIN &&
                            latestState.draft.selectedIconName == name
                    ) {
                        actions.onSelectBuiltinIcon(name)
                        dialog.dismiss()
                    }.apply { addView(iconChoiceGlyph(iconGlyph(name), iconLabel(name))) })
                }
            })
        }
        dialog.setContentView(ScrollView(context).apply { addView(content) })
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((context.resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    fun showMoreDialog() {
        val dialog = Dialog(context)
        val state = latestState
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = rounded(tokens.cardBackground, tokens.border, dp(20).toFloat())
            addView(sectionTitle("更多配置"))
            addView(switchRow(
                "启动时打开独立实例页",
                "关闭后在主应用内执行。",
                state.draft.launchOpenInstance
            ) { actions.onSetLaunchOpenInstance(it) })
            addView(switchRow(
                "保留结束通知",
                "运行结束后保留可清除的结果通知。",
                state.draft.keepFinishedNotification
            ) { actions.onSetKeepFinishedNotification(it) })
            addView(commandRow(
                "桌面快捷方式",
                if (state.draft.shortcutRequested) "保存后申请" else "点击后申请创建"
            ) {
                actions.onSetShortcutRequested(true)
            })
            addView(commandRow("保存修改", "校验并写入共享卡片目录") {
                dialog.dismiss()
                actions.onSave()
            })
            if (!state.isNew) {
                addView(commandRow("删除配置", "从共享卡片目录移除", danger = true) {
                    dialog.dismiss()
                    showDeleteConfirm()
                })
            }
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
            setLayout((context.resources.displayMetrics.widthPixels * 0.86f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun header(): View = row().apply {
        setPadding(dp(18), dp(14), dp(18), dp(10))
        addView(iconButton("‹", "返回") { actions.onBack() })
        titleView.apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
        }
        addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rightAction.apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            contentDescription = "编辑器操作"
            setOnClickListener {
                if (latestState.isNew) actions.onSave() else showMoreDialog()
            }
        }
        addView(rightAction, LinearLayout.LayoutParams(dp(44), dp(44)))
    }

    private fun identityPanel(): View = row().apply {
        setPadding(0, dp(8), 0, dp(14))
        addView(iconHost, LinearLayout.LayoutParams(dp(58), dp(58)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
            addView(nameInput.apply {
                hint = "输入卡片名称"
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                setSingleLine(true)
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textTertiary)
                setPadding(0, 0, 0, 0)
                background = null
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)))
            addView(View(context).apply { setBackgroundColor(tokens.textPrimary) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
            addView(TextView(context).apply {
                text = "点击头像选择图片 ›"
                textSize = 8.8f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(5), 0, 0)
                setOnClickListener { showIconDialog() }
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        iconHost.setOnClickListener { showIconDialog() }
    }

    private fun descriptionPanel(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = "说明"
            textSize = 11f
            setTextColor(tokens.textSecondary)
        })
        addView(descriptionInput.apply {
            hint = "简短说明这张卡片会做什么"
            textSize = 12.5f
            setSingleLine(true)
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            background = rounded(tokens.inputBackground, tokens.border, dp(12).toFloat())
            setPadding(dp(12), 0, dp(12), 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
            setMargins(0, dp(6), 0, 0)
        })
    }

    private fun templateRow(): View = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(row().apply {
            setPadding(0, 0, 0, dp(8))
            addView(templateChip("打开网页", KiteRecipe.TYPE_OPEN_URL))
            addView(templateChip("命令 + 网页", KiteRecipe.TYPE_COMMAND_WEB))
            addView(templateChip("启动服务", KiteRecipe.TYPE_START_SERVICE))
        })
    }

    private fun templateChip(label: String, type: String): View = TextView(context).apply {
        text = label
        textSize = 10.5f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(tokens.primaryStrong)
        background = rounded(tokens.primarySubtle, tokens.primarySoft, dp(14).toFloat())
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { actions.onApplyTemplate(type) }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)).apply {
            setMargins(0, 0, dp(7), 0)
        }
    }

    private fun addStepButton(): View = TextView(context).apply {
        text = "+  添加动作"
        textSize = 11.5f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(tokens.primaryStrong)
        background = rounded(tokens.surface, tokens.primarySoft, dp(18).toFloat())
        setOnClickListener { showStepDialog() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply {
            setMargins(0, dp(14), 0, dp(4))
        }
    }

    private fun groupRow(): View = row().apply {
        setPadding(0, dp(16), 0, dp(4))
        isClickable = true
        setOnClickListener { showGroupDialog() }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = "所属分组"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            groupDetail.apply {
                textSize = 11f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(3), dp(8), 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            addView(groupDetail)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = "选择"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            background = rounded(tokens.primarySubtle, tokens.primarySoft, dp(14).toFloat())
        }, LinearLayout.LayoutParams(dp(66), dp(34)))
    }

    private fun renderStatus(state: RecipeEditorUiState) {
        statusHost.removeAllViews()
        val message = when {
            state.phase == RecipeEditorPhase.Loading -> "正在读取卡片"
            state.phase == RecipeEditorPhase.Saving -> "正在保存"
            state.phase == RecipeEditorPhase.Deleting -> "正在删除"
            state.validationErrors.isNotEmpty() -> state.validationErrors.first().message
            state.errorMessage != null -> state.errorMessage
            state.phase == RecipeEditorPhase.Failed -> "操作失败"
            else -> null
        } ?: return
        statusHost.addView(TextView(context).apply {
            text = message
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(if (state.phase == RecipeEditorPhase.Failed || state.errorMessage != null || state.validationErrors.isNotEmpty()) tokens.danger else tokens.textSecondary)
            background = rounded(tokens.surface, tokens.border, dp(12).toFloat())
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(24), 0, dp(24), dp(8))
        })
    }

    private fun renderIcon(state: RecipeEditorUiState) {
        val draft = state.draft
        val signature = "${draft.selectedIconType}:${draft.selectedIconName}:${draft.selectedIconSource}"
        if (signature == iconSignature) return
        iconSignature = signature
        iconHost.removeAllViews()
        iconHost.background = rounded(tokens.primarySubtle, Color.TRANSPARENT, dp(18).toFloat())
        iconHost.clipToOutline = true
        val bytes = draft.selectedIconSource.takeIf {
            draft.selectedIconType == KiteRecipeIcon.TYPE_IMAGE
        }?.let(iconBytes)
        if (bytes != null) {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                iconHost.addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(bitmap)
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
        if (iconHost.childCount == 0) {
            iconHost.addView(TextView(context).apply {
                text = displayIconGlyph(draft.selectedIconName)
                textSize = 19.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.primaryStrong)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        iconHost.addView(TextView(context).apply {
            text = "✎"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            background = rounded(tokens.surfaceElevated, Color.TRANSPARENT, dp(9).toFloat())
        }, FrameLayout.LayoutParams(dp(19), dp(19), Gravity.END or Gravity.BOTTOM))
    }

    private fun renderSteps(state: RecipeEditorUiState) {
        val signature = state.draft.steps.joinToString("|") {
            "${it.type}:${it.command}:${it.url}:${it.workdir}"
        }
        if (signature == stepsSignature) return
        stepsSignature = signature
        stepsHost.removeAllViews()
        state.draft.steps.forEachIndexed { index, step ->
            stepsHost.addView(stepRow(index, step, state.draft.steps.size))
        }
        if (state.draft.steps.isEmpty()) {
            stepsHost.addView(stateText("尚未添加动作"))
        }
    }

    private fun stepRow(index: Int, step: RecipeEditorStepDraft, count: Int): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(row().apply {
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener { showStepDialog(index, step) }
                addView(TextView(context).apply {
                    text = "${index + 1}"
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(tokens.textSecondary)
                }, LinearLayout.LayoutParams(dp(18), dp(38)).apply { setMargins(0, 0, dp(11), 0) })
                addView(stepIcon(step))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(11), dp(2), 0, 0)
                    addView(TextView(context).apply {
                        text = stepTypeLabel(step)
                        textSize = 11.8f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.textPrimary)
                    })
                    addView(TextView(context).apply {
                        text = stepSummary(step)
                        textSize = 10f
                        setTextColor(tokens.textSecondary)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(stepMoveButton("↑", index > 0) { actions.onMoveStep(index, index - 1) })
                addView(stepMoveButton("↓", index < count - 1) { actions.onMoveStep(index, index + 1) })
            })
            addView(View(context).apply { setBackgroundColor(tokens.border) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        }

    private fun renderExistingRows(state: RecipeEditorUiState) {
        existingOnlyHost.removeAllViews()
        val recipe = state.originalRecipe ?: return
        existingOnlyHost.addView(divider(dp(22)))
        existingOnlyHost.addView(commandRow("查看原始 JSON", "检查完整卡片定义") {
            actions.onOpenRawJson(recipe.id)
        })
        existingOnlyHost.addView(commandRow("最近运行", runHistorySummary(state)) {
            actions.onOpenRunHistory(recipe.id)
        })
    }

    private fun renderRunActions(state: RecipeEditorUiState) {
        val recipe = state.originalRecipe
        runActionHost.visibility = if (recipe == null) View.GONE else View.VISIBLE
        if (recipe == null) return
        val projection = state.runProjection ?: return
        val signature = "${state.run?.status}:${projection.primaryAction}:${state.phase}"
        if (signature == runSignature && runActionHost.childCount > 0) return
        runSignature = signature
        runActionHost.removeAllViews()
        runActionHost.gravity = Gravity.CENTER_VERTICAL
        if (projection.live) {
            runActionHost.background = rounded(tokens.primarySubtle, tokens.border, dp(16).toFloat())
            runActionHost.addView(runButton("打开", tokens.primaryStrong, state.run?.status != CardRunStatus.Stopping) {
                acknowledgeRun()
                actions.onRun(KiteRecipeActionIntent.Open)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 7f))
            runActionHost.addView(View(context).apply { setBackgroundColor(tokens.border) },
                LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(0, dp(10), 0, dp(10)) })
            runActionHost.addView(runButton("停止", tokens.danger, state.run?.status != CardRunStatus.Stopping) {
                acknowledgeRun()
                actions.onRun(KiteRecipeActionIntent.Stop)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f))
        } else {
            val enabled = projection.primaryAction != KiteRunPrimaryAction.Blocked &&
                state.phase != RecipeEditorPhase.Saving
            runActionHost.background = rounded(
                if (enabled) tokens.primaryStrong else tokens.surface,
                if (enabled) tokens.primaryStrong else tokens.border,
                dp(16).toFloat()
            )
            runActionHost.addView(runButton(
                if (projection.primaryAction == KiteRunPrimaryAction.Retry) "重新启动" else "启动",
                if (enabled) Color.WHITE else tokens.textSecondary,
                enabled
            ) {
                acknowledgeRun()
                actions.onRun(KiteRecipeActionIntent.Start)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun showStepDialog(index: Int? = null, initial: RecipeEditorStepDraft? = null) {
        val dialog = Dialog(context)
        var type = initial?.type ?: KiteRecipe.STEP_SHELL
        var command = initial?.command.orEmpty()
        var url = initial?.url.orEmpty()
        var workdir = initial?.workdir.orEmpty()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(24))
            setBackgroundColor(tokens.pageBackground)
        }
        val fields = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val tabs = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        fun renderFields() {
            fields.removeAllViews()
            when (type) {
                KiteRecipe.STEP_TERMINAL ->
                    fields.addView(stepDialogField("终端输入", "可留空，只打开终端", command) { command = it })
                KiteRecipe.STEP_SHELL -> {
                    fields.addView(stepDialogField("sh 命令", "echo hello", command) { command = it })
                    fields.addView(stepDialogField("执行位置（可选）", "/workspace", workdir) { workdir = it })
                }
                else -> fields.addView(stepDialogField("网页地址", "http://127.0.0.1:8648", url) { url = it })
            }
        }

        fun renderTabs() {
            tabs.removeAllViews()
            listOf(
                KiteRecipe.STEP_TERMINAL to ">_ 终端",
                KiteRecipe.STEP_SHELL to "sh 命令",
                KiteRecipe.STEP_OPEN_WEB to "◎ 网页"
            ).forEach { (value, label) ->
                tabs.addView(TextView(context).apply {
                    text = label
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(if (type == value) tokens.primaryStrong else tokens.textSecondary)
                    background = rounded(
                        if (type == value) tokens.primarySubtle else Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        dp(18).toFloat()
                    )
                    setOnClickListener {
                        type = value
                        renderTabs()
                        renderFields()
                    }
                }, LinearLayout.LayoutParams(0, dp(40), 1f))
            }
        }

        content.addView(row().apply {
            addView(iconButton("‹", "关闭") { dialog.dismiss() })
            addView(TextView(context).apply {
                text = if (index == null) "添加动作" else "编辑动作"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (index != null) {
                addView(iconButton("删", "删除动作", tokens.danger) {
                    dialog.dismiss()
                    actions.onRemoveStep(index)
                })
            } else {
                addView(View(context), LinearLayout.LayoutParams(dp(44), dp(44)))
            }
        })
        content.addView(tabs.apply {
            background = rounded(tokens.surface, tokens.border, dp(22).toFloat())
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            setMargins(0, dp(14), 0, dp(18))
        })
        content.addView(fields, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(dialogAction(if (index == null) "添加" else "保存", Color.WHITE, soft = false) {
            val step = when (type) {
                KiteRecipe.STEP_TERMINAL -> RecipeEditorStepDraft.terminal(command.trim())
                KiteRecipe.STEP_SHELL -> {
                    if (command.isBlank()) return@dialogAction
                    RecipeEditorStepDraft.shell(command.trim(), workdir.trim())
                }
                else -> {
                    if (url.isBlank()) return@dialogAction
                    RecipeEditorStepDraft.openWeb(url.trim())
                }
            }
            dialog.dismiss()
            actions.onPutStep(index, step)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        renderTabs()
        renderFields()
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(tokens.pageBackground))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    private fun stepDialogField(
        label: String,
        hint: String,
        value: String,
        changed: (String) -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(16))
        addView(TextView(context).apply {
            text = label
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
            setPadding(0, 0, 0, dp(7))
        })
        addView(editorInput(hint).apply {
            setText(value)
            addTextChangedListener(watcher(changed))
        })
    }

    private fun stepIcon(step: RecipeEditorStepDraft): View = TextView(context).apply {
        val shell = step.type == KiteRecipe.STEP_SHELL || step.type == KiteRecipe.STEP_TERMINAL
        text = if (shell) ">_" else "◎"
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (shell) tokens.primaryStrong else tokens.success)
        background = rounded(
            KiteTheme.tint(if (shell) tokens.primaryStrong else tokens.success, 0.88f),
            Color.TRANSPARENT,
            dp(11).toFloat()
        )
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
    }

    private fun stepMoveButton(label: String, enabled: Boolean, click: () -> Unit): View =
        TextView(context).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(tokens.textSecondary)
            alpha = if (enabled) 1f else 0.3f
            isEnabled = enabled
            contentDescription = if (label == "↑") "上移动作" else "下移动作"
            if (enabled) setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(38))
        }

    private fun stepTypeLabel(step: RecipeEditorStepDraft): String = when (step.type) {
        KiteRecipe.STEP_TERMINAL -> "终端"
        KiteRecipe.STEP_SHELL -> "sh 命令"
        else -> "打开网页"
    }

    private fun stepSummary(step: RecipeEditorStepDraft): String = when (step.type) {
        KiteRecipe.STEP_TERMINAL -> step.command.ifBlank { "打开终端" }
        KiteRecipe.STEP_SHELL -> step.command.ifBlank { "未填写 sh 命令" }
        else -> step.url.ifBlank { "未填写打开地址" }
    }

    private fun runHistorySummary(state: RecipeEditorUiState): String {
        val run = state.run ?: return "暂无运行记录"
        if (run.instanceId.startsWith("idle_") && run.status == CardRunStatus.Unknown) {
            return "暂无运行记录"
        }
        return "${run.status.label} · 步骤 ${(run.currentStepIndex + 1).coerceAtLeast(1)}/${run.stepCount.coerceAtLeast(1)}"
    }

    private fun groupLabel(state: RecipeEditorUiState): String =
        state.groups.firstOrNull { it.id == state.draft.groupId }?.name
            ?: KiteRecipe.normalizeCategory(state.originalRecipe?.category).ifBlank { "未分组" }

    private fun groupChoice(group: KiteCardGroup, selected: Boolean, click: () -> Unit): View =
        TextView(context).apply {
            text = group.name
            textSize = 14f
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(if (selected) tokens.primaryStrong else tokens.textPrimary)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(
                if (selected) tokens.primarySubtle else tokens.surface,
                if (selected) tokens.primarySoft else tokens.border,
                dp(14).toFloat()
            )
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                setMargins(0, 0, 0, dp(8))
            }
        }

    private fun imageIconChoice(source: String, click: () -> Unit): View =
        iconChoiceFrame(
            latestState.draft.selectedIconType == KiteRecipeIcon.TYPE_IMAGE &&
                latestState.draft.selectedIconSource == source,
            click
        ).apply {
            val bytes = iconBytes(source)
            val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            addView(FrameLayout(context).apply {
                background = rounded(tokens.surface, tokens.border, dp(16).toFloat())
                clipToOutline = true
                if (bitmap != null) {
                    addView(ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageBitmap(bitmap)
                    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            }, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(4)
            })
            addView(TextView(context).apply {
                text = "自定义"
                textSize = 10.5f
                gravity = Gravity.CENTER
                setTextColor(tokens.textSecondary)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20), Gravity.BOTTOM))
        }

    private fun iconChoiceFrame(selected: Boolean, click: () -> Unit): FrameLayout =
        FrameLayout(context).apply {
            val width = ((context.resources.displayMetrics.widthPixels * 0.92f).toInt() - dp(36)) / 4
            background = rounded(
                if (selected) tokens.primarySubtle else Color.TRANSPARENT,
                if (selected) tokens.primarySoft else Color.TRANSPARENT,
                dp(18).toFloat()
            )
            layoutParams = ViewGroup.MarginLayoutParams(width, dp(76)).apply { setMargins(0, 0, 0, dp(8)) }
            isClickable = true
            setOnClickListener { click() }
        }

    private fun iconChoiceGlyph(glyph: String, label: String): View =
        FrameLayout(context).apply {
            addView(TextView(context).apply {
                text = glyph
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.primaryStrong)
                background = rounded(tokens.primarySubtle, Color.TRANSPARENT, dp(16).toFloat())
            }, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(4)
            })
            addView(TextView(context).apply {
                text = label
                textSize = 10.5f
                gravity = Gravity.CENTER
                setTextColor(tokens.textSecondary)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20), Gravity.BOTTOM))
        }

    private fun iconGrid(): GridLayout = GridLayout(context).apply { columnCount = 4 }

    private fun iconSectionTitle(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textPrimary)
        setPadding(0, 0, 0, dp(8))
    }

    private fun switchRow(title: String, detail: String, checked: Boolean, changed: (Boolean) -> Unit): View =
        row().apply {
            setPadding(0, dp(12), 0, dp(12))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = title
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = detail
                    textSize = 11f
                    setTextColor(tokens.textSecondary)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(context).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, value -> changed(value) }
            })
        }

    private fun commandRow(title: String, detail: String, danger: Boolean = false, click: () -> Unit): View =
        row().apply {
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
            setOnClickListener { click() }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = title
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (danger) tokens.danger else tokens.textPrimary)
                })
                addView(TextView(context).apply {
                    text = detail
                    textSize = 11f
                    setTextColor(tokens.textSecondary)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = "›"
                textSize = 20f
                setTextColor(if (danger) tokens.danger else tokens.textSecondary)
            })
        }

    private fun runButton(label: String, color: Int, enabled: Boolean, click: () -> Unit): View =
        TextView(context).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(color)
            alpha = if (enabled) 1f else 0.52f
            isEnabled = enabled
            if (enabled) setOnClickListener { click() }
        }

    private fun dialogAction(
        label: String,
        textColor: Int,
        soft: Boolean,
        danger: Boolean = false,
        click: () -> Unit
    ): View = TextView(context).apply {
        text = label
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(textColor)
        background = rounded(
            when {
                danger -> tokens.danger
                soft -> tokens.surface
                else -> tokens.primaryStrong
            },
            if (soft) tokens.border else Color.TRANSPARENT,
            dp(13).toFloat()
        )
        setOnClickListener { click() }
    }

    private fun iconButton(text: String, description: String, color: Int = tokens.textPrimary, click: () -> Unit): View =
        TextView(context).apply {
            this.text = text
            contentDescription = description
            textSize = if (text == "删") 13f else 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(color)
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }

    private fun editorInput(hint: String): EditText = EditText(context).apply {
        this.hint = hint
        textSize = 13f
        setSingleLine(true)
        setTextColor(tokens.textPrimary)
        setHintTextColor(tokens.textTertiary)
        setPadding(dp(12), 0, dp(12), 0)
        background = rounded(tokens.inputBackground, tokens.border, dp(12).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
    }

    private fun stateText(text: String): View = TextView(context).apply {
        this.text = text
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(tokens.textTertiary)
        setPadding(0, dp(24), 0, dp(24))
    }

    private fun sectionTitle(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 13.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textPrimary)
        setPadding(0, 0, 0, dp(12))
    }

    private fun divider(top: Int): View = View(context).apply {
        setBackgroundColor(tokens.border)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            setMargins(0, top, 0, dp(18))
        }
    }

    private fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun rounded(fill: Int, stroke: Int, radius: Float) = ui.roundedBox(fill, stroke, radius)

    private fun dp(value: Int): Int = ui.dp(value)

    private fun watcher(changed: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            changed(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: Editable?) = Unit
    }

    private fun setTextIfChanged(input: EditText, value: String) {
        if (input.text?.toString() == value) return
        input.setText(value)
        input.setSelection(input.text?.length ?: 0)
    }

    private fun View.childrenTextViews(): List<TextView> = buildList {
        if (this@childrenTextViews is TextView) add(this@childrenTextViews)
        if (this@childrenTextViews is ViewGroup) {
            for (index in 0 until childCount) addAll(getChildAt(index).childrenTextViews())
        }
    }

    private fun displayIconGlyph(name: String): String = when (name) {
        "terminal", "code", "server" -> "▣"
        else -> iconGlyph(name)
    }

    private fun iconGlyph(name: String): String = when (name) {
        "terminal" -> ">_"
        "web" -> "◎"
        "bot" -> "AI"
        "file" -> "文"
        "tools" -> "⚙"
        "server" -> "▷"
        "code" -> "{ }"
        "logs" -> "日"
        else -> "◎"
    }

    private fun iconLabel(name: String): String = when (name) {
        "terminal" -> "终端"
        "web" -> "网页"
        "bot" -> "AI"
        "file" -> "文件"
        "tools" -> "工具"
        "server" -> "服务"
        "code" -> "代码"
        "logs" -> "日志"
        else -> "图标"
    }

    private companion object {
        val presetIcons = listOf("terminal", "web", "bot", "file", "tools", "server", "code", "logs")

        fun editorTokens(context: Context): ThemeTokens {
            return context.kiteThemeEnvironment(ThemeScope.EDITOR).tokens
        }
    }
}
