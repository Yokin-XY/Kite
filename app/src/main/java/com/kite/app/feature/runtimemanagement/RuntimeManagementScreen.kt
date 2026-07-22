package com.kite.app.feature.runtimemanagement

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import com.kite.app.R
import com.kite.app.recipe.KiteRecipeIcon
import com.kite.app.run.KiteRunUiTone
import com.kite.app.ui.RecipeIconBitmapRepository
import com.kite.app.ui.UiActionRole
import com.kite.app.ui.UiDialogAction
import com.kite.app.ui.UiDialogField
import com.kite.app.ui.UiKit
import com.kite.app.ui.UiTextRole
import com.kite.app.ui.theme.kiteThemeEnvironment
import com.google.android.material.bottomsheet.BottomSheetDialog

/** 一级页选择运行作用域，二级页复用同一套应用组和进程树。 */
internal class RuntimeManagementScreen(
    private val context: Context,
    initialScrollY: Int,
    private val onBack: () -> Unit,
    private val onRefresh: () -> Unit,
    private val onAction: (RuntimeManagementActionUiState) -> Unit,
    initialScopeKey: String? = null,
) {
    private val environment = context.kiteThemeEnvironment()
    private val tokens = environment.tokens
    private val ui = UiKit(context, environment)
    private val titleView = TextView(context)
    private val scroll = ScrollView(context).apply { isFillViewport = true }
    private val contentHost = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(environment.foundations.spacing.pageHorizontal), dp(8), dp(environment.foundations.spacing.pageHorizontal), dp(96))
    }
    private val runBindings = linkedMapOf<String, RunBinding>()
    private val processBindings = linkedMapOf<String, ProcessBinding>()
    private var cardContextBinding: CardContextBinding? = null
    private val collapsedGroupKeys = mutableSetOf<String>()
    private var scope: Scope = Scope.restore(initialScopeKey)
    private var latestState = RuntimeManagementUiState()
    private var structureSignature = ""
    private var bodyRebuildCount = 0
    private var restoredScrollY = initialScrollY.coerceAtLeast(0)
    private var dialog: Dialog? = null
    private var disposed = false

    val root: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(tokens.pageBackground)
        addView(header())
        scroll.addView(contentHost)
        addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        post { scroll.scrollTo(0, restoredScrollY) }
    }

    fun render(state: RuntimeManagementUiState) {
        if (disposed) return
        latestState = state
        if (scope is Scope.Card && state.runs.none { it.instanceId == (scope as Scope.Card).instanceId }) {
            scope = Scope.Overview
        }
        titleView.text = scope.title(state, context)
        val nextSignature = "${scope.key}|${state.structureSignature()}|${collapsedGroupKeys.sorted()}"
        if (nextSignature != structureSignature || contentHost.childCount == 0) {
            structureSignature = nextSignature
            rebuildBody(state)
            return
        }
        state.runs.forEach { run -> runBindings[run.instanceId]?.let { bindRun(it, run) } }
        (scope as? Scope.Card)?.let { cardScope ->
            state.runs.firstOrNull { it.instanceId == cardScope.instanceId }?.let { run ->
                cardContextBinding?.let { bindCardContext(it, run) }
            }
        }
        state.allProcesses().associateBy(RuntimeManagementProcessUiState::key).let { processes ->
            processBindings.forEach { (key, binding) -> processes[key]?.let { bindProcess(binding, it) } }
        }
    }

    fun navigateUp(): Boolean {
        if (scope == Scope.Overview) return false
        restoredScrollY = 0
        scope = Scope.Overview
        structureSignature = ""
        render(latestState)
        return true
    }

    fun scrollY(): Int = scroll.scrollY
    fun scopeKey(): String = scope.key

    fun dispose() {
        disposed = true
        restoredScrollY = scroll.scrollY
        dialog?.dismiss()
        dialog = null
        runBindings.clear()
        processBindings.clear()
        cardContextBinding = null
        contentHost.removeAllViews()
    }

    internal fun bodyRebuildCountForTesting(): Int = bodyRebuildCount
    internal fun runRootForTesting(instanceId: String): View? = runBindings[instanceId]?.root
    internal fun processRootForTesting(key: String): View? = processBindings[key]?.root
    internal fun openAllForTesting() = openScope(Scope.All)
    internal fun openProcessMenuForTesting(key: String) = processBindings[key]?.item?.let(::showProcessMenu)
    internal fun scopeKeyForTesting(): String = scope.key

    private fun header(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(10))
        addView(ui.imageButton(
            context = context,
            iconRes = R.drawable.ic_arrow_back_light,
            contentDescription = context.getString(R.string.common_back),
            onClick = { if (!navigateUp()) onBack() },
        ), LinearLayout.LayoutParams(dp(environment.foundations.minimumTouchTarget), dp(environment.foundations.minimumTouchTarget)))
        addView(titleView.apply {
            ui.applyTextRole(this, UiTextRole.PageTitle)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(menuButton(context.getString(R.string.common_more), ::showPageMenu), LinearLayout.LayoutParams(
            dp(environment.foundations.minimumTouchTarget),
            dp(environment.foundations.minimumTouchTarget),
        ))
    }

    private fun rebuildBody(state: RuntimeManagementUiState) {
        bodyRebuildCount += 1
        runBindings.clear()
        processBindings.clear()
        cardContextBinding = null
        contentHost.removeAllViews()
        when (val current = scope) {
            Scope.Overview -> buildOverview(state)
            Scope.All -> buildDetail(
                groups = state.allProcessGroups,
                summary = context.getString(R.string.runtime_management_all_summary, state.summary.runningProcesses),
            )
            Scope.Unassigned -> buildDetail(
                groups = state.unassignedProcessGroups,
                summary = context.getString(R.string.runtime_management_unassigned_summary, state.unassignedProcessGroups.sumOf { it.processCount }),
            )
            is Scope.Card -> state.runs.firstOrNull { it.instanceId == current.instanceId }?.let(::buildCardDetail)
        }
        scroll.post { scroll.scrollTo(0, restoredScrollY) }
    }

    private fun buildOverview(state: RuntimeManagementUiState) {
        contentHost.addView(searchPlaceholder())
        contentHost.addView(navigationRow(
            title = context.getString(R.string.runtime_management_all_title),
            summary = context.getString(R.string.runtime_management_all_caption),
            count = state.summary.runningProcesses,
            onClick = { openScope(Scope.All) },
        ))
        contentHost.addView(divider(), marginParams(height = 1, top = 8, bottom = 22))
        contentHost.addView(sectionTitle(context.getString(R.string.runtime_management_cards_heading, state.runs.size)))
        if (state.runs.isEmpty()) {
            contentHost.addView(emptyCards())
        } else {
            state.runs.forEach { run ->
                val binding = createRunRow(run)
                runBindings[run.instanceId] = binding
                contentHost.addView(binding.root)
            }
        }
        contentHost.addView(divider(), marginParams(height = 1, top = 10, bottom = 8))
        contentHost.addView(navigationRow(
            title = context.getString(R.string.runtime_management_unassigned_title),
            summary = context.getString(R.string.runtime_management_unassigned_caption),
            count = state.unassignedProcessGroups.sumOf { it.processCount },
            onClick = { openScope(Scope.Unassigned) },
        ))
    }

    private fun buildCardDetail(run: RuntimeManagementRunUiState) {
        cardContextBinding = createCardContext(run).also { contentHost.addView(it.root) }
        buildDetail(
            groups = run.processGroups,
            summary = context.getString(R.string.runtime_management_card_scope_summary, run.processCount),
            addTopPadding = false,
        )
    }

    private fun buildDetail(
        groups: List<RuntimeManagementProcessGroupUiState>,
        summary: String,
        addTopPadding: Boolean = true,
    ) {
        contentHost.addView(TextView(context).apply {
            text = summary
            ui.applyTextRole(this, UiTextRole.Supporting)
        }, marginParams(top = if (addTopPadding) 8 else 16, bottom = 14))
        if (groups.isEmpty()) {
            contentHost.addView(emptyProcesses())
            return
        }
        groups.forEach { group -> contentHost.addView(processGroup(group)) }
    }

    private fun searchPlaceholder(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(16), 0)
        background = ui.containerBackground(tokens.surface, tokens.border, environment.components.control)
        alpha = 0.82f
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.runtime_management_search_coming_soon)
        addView(ImageView(context).apply {
            setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_material_search))
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(dp(24), dp(24)))
        addView(TextView(context).apply {
            text = context.getString(R.string.runtime_management_search_hint)
            ui.applyTextRole(this, UiTextRole.Body)
            setTextColor(tokens.textSecondary)
            setPadding(dp(12), 0, 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }.also { it.layoutParams = marginParams(height = 54, bottom = 20) }

    private fun navigationRow(
        title: String,
        summary: String,
        count: Int,
        onClick: () -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(13), dp(2), dp(13))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                ui.applyTextRole(this, UiTextRole.CardTitle)
            })
            addView(TextView(context).apply {
                text = summary
                ui.applyTextRole(this, UiTextRole.Supporting)
                setPadding(0, dp(4), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = count.toString()
            ui.applyTextRole(this, UiTextRole.Body)
            setTextColor(tokens.textSecondary)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(48), dp(40)))
        addView(chevron(), LinearLayout.LayoutParams(dp(28), dp(40)))
    }

    private fun createRunRow(run: RuntimeManagementRunUiState): RunBinding {
        val title = TextView(context)
        val subtitle = TextView(context)
        val statusDot = View(context)
        val more = menuButton(context.getString(R.string.runtime_management_card_actions)) { _ ->
            showRunMenu(runBindings[run.instanceId]?.item ?: run)
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(8), dp(14))
            background = ui.containerBackground(tokens.cardBackground, tokens.border, environment.components.interactiveCard)
            elevation = dp(environment.components.interactiveCard.elevation).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { openScope(Scope.Card(run.instanceId)) }
            addView(cardIcon(run.icon, dp(42)), LinearLayout.LayoutParams(dp(42), dp(42)).apply { setMargins(0, 0, dp(13), 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title.apply {
                    ui.applyTextRole(this, UiTextRole.CardTitle)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(5), 0, 0)
                    addView(statusDot, LinearLayout.LayoutParams(dp(7), dp(7)).apply { setMargins(0, 0, dp(7), 0) })
                    addView(subtitle.apply {
                        ui.applyTextRole(this, UiTextRole.Supporting)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(more, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(chevron(), LinearLayout.LayoutParams(dp(24), dp(44)))
        }
        root.layoutParams = marginParams(bottom = environment.foundations.spacing.itemGap)
        return RunBinding(root, title, subtitle, statusDot, run).also { bindRun(it, run) }
    }

    private fun bindRun(binding: RunBinding, run: RuntimeManagementRunUiState) {
        binding.item = run
        binding.title.text = run.title
        binding.subtitle.text = context.getString(R.string.runtime_management_run_process_count, run.statusLabel, run.processCount)
        binding.statusDot.background = ui.containerBackground(statusColors(run.statusTone).text, Color.TRANSPARENT, environment.components.chip)
    }

    private fun createCardContext(run: RuntimeManagementRunUiState): CardContextBinding {
        val status = TextView(context)
        val count = TextView(context)
        val more = menuButton(context.getString(R.string.runtime_management_card_actions)) { _ ->
            showRunMenu(cardContextBinding?.item ?: run)
        }
        val root = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(13), dp(10), dp(13))
        background = ui.containerBackground(tokens.cardBackground, tokens.border, environment.components.interactiveCard)
        addView(cardIcon(run.icon, dp(38)), LinearLayout.LayoutParams(dp(38), dp(38)).apply { setMargins(0, 0, dp(12), 0) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(status.apply {
                ui.applyTextRole(this, UiTextRole.Body)
            })
            addView(count.apply {
                ui.applyTextRole(this, UiTextRole.Supporting)
                setPadding(0, dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(more, LinearLayout.LayoutParams(dp(44), dp(44)))
        }.also { it.layoutParams = marginParams(bottom = 2) }
        return CardContextBinding(root, status, count, run).also { bindCardContext(it, run) }
    }

    private fun bindCardContext(binding: CardContextBinding, run: RuntimeManagementRunUiState) {
        binding.item = run
        binding.status.text = run.statusLabel
        binding.count.text = context.getString(R.string.runtime_management_card_surface_count, run.surfaces.size)
    }

    private fun processGroup(group: RuntimeManagementProcessGroupUiState): View {
        val expanded = group.key !in collapsedGroupKeys
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.containerBackground(tokens.cardBackground, tokens.border, environment.components.card)
            elevation = dp(environment.components.card.elevation).toFloat()
            layoutParams = marginParams(bottom = environment.foundations.spacing.itemGap)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(11), dp(10), dp(11))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (!collapsedGroupKeys.add(group.key)) collapsedGroupKeys.remove(group.key)
                    structureSignature = ""
                    render(latestState)
                }
                addView(processIcon(dp(34), group.isInfrastructure), LinearLayout.LayoutParams(dp(34), dp(34)).apply { setMargins(0, 0, dp(11), 0) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = group.title
                        ui.applyTextRole(this, UiTextRole.Body)
                        typeface = Typeface.DEFAULT_BOLD
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    addView(TextView(context).apply {
                        text = buildList {
                            add(context.getString(R.string.runtime_management_group_process_count, group.processCount))
                            if (group.cardLabels.size > 1) add(context.getString(R.string.runtime_management_group_card_count, group.cardLabels.size))
                        }.joinToString(" · ")
                        ui.applyTextRole(this, UiTextRole.Supporting)
                        setPadding(0, dp(3), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                group.stopAction?.let {
                    addView(
                        menuButton(context.getString(R.string.runtime_management_process_actions)) { _ ->
                            showProcessGroupMenu(group)
                        },
                        LinearLayout.LayoutParams(dp(44), dp(44)),
                    )
                }
                addView(chevron().apply { rotation = if (expanded) 90f else 0f }, LinearLayout.LayoutParams(dp(28), dp(40)))
            })
            if (expanded) {
                group.processes.forEach { process ->
                    addView(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { setMargins(dp(58), 0, dp(12), 0) })
                    val binding = createProcessRow(process)
                    processBindings[process.key] = binding
                    addView(binding.root)
                }
            }
        }
    }

    private fun createProcessRow(process: RuntimeManagementProcessUiState): ProcessBinding {
        val title = TextView(context)
        val subtitle = TextView(context)
        val more = menuButton(context.getString(R.string.runtime_management_process_actions)) { _ ->
            showProcessMenu(processBindings[process.key]?.item ?: process)
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12 + process.depth.coerceIn(0, 4) * 14), dp(10), dp(8), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { showProcessDetails(processBindings[process.key]?.item ?: process) }
            addView(processIcon(dp(32), process.isInfrastructure), LinearLayout.LayoutParams(dp(32), dp(32)).apply { setMargins(0, 0, dp(10), 0) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title.apply {
                    ui.applyTextRole(this, UiTextRole.Body)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(subtitle.apply {
                    ui.applyTextRole(this, UiTextRole.Supporting)
                    setPadding(0, dp(3), 0, 0)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(more, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        return ProcessBinding(root, title, subtitle, process).also { bindProcess(it, process) }
    }

    private fun bindProcess(binding: ProcessBinding, process: RuntimeManagementProcessUiState) {
        binding.item = process
        binding.title.text = process.title
        binding.subtitle.text = buildList {
            if (process.pid > 0) add("PID ${process.pid}")
            add(process.subtitle)
        }.filter(String::isNotBlank).joinToString(" · ")
    }

    private fun showPageMenu(anchor: View) {
        PopupMenu(context, anchor).apply {
            menu.add(context.getString(R.string.runtime_management_refresh))
            setOnMenuItemClickListener { onRefresh(); true }
        }.show()
    }

    private fun menuButton(contentDescription: String, onClick: (View) -> Unit): ImageView {
        lateinit var button: ImageView
        button = ui.imageButton(
            context = context,
            iconRes = R.drawable.ic_more_vert_light,
            contentDescription = contentDescription,
            onClick = { onClick(button) },
        )
        return button
    }

    private fun showRunMenu(run: RuntimeManagementRunUiState) {
        showActionSheet(
            title = run.title,
            subtitle = listOf(run.statusLabel, context.getString(R.string.runtime_management_process_count_summary, run.processCount))
                .joinToString(" · "),
            actions = buildList {
                add(RuntimeActionSheetItem(context.getString(R.string.runtime_management_action_view_processes)) {
                    openScope(Scope.Card(run.instanceId))
                })
                run.surfaces.forEach { surface ->
                    add(RuntimeActionSheetItem(context.getString(R.string.runtime_management_action_open_named, surface.title)) {
                        onAction(surface.openAction)
                    })
                }
                run.stopAction?.let { action ->
                    add(RuntimeActionSheetItem(action.label, danger = true, enabled = action.enabled) {
                        confirmAction(run.title, action)
                    })
                }
            },
        )
    }

    private fun showProcessMenu(process: RuntimeManagementProcessUiState) {
        val subtitle = buildList {
            if (process.pid > 0) add("PID ${process.pid}")
            add(process.stateLabel)
            add(process.cardLabel ?: process.ownerLabel)
        }.filter(String::isNotBlank).joinToString(" · ")
        showActionSheet(
            title = process.title,
            subtitle = subtitle,
            actions = buildList {
                add(RuntimeActionSheetItem(context.getString(R.string.runtime_management_action_details)) {
                    showProcessDetails(process)
                })
                add(RuntimeActionSheetItem(context.getString(R.string.runtime_management_action_copy_info)) {
                    copyProcessInfo(process)
                })
                process.stopAction?.let { action ->
                    add(RuntimeActionSheetItem(action.label, danger = true, enabled = action.enabled) {
                        confirmAction(process.title, action)
                    })
                }
            },
        )
    }

    private fun showProcessGroupMenu(group: RuntimeManagementProcessGroupUiState) {
        val action = group.stopAction ?: return
        showActionSheet(
            title = group.title,
            subtitle = context.getString(R.string.runtime_management_process_count_summary, group.processCount),
            actions = listOf(
                RuntimeActionSheetItem(action.label, danger = true, enabled = action.enabled) {
                    confirmAction(group.title, action)
                },
            ),
        )
    }

    private fun showActionSheet(
        title: String,
        subtitle: String,
        actions: List<RuntimeActionSheetItem>,
    ) {
        dialog?.dismiss()
        val bottomSheet = BottomSheetDialog(context)
        bottomSheet.setContentView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(24))
            background = ui.containerBackground(tokens.cardBackground, Color.TRANSPARENT, environment.components.dialog)
            addView(View(context).apply {
                background = ui.containerBackground(tokens.borderStrong, Color.TRANSPARENT, environment.components.chip)
            }, LinearLayout.LayoutParams(dp(38), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(TextView(context).apply {
                text = title
                ui.applyTextRole(this, UiTextRole.CardTitle)
                setPadding(dp(4), dp(20), dp(4), 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            if (subtitle.isNotBlank()) {
                addView(TextView(context).apply {
                    text = subtitle
                    ui.applyTextRole(this, UiTextRole.Supporting)
                    setPadding(dp(4), dp(5), dp(4), dp(12))
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
            } else {
                addView(View(context), LinearLayout.LayoutParams(1, dp(12)))
            }
            actions.forEachIndexed { index, action ->
                addView(TextView(context).apply {
                    text = action.label
                    ui.applyTextRole(this, UiTextRole.Action)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), 0, dp(16), 0)
                    setTextColor(if (action.danger) tokens.danger else tokens.textPrimary)
                    background = ui.containerBackground(
                        fill = if (action.danger) tokens.dangerSoft else tokens.surface,
                        stroke = if (action.danger) tokens.dangerBorder else tokens.border,
                        recipe = environment.components.control,
                    )
                    isEnabled = action.enabled
                    alpha = if (action.enabled) 1f else 0.62f
                    setOnClickListener {
                        if (!action.enabled) return@setOnClickListener
                        bottomSheet.dismiss()
                        action.onClick()
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
                    if (index > 0) setMargins(0, dp(8), 0, 0)
                })
            }
        })
        bottomSheet.show()
        dialog = bottomSheet
    }

    private fun showProcessDetails(process: RuntimeManagementProcessUiState) {
        dialog?.dismiss()
        val bottomSheet = BottomSheetDialog(context)
        val fields = buildList {
            if (process.pid > 0) add(UiDialogField("PID", process.pid.toString()))
            if (process.parentPid > 0) add(UiDialogField("PPID", process.parentPid.toString()))
            process.processGroupId?.takeIf { it > 1 }?.let { add(UiDialogField("PGID", it.toString())) }
            add(UiDialogField(context.getString(R.string.runtime_management_dialog_status), process.stateLabel))
            add(
                UiDialogField(
                    context.getString(R.string.runtime_management_dialog_process_identity),
                    context.getString(
                        if (process.identityVerified) {
                            R.string.runtime_management_identity_verified
                        } else {
                            R.string.runtime_management_identity_legacy
                        },
                    ),
                ),
            )
            add(UiDialogField(context.getString(R.string.runtime_management_dialog_owner), process.cardLabel ?: process.ownerLabel))
            add(UiDialogField(context.getString(R.string.runtime_management_dialog_purpose), process.purpose))
            if (process.commandLine.isNotBlank()) add(UiDialogField(context.getString(R.string.runtime_management_dialog_command), process.commandLine))
        }
        bottomSheet.setContentView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(24))
            background = ui.containerBackground(tokens.cardBackground, Color.TRANSPARENT, environment.components.dialog)
            addView(View(context).apply {
                background = ui.containerBackground(tokens.borderStrong, Color.TRANSPARENT, environment.components.chip)
            }, LinearLayout.LayoutParams(dp(38), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(TextView(context).apply {
                text = process.title
                ui.applyTextRole(this, UiTextRole.CardTitle)
                setPadding(0, dp(20), 0, dp(2))
            })
            fields.forEach { field ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = field.label
                        ui.applyTextRole(this, UiTextRole.Supporting)
                    })
                    addView(TextView(context).apply {
                        text = field.value
                        ui.applyTextRole(this, UiTextRole.Body)
                        setPadding(0, dp(3), 0, 0)
                        setTextIsSelectable(true)
                    })
                }, marginParams(top = 14))
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(context).apply {
                    text = context.getString(R.string.runtime_management_dialog_close)
                    ui.applyActionRole(this, UiActionRole.Secondary)
                    setOnClickListener { bottomSheet.dismiss() }
                }, LinearLayout.LayoutParams(0, dp(48), 1f))
                process.stopAction?.let { action ->
                    addView(TextView(context).apply {
                        text = action.label
                        ui.applyActionRole(this, UiActionRole.Danger)
                        isEnabled = action.enabled
                        alpha = if (action.enabled) 1f else 0.62f
                        setOnClickListener {
                            if (!action.enabled) return@setOnClickListener
                            bottomSheet.dismiss()
                            confirmAction(process.title, action)
                        }
                    }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(12), 0, 0, 0) })
                }
            }, marginParams(top = 22))
        })
        bottomSheet.show()
        dialog = bottomSheet
    }

    private data class RuntimeActionSheetItem(
        val label: String,
        val danger: Boolean = false,
        val enabled: Boolean = true,
        val onClick: () -> Unit,
    )

    private fun confirmAction(title: String, action: RuntimeManagementActionUiState) {
        dialog?.dismiss()
        dialog = ui.showDetailDialog(
            context = context,
            title = context.getString(R.string.runtime_management_confirm_title, action.label),
            fields = listOf(UiDialogField(title, context.getString(R.string.runtime_management_confirm_summary))),
            dismissLabel = context.getString(R.string.common_cancel),
            primaryAction = UiDialogAction(action.label, UiActionRole.Danger, action.enabled) { onAction(action) },
        )
    }

    private fun copyProcessInfo(process: RuntimeManagementProcessUiState) {
        val text = buildList {
            add(process.title)
            if (process.pid > 0) add("PID: ${process.pid}")
            if (process.parentPid > 0) add("PPID: ${process.parentPid}")
            process.processGroupId?.takeIf { it > 1 }?.let { add("PGID: $it") }
            add("${context.getString(R.string.runtime_management_dialog_status)}: ${process.stateLabel}")
            add(
                "${context.getString(R.string.runtime_management_dialog_process_identity)}: " +
                    context.getString(
                        if (process.identityVerified) R.string.runtime_management_identity_verified
                        else R.string.runtime_management_identity_legacy,
                    ),
            )
            add("${context.getString(R.string.runtime_management_dialog_owner)}: ${process.cardLabel ?: process.ownerLabel}")
            if (process.commandLine.isNotBlank()) add("${context.getString(R.string.runtime_management_dialog_command)}: ${process.commandLine}")
        }.joinToString("\n")
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(process.title, text))
        Toast.makeText(context, R.string.runtime_management_copied, Toast.LENGTH_SHORT).show()
    }

    private fun openScope(next: Scope) {
        restoredScrollY = 0
        scope = next
        structureSignature = ""
        render(latestState)
    }

    private fun cardIcon(icon: RuntimeManagementCardIconUiState, size: Int): View {
        val fallback = TextView(context).apply {
            text = iconGlyph(icon.name)
            textSize = 15f
            includeFontPadding = false
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.primaryStrong)
            background = ui.containerBackground(tokens.primarySubtle, tokens.border, environment.components.iconTile)
        }
        if (icon.type != KiteRecipeIcon.TYPE_IMAGE || icon.source.isBlank()) return fallback
        return FrameLayout(context).apply {
            background = ui.containerBackground(tokens.surface, tokens.border, environment.components.iconTile)
            clipToOutline = true
            addView(fallback, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            RecipeIconBitmapRepository.load(context, icon.source, size) { bitmap ->
                if (parent == null) return@load
                removeAllViews()
                addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(bitmap)
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
    }

    private fun processIcon(size: Int, infrastructure: Boolean): View = ImageView(context).apply {
        setImageDrawable(AppCompatResources.getDrawable(context, if (infrastructure) R.drawable.ic_bridge else R.drawable.ic_material_view_list))
        imageTintList = ColorStateList.valueOf(if (infrastructure) tokens.textSecondary else tokens.primaryStrong)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(7), dp(7), dp(7), dp(7))
        background = ui.containerBackground(if (infrastructure) tokens.surface else tokens.primarySubtle, Color.TRANSPARENT, environment.components.iconTile)
        layoutParams = LinearLayout.LayoutParams(size, size)
    }

    private fun chevron(): ImageView = ImageView(context).apply {
        setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_chevron_right_light))
        imageTintList = ColorStateList.valueOf(tokens.textSecondary)
        scaleType = ImageView.ScaleType.CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun sectionTitle(value: String): View = TextView(context).apply {
        text = value
        ui.applyTextRole(this, UiTextRole.SectionTitle)
        setPadding(dp(2), 0, 0, dp(12))
    }

    private fun emptyCards(): View = TextView(context).apply {
        text = context.getString(R.string.runtime_management_empty_cards)
        ui.applyTextRole(this, UiTextRole.Supporting)
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(26), dp(16), dp(26))
    }

    private fun emptyProcesses(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(34), dp(18), dp(34))
        background = ui.containerBackground(tokens.cardBackground, tokens.border, environment.components.card)
        addView(TextView(context).apply {
            text = context.getString(R.string.runtime_management_empty_title)
            ui.applyTextRole(this, UiTextRole.CardTitle)
            gravity = Gravity.CENTER
        })
        addView(TextView(context).apply {
            text = context.getString(R.string.runtime_management_empty_summary)
            ui.applyTextRole(this, UiTextRole.Supporting)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        })
    }

    private fun divider(): View = View(context).apply { setBackgroundColor(tokens.border) }

    private fun marginParams(
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        top: Int = 0,
        bottom: Int = 0,
    ): LinearLayout.LayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (height > 0) dp(height) else height).apply {
        setMargins(0, dp(top), 0, dp(bottom))
    }

    private fun statusColors(tone: KiteRunUiTone): StatusColors = when (tone) {
        KiteRunUiTone.Info -> StatusColors(tokens.info)
        KiteRunUiTone.Success -> StatusColors(tokens.success)
        KiteRunUiTone.Warning -> StatusColors(tokens.warning)
        KiteRunUiTone.Danger -> StatusColors(tokens.danger)
        KiteRunUiTone.Neutral -> StatusColors(tokens.textTertiary)
    }

    private fun iconGlyph(name: String): String = when (name) {
        KiteRecipeIcon.ICON_TERMINAL -> ">_"
        KiteRecipeIcon.ICON_WEB -> "◎"
        KiteRecipeIcon.ICON_BOT -> "AI"
        KiteRecipeIcon.ICON_CODE -> "{ }"
        KiteRecipeIcon.ICON_SERVER -> "▷"
        else -> "◎"
    }

    private fun RuntimeManagementUiState.structureSignature(): String = buildString {
        append(runs.joinToString { run -> "${run.instanceId}:${run.icon.type}:${run.icon.source}:${run.processGroups.signature()}:${run.surfaces.joinToString { it.key }}" })
        append('|').append(allProcessGroups.signature())
        append('|').append(unassignedProcessGroups.signature())
    }

    private fun List<RuntimeManagementProcessGroupUiState>.signature(): String = joinToString { group ->
        "${group.key}:${group.title}:${group.processCount}:${group.stopAction?.mutationKey}:${group.stopAction?.enabled}:" +
            group.processes.joinToString { "${it.key}@${it.depth}" }
    }

    private fun RuntimeManagementUiState.allProcesses(): List<RuntimeManagementProcessUiState> =
        (allProcessGroups + runs.flatMap(RuntimeManagementRunUiState::processGroups))
            .flatMap(RuntimeManagementProcessGroupUiState::processes)
            .distinctBy(RuntimeManagementProcessUiState::key)

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private sealed interface Scope {
        val key: String

        data object Overview : Scope { override val key: String = "overview" }
        data object All : Scope { override val key: String = "all" }
        data object Unassigned : Scope { override val key: String = "unassigned" }
        data class Card(val instanceId: String) : Scope { override val key: String = "card:$instanceId" }

        fun title(state: RuntimeManagementUiState, context: Context): String = when (this) {
            Overview -> context.getString(R.string.runtime_management_title)
            All -> context.getString(R.string.runtime_management_all_title)
            Unassigned -> context.getString(R.string.runtime_management_unassigned_title)
            is Card -> state.runs.firstOrNull { it.instanceId == instanceId }?.title
                ?: context.getString(R.string.runtime_management_title)
        }

        companion object {
            fun restore(value: String?): Scope = when {
                value == All.key -> All
                value == Unassigned.key -> Unassigned
                value?.startsWith("card:") == true -> Card(value.removePrefix("card:"))
                else -> Overview
            }
        }
    }

    private data class RunBinding(
        val root: LinearLayout,
        val title: TextView,
        val subtitle: TextView,
        val statusDot: View,
        var item: RuntimeManagementRunUiState,
    )

    private data class ProcessBinding(
        val root: LinearLayout,
        val title: TextView,
        val subtitle: TextView,
        var item: RuntimeManagementProcessUiState,
    )

    private data class CardContextBinding(
        val root: LinearLayout,
        val status: TextView,
        val count: TextView,
        var item: RuntimeManagementRunUiState,
    )

    private data class StatusColors(val text: Int)

}
