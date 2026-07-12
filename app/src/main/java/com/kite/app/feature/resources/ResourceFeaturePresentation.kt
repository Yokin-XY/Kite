package com.kite.app.feature.resources

import com.kite.app.resources.KiteResourceHomeLayout
import com.kite.app.resources.KiteResourceHomeSection
import com.kite.app.resources.KiteResourceInstallRecipes
import java.util.Locale

internal data class ResourceItemPresentation(
    val id: String,
    val name: String,
    val description: String,
    val section: String,
    val category: String,
    val iconText: String,
    val iconAsset: String,
    val iconFit: String,
    val accent: String,
    val version: String,
    val sizeLabel: String,
    val sourceLabel: String,
    val stateLabel: String,
    val actionLabel: String,
    val actionEnabled: Boolean,
    val secondaryActionLabel: String?,
    val searchableText: String
)

internal data class ResourceSectionPresentation(
    val id: String,
    val title: String,
    val style: String,
    val items: List<ResourceItemUiState>
)

internal fun ResourceItemUiState.presentation(): ResourceItemPresentation {
    val manifest = descriptor.manifest
    val sourceLabel = when (manifest?.sourceType) {
        "bundled" -> "内置"
        "apt" -> "apt"
        "official_script" -> "官方脚本"
        "npm" -> "npm"
        "git" -> "GitHub"
        "command" -> "网络"
        else -> ""
    }
    val category = manifest?.displayCategory.orEmpty().ifBlank {
        val tags = manifest?.tags.orEmpty().map { it.lowercase(Locale.ROOT) }.toSet()
        val provides = manifest?.provides.orEmpty().map { it.lowercase(Locale.ROOT) }
        when {
            "ai" in tags || "agent" in tags || "coding-agent" in tags ||
                provides.any { it.startsWith("agent.") } -> "AI"
            provides.any { it.startsWith("runtime.node") || it == "tool.npm" || it == "tool.npx" } ->
                "JavaScript"
            provides.any {
                it.startsWith("runtime.python") || it == "tool.pip" || it == "tool.venv" ||
                    it == "tool.uv" || it == "tool.uvx"
            } -> "Python"
            else -> "系统工具"
        }
    }
    val accent = manifest?.displayAccent.orEmpty().ifBlank {
        when (category) {
            "AI" -> "teal"
            "JavaScript" -> "green"
            "Python" -> "blue"
            else -> "orange"
        }
    }
    val name = descriptor.name.ifBlank { descriptor.id }
    val description = manifest?.description.orEmpty().ifBlank { sourceLabel.ifBlank { descriptor.id } }
    val section = sectionLabel(manifest?.sections?.firstOrNull()).ifBlank { "更多资源" }
    val iconText = manifest?.iconText.orEmpty().ifBlank {
        name.trim().take(2).ifBlank { "R" }
    }
    val sizeLabel = manifest?.displaySizeLabel.orEmpty().ifBlank {
        when (manifest?.sourceType) {
            "bundled" -> "内置包"
            "apt", "npm", "official_script", "git", "command" -> "网络包"
            else -> ""
        }
    }
    val searchableText = listOf(
        name,
        description,
        category,
        sourceLabel,
        manifest?.tags.orEmpty().joinToString(" "),
        manifest?.provides.orEmpty().joinToString(" ")
    ).joinToString(" ")
    return ResourceItemPresentation(
        id = descriptor.id,
        name = name,
        description = description,
        section = section,
        category = category,
        iconText = iconText,
        iconAsset = manifest?.iconAsset.orEmpty(),
        iconFit = manifest?.iconFit.orEmpty(),
        accent = accent,
        version = manifest?.version.orEmpty().ifBlank { "latest" },
        sizeLabel = sizeLabel,
        sourceLabel = sourceLabel,
        stateLabel = projection.stateLabel,
        actionLabel = projection.actionLabel,
        actionEnabled = projection.actionEnabled,
        secondaryActionLabel = projection.secondaryActionLabel,
        searchableText = searchableText
    )
}

