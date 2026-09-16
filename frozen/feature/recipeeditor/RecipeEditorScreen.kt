package com.kite.app.feature.recipeeditor

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import com.kite.app.R
import com.kite.app.action.KiteRecipeActionIntent
import com.kite.app.agent.registration.AgentConfigurationStatus
import com.kite.app.agent.registration.AgentInstallationStatus
import com.kite.app.agent.registration.AgentLaunchStatus
import com.kite.app.agent.registration.AgentRegistryEntry
import com.kite.app.agent.registration.AgentRuntimeStatus
import com.kite.app.recipe.KiteCardGroup
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.run.CardRunStatus
import com.kite.app.run.KiteRunPrimaryAction
import com.kite.app.theme.KiteTheme
import com.kite.app.ui.theme.kiteThemeEnvironment
import com.kite.app.ui.UiKit
import com.kite.app.ui.UiTextRole

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
    fun onRequestShortcut()
    fun onPutStep(index: Int?, step: RecipeEditorStepDraft)
    fun onRemoveStep(index: Int)
    fun onMoveStep(from: Int, to: Int)
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
    private val environment = context.kiteThemeEnvironment()
    private val tokens = environment.tokens
    private val ui = UiKit(context, environment)
    private val titleView = TextView(context)
    private val rightAction = TextView(context)
    private val moreAction = ui.imageButton(
        context = context,
        iconRes = R.drawable.ic_more_vert_light,
        contentDescription = context.getString(R.string.recipe_editor_more),
        onClick = ::showMoreDialog,
    )
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
            setPadding(
                dp(environment.foundations.spacing.pageHorizontal),
                dp(environment.foundations.spacing.sectionGap),
                dp(environment.foundations.spacing.pageHorizontal),
                dp(148),
            )
            addView(identityPanel())
            addView(descriptionPanel())
            addView(divider(dp(24)))
            addView(sectionTitle(context.getString(R.string.recipe_editor_flow_title)))
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
        titleView.text = context.getString(
            if (state.isNew) R.string.recipe_editor_new_title else R.string.recipe_editor_edit_title,
        )
        rightAction.visibility = if (state.isNew) View.VISIBLE else View.GONE
        moreAction.visibility = if (state.isNew) View.GONE else View.VISIBLE
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
        val startLabels = setOf(
            context.getString(R.string.recipe_editor_run_start),
            context.getString(R.string.recipe_editor_run_retry),
        )
        runActionHost.childrenTextViews().forEach { button ->
            button.isEnabled = false
            button.alpha = 0.58f
            if (button.text.toString() in startLabels) {
                button.text = context.getString(R.string.recipe_editor_run_starting)
            }
            if (button.text == context.getString(R.string.recipe_editor_run_stop)) {
                button.text = context.getString(R.string.recipe_editor_run_stopping)
            }
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
                text = context.getString(
                    if (creatingNew) R.string.recipe_editor_unsaved_new_title
                    else R.string.recipe_editor_unsaved_edit_title,
                )
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
            })
            addView(row().apply {
                setPadding(0, dp(20), 0, 0)
                addView(dialogAction(
                    context.getString(
                        if (creatingNew) R.string.recipe_editor_cancel else R.string.recipe_editor_discard,
                    ),
                    tokens.danger,
                    soft = true
                ) {
                    dialog.dismiss()
                    onDiscard()
                }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(0, 0, dp(8), 0)
                })
                addView(dialogAction(
                    context.getString(
                        if (creatingNew) R.string.recipe_editor_continue_editing else R.string.recipe_editor_save,
                    ),
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
                text = context.getString(R.string.recipe_editor_delete_title, recipe.name)
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = context.getString(R.string.recipe_editor_delete_summary)
                textSize = 12.5f
                setTextColor(tokens.textSecondary)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
            addView(row().apply {
                setPadding(0, dp(18), 0, 0)
                addView(dialogAction(context.getString(R.string.recipe_editor_cancel), tokens.textPrimary, soft = true) { dialog.dismiss() },
                    LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, 0, dp(8), 0) })
                addView(dialogAction(context.getString(R.string.recipe_editor_delete), Color.WHITE, soft = false, danger = true) {
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
        val input = editorInput(context.getString(R.string.recipe_editor_new_group_hint))
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = rounded(tokens.cardBackground, tokens.border, dp(22).toFloat())
            addView(TextView(context).apply {
                text = context.getString(R.string.recipe_editor_select_group)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
            })
            addView(ScrollView(context).apply {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    if (latestState.groups.isEmpty()) {
                        addView(stateText(context.getString(R.string.recipe_editor_no_groups)))
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
                addView(dialogAction(context.getString(R.string.recipe_editor_create_group), Color.WHITE, soft = false) {
                    val name = input.text?.toString().orEmpty().trim()
                    if (name.isBlank()) input.error = context.getString(R.string.recipe_editor_group_required) else {
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
            addView(sectionTitle(context.getString(R.string.recipe_editor_icon_picker_title)))
            addView(TextView(context).apply {
                text = context.getString(R.string.recipe_editor_icon_picker_summary)
                textSize = 12f
                setTextColor(tokens.textSecondary)
                setPadding(0, dp(4), 0, dp(12))
            })
            addView(iconSectionTitle(context.getString(R.string.recipe_editor_icon_collection)))
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
                    addView(iconChoiceGlyph("+", context.getString(R.string.recipe_editor_icon_add)))
                })
            })
            addView(iconSectionTitle(context.getString(R.string.recipe_editor_builtin_icons)).apply {
                setPadding(0, dp(14), 0, dp(8))
            })
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
            addView(sectionTitle(context.getString(R.string.recipe_editor_more_title)))
            addView(switchRow(
                context.getString(R.string.recipe_editor_open_instance_title),
                context.getString(R.string.recipe_editor_open_instance_summary),
                state.draft.launchOpenInstance
            ) { actions.onSetLaunchOpenInstance(it) })
            addView(switchRow(
                context.getString(R.string.recipe_editor_keep_notification_title),
                context.getString(R.string.recipe_editor_keep_notification_summary),
                state.draft.keepFinishedNotification
            ) { actions.onSetKeepFinishedNotification(it) })
            addView(commandRow(
                context.getString(R.string.recipe_editor_shortcut_title),
                context.getString(R.string.recipe_editor_shortcut_request)
            ) {
                dialog.dismiss()
                actions.onRequestShortcut()
            })
            if (!state.isNew) {
                addView(commandRow(
                    context.getString(R.string.recipe_editor_delete_config),
                    context.getString(R.string.recipe_editor_delete_config_summary),
                    danger = true,
                ) {
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
        addView(ui.imageButton(
            context = context,
            iconRes = R.drawable.ic_arrow_back_light,
            contentDescription = context.getString(R.string.common_back),
            onClick = actions::onBack,
        ), LinearLayout.LayoutParams(
            dp(environment.foundations.minimumTouchTarget),
            dp(environment.foundations.minimumTouchTarget),
        ))
        titleView.apply {
            ui.applyTextRole(this, UiTextRole.PageTitle)
            gravity = Gravity.CENTER
        }
        addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rightAction.apply {
            text = context.getString(R.string.recipe_editor_save)
            ui.applyTextRole(this, UiTextRole.Action)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(tokens.primaryStrong)
            contentDescription = context.getString(R.string.recipe_editor_save)
            setOnClickListener { actions.onSave() }
        }
        addView(FrameLayout(context).apply {
            addView(rightAction, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            addView(moreAction, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }, LinearLayout.LayoutParams(
            dp(environment.foundations.minimumTouchTarget),
            dp(environment.foundations.minimumTouchTarget),
        ))
    }

    private fun identityPanel(): View = row().apply {
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = ui.containerBackground(
            tokens.cardBackground,
            tokens.border,
            environment.components.card,
        )
        elevation = dp(environment.components.card.elevation).toFloat()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dp(environment.foundations.spacing.sectionGap)) }
        addView(iconHost, LinearLayout.LayoutParams(dp(58), dp(58)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
            addView(nameInput.apply {
                hint = context.getString(R.string.recipe_editor_name_hint)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setSingleLine(true)
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textTertiary)
                setPadding(dp(14), 0, dp(14), 0)
                background = ui.containerBackground(
                    tokens.inputBackground,
                    tokens.border,
                    environment.components.control,
                )
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
            addView(TextView(context).apply {
                text = context.getString(R.string.recipe_editor_choose_icon)
                ui.applyTextRole(this, UiTextRole.Supporting)
                setPadding(dp(2), dp(6), 0, 0)
                setOnClickListener { showIconDialog() }
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        iconHost.setOnClickListener { showIconDialog() }
    }

    private fun descriptionPanel(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = ui.containerBackground(
            tokens.cardBackground,
            tokens.border,
            environment.components.card,
        )
        elevation = dp(environment.components.card.elevation).toFloat()
        addView(TextView(context).apply {
            text = context.getString(R.string.recipe_editor_description_title)
            ui.applyTextRole(this, UiTextRole.CardTitle)
        })
        addView(descriptionInput.apply {
            hint = context.getString(R.string.recipe_editor_description_hint)
            textSize = environment.foundations.typography.body
            setSingleLine(true)
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            background = ui.containerBackground(
                tokens.inputBackground,
                tokens.border,
                environment.components.control,
            )
            setPadding(dp(14), 0, dp(14), 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            setMargins(0, dp(10), 0, 0)
        })
    }

    private fun addStepButton(): View = row().apply {
        gravity = Gravity.CENTER
        background = ui.containerBackground(
            tokens.surface,
            tokens.primarySoft,
            environment.components.control,
        )
        addView(ImageView(context).apply {
            setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_material_add))
            imageTintList = ColorStateList.valueOf(tokens.primaryStrong)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { setMargins(0, 0, dp(8), 0) })
        addView(TextView(context).apply {
            text = context.getString(R.string.recipe_editor_add_action)
            ui.applyTextRole(this, UiTextRole.Action)
            setTextColor(tokens.primaryStrong)
        })
        contentDescription = context.getString(R.string.recipe_editor_add_action)
        setOnClickListener { showStepDialog() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            setMargins(0, dp(14), 0, dp(4))
        }
    }

    private fun groupRow(): View = row().apply {
        setPadding(dp(18), dp(14), dp(16), dp(14))
        background = ui.containerBackground(
            tokens.cardBackground,
            tokens.border,
            environment.components.interactiveCard,
        )
        elevation = dp(environment.components.interactiveCard.elevation).toFloat()
        isClickable = true
        isFocusable = true
        setOnClickListener { showGroupDialog() }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = context.getString(R.string.recipe_editor_group_title)
                ui.applyTextRole(this, UiTextRole.CardTitle)
            })
            groupDetail.apply {
                ui.applyTextRole(this, UiTextRole.Supporting)
                setPadding(0, dp(3), dp(8), 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            addView(groupDetail)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = context.getString(R.string.recipe_editor_choose)
            ui.applyTextRole(this, UiTextRole.Action)
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            background = ui.containerBackground(
                tokens.primarySubtle,
                tokens.primarySoft,
                environment.components.chip,
            )
        }, LinearLayout.LayoutParams(dp(72), dp(38)))
    }

    private fun renderStatus(state: RecipeEditorUiState) {
        statusHost.removeAllViews()
        val message = when {
            state.phase == RecipeEditorPhase.Loading -> context.getString(R.string.recipe_editor_status_loading)
            state.phase == RecipeEditorPhase.Saving -> context.getString(R.string.recipe_editor_status_saving)
            state.phase == RecipeEditorPhase.Deleting -> context.getString(R.string.recipe_editor_status_deleting)
            state.validationErrors.isNotEmpty() -> state.validationErrors.first().message
            state.errorMessage != null -> state.errorMessage
            state.phase == RecipeEditorPhase.Failed -> context.getString(R.string.recipe_editor_status_failed)
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
            stepsHost.addView(stateText(context.getString(R.string.recipe_editor_no_actions)))
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
        existingOnlyHost.addView(commandRow(
            context.getString(R.string.recipe_editor_raw_json),
            context.getString(R.string.recipe_editor_raw_json_summary),
        ) {
            actions.onOpenRawJson(recipe.id)
        })
        existingOnlyHost.addView(commandRow(context.getString(R.string.recipe_editor_recent_runs), runHistorySummary(state)) {
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
            runActionHost.addView(runButton(context.getString(R.string.recipe_editor_run_open), tokens.primaryStrong, state.run?.status != CardRunStatus.Stopping) {
                acknowledgeRun()
                actions.onRun(KiteRecipeActionIntent.Open)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 7f))
            runActionHost.addView(View(context).apply { setBackgroundColor(tokens.border) },
                LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(0, dp(10), 0, dp(10)) })
            runActionHost.addView(runButton(context.getString(R.string.recipe_editor_run_stop), tokens.danger, state.run?.status != CardRunStatus.Stopping) {
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
                context.getString(
                    if (projection.primaryAction == KiteRunPrimaryAction.Retry) R.string.recipe_editor_run_retry
                    else R.string.recipe_editor_run_start,
                ),
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
        var agentId = initial?.agentId.orEmpty()
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
                    fields.addView(stepDialogField(
                        context.getString(R.string.recipe_editor_terminal_input),
                        context.getString(R.string.recipe_editor_terminal_input_hint),
                        command,
                    ) { command = it })
                KiteRecipe.STEP_SHELL -> {
                    fields.addView(stepDialogField(context.getString(R.string.recipe_editor_shell_command), "echo hello", command) { command = it })
                    fields.addView(stepDialogField(context.getString(R.string.recipe_editor_workdir), "/workspace", workdir) { workdir = it })
                }
                KiteRecipe.STEP_AGENT -> {
                    fields.addView(TextView(context).apply {
                        text = context.getString(R.string.recipe_editor_agent_choice)
                        textSize = 12.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(tokens.textPrimary)
                        setPadding(0, 0, 0, dp(8))
                    })
                    if (latestState.agents.isEmpty()) {
                        fields.addView(TextView(context).apply {
                            text = if (initial?.legacyProviderId.isNullOrBlank()) {
                                context.getString(R.string.recipe_editor_agent_empty)
                            } else {
                                context.getString(
                                    R.string.recipe_editor_agent_legacy_unresolved,
                                    initial?.legacyProviderId.orEmpty()
                                )
                            }
                            textSize = 13f
                            setTextColor(tokens.textSecondary)
                            setPadding(dp(14), dp(14), dp(14), dp(18))
                            background = rounded(tokens.surface, tokens.border, dp(14).toFloat())
                        })
                    } else {
                        latestState.agents.forEach { entry ->
                            fields.addView(agentChoice(entry, entry.registration.definition.agentId == agentId) {
                                agentId = entry.registration.definition.agentId
                                renderFields()
                            })
                        }
                    }
                    fields.addView(stepDialogField(
                        context.getString(R.string.recipe_editor_workdir),
                        "/workspace",
                        workdir,
                    ) { workdir = it })
                }
                else -> fields.addView(stepDialogField(
                    context.getString(R.string.recipe_editor_web_address),
                    "http://127.0.0.1:8648",
                    url,
                ) { url = it })
            }
        }

        fun renderTabs() {
            tabs.removeAllViews()
            listOf(
                KiteRecipe.STEP_TERMINAL to ">_ ${context.getString(R.string.recipe_editor_step_terminal)}",
                KiteRecipe.STEP_SHELL to context.getString(R.string.recipe_editor_shell_command),
                KiteRecipe.STEP_OPEN_WEB to "◎ ${context.getString(R.string.recipe_editor_step_web)}",
                KiteRecipe.STEP_AGENT to "✦ ${context.getString(R.string.recipe_editor_step_agent)}"
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
            addView(ui.imageButton(
                context = context,
                iconRes = R.drawable.ic_close_light,
                contentDescription = context.getString(R.string.common_close),
                onClick = { dialog.dismiss() },
            ), LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(TextView(context).apply {
                text = context.getString(
                    if (index == null) R.string.recipe_editor_add_action else R.string.recipe_editor_edit_action,
                )
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tokens.textPrimary)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (index != null) {
                addView(ui.imageButton(
                    context = context,
                    iconRes = R.drawable.ic_delete_light,
                    contentDescription = context.getString(R.string.recipe_editor_delete_action),
                    tint = tokens.danger,
                    onClick = {
                        dialog.dismiss()
                        actions.onRemoveStep(index)
                    },
                ), LinearLayout.LayoutParams(dp(44), dp(44)))
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
        content.addView(dialogAction(context.getString(
            if (index == null) R.string.recipe_editor_add else R.string.recipe_editor_save,
        ), Color.WHITE, soft = false) {
            val step = when (type) {
                KiteRecipe.STEP_TERMINAL -> RecipeEditorStepDraft.terminal(command.trim())
                KiteRecipe.STEP_SHELL -> {
                    if (command.isBlank()) return@dialogAction
                    RecipeEditorStepDraft.shell(command.trim(), workdir.trim())
                }
                KiteRecipe.STEP_AGENT -> {
                    if (agentId.isBlank()) return@dialogAction
                    RecipeEditorStepDraft.agent(agentId, workdir.trim().ifBlank { "/workspace" })
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

    private fun agentChoice(
        entry: AgentRegistryEntry,
        selected: Boolean,
        click: () -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(11), dp(14), dp(11))
        contentDescription = context.getString(
            R.string.recipe_editor_agent_choice_description,
            entry.registration.definition.displayName
        )
        background = rounded(
            if (selected) tokens.primarySubtle else tokens.surface,
            if (selected) tokens.primaryStrong else tokens.border,
            dp(14).toFloat()
        )
        addView(TextView(context).apply {
            text = entry.registration.definition.displayName
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (selected) tokens.primaryStrong else tokens.textPrimary)
        })
        addView(TextView(context).apply {
            text = "${entry.registration.definition.agentId} · ${agentStatus(entry)}"
            textSize = 12f
            setTextColor(tokens.textSecondary)
        })
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(8)) }
    }

    private fun agentStatus(entry: AgentRegistryEntry): String = when {
        entry.installationStatus == AgentInstallationStatus.NotInstalled ->
            context.getString(R.string.recipe_editor_agent_not_installed)
        entry.configurationStatus == AgentConfigurationStatus.Required ->
            context.getString(R.string.recipe_editor_agent_needs_configuration)
        entry.launchStatus == AgentLaunchStatus.Unsupported ->
            context.getString(R.string.recipe_editor_agent_unsupported)
        entry.runtimeStatus == AgentRuntimeStatus.Running ->
            context.getString(R.string.recipe_editor_agent_running)
        else -> context.getString(R.string.recipe_editor_agent_ready)
    }

    private fun stepIcon(step: RecipeEditorStepDraft): View = TextView(context).apply {
        val shell = step.type == KiteRecipe.STEP_SHELL || step.type == KiteRecipe.STEP_TERMINAL
        val agent = step.type == KiteRecipe.STEP_AGENT
        text = when {
            shell -> ">_"
            agent -> "AI"
            else -> "◎"
        }
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
            contentDescription = context.getString(
                if (label == "↑") R.string.recipe_editor_move_up else R.string.recipe_editor_move_down,
            )
            if (enabled) setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(38))
        }

    private fun stepTypeLabel(step: RecipeEditorStepDraft): String = when (step.type) {
        KiteRecipe.STEP_TERMINAL -> context.getString(R.string.recipe_editor_step_terminal)
        KiteRecipe.STEP_SHELL -> context.getString(R.string.recipe_editor_shell_command)
        KiteRecipe.STEP_AGENT -> context.getString(R.string.recipe_editor_step_agent)
        else -> context.getString(R.string.recipe_editor_step_open_web)
    }

    private fun stepSummary(step: RecipeEditorStepDraft): String = when (step.type) {
        KiteRecipe.STEP_TERMINAL -> step.command.ifBlank {
            context.getString(R.string.recipe_editor_step_open_terminal)
        }
        KiteRecipe.STEP_SHELL -> step.command.ifBlank {
            context.getString(R.string.recipe_editor_step_missing_shell)
        }
        KiteRecipe.STEP_AGENT -> latestState.agents
            .firstOrNull { it.registration.definition.agentId == step.agentId }
            ?.let { "${it.registration.definition.displayName} · ${agentStatus(it)}" }
            ?: step.agentId.ifBlank {
                step.legacyProviderId.takeIf(String::isNotBlank)
                    ?.let { context.getString(R.string.recipe_editor_agent_legacy_unresolved, it) }
                    ?: context.getString(R.string.recipe_editor_agent_missing)
            }
        else -> step.url.ifBlank { context.getString(R.string.recipe_editor_step_missing_url) }
    }

    private fun runHistorySummary(state: RecipeEditorUiState): String {
        val run = state.run ?: return context.getString(R.string.recipe_editor_no_runs)
        if (run.instanceId.startsWith("idle_") && run.status == CardRunStatus.Unknown) {
            return context.getString(R.string.recipe_editor_no_runs)
        }
        return context.getString(
            R.string.recipe_editor_run_history,
            localizedRunStatus(run.status),
            (run.currentStepIndex + 1).coerceAtLeast(1),
            run.stepCount.coerceAtLeast(1),
        )
    }

    private fun groupLabel(state: RecipeEditorUiState): String =
        state.groups.firstOrNull { it.id == state.draft.groupId }?.name
            ?: KiteRecipe.normalizeCategory(state.originalRecipe?.category)
                .ifBlank { context.getString(R.string.home_ungrouped) }

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
                text = context.getString(R.string.recipe_editor_icon_custom)
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
            contentDescription = title
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
        ui.applyTextRole(this, UiTextRole.SectionTitle)
        setPadding(0, 0, 0, dp(12))
    }

    private fun divider(top: Int): View = View(context).apply {
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
        "file" -> "F"
        "tools" -> "⚙"
        "server" -> "▷"
        "code" -> "{ }"
        "logs" -> "LOG"
        else -> "◎"
    }

    private fun iconLabel(name: String): String = when (name) {
        "terminal" -> context.getString(R.string.recipe_editor_icon_terminal)
        "web" -> context.getString(R.string.recipe_editor_icon_web)
        "bot" -> "AI"
        "file" -> context.getString(R.string.recipe_editor_icon_file)
        "tools" -> context.getString(R.string.recipe_editor_icon_tools)
        "server" -> context.getString(R.string.recipe_editor_icon_server)
        "code" -> context.getString(R.string.recipe_editor_icon_code)
        "logs" -> context.getString(R.string.recipe_editor_icon_logs)
        else -> context.getString(R.string.recipe_editor_icon_generic)
    }

    private fun localizedRunStatus(status: CardRunStatus): String = context.getString(when (status) {
        CardRunStatus.Unknown -> R.string.runtime_management_status_unknown
        CardRunStatus.Stopped -> R.string.runtime_management_status_stopped
        CardRunStatus.Starting -> R.string.runtime_management_status_starting
        CardRunStatus.Running -> R.string.runtime_management_status_running
        CardRunStatus.WaitingTerminal -> R.string.runtime_management_status_waiting_terminal
        CardRunStatus.AlreadyRunning -> R.string.runtime_management_status_already_running
        CardRunStatus.Opened -> R.string.runtime_management_status_opened
        CardRunStatus.Completed -> R.string.runtime_management_status_completed
        CardRunStatus.Failed -> R.string.runtime_management_status_failed
        CardRunStatus.Stopping -> R.string.runtime_management_status_stopping
        CardRunStatus.CleanupPending -> R.string.runtime_management_status_cleanup_pending
        CardRunStatus.BridgeUnavailable -> R.string.runtime_management_status_bridge_unavailable
    })

    private companion object {
        val presetIcons = listOf("terminal", "web", "bot", "file", "tools", "server", "code", "logs")
    }
}
