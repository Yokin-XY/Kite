package com.kite.app.feature.resources

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kite.app.action.KiteResourceActionIntent

/** 搜索页面的真实视图所有者；过滤只针对 Controller 已加载的内存目录。 */
internal class ResourceSearchScreen(
    context: Context,
    initialQuery: String,
    initialScrollY: Int,
    private val onBack: () -> Unit,
    private val onOpenDetail: (String) -> Unit,
    private val onPrimaryAction: (String) -> Unit,
    private val onRetry: () -> Unit
) {
    private val factory = ResourceFeatureViewFactory(
        context = context,
        tokens = ResourceFeatureTheme.tokens(context),
        onOpenDetail = onOpenDetail,
        onPrimaryAction = onPrimaryAction
    )
    private val resultsHost = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        contentDescription = "资源搜索结果"
    }
    private val scrollView = ScrollView(context).apply {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(factory.dp(22), factory.dp(12), factory.dp(22), factory.dp(88))
            addView(resultsHost)
        })
    }
    private val input = EditText(context).apply {
        setText(initialQuery)
        setSelection(text?.length ?: 0)
        hint = "搜索资源"
        textSize = 17f
        includeFontPadding = false
        setSingleLine(true)
        maxLines = 1
        background = null
        inputType = InputType.TYPE_CLASS_TEXT
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        setPadding(0, 0, 0, 0)
        setTextColor(factory.tokens.textPrimary)
        setHintTextColor(factory.tokens.textTertiary)
        contentDescription = "搜索资源输入框"
    }
    private val bindings = linkedMapOf<String, ResourceItemViewBinding>()
    private var latestState = ResourceFeatureUiState()
    private var structureSignature = ""
    private var renderGeneration = 0L
    private var restoredScrollY = initialScrollY.coerceAtLeast(0)

    val root: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        contentDescription = "资源搜索"
        setBackgroundColor(factory.tokens.pageBackground)
        addView(topBar(context))
        addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
    }

    init {
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                structureSignature = ""
                render(latestState)
            }
            override fun afterTextChanged(value: Editable?) = Unit
        })
        input.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(view)
                true
            } else {
                false
            }
        }
    }

    fun render(state: ResourceFeatureUiState) {
        latestState = state
        if (state.phase == ResourceCatalogPhase.Loading && state.items.isEmpty()) {
            replaceResults(factory.stateBlock("正在搜索资源", "资源目录会在后台加载。", loading = true))
            return
        }
        if (state.phase == ResourceCatalogPhase.Failed && state.items.isEmpty()) {
            replaceResults(factory.stateBlock(
                "资源搜索失败",
                state.errorMessage ?: "暂时无法读取资源目录",
                retry = onRetry
            ))
            return
        }
        val query = query()
        val items = state.items.searchResources(query)
        val nextSignature = buildString {
            append(query)
            items.forEach { item ->
                val presentation = item.presentation()
                append('|').append(item.resourceId)
                append(':').append(presentation.name)
                append(':').append(presentation.description)
                append(':').append(presentation.iconAsset)
            }
        }
        if (nextSignature != structureSignature || resultsHost.childCount == 0) {
            structureSignature = nextSignature
            rebuild(items, query)
        } else {
            items.forEach { item -> bindings[item.resourceId]?.let { factory.bind(it, item) } }
        }
    }

    fun acknowledge(resourceId: String, intent: KiteResourceActionIntent) {
        val label = when (intent) {
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
        factory.acknowledge(bindings[resourceId], label)
    }

    fun focusInput() {
        input.requestFocus()
        input.post {
            (input.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun query(): String = input.text?.toString().orEmpty().trim()

    fun scrollY(): Int = scrollView.scrollY

    fun dispose() {
        renderGeneration += 1L
        bindings.clear()
    }

    private fun rebuild(items: List<ResourceItemUiState>, query: String) {
        val generation = ++renderGeneration
        resultsHost.removeAllViews()
        bindings.clear()
        if (items.isEmpty()) {
            resultsHost.addView(emptyState(query))
            return
        }
        renderBatch(items, generation, 0)
    }

    private fun renderBatch(items: List<ResourceItemUiState>, generation: Long, startIndex: Int) {
        if (generation != renderGeneration) return
        val end = (startIndex + 4).coerceAtMost(items.size)
        for (index in startIndex until end) {
            val binding = factory.listRow(items[index])
            bindings[binding.resourceId] = binding
            resultsHost.addView(binding.root)
            if (index != items.lastIndex) {
                resultsHost.addView(factory.divider(), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    factory.dp(1)
                ).apply { setMargins(factory.dp(64), factory.dp(8), factory.dp(12), factory.dp(8)) })
            }
        }
        if (end < items.size) {
            resultsHost.postDelayed({ renderBatch(items, generation, end) }, 16L)
        } else if (restoredScrollY > 0) {
            val target = restoredScrollY
            restoredScrollY = 0
            scrollView.post { scrollView.scrollTo(0, target) }
        }
    }

    private fun replaceResults(view: View) {
        renderGeneration += 1L
        bindings.clear()
        resultsHost.removeAllViews()
        resultsHost.addView(view)
        structureSignature = ""
    }

    private fun emptyState(query: String): View = LinearLayout(root.context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(factory.dp(20), factory.dp(34), factory.dp(20), factory.dp(34))
        background = factory.roundedBox(factory.tokens.cardBackground, factory.tokens.border, factory.dp(18).toFloat())
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, factory.dp(24), 0, 0) }
        addView(TextView(context).apply {
            text = "没有找到相关资源"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(factory.tokens.textPrimary)
        })
        addView(TextView(context).apply {
            text = query
            textSize = 12.5f
            setTextColor(factory.tokens.textSecondary)
            setPadding(0, factory.dp(8), 0, 0)
        })
    }

    private fun topBar(context: Context): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(factory.dp(16), factory.dp(12), factory.dp(16), factory.dp(8))
        addView(TextView(context).apply {
            text = "‹"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(factory.tokens.textPrimary)
            contentDescription = "返回"
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(factory.dp(44), factory.dp(44)))
        addView(FrameLayout(context).apply {
            background = factory.roundedBox(factory.tokens.surfaceElevated, factory.tokens.border, factory.dp(22).toFloat())
            addView(TextView(context).apply {
                text = "⌕"
                textSize = 28f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(factory.tokens.textPrimary)
            }, FrameLayout.LayoutParams(factory.dp(44), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
            addView(input, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { setMargins(factory.dp(44), 0, factory.dp(42), 0) })
            addView(TextView(context).apply {
                text = "×"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(factory.tokens.textTertiary)
                contentDescription = "清空搜索"
                setOnClickListener {
                    input.setText("")
                    input.requestFocus()
                }
            }, FrameLayout.LayoutParams(factory.dp(42), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
        }, LinearLayout.LayoutParams(0, factory.dp(46), 1f).apply {
            setMargins(factory.dp(6), 0, factory.dp(10), 0)
        })
    }

    private fun hideKeyboard(anchor: View) {
        (anchor.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(anchor.windowToken, 0)
    }
}
