package com.kite.app.feature.resources

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import com.kite.app.action.KiteResourceActionIntent
import com.kite.app.resources.KiteResourceHomeLayout
import com.kite.app.resources.KiteResourceHomeTab

/** 资源目录页面的真实视图所有者。只绑定 UiState，不读取 Store、文件或 manifest loader。 */
internal class ResourceCatalogScreen(
    context: Context,
    initialTabId: String,
    initialScrollY: Int,
    private val onSearch: () -> Unit,
    private val onManage: () -> Unit,
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
    private val statusHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val heroHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val sectionsHost = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        contentDescription = "资源目录内容"
    }
    private val tabLayout = TabLayout(context).apply {
        contentDescription = "资源分类"
        tabMode = TabLayout.MODE_SCROLLABLE
        tabGravity = TabLayout.GRAVITY_START
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        setBackgroundColor(Color.TRANSPARENT)
        setSelectedTabIndicatorColor(factory.tokens.primaryStrong)
        setTabTextColors(factory.tokens.textSecondary, factory.tokens.primaryStrong)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, factory.dp(14)) }
    }
    private val scrollView = ScrollView(context)
    private val bindings = linkedMapOf<String, MutableList<ResourceItemViewBinding>>()
    private var latestState = ResourceFeatureUiState()
    private var selectedTabId = initialTabId.ifBlank { RESOURCE_HOME_TAB_ALL }
    private var tabsSignature = ""
    private var heroSignature = ""
    private var structureSignature = ""
    private var renderGeneration = 0L
    private var restoredScrollY = initialScrollY.coerceAtLeast(0)

    val root: View = FrameLayout(context).apply {
        contentDescription = "资源目录"
        setBackgroundColor(factory.tokens.pageBackground)
        scrollView.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(factory.dp(22), 0, factory.dp(22), factory.dp(96))
            addView(header(context))
            addView(statusHost)
            addView(heroHost)
            addView(tabLayout)
            addView(sectionsHost)
        })
        addView(scrollView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        addView(searchPill(context), FrameLayout.LayoutParams(
            factory.dp(184),
            factory.dp(42),
            Gravity.TOP or Gravity.END
        ).apply {
            setMargins(0, factory.dp(11), factory.dp(20), 0)
        })
    }

    init {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val next = tab.tag as? String ?: RESOURCE_HOME_TAB_ALL
                if (selectedTabId == next) return
                selectedTabId = next
                structureSignature = ""
                render(latestState)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    fun render(state: ResourceFeatureUiState) {
        latestState = state
        renderStatus(state)
        if (state.items.isEmpty()) {
            renderGeneration += 1L
            sectionsHost.removeAllViews()
            bindings.clear()
            structureSignature = ""
            return
        }
        renderHero(state)
        renderTabs(state.homeLayout)
        val sections = buildResourceSections(state.items, state.homeLayout, selectedTabId)
        val nextSignature = buildString {
            append(selectedTabId)
            sections.forEach { section ->
                append('|').append(section.id).append(':').append(section.style)
                section.items.forEach { item ->
                    val presentation = item.presentation()
                    append(',').append(item.resourceId)
                    append(':').append(presentation.name)
                    append(':').append(presentation.description)
                    append(':').append(presentation.iconAsset)
                }
            }
        }
        if (nextSignature != structureSignature || sectionsHost.childCount == 0) {
            structureSignature = nextSignature
            rebuildSections(sections)
        } else {
            state.items.associateBy(ResourceItemUiState::resourceId).forEach { (resourceId, item) ->
                bindings[resourceId].orEmpty().forEach { factory.bind(it, item) }
            }
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
        bindings[resourceId].orEmpty().forEach { factory.acknowledge(it, label) }
    }

    fun selectedTabId(): String = selectedTabId

    fun scrollY(): Int = scrollView.scrollY

    fun dispose() {
        renderGeneration += 1L
        bindings.clear()
    }

    private fun renderStatus(state: ResourceFeatureUiState) {
        statusHost.removeAllViews()
        when {
            state.phase == ResourceCatalogPhase.Loading && state.items.isEmpty() ->
                statusHost.addView(factory.stateBlock("正在读取资源", "资源目录会在后台加载。", loading = true))
            state.phase == ResourceCatalogPhase.Failed && state.items.isEmpty() ->
                statusHost.addView(factory.stateBlock(
                    "资源请求失败",
                    state.errorMessage ?: "暂时无法读取资源目录",
                    retry = onRetry
                ))
            state.phase == ResourceCatalogPhase.Failed ->
                statusHost.addView(factory.stateBlock(
                    "目录更新失败",
                    "仍显示上一次可用内容，可点击重试。",
                    retry = onRetry
                ))
        }
    }

    private fun renderTabs(layout: KiteResourceHomeLayout?) {
        val tabs = layout?.tabs.orEmpty().ifEmpty {
            listOf(KiteResourceHomeTab(RESOURCE_HOME_TAB_ALL, "全部", emptyList()))
        }
        if (tabs.none { it.id == selectedTabId }) selectedTabId = RESOURCE_HOME_TAB_ALL
        val signature = tabs.joinToString("|") { "${it.id}:${it.label}" }
        if (tabsSignature == signature && tabLayout.tabCount == tabs.size) return
        tabsSignature = signature
        tabLayout.removeAllTabs()
        tabs.forEach { tab ->
            tabLayout.addTab(
                tabLayout.newTab().setText(tab.label).setTag(tab.id),
                tab.id == selectedTabId
            )
        }
    }

    private fun renderHero(state: ResourceFeatureUiState) {
        val hero = state.homeLayout?.hero
        val item = hero?.resourceId?.let(state::item)
        val signature = if (hero == null || item == null) {
            ""
        } else {
            "${hero.resourceId}:${hero.imageAsset}:${hero.contentDescription}"
        }
        if (heroSignature == signature) return
        heroSignature = signature
        heroHost.removeAllViews()
        if (hero == null || item == null || hero.imageAsset.isBlank()) return
        heroHost.addView(HorizontalScrollView(root.context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, factory.dp(22), 0, factory.dp(18)) }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(factory.heroPoster(item, hero.imageAsset, hero.contentDescription))
            })
        })
    }

    private fun rebuildSections(sections: List<ResourceSectionPresentation>) {
        val generation = ++renderGeneration
        sectionsHost.removeAllViews()
        bindings.clear()
        if (sections.isEmpty()) {
            sectionsHost.addView(factory.stateBlock("暂无资源", "当前分类还没有可显示的资源。"))
            return
        }
        renderSectionBatch(sections, generation, 0)
    }

    private fun renderSectionBatch(
        sections: List<ResourceSectionPresentation>,
        generation: Long,
        index: Int
    ) {
        if (generation != renderGeneration || index >= sections.size) {
            restoreScrollIfNeeded()
            return
        }
        sectionsHost.addView(sectionView(sections[index], generation))
        sectionsHost.postDelayed(
            { renderSectionBatch(sections, generation, index + 1) },
            16L
        )
    }

    private fun sectionView(section: ResourceSectionPresentation, generation: Long): View =
        if (section.style.equals("shelf", ignoreCase = true)) {
            shelfSection(section, generation)
        } else {
            listSection(section, generation)
        }

    private fun listSection(section: ResourceSectionPresentation, generation: Long): View {
        val sectionRoot = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, factory.dp(24), 0, 0)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(factory.sectionTitle(section.title), LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ))
            })
        }
        val rows = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(factory.dp(14), factory.dp(10), factory.dp(14), factory.dp(10))
            background = factory.roundedBox(factory.tokens.cardBackground, factory.tokens.border, factory.dp(18).toFloat())
            elevation = factory.dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, factory.dp(12), 0, 0) }
        }
        sectionRoot.addView(rows)
        rows.post { renderRowBatch(section.items, rows, generation, 0) }
        return sectionRoot
    }

    private fun renderRowBatch(
        items: List<ResourceItemUiState>,
        host: LinearLayout,
        generation: Long,
        startIndex: Int
    ) {
        if (generation != renderGeneration || host.parent == null) return
        val end = (startIndex + 4).coerceAtMost(items.size)
        for (index in startIndex until end) {
            val binding = factory.listRow(items[index])
            bindings.getOrPut(binding.resourceId) { mutableListOf() } += binding
            host.addView(binding.root)
            if (index != items.lastIndex) {
                host.addView(factory.divider(), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    factory.dp(1)
                ).apply { setMargins(factory.dp(64), factory.dp(8), factory.dp(12), factory.dp(8)) })
            }
        }
        if (end < items.size) host.postDelayed(
            { renderRowBatch(items, host, generation, end) },
            16L
        )
    }

    private fun shelfSection(section: ResourceSectionPresentation, generation: Long): View {
        val sectionRoot = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, factory.dp(18), 0, 0)
        }
        val itemsHost = LinearLayout(root.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        sectionRoot.addView(HorizontalScrollView(root.context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(itemsHost)
        })
        itemsHost.post { renderShelfBatch(section.items, itemsHost, generation, 0) }
        return sectionRoot
    }

    private fun renderShelfBatch(
        items: List<ResourceItemUiState>,
        host: LinearLayout,
        generation: Long,
        startIndex: Int
    ) {
        if (generation != renderGeneration || host.parent == null) return
        val end = (startIndex + 5).coerceAtMost(items.size)
        for (index in startIndex until end) {
            val binding = factory.shelfItem(items[index])
            bindings.getOrPut(binding.resourceId) { mutableListOf() } += binding
            host.addView(binding.root, LinearLayout.LayoutParams(
                factory.dp(68),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index != items.lastIndex) setMargins(0, 0, factory.dp(7), 0)
            })
        }
        if (end < items.size) host.postDelayed(
            { renderShelfBatch(items, host, generation, end) },
            16L
        )
    }

    private fun header(context: Context): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, factory.dp(16), factory.dp(198), 0)
        isClickable = true
        isFocusable = true
        contentDescription = "打开资源管理"
        setOnClickListener { onManage() }
        addView(TextView(context).apply {
            text = "资源 ›"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(factory.tokens.textPrimary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun searchPill(context: Context): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        elevation = factory.dp(5).toFloat()
        background = factory.roundedBox(factory.tokens.surfaceElevated, factory.tokens.borderStrong, factory.dp(21).toFloat())
        contentDescription = "搜索资源"
        setPadding(factory.dp(16), 0, factory.dp(12), 0)
        setOnClickListener { onSearch() }
        addView(TextView(context).apply {
            text = "搜索资源"
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(factory.tokens.textSecondary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = "⌕"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(factory.tokens.textPrimary)
        })
    }

    private fun restoreScrollIfNeeded() {
        if (restoredScrollY <= 0) return
        val target = restoredScrollY
        restoredScrollY = 0
        scrollView.post { scrollView.scrollTo(0, target) }
    }
}