internal fun buildResourceSections(
    items: List<ResourceItemUiState>,
    layout: KiteResourceHomeLayout?,
    tabId: String
): List<ResourceSectionPresentation> {
    val tab = layout?.tabs.orEmpty().firstOrNull { it.id == tabId }
    val specs = tab?.sections?.takeIf(List<KiteResourceHomeSection>::isNotEmpty)
        ?: layout?.sections.orEmpty()
    val includeFallback = tab == null || tab.id == RESOURCE_HOME_TAB_ALL || tab.sections.isEmpty()
    val byId = items.associateBy(ResourceItemUiState::resourceId)
    val usedIds = linkedSetOf<String>()
    val declared = specs.mapNotNull { section ->
        val sectionItems = section.items.mapNotNull { id -> byId[id]?.also { usedIds += it.resourceId } }
        sectionItems.takeIf(List<ResourceItemUiState>::isNotEmpty)?.let {
            ResourceSectionPresentation(section.id, section.title, section.style, it)
        }
    }
    val fallback = if (!includeFallback) {
        emptyList()
    } else {
        items.filterNot { it.resourceId in usedIds }
            .filterNot { it.presentation().section == "仅搜索" }
            .groupBy { it.presentation().section }
            .map { (title, sectionItems) ->
                ResourceSectionPresentation(
                    id = KiteResourceInstallRecipes.safeId(title),
                    title = title.ifBlank { "更多资源" },
                    style = "list",
                    items = sectionItems
                )
            }
    }
    return declared + fallback
}

internal fun List<ResourceItemUiState>.searchResources(query: String): List<ResourceItemUiState> {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return this
    return mapIndexedNotNull { index, item ->
        val score = item.searchScore(cleanQuery)
        if (score <= 0) null else Triple(score, index, item)
    }.sortedWith(compareByDescending<Triple<Int, Int, ResourceItemUiState>> { it.first }.thenBy { it.second })
        .map(Triple<Int, Int, ResourceItemUiState>::third)
}

private fun ResourceItemUiState.searchScore(query: String): Int {
    val presentation = presentation()
    val needle = normalizeSearchText(query)
    if (needle.isBlank()) return 0
    val compactName = normalizeSearchText(presentation.name)
    val acronym = presentation.name.split(Regex("[^A-Za-z0-9]+"))
        .mapNotNull { it.firstOrNull()?.lowercaseChar() }
        .joinToString("")
    val nameIndex = compactName.indexOf(needle)
    return when {
        compactName == needle -> 10_000
        compactName.startsWith(needle) -> 9_000 - (compactName.length - needle.length).coerceAtLeast(0)
        nameIndex >= 0 -> 8_000 - (nameIndex * 80) - (compactName.length - needle.length).coerceAtMost(80)
        acronym.startsWith(needle) -> 7_000
        needle.length == 1 -> 0
        normalizeSearchText(presentation.searchableText).contains(needle) -> 3_000
        else -> fuzzyNameScore(needle, compactName)
    }
}

private fun normalizeSearchText(value: String): String =
    value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9\\p{L}]+"), "")

private fun fuzzyNameScore(needle: String, candidate: String): Int {
    if (needle.length < 3 || candidate.isBlank()) return 0
    var candidateIndex = 0
    var matched = 0
    needle.forEach { char ->
        val next = candidate.indexOf(char, candidateIndex)
        if (next < 0) return 0
        matched += 1
        candidateIndex = next + 1
    }
    return if (matched == needle.length) 1_000 - (candidate.length - needle.length).coerceAtMost(200) else 0
}

private fun sectionLabel(section: String?): String = when (section) {
    "foundation" -> "基础环境"
    "ai-vendor" -> "厂商工具"
    "ai-community" -> "独立工具"
    "featured" -> "精选推荐"
    "quick" -> "快速开始"
    "more" -> "更多资源"
    "search-only" -> "仅搜索"
    else -> ""
}

internal const val RESOURCE_HOME_TAB_ALL = "all"
