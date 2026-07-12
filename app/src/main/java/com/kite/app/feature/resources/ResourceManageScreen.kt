package com.kite.app.feature.resources

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.resources.KiteResourceStepTone

/** 资源管理页面的真实视图所有者；队列与已获取列表分别做结构更新和事实重绑。 */
internal class ResourceManageScreen(
    context: Context,
    initialScrollY: Int,
    private val onBack: () -> Unit,
    private val onOpenDetail: (String) -> Unit,
    private val onPrimaryAction: (String) -> Unit,
    private val onOpenPlan: (String) -> Unit,
    private val onCancelPlan: (String, List<String>) -> Unit,
    private val onRetry: () -> Unit
) {
    private val factory = ResourceFeatureViewFactory(
        context = context,
        tokens = ResourceFeatureTheme.tokens(context),
        onOpenDetail = onOpenDetail,
        onPrimaryAction = onPrimaryAction
    )
    private val queueHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val installedHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scrollView = ScrollView(context).apply { isFillViewport = true }
    private val installedBindings = linkedMapOf<String, ResourceItemViewBinding>()
    private var queueStructureSignature = ""
    private var installedStructureSignature = ""
    private var queueBinding: QueueBinding? = null
    private var restoredScrollY = initialScrollY.coerceAtLeast(0)

    val root: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        contentDescription = "资源管理"
        setBackgroundColor(factory.tokens.pageBackground)
        addView(topBar())
        scrollView.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(factory.dp(22), factory.dp(18), factory.dp(22), factory.dp(34))
            addView(factory.sectionTitle("执行队列"))
            addView(queueHost, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, factory.dp(12), 0, 0) })
            addView(factory.sectionTitle("已获取资源").apply {
                setPadding(0, factory.dp(24), 0, factory.dp(12))
            })
            addView(installedHost)
        })
        addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
    }

    fun render(state: ResourceFeatureUiState) {
        if (state.phase in setOf(ResourceCatalogPhase.Idle, ResourceCatalogPhase.Loading) && state.items.isEmpty()) {
            renderLoading()
            return
        }
        if (state.phase == ResourceCatalogPhase.Failed && state.items.isEmpty()) {
            renderFailure(state.errorMessage.orEmpty())
            return
        }
        renderQueue(state)
        renderInstalled(state)
        restoreScrollIfNeeded()
    }

    fun acknowledge(resourceId: String, intent: KiteResourceActionIntent) {
        factory.acknowledge(installedBindings[resourceId], acknowledgementLabel(intent))
    }

    fun scrollY(): Int = scrollView.scrollY.takeIf { it > 0 } ?: restoredScrollY

    fun dispose() {
        queueBinding = null
        installedBindings.clear()
    }

    private fun renderLoading() {
        if (queueStructureSignature == "loading") return
        queueStructureSignature = "loading"
        queueBinding = null
        queueHost.removeAllViews()
        queueHost.addView(factory.stateBlock(
            title = "正在读取资源管理信息",
            detail = "执行队列和已获取资源会在后台加载，避免阻塞当前页面。",
            loading = true
        ))
        installedStructureSignature = "loading"
        installedBindings.clear()
        installedHost.removeAllViews()
        installedHost.addView(emptyBlock("正在校准已获取资源", "读取完成后会直接更新当前区域。"))
    }

    private fun renderFailure(message: String) {
        queueStructureSignature = "failure:$message"
        queueBinding = null
        queueHost.removeAllViews()
        queueHost.addView(factory.stateBlock(
            title = "资源管理读取失败",
            detail = message.ifBlank { "稍后返回资源页后可重新进入。" },
            retry = onRetry
        ))
        if (installedHost.childCount == 0) {
            installedHost.addView(emptyBlock("暂无已获取资源", "资源目录恢复后会在这里重新校准。"))
        }
    }

    private fun renderQueue(state: ResourceFeatureUiState) {
        val plan = state.plan
        val resourceIds = plan.resourceIds.distinct()
        if (resourceIds.isEmpty()) {
            if (queueStructureSignature != "empty") {
                queueStructureSignature = "empty"
                queueBinding = null
                queueHost.removeAllViews()
                queueHost.addView(emptyBlock(
                    "暂无执行任务",
                    "从资源商店点击获取或卸载后，这里会显示当前队列。"
                ))
            }
            return
        }
        val targetId = plan.targetResourceId.ifBlank { resourceIds.last() }
        val signature = "$targetId|${resourceIds.joinToString(",")}"
        val binding = if (queueStructureSignature == signature) {
            queueBinding
        } else {
            queueStructureSignature = signature
            queueHost.removeAllViews()
            queueCard(targetId, resourceIds).also { next ->
                queueBinding = next
                queueHost.addView(next.root)
            }
        } ?: return
        bindQueue(binding, state, targetId, resourceIds)
    }

    private fun queueCard(targetId: String, resourceIds: List<String>): QueueBinding {
        val title = TextView(root.context)
        val detail = TextView(root.context)
        val badge = TextView(root.context)
        val card = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(factory.dp(16), factory.dp(15), factory.dp(16), factory.dp(15))
            background = factory.roundedBox(
                factory.tokens.cardBackground,
                factory.tokens.border,
                factory.dp(18).toFloat()
            )
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(factory.icon(
                    textValue = "↓",
                    accent = "teal",
                    assetPath = "",
                    iconFit = "",
                    size = factory.dp(44),
                    padding = factory.dp(8),
                    radius = factory.dp(13).toFloat(),
                    textSize = 16f
                ), LinearLayout.LayoutParams(factory.dp(44), factory.dp(44)).apply {
                    setMargins(0, 0, factory.dp(12), 0)
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(title.apply {
                        textSize = 16f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(factory.tokens.textPrimary)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    addView(detail.apply {
                        textSize = 12f
                        setTextColor(factory.tokens.textSecondary)
                        setPadding(0, factory.dp(4), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(badge.apply {
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(factory.dp(9), 0, factory.dp(9), 0)
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    factory.dp(22)
                ))
            })
        }
        bindQueueGesture(card, targetId, resourceIds)
        return QueueBinding(card, title, detail, badge)
    }

    private fun bindQueue(
        binding: QueueBinding,
        state: ResourceFeatureUiState,
        targetId: String,
        resourceIds: List<String>
    ) {
        val stepsById = state.plan.steps.associateBy(ResourcePlanStepUiState::resourceId)
        val itemsById = state.items.associateBy(ResourceItemUiState::resourceId)
        val completedCount = resourceIds.count { resourceId ->
            stepsById[resourceId]?.projection?.statusLabel == "已完成" ||
                itemsById[resourceId]?.phase in installedPhases
        }
        val hasUninstalling = resourceIds.any { stepsById[it]?.projection?.uninstalling == true }
        val hasUninstallFailure = resourceIds.any { itemsById[it]?.phase == ResourceItemPhase.UninstallFailed }
        val hasFailure = resourceIds.any { resourceId ->
            itemsById[resourceId]?.phase == ResourceItemPhase.InstallFailed ||
                (stepsById[resourceId]?.projection?.failed == true &&
                    itemsById[resourceId]?.phase != ResourceItemPhase.UninstallFailed)
        }
        val hasRunningStep = resourceIds.any { resourceId ->
            stepsById[resourceId]?.projection?.statusLabel == "获取中" ||
                itemsById[resourceId]?.phase in setOf(ResourceItemPhase.Preparing, ResourceItemPhase.Installing)
        }
        val status = when {
            hasRunningStep -> QueueStatus("获取中", KiteResourceStepTone.Primary)
            hasUninstalling -> QueueStatus("卸载中", KiteResourceStepTone.Primary)
            hasUninstallFailure -> QueueStatus("卸载失败", KiteResourceStepTone.Danger)
            hasFailure -> QueueStatus("已停止", KiteResourceStepTone.Danger)
            completedCount >= resourceIds.size -> QueueStatus("已完成", KiteResourceStepTone.Success)
            else -> QueueStatus("等待中", KiteResourceStepTone.Neutral)
        }
        val targetName = itemsById[targetId]?.presentation()?.name ?: targetId.ifBlank { "获取任务" }
        val tone = when (status.tone) {
            KiteResourceStepTone.Primary -> factory.tokens.primaryStrong
            KiteResourceStepTone.Success -> factory.tokens.success
            KiteResourceStepTone.Danger -> factory.tokens.danger
            KiteResourceStepTone.Neutral -> factory.tokens.textSecondary
        }
        binding.root.contentDescription = "$targetName，${status.label}，点击打开，上滑关闭"
        binding.title.text = targetName
        binding.detail.text = "$completedCount/${resourceIds.size} · ${status.label} · 点击打开，上滑关闭"
        binding.badge.apply {
            text = status.label
            setTextColor(tone)
            background = factory.roundedBox(
                colorWithAlpha(tone, 0.11f),
                Color.TRANSPARENT,
                factory.dp(11).toFloat()
            )
        }
    }

    private fun bindQueueGesture(view: View, targetId: String, resourceIds: List<String>) {
        var downX = 0f
        var downY = 0f
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener { onOpenPlan(targetId) }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (dy < -factory.dp(16) && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                        view.translationY = dy.coerceAtLeast(-factory.dp(56).toFloat())
                        view.alpha = (1f + dy / factory.dp(160).toFloat()).coerceIn(0.62f, 1f)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    resetGesture(view)
                    if (dy < -factory.dp(48) && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.2f) {
                        onCancelPlan(targetId, resourceIds)
                    } else {
                        view.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    resetGesture(view)
                    true
                }
                else -> true
            }
        }
    }

    private fun resetGesture(view: View) {
        view.parent?.requestDisallowInterceptTouchEvent(false)
        view.translationY = 0f
        view.alpha = 1f
    }

    private fun renderInstalled(state: ResourceFeatureUiState) {
        val installed = state.items.filter { it.phase in installedPhases }
        val signature = installed.joinToString("|") { item ->
            val presentation = item.presentation()
            listOf(
                item.resourceId,
                presentation.name,
                presentation.description,
                presentation.version,
                presentation.sizeLabel,
                presentation.iconAsset
            ).joinToString(":")
        }.ifBlank { "empty" }
        if (installedStructureSignature != signature) {
            installedStructureSignature = signature
            installedBindings.clear()
            installedHost.removeAllViews()
            if (installed.isEmpty()) {
                installedHost.addView(emptyBlock(
                    "暂无已获取资源",
                    "获取成功并完成注册后，会出现在这里。"
                ))
            } else {
                installedHost.addView(LinearLayout(root.context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(factory.dp(14), factory.dp(10), factory.dp(14), factory.dp(10))
                    background = factory.roundedBox(
                        factory.tokens.cardBackground,
                        factory.tokens.border,
                        factory.dp(18).toFloat()
                    )
                    installed.forEachIndexed { index, item ->
                        val binding = factory.listRow(item)
                        installedBindings[item.resourceId] = binding
                        addView(binding.root)
                        if (index != installed.lastIndex) {
                            addView(factory.divider(), LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                factory.dp(1)
                            ).apply { setMargins(factory.dp(64), factory.dp(8), factory.dp(12), factory.dp(8)) })
                        }
                    }
                })
            }
        } else {
            installed.forEach { item -> factory.bind(installedBindings[item.resourceId] ?: return@forEach, item) }
        }
    }

    private fun topBar(): View = FrameLayout(rootContext()).apply {
        setPadding(factory.dp(18), 0, factory.dp(18), 0)
        addView(TextView(context).apply {
            text = "‹"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            contentDescription = "返回"
            setTextColor(factory.tokens.textPrimary)
            setOnClickListener { onBack() }
        }, FrameLayout.LayoutParams(factory.dp(44), factory.dp(56), Gravity.START or Gravity.CENTER_VERTICAL))
        addView(TextView(context).apply {
            text = "资源管理"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(factory.tokens.textPrimary)
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            factory.dp(56),
            Gravity.CENTER
        ).apply { setMargins(factory.dp(52), 0, factory.dp(52), 0) })
    }

    private fun emptyBlock(title: String, detail: String): View = LinearLayout(rootContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(factory.dp(16), factory.dp(17), factory.dp(16), factory.dp(17))
        background = factory.roundedBox(
            factory.tokens.cardBackground,
            factory.tokens.border,
            factory.dp(18).toFloat()
        )
        addView(TextView(context).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(factory.tokens.textPrimary)
        })
        addView(TextView(context).apply {
            text = detail
            textSize = 12f
            setTextColor(factory.tokens.textSecondary)
            setPadding(0, factory.dp(5), 0, 0)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
    }

    private fun restoreScrollIfNeeded() {
        if (restoredScrollY <= 0) return
        val target = restoredScrollY
        restoredScrollY = 0
        scrollView.post { scrollView.scrollTo(0, target) }
    }

    private fun rootContext(): Context = scrollView.context

    private fun acknowledgementLabel(intent: KiteResourceActionIntent): String = when (intent) {
        KiteResourceActionIntent.Install,
        KiteResourceActionIntent.ReopenInstall -> "准备中"
        KiteResourceActionIntent.Open -> "打开中"
        KiteResourceActionIntent.Stop -> "停止中"
        KiteResourceActionIntent.Uninstall -> "卸载中"
        KiteResourceActionIntent.CancelInstall,
        KiteResourceActionIntent.CancelFailedInstall -> "取消中"
        KiteResourceActionIntent.BusyStatus,
        KiteResourceActionIntent.Unsupported -> "处理中"
    }

    private fun colorWithAlpha(color: Int, alpha: Float): Int = Color.argb(
        (255 * alpha.coerceIn(0f, 1f)).toInt(),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    private data class QueueBinding(
        val root: View,
        val title: TextView,
        val detail: TextView,
        val badge: TextView
    )

    private data class QueueStatus(val label: String, val tone: KiteResourceStepTone)

    private companion object {
        val installedPhases = setOf(
            ResourceItemPhase.Installed,
            ResourceItemPhase.Starting,
            ResourceItemPhase.Running,
            ResourceItemPhase.Stopping
        )
    }
}
