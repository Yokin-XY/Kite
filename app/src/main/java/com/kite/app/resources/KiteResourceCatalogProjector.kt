package com.kite.app.resources

/** 把资源自身的目录声明投影为现有 UI 使用的 section/tab items。 */
internal object KiteResourceCatalogProjector {
    fun project(
        layout: KiteResourceHomeLayout,
        manifests: Collection<KiteResourceManifest>
    ): KiteResourceHomeLayout {
        val visible = manifests
            .filter { it.sections.isNotEmpty() }
            .associateBy(KiteResourceManifest::id)
        val sections = layout.sections.mapNotNull { section ->
            val automatic = visible.values
                .filter { section.id in it.sections }
                .sortedWith(resourceOrder)
                .map(KiteResourceManifest::id)
            section.copy(items = merge(section.items, automatic, visible.keys))
                .takeIf { it.items.isNotEmpty() }
        }
        val tabs = layout.tabs.map { tab ->
            if (tab.id == TAB_ALL) return@map tab.copy(sections = emptyList())
            val candidates = visible.values
                .filter { tab.id in it.catalogTabs }
                .sortedWith(resourceOrder)
            val projectedSections = when {
                tab.sections.size == 1 -> {
                    val section = tab.sections.single()
                    val automatic = candidates.ifEmpty {
                        visible.values
                            .filter { section.id in it.sections }
                            .sortedWith(resourceOrder)
                    }
                    listOf(
                        section.copy(
                            items = merge(
                                explicit = section.items,
                                automatic = automatic.map(KiteResourceManifest::id),
                                availableIds = visible.keys
                            )
                        )
                    ).filter { it.items.isNotEmpty() }
                }
                tab.sections.isNotEmpty() -> tab.sections.mapNotNull { section ->
                    val automatic = (if (candidates.isEmpty()) visible.values else candidates)
                        .filter { section.id in it.sections }
                        .sortedWith(resourceOrder)
                        .map(KiteResourceManifest::id)
                    section.copy(items = merge(section.items, automatic, visible.keys))
                        .takeIf { it.items.isNotEmpty() }
                }
                candidates.isNotEmpty() -> listOf(
                    KiteResourceHomeSection(
                        id = tab.id,
                        title = tab.label,
                        style = "list",
                        items = candidates.map(KiteResourceManifest::id)
                    )
                )
                else -> emptyList()
            }
            tab.copy(sections = projectedSections)
        }
        return layout.copy(
            sections = sections,
            hero = layout.hero?.takeIf { it.resourceId in visible },
            tabs = tabs
        )
    }

    private fun merge(
        explicit: List<String>,
        automatic: List<String>,
        availableIds: Set<String>
    ): List<String> = (explicit.filter(availableIds::contains) + automatic)
        .distinct()

    private val resourceOrder = compareBy<KiteResourceManifest>(
        KiteResourceManifest::catalogOrder,
        KiteResourceManifest::id
    )

    private const val TAB_ALL = "all"
}
