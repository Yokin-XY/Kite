package com.kite.app.feature.home

import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.doAfterTextChanged
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.kite.app.R
import com.kite.app.recipe.KiteCardGroup
import com.kite.app.recipe.KiteRecipe
import com.kite.app.ui.UiKit
import com.kite.app.ui.UiMenuItem
import java.util.Locale
import kotlin.math.abs

internal const val HOME_PAGE_ALL = "all"
internal const val HOME_PAGE_RUNNING = "opened"
private const val HOME_PAGE_GROUP_PREFIX = "group:"
private const val HOME_PAGE_CATEGORY_PREFIX = "category:"

internal enum class HomeSortMode(val storageValue: String) {
    Default("default"),
    Name("name"),
    Recent("recent");

    companion object {
        fun fromStorage(value: String?): HomeSortMode =
            entries.firstOrNull { it.storageValue == value } ?: Default
    }
}

/** 首页分页、滚动、卡片结构和局部运行绑定的真实所有者。 */
internal class HomeScreen(
    private val context: Context,
    initialPageId: String,
    initialScrollY: Int,
    initialSearchQuery: String,
    initialSortMode: HomeSortMode,
    private val onOpenEditor: (String) -> Unit,
    private val onPrimaryAction: (HomePrimaryActionTarget) -> Unit,
    private val onCreateGroup: () -> Unit,
    private val onExternalRefresh: () -> Unit,
    private val onRetry: () -> Unit
) {
    private val themeEnvironment = HomeFeatureTheme.environment(context)
    private val ui = UiKit(context, themeEnvironment)
    private val factory = HomeFeatureViewFactory(
        context = context,
        tokens = themeEnvironment.tokens,
        foundations = themeEnvironment.foundations,
        components = themeEnvironment.components,
        onOpenEditor = onOpenEditor,
        onPrimaryAction = onPrimaryAction
    )
    private var searchQuery = initialSearchQuery.trim()
    private var sortMode = initialSortMode
    private var anchoredMenu: PopupWindow? = null
    private val searchInput = EditText(context).apply {
        hint = context.getString(R.string.home_search_hint)
        contentDescription = context.getString(R.string.home_search_description)
        textSize = 15f
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        maxLines = 1
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT
        imeOptions = EditorInfo.IME_ACTION_DONE
        setTextColor(factory.tokens.textPrimary)
        setHintTextColor(factory.tokens.textTertiary)
        setPadding(factory.dp(15), 0, factory.dp(14), 0)
        compoundDrawablePadding = factory.dp(9)
        AppCompatResources.getDrawable(context, R.drawable.ic_material_search)?.let { icon ->
            icon.setTint(factory.tokens.textSecondary)
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        }
        background = factory.roundedBox(
            factory.tokens.inputBackground,
            factory.tokens.border,
            factory.dp(factory.components.control.radius).toFloat(),
            factory.dp(factory.components.control.strokeWidth),
        )
        setText(searchQuery)
        doAfterTextChanged { editable ->
            val next = editable?.toString().orEmpty().trim()
            if (next == searchQuery) return@doAfterTextChanged
            searchQuery = next
            scrollByPage[selectedPageId] = 0
            structureSignature = ""
            render(latestState)
        }
    }
    private val arrangeButton = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = sortContentDescription()
        background = factory.roundedBox(
            factory.tokens.surface,
            factory.tokens.border,
            factory.dp(factory.components.control.radius).toFloat(),
            factory.dp(factory.components.control.strokeWidth),
        )
        addView(iconView(R.drawable.ic_material_sort, factory.tokens.textSecondary, factory.dp(21)))
        addView(TextView(context).apply {
            text = context.getString(R.string.home_arrange)
            textSize = 13.5f
            includeFontPadding = false
            setTextColor(factory.tokens.textSecondary)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(factory.dp(7), 0, 0, 0) })
        setOnClickListener { showSortMenu(this) }
    }
    private val chipRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val chipScroller = HorizontalScrollView(context).apply {
        contentDescription = context.getString(R.string.home_tabs_description)
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        clipToPadding = false
        addView(chipRow, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
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
        setPadding(factory.dp(14), factory.dp(6), factory.dp(14), factory.dp(92))
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
            setPadding(
                factory.dp(factory.foundations.spacing.pageHorizontal),
                factory.dp(4),
                factory.dp(factory.foundations.spacing.pageHorizontal),
                0
            )
            addView(searchInput, LinearLayout.LayoutParams(0, factory.dp(48), 1f))
            addView(arrangeButton, LinearLayout.LayoutParams(factory.dp(98), factory.dp(48)).apply {
                setMargins(factory.dp(factory.foundations.spacing.sectionGap), 0, 0, 0)
            })
        })
        addView(chipScroller, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            factory.dp(58)
        ).apply {
            setMargins(0, factory.dp(8), 0, 0)
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
    }

    fun render(state: HomeFeatureUiState) {
        latestState = state
        val pages = pages(state)
        if (pages.none { it.id == selectedPageId }) selectedPageId = HOME_PAGE_ALL
        renderTabs(pages)
        refresh.isRefreshing = state.phase == HomeCatalogPhase.Loading && state.items.isNotEmpty()

        val visibleItems = visibleItems(state)
        when {
            state.phase == HomeCatalogPhase.Loading && state.items.isEmpty() ->
                showState(
                    context.getString(R.string.home_loading_title),
                    context.getString(R.string.home_loading_summary)
                )
            state.phase == HomeCatalogPhase.Failed && state.items.isEmpty() ->
                showState(
                    context.getString(R.string.home_load_failed_title),
                    state.errorMessage ?: context.getString(R.string.home_load_failed_summary),
                    onRetry
                )
            visibleItems.isEmpty() ->
                showState(
                    context.getString(R.string.home_empty_title),
                    context.getString(
                        when {
                            searchQuery.isNotBlank() -> R.string.home_empty_search_summary
                            selectedPageId == HOME_PAGE_ALL -> R.string.home_empty_all_summary
                            else -> R.string.home_empty_page_summary
                        }
                    )
                )
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

    fun searchQuery(): String = searchQuery

    fun sortMode(): HomeSortMode = sortMode

    fun dispose() {
        disposed = true
        elapsedTickPosted = false
        anchoredMenu?.dismiss()
        anchoredMenu = null
        bindings.clear()
    }

    internal fun actionViewForTest(recipeId: String): TextView? = bindings[recipeId]?.actionButton

    internal fun visibleRecipeIdsForTest(): List<String> = bindings.keys.toList()

    internal fun chipLabelsForTest(): List<String> =
        (0 until chipRow.childCount).mapNotNull { index ->
            val chip = chipRow.getChildAt(index) as? ViewGroup ?: return@mapNotNull null
            (0 until chip.childCount)
                .mapNotNull { childIndex -> chip.getChildAt(childIndex) as? TextView }
                .firstOrNull()
                ?.text
                ?.toString()
        }

    internal fun searchViewForTest(): EditText = searchInput

    internal fun arrangeViewForTest(): View = arrangeButton

    internal fun anchoredMenuForTest(): PopupWindow? = anchoredMenu

    private fun renderTabs(pages: List<HomePage>) {
        val signature = pages.joinToString("|") { page ->
            "${page.id}:${page.label}:${page.id == selectedPageId}"
        }
        if (signature == tabsSignature && chipRow.childCount == pages.size + 1) return
        tabsSignature = signature
        chipRow.removeAllViews()
        chipRow.setPadding(
            factory.dp(factory.foundations.spacing.pageHorizontal),
            0,
            factory.dp(factory.foundations.spacing.pageHorizontal),
            0
        )
        pages.forEach { page ->
            chipRow.addView(pageChip(page), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                factory.dp(42)
            ).apply { setMargins(0, 0, factory.dp(factory.foundations.spacing.itemGap), 0) })
        }
        chipRow.addView(createGroupChip(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            factory.dp(42)
        ))
    }

    private fun pageChip(page: HomePage): View = LinearLayout(context).apply {
        val selected = page.id == selectedPageId
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = if (selected) {
            context.getString(R.string.home_selected_page_description, page.label)
        } else {
            page.label
        }
        setPadding(factory.dp(14), 0, factory.dp(15), 0)
        background = factory.roundedBox(
            if (selected) factory.tokens.primarySubtle else factory.tokens.surface,
            if (selected) factory.tokens.primaryStrong else factory.tokens.border,
            factory.dp(factory.components.chip.radius).toFloat(),
            factory.dp(factory.components.chip.strokeWidth),
        )
        addView(iconView(
            page.iconRes,
            if (selected) factory.tokens.primaryStrong else factory.tokens.textSecondary,
            factory.dp(18)
        ))
        addView(TextView(context).apply {
            text = page.label
            textSize = 13f
            includeFontPadding = false
            typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            setTextColor(if (selected) factory.tokens.primaryStrong else factory.tokens.textSecondary)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(factory.dp(7), 0, 0, 0) })
        setOnClickListener { selectPage(page.id) }
    }

    private fun createGroupChip(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.home_create_group_description)
        setPadding(factory.dp(14), 0, factory.dp(15), 0)
        background = factory.roundedBox(
            factory.tokens.pageBackground,
            factory.tokens.borderStrong,
            factory.dp(factory.components.chip.radius).toFloat(),
            factory.dp(factory.components.chip.strokeWidth),
            dashWidth = factory.dp(4).toFloat(),
            dashGap = factory.dp(3).toFloat()
        )
        addView(iconView(
            R.drawable.ic_material_add,
            factory.tokens.textSecondary,
            factory.dp(18)
        ))
        addView(TextView(context).apply {
            text = context.getString(R.string.home_group_action)
            textSize = 13f
            includeFontPadding = false
            setTextColor(factory.tokens.textSecondary)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(factory.dp(6), 0, 0, 0) })
        setOnClickListener { onCreateGroup() }
    }

    private fun iconView(@DrawableRes iconRes: Int, tint: Int, size: Int): ImageView =
        ImageView(context).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(tint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(size, size)
        }

    private fun showSortMenu(anchor: View) {
        anchoredMenu?.dismiss()
        val options = listOf(
            HomeSortMode.Default to R.string.home_sort_default,
            HomeSortMode.Name to R.string.home_sort_name,
            HomeSortMode.Recent to R.string.home_sort_recent,
        )
        val popup = ui.showAnchoredMenu(
            context = context,
            anchor = anchor,
            items = options.map { (mode, labelRes) ->
                UiMenuItem(
                    label = context.getString(labelRes),
                    selected = mode == sortMode,
                    checkable = true,
                    onClick = { selectSortMode(mode) },
                )
            },
        )
        anchoredMenu = popup
        popup.setOnDismissListener {
            if (anchoredMenu === popup) anchoredMenu = null
        }
    }

    private fun selectSortMode(next: HomeSortMode) {
        if (next == sortMode) return
        sortMode = next
        arrangeButton.contentDescription = sortContentDescription()
        scrollByPage[selectedPageId] = 0
        structureSignature = ""
        render(latestState)
    }

    private fun sortContentDescription(): String = context.getString(
        R.string.home_arrange_description,
        context.getString(
            when (sortMode) {
                HomeSortMode.Default -> R.string.home_sort_default
                HomeSortMode.Name -> R.string.home_sort_name
                HomeSortMode.Recent -> R.string.home_sort_recent
            }
        )
    )

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
            val cardWidth = ((root.resources.displayMetrics.widthPixels - factory.dp(44)) / 2)
                .coerceAtLeast(factory.dp(132))
            items.forEach { item ->
                val binding = factory.card(item, groupLabel(item.recipe, groups))
                bindings[item.recipeId] = binding
                grid.addView(binding.root, GridLayout.LayoutParams().apply {
                    width = cardWidth
                    height = factory.dp(148)
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
        val fixedPages = listOf(
            HomePage(
                HOME_PAGE_ALL,
                context.getString(R.string.home_tab_all, state.items.size),
                R.drawable.ic_material_view_module
            ),
            HomePage(
                HOME_PAGE_RUNNING,
                context.getString(R.string.home_tab_running, opened),
                R.drawable.ic_material_play_arrow
            )
        )
        val groupPages = state.groups.map { group ->
            HomePage(groupPageId(group.id), group.name, R.drawable.ic_material_label)
        }
        val groupNames = state.groups
            .map(KiteCardGroup::name)
        val categoryPages = state.items.asSequence()
            .filter { it.recipe.groupId.isBlank() }
            .map { KiteRecipe.normalizeCategory(it.recipe.category) }
            .filter(String::isNotBlank)
            .distinct()
            .filter { category -> groupNames.none { name -> sameLabel(name, category) } }
            .map { category ->
                HomePage(categoryPageId(category), category, R.drawable.ic_material_label)
            }
            .toList()
        return fixedPages + groupPages + categoryPages
    }

    private fun itemsForPage(state: HomeFeatureUiState, pageId: String): List<HomeRecipeItemUiState> =
        when (pageId) {
            HOME_PAGE_ALL -> state.items
            HOME_PAGE_RUNNING -> state.items.filter { it.projection.live }
            else -> when {
                groupId(pageId) != null -> {
                    val id = requireNotNull(groupId(pageId))
                    val group = state.groups.firstOrNull { it.id == id }
                    state.items.filter { item ->
                        item.recipe.groupId == id || (
                            item.recipe.groupId.isBlank() &&
                                group != null &&
                                sameLabel(KiteRecipe.normalizeCategory(item.recipe.category), group.name)
                            )
                    }
                }
                categoryId(pageId) != null -> {
                    val category = requireNotNull(categoryId(pageId))
                    state.items.filter { item ->
                        item.recipe.groupId.isBlank() &&
                            sameLabel(KiteRecipe.normalizeCategory(item.recipe.category), category)
                    }
                }
                else -> state.items
            }
        }

    private fun visibleItems(state: HomeFeatureUiState): List<HomeRecipeItemUiState> {
        val groups = state.groups
        val query = searchQuery.lowercase(Locale.getDefault())
        val filtered = itemsForPage(state, selectedPageId).filter { item ->
            if (query.isBlank()) return@filter true
            sequenceOf(
                item.recipe.name,
                item.recipe.description,
                item.recipe.category,
                groupLabel(item.recipe, groups)
            ).any { value -> value.lowercase(Locale.getDefault()).contains(query) }
        }
        return when (sortMode) {
            HomeSortMode.Default -> filtered
            HomeSortMode.Name -> filtered.sortedBy { it.recipe.name.lowercase(Locale.getDefault()) }
            HomeSortMode.Recent -> filtered.sortedWith(
                compareByDescending<HomeRecipeItemUiState> { it.run.updatedAt }
                    .thenBy { it.recipe.name.lowercase(Locale.getDefault()) }
            )
        }
    }

    private fun groupLabel(recipe: KiteRecipe, groups: List<KiteCardGroup>): String =
        groups.firstOrNull { it.id == recipe.groupId }?.name
            ?: KiteRecipe.normalizeCategory(recipe.category)

    private fun sameLabel(left: String, right: String): Boolean = left.equals(right, ignoreCase = true)

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

    private fun categoryPageId(category: String): String = "$HOME_PAGE_CATEGORY_PREFIX$category"

    private fun groupId(pageId: String): String? =
        pageId.takeIf { it.startsWith(HOME_PAGE_GROUP_PREFIX) }
            ?.removePrefix(HOME_PAGE_GROUP_PREFIX)
            ?.takeIf(String::isNotBlank)

    private fun categoryId(pageId: String): String? =
        pageId.takeIf { it.startsWith(HOME_PAGE_CATEGORY_PREFIX) }
            ?.removePrefix(HOME_PAGE_CATEGORY_PREFIX)
            ?.takeIf(String::isNotBlank)

    private data class HomePage(
        val id: String,
        val label: String,
        @DrawableRes val iconRes: Int
    )

    private companion object {
    }
}
