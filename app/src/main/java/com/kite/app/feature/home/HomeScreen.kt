package com.kite.app.feature.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.tabs.TabLayout
import com.kite.app.recipe.KiteCardGroup
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunStatus
import kotlin.math.abs

internal const val HOME_PAGE_ALL = "all"
internal const val HOME_PAGE_OPENED = "opened"
internal const val HOME_PAGE_STOPPED = "stopped"
private const val HOME_PAGE_GROUP_PREFIX = "group:"

/** 首页分页、滚动、卡片结构和局部运行绑定的真实所有者。 */
internal class HomeScreen(
    context: Context,
    initialPageId: String,
    initialScrollY: Int,
    private val onOpenEditor: (String) -> Unit,
    private val onPrimaryAction: (String) -> Unit,
    private val onCreateGroup: () -> Unit,
    private val onExternalRefresh: () -> Unit,
    private val onRetry: () -> Unit
) {
    private val factory = HomeFeatureViewFactory(
        context = context,
        tokens = HomeFeatureTheme.tokens(context),
        onOpenEditor = onOpenEditor,
        onPrimaryAction = onPrimaryAction
    )
    private val tabs = TabLayout(context).apply {
        contentDescription = "配置分页"
        tabMode = TabLayout.MODE_SCROLLABLE
        tabGravity = TabLayout.GRAVITY_START
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        setBackgroundColor(Color.TRANSPARENT)
        setSelectedTabIndicatorColor(factory.tokens.primaryStrong)
        setTabTextColors(factory.tokens.textSecondary, factory.tokens.primaryStrong)
    }
    private val bodyHost = object : FrameLayout(context) {
        private var downX = 0f
        private var downY = 0f

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) > factory.dp(54) && abs(dx) > abs(dy) * 1.4f) {
                        selectPageOffset(if (dx < 0) 1 else -1)
                        return true
                    }
                }
            }
            return super.dispatchTouchEvent(event)
        }
    }
    private val refresh = SwipeRefreshLayout(context).apply {
        setColorSchemeColors(factory.tokens.primaryStrong)
        setProgressBackgroundColorSchemeColor(factory.tokens.surfaceElevated)
        setOnRefreshListener { onExternalRefresh() }
    }
    private val scroll = ScrollView(context).apply { isFillViewport = true }
    private val grid = GridLayout(context).apply {
        columnCount = 2
        setPadding(factory.dp(10), factory.dp(8), factory.dp(10), factory.dp(92))
        clipToPadding = false
    }
    private val bindings = linkedMapOf<String, HomeCardBinding>()
    private val scrollByPage = linkedMapOf<String, Int>()
    private var latestState = HomeFeatureUiState()
    private var selectedPageId = initialPageId.ifBlank { HOME_PAGE_ALL }
    private var initialScrollY = initialScrollY.coerceAtLeast(0)
    private var tabsSignature = ""
    private var structureSignature = ""
    private var contentMode = ""
    private var disposed = false
    private var elapsedTickPosted = false

    val root: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(factory.tokens.pageBackground)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(factory.dp(18), factory.dp(2), factory.dp(18), 0)
            addView(tabs, LinearLayout.LayoutParams(0, factory.dp(48), 1f))
            addView(TextView(context).apply {
                text = "+"
                textSize = 23f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                contentDescription = "新建卡片分组"
                setTextColor(factory.tokens.primaryStrong)
                background = factory.roundedBox(
                    factory.tokens.surface,
                    factory.tokens.border,
                    factory.dp(18).toFloat()
                )
                setOnClickListener { onCreateGroup() }
            }, LinearLayout.LayoutParams(factory.dp(42), factory.dp(38)).apply {
                setMargins(factory.dp(8), factory.dp(5), 0, 0)
            })
        })
        addView(bodyHost, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
    }

    init {
        scroll.addView(grid)
        refresh.addView(scroll, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectPage(tab.tag as? String ?: HOME_PAGE_ALL)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    fun render(state: HomeFeatureUiState) {
        latestState = state
        val pages = pages(state)
        if (pages.none { it.id == selectedPageId }) selectedPageId = HOME_PAGE_ALL
        renderTabs(pages)
        refresh.isRefreshing = state.phase == HomeCatalogPhase.Loading && state.items.isNotEmpty()

        val visibleItems = itemsForPage(state, selectedPageId)
        when {
            state.phase == HomeCatalogPhase.Loading && state.items.isEmpty() ->
                showState("正在读取卡片", "卡片目录会在后台加载。")
            state.phase == HomeCatalogPhase.Failed && state.items.isEmpty() ->
                showState("卡片读取失败", state.errorMessage ?: "暂时无法读取卡片目录", onRetry)
            visibleItems.isEmpty() ->
                showState("暂无卡片", if (selectedPageId == HOME_PAGE_ALL) "新建或导入卡片后会显示在这里。" else "当前分页没有卡片。")
            else -> renderGrid(visibleItems, state.groups)
        }
    }

    fun acknowledge(recipeId: String) {
        factory.acknowledge(bindings[recipeId])
    }

    fun selectGroup(groupId: String) {
        val pageId = groupPageId(groupId)
        if (pages(latestState).any { it.id == pageId }) selectPage(pageId)
    }

    fun selectedPageId(): String = selectedPageId

    fun scrollY(): Int = scroll.scrollY

    fun dispose() {
        disposed = true
        elapsedTickPosted = false
        bindings.clear()
    }

    internal fun actionViewForTest(recipeId: String): TextView? = bindings[recipeId]?.actionButton

    internal fun visibleRecipeIdsForTest(): List<String> = bindings.keys.toList()

    private fun renderTabs(pages: List<HomePage>) {
        val signature = pages.joinToString("|") { it.id }
        if (signature != tabsSignature || tabs.tabCount != pages.size) {
            tabsSignature = signature
            tabs.removeAllTabs()
            pages.forEach { page ->
                tabs.addTab(
                    tabs.newTab().setText(page.label).setTag(page.id),
                    page.id == selectedPageId
                )
            }
            return
        }
        pages.forEachIndexed { index, page ->
            val tab = tabs.getTabAt(index) ?: return@forEachIndexed
            if (tab.text?.toString() != page.label) tab.text = page.label
        }
        val selectedIndex = pages.indexOfFirst { it.id == selectedPageId }
        if (selectedIndex >= 0 && tabs.selectedTabPosition != selectedIndex) {
            tabs.selectTab(tabs.getTabAt(selectedIndex))
        }
    }

    private fun renderGrid(items: List<HomeRecipeItemUiState>, groups: List<KiteCardGroup>) {
        val signature = buildString {
            append(selectedPageId)
            items.forEach { item ->
                append('|').append(item.recipeId)
                append(':').append(item.recipe.name)
                append(':').append(item.recipe.category)
                append(':').append(item.recipe.groupId)
                append(':').append(item.recipe.icon.type)
                append(':').append(item.recipe.icon.name)
                append(':').append(item.recipe.icon.source)
                append(':').append(item.recipe.steps.size)
            }
        }
        showGridHost()
        if (signature != structureSignature || bindings.keys.toList() != items.map { it.recipeId }) {
            structureSignature = signature
            val restoreY = scrollByPage[selectedPageId] ?: initialScrollY
            initialScrollY = 0
            grid.removeAllViews()
            bindings.clear()
            val cardWidth = ((root.resources.displayMetrics.widthPixels - factory.dp(36)) / 2)
                .coerceAtLeast(factory.dp(132))
            items.forEach { item ->
                val binding = factory.card(item, groupLabel(item.recipe, groups))
                bindings[item.recipeId] = binding
                grid.addView(binding.root, GridLayout.LayoutParams().apply {
                    width = cardWidth
                    height = factory.dp(130)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED)
                    setMargins(factory.dp(4), factory.dp(4), factory.dp(4), factory.dp(8))
                })
            }
            scroll.post { scroll.scrollTo(0, restoreY) }
        } else {
            items.forEach { item -> bindings[item.recipeId]?.let { factory.bind(it, item) } }
        }
        scheduleElapsedTickIfNeeded()
    }

    private fun showGridHost() {
        if (contentMode == "grid" && refresh.parent === bodyHost) return
        contentMode = "grid"
        bodyHost.removeAllViews()
        bodyHost.addView(refresh, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun showState(title: String, detail: String, retry: (() -> Unit)? = null) {
        val key = "state:$title:$detail:${retry != null}"
        if (contentMode == key) return
        contentMode = key
        structureSignature = ""
        bindings.clear()
        bodyHost.removeAllViews()
        bodyHost.addView(factory.stateBlock(title, detail, retry), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))
    }

    private fun selectPageOffset(offset: Int) {
        val pages = pages(latestState)
        val index = pages.indexOfFirst { it.id == selectedPageId }.takeIf { it >= 0 } ?: 0
        pages.getOrNull(index + offset)?.let { selectPage(it.id) }
    }

    private fun selectPage(pageId: String) {
        if (selectedPageId == pageId || pages(latestState).none { it.id == pageId }) return
        scrollByPage[selectedPageId] = scroll.scrollY
        selectedPageId = pageId
        structureSignature = ""
        render(latestState)
    }

    private fun pages(state: HomeFeatureUiState): List<HomePage> {
        val opened = state.items.count { it.projection.live }
        val stopped = state.items.count { it.run.status in stoppedStatuses }
        return listOf(
            HomePage(HOME_PAGE_ALL, "▦  全部 ${state.items.size}"),
            HomePage(HOME_PAGE_OPENED, "▶  已打开 $opened"),
            HomePage(HOME_PAGE_STOPPED, "■  已停止 $stopped")
        ) + state.groups.map { group -> HomePage(groupPageId(group.id), group.name) }
    }

    private fun itemsForPage(state: HomeFeatureUiState, pageId: String): List<HomeRecipeItemUiState> =
        when (pageId) {
            HOME_PAGE_ALL -> state.items
            HOME_PAGE_OPENED -> state.items.filter { it.projection.live }
            HOME_PAGE_STOPPED -> state.items.filter { it.run.status in stoppedStatuses }
            else -> groupId(pageId)?.let { id ->
                val group = state.groups.firstOrNull { it.id == id }
                state.items.filter { item ->
                    item.recipe.groupId == id || (
                        item.recipe.groupId.isBlank() &&
                            group != null &&
                            KiteRecipe.normalizeCategory(item.recipe.category) == group.name
                        )
                }
            } ?: state.items
        }

    private fun groupLabel(recipe: KiteRecipe, groups: List<KiteCardGroup>): String =
        groups.firstOrNull { it.id == recipe.groupId }?.name
            ?: KiteRecipe.normalizeCategory(recipe.category)

    private fun scheduleElapsedTickIfNeeded() {
        if (elapsedTickPosted || disposed || bindings.values.none { it.item.projection.live }) return
        elapsedTickPosted = true
        root.postDelayed({
            elapsedTickPosted = false
            if (disposed) return@postDelayed
            bindings.values.forEach(factory::refreshElapsed)
            scheduleElapsedTickIfNeeded()
        }, 1000L)
    }

    private fun groupPageId(groupId: String): String = "$HOME_PAGE_GROUP_PREFIX$groupId"

    private fun groupId(pageId: String): String? =
        pageId.takeIf { it.startsWith(HOME_PAGE_GROUP_PREFIX) }
            ?.removePrefix(HOME_PAGE_GROUP_PREFIX)
            ?.takeIf(String::isNotBlank)

    private data class HomePage(val id: String, val label: String)

    private companion object {
        val stoppedStatuses = setOf(
            CardRunStatus.Stopped,
            CardRunStatus.Completed,
            CardRunStatus.Failed,
            CardRunStatus.BridgeUnavailable
        )
    }
}
