package com.kite.app.resources

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.kite.app.foundation.runtime.RuntimeExecutionGuaranteeCodec
import org.json.JSONArray
import org.json.JSONObject

data class KiteResourceManifest(
    val id: String,
    val availability: KiteResourceAvailability = KiteResourceAvailability.STABLE,
    val name: String,
    val description: String,
    val version: String,
    val iconText: String,
    val iconAsset: String,
    val iconFit: String = "",
    val displayCategory: String,
    val displayAccent: String,
    val displaySizeLabel: String,
    val displayLongDescription: String,
    val displayBadge: KiteResourceBadgeSpec?,
    val displayMedia: KiteResourceMediaSpec?,
    val displayPreviewCards: List<KiteResourcePreviewSpec>,
    val displayRequirementRows: List<KiteResourceDisplayRowSpec>,
    val displayRecommendations: List<KiteResourceRecommendationSpec>,
    val sections: List<String>,
    val tags: List<String>,
    val provides: List<String>,
    val baseRequirements: List<String>,
    val defaultRequirements: List<String>,
    val extensions: List<String>,
    val management: KiteResourceManagementSpec,
    val source: KiteResourceSourceSpec,
    val sourceType: String,
    val installActions: List<KiteResourceShellAction>,
    val updateActions: List<KiteResourceShellAction>,
    val uninstallActions: List<KiteResourceShellAction>,
    val agentProfiles: List<KiteResourceAgentProfile> = emptyList(),
    val openRecipe: JSONObject?,
    val homeCards: List<KiteResourceHomeCard>,
    val rawJson: JSONObject,
    val installRoot: String = "",
    val catalogTabs: List<String> = emptyList(),
    val catalogOrder: Int = Int.MAX_VALUE
)

/**
 * 资源注册时声明的 Agent 启动能力。
 *
 * 这里只描述协议、传输、结构化 argv 和启动前依赖，不保存运行中的连接或会话事实。运行时能力仍以
 * initialize 协商结果为准，不能从清单预判模型、权限或会话操作。
 */
data class KiteResourceAgentProfile(
    val agentId: String,
    val displayName: String,
    val description: String,
    val launchMode: String,
    val providerId: String,
    val protocol: String,
    val transport: String,
    val argv: List<String>,
    val runtimeGuarantees: Set<String> = emptySet(),
    val environmentFiles: Map<String, String> = emptyMap(),
    val runtimeDependencies: List<KiteResourceAgentRuntimeDependency> = emptyList(),
    val connectionReference: String = "",
    val configurationRequired: Boolean = false,
    val configAdapterId: String = "",
    val sessionAdapterId: String = "",
    val title: String = ""
)

/**
 * Managed Agent 在启动协议进程前需要的共享后台运行项。
 *
 * 资源只声明结构化命令、私有环境文件和轻量就绪探测；进程、健康和恢复事实仍统一归
 * BackgroundRuntimeRegistry / BackgroundRuntimeHost 所有。
 */
data class KiteResourceAgentRuntimeDependency(
    val id: String,
    val title: String,
    val argv: List<String>,
    val workdir: String = "/workspace",
    val environment: Map<String, String> = emptyMap(),
    val runtimeGuarantees: Set<String> = emptySet(),
    val environmentFiles: Map<String, String> = emptyMap(),
    val bindAddress: String = "",
    val bindPort: Int? = null,
    val healthHttpPath: String = "",
    val startupTimeoutMs: Long = 30_000L,
    val healthCheckStartupDelayMs: Long = 0L,
    val restartPolicy: String = "on_failure",
    val retentionClass: String = "resident",
)

data class KiteResourceShellAction(
    val type: String,
    val cmd: String,
    val surfaceMode: String,
    val workdir: String,
    val timeoutMs: Long,
    val managedCommands: List<String>,
    val cleanInstallRoot: Boolean,
    val npmUninstallPackages: List<String>,
    val installSteps: List<KiteResourceInstallStep> = emptyList(),
    val verifications: List<KiteResourceInstallVerification> = emptyList()
)

data class KiteResourceInstallStep(
    val id: String,
    val type: String,
    val cmd: String = "",
    val urls: List<String> = emptyList(),
    val destination: String = "",
    val sha256: String = "",
    val interpreter: String = "",
    val path: String = "",
    val arguments: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val packages: List<String> = emptyList(),
    val updateIndex: Boolean = false,
    val repository: String = "",
    val ref: String = "",
    val depth: Int = 1,
    val retryAttempts: Int = 4,
    val retryDelaySeconds: Int = 2
)

data class KiteResourceInstallVerification(
    val id: String,
    val cmd: String
)

data class KiteResourceHomeCard(
    val label: String,
    val policy: String,
    val recipe: JSONObject
)

data class KiteResourceRecommendationSpec(
    val resourceId: String,
    val label: String
)

data class KiteResourceBadgeSpec(
    val label: String,
    val iconText: String,
    val accent: String
)

data class KiteResourceMediaSpec(
    val type: String,
    val asset: String,
    val contentDescription: String
)

data class KiteResourcePreviewSpec(
    val title: String,
    val subtitle: String,
    val symbol: String,
    val accent: String,
    val iconAsset: String,
    val iconFit: String
)

data class KiteResourceDisplayRowSpec(
    val label: String,
    val value: String
)

data class KiteResourceHomeLayout(
    val sections: List<KiteResourceHomeSection>,
    val hero: KiteResourceHomeHero?,
    val tabs: List<KiteResourceHomeTab>,
    val chips: List<String>,
    val rawJson: JSONObject
)

data class KiteResourceHomeSection(
    val id: String,
    val title: String,
    val style: String,
    val items: List<String>
)

data class KiteResourceHomeTab(
    val id: String,
    val label: String,
    val sections: List<KiteResourceHomeSection>
)

data class KiteResourceHomeHero(
    val resourceId: String,
    val imageAsset: String,
    val contentDescription: String
)

data class KiteResourceRelationTargets(
    val base: List<KiteResourceRequirementTarget>,
    val defaults: List<KiteResourceRequirementTarget>,
    val extensions: List<KiteResourceRequirementTarget>
)

data class KiteResourceRequirementTarget(
    val requirement: String,
    val providerIds: List<String>
)

data class KiteResourceManifestRequest(
    val resourceId: String
)

data class KiteResourceProviderRequest(
    val requirement: String
)

data class KiteResourceInstallPlanRequest(
    val resourceId: String,
    val registeredResourceIds: Set<String> = emptySet(),
    val registeredCapabilities: Set<String> = emptySet()
)

data class KiteResourceInstallPlanPayload(
    val targetResourceId: String,
    val revision: String,
    val resourceIds: List<String>,
    val missing: List<KiteResourcePlanMissingRequirement>,
    val rawJson: JSONObject
)

data class KiteResourcePlanMissingRequirement(
    val requirement: String,
    val requestedByResourceId: String
)

class KiteResourceManifestLoader private constructor(
    private val isDebugBuild: Boolean,
    definitionSource: KiteResourceDefinitionSource
) {
    constructor(
        context: Context,
        isDebugBuild: Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    ) : this(
        isDebugBuild = isDebugBuild,
        definitionSource = KiteResourceAssetDefinitionSource(context)
    )

    internal constructor(
        isDebugBuild: Boolean,
        definitionSources: List<KiteResourceDefinitionSource>
    ) : this(
        isDebugBuild = isDebugBuild,
        definitionSource = KiteResourceCompositeDefinitionSource(definitionSources)
    )

    private val supply: KiteResourceManifestSupply = DefinitionResourceManifestSupply(definitionSource)

    fun requestManifest(resourceId: String): KiteResourceManifest? =
        supply.requestManifest(KiteResourceManifestRequest(resourceId))

    fun requestOpenRecipeTemplate(resourceId: String): JSONObject? =
        requestManifest(resourceId)?.openRecipe?.deepCopy()

    fun requestInstallActions(resourceId: String): List<KiteResourceShellAction> =
        requestManifest(resourceId)?.installActions.orEmpty()

    fun requestUninstallActions(resourceId: String): List<KiteResourceShellAction> =
        requestManifest(resourceId)?.uninstallActions.orEmpty()

    fun requestHomeLayout(): KiteResourceHomeLayout? =
        supply.requestHomeLayout()

    fun requestFirstHomeCardRecipeTemplate(resourceId: String): JSONObject? =
        requestManifest(resourceId)?.homeCards?.firstOrNull()?.recipe?.deepCopy()

    fun requestHasHomeCardTemplate(resourceId: String): Boolean =
        requestManifest(resourceId)?.homeCards?.isNotEmpty() == true

    fun requestExecutionManifestJson(resourceId: String): JSONObject? =
        requestManifest(resourceId)?.rawJson?.deepCopy()

    internal fun parseManifestJson(rawJson: String): KiteResourceManifest =
        parseManifest(JSONObject(rawJson))

    fun requestRelationTargets(resourceId: String): KiteResourceRelationTargets {
        val manifest = requestManifest(resourceId)
            ?: return KiteResourceRelationTargets(base = emptyList(), defaults = emptyList(), extensions = emptyList())
        return KiteResourceRelationTargets(
            base = manifest.baseRequirements.resolveRequirements(),
            defaults = manifest.defaultRequirements.resolveRequirements(),
            extensions = manifest.extensions.map { extension ->
                KiteResourceRequirementTarget(
                    requirement = extension,
                    providerIds = listOf(extension).filter { requestManifest(it) != null }
                )
            }
        )
    }

    fun requestProviderIdsFor(requirement: String): List<String> =
        supply.requestProviderIds(KiteResourceProviderRequest(requirement))

    fun requestInstallPlan(
        resourceId: String,
        registeredResourceIds: Set<String> = emptySet(),
        registeredCapabilities: Set<String> = emptySet()
    ): KiteResourceInstallPlanPayload? =
        supply.requestInstallPlan(
            KiteResourceInstallPlanRequest(
                resourceId = resourceId,
                registeredResourceIds = registeredResourceIds,
                registeredCapabilities = registeredCapabilities
            )
        )

    fun manifest(resourceId: String): KiteResourceManifest? =
        requestManifest(resourceId)

    fun openRecipeTemplate(resourceId: String): JSONObject? =
        requestOpenRecipeTemplate(resourceId)

    fun firstHomeCardRecipeTemplate(resourceId: String): JSONObject? =
        requestFirstHomeCardRecipeTemplate(resourceId)

    fun hasHomeCardTemplate(resourceId: String): Boolean =
        requestHasHomeCardTemplate(resourceId)

    fun relationTargets(resourceId: String): KiteResourceRelationTargets =
        requestRelationTargets(resourceId)

    fun providerIdsFor(requirement: String): List<String> =
        requestProviderIdsFor(requirement)

    fun installPlan(
        resourceId: String,
        registeredResourceIds: Set<String> = emptySet(),
        registeredCapabilities: Set<String> = emptySet()
    ): KiteResourceInstallPlanPayload? =
        requestInstallPlan(resourceId, registeredResourceIds, registeredCapabilities)

    fun manifests(): Map<String, KiteResourceManifest> =
        supply.requestAllManifests()

    fun invalidate() {
        supply.invalidate()
    }

    private interface KiteResourceManifestSupply {
        fun requestManifest(request: KiteResourceManifestRequest): KiteResourceManifest?
        fun requestProviderIds(request: KiteResourceProviderRequest): List<String>
        fun requestInstallPlan(request: KiteResourceInstallPlanRequest): KiteResourceInstallPlanPayload?
        fun requestAllManifests(): Map<String, KiteResourceManifest>
        fun requestHomeLayout(): KiteResourceHomeLayout?
        fun invalidate()
    }

    private inner class DefinitionResourceManifestSupply(
        private val definitionSource: KiteResourceDefinitionSource
    ) : KiteResourceManifestSupply {
        private val lock = Any()
        private val requestedManifests = linkedMapOf<String, KiteResourceManifest?>()
        private val requestedProviders = linkedMapOf<String, List<String>>()
        private val requestedPlans = linkedMapOf<String, KiteResourceInstallPlanPayload?>()
        private var definitionSnapshotCache: KiteResourceDefinitionSnapshot? = null
        private var allManifestCache: Map<String, KiteResourceManifest>? = null
        private var homeLayoutCache: KiteResourceHomeLayout? = null
        private var homeLayoutLoaded = false

        override fun requestManifest(request: KiteResourceManifestRequest): KiteResourceManifest? {
            val resourceId = request.resourceId.trim()
            if (resourceId.isBlank()) return null
            return synchronized(lock) {
                if (requestedManifests.containsKey(resourceId)) return@synchronized requestedManifests[resourceId]
                val manifest = requestAllManifests()[resourceId]
                requestedManifests[resourceId] = manifest
                manifest
            }
        }

        override fun requestProviderIds(request: KiteResourceProviderRequest): List<String> {
            val requirement = request.requirement.trim()
            if (requirement.isBlank()) return emptyList()
            val requestKey = KiteResourceRequestPolicy.providerLookupKey(requirement)
            return synchronized(lock) {
                requestedProviders[requestKey]?.let { return@synchronized it }
                val direct = requestManifest(KiteResourceManifestRequest(requirement))
                    ?.takeIf { it.isVisibleResourceCard() }
                    ?.let { listOf(it.id) }
                    .orEmpty()
                val capable = requestAllManifests().values
                    .filter { manifest ->
                        manifest.isVisibleResourceCard() &&
                            manifest.provides.any { provided -> capabilitySatisfies(requirement, provided) }
                    }
                    .map { it.id }
                (direct + capable).distinct().also { requestedProviders[requestKey] = it }
            }
        }

        override fun requestInstallPlan(request: KiteResourceInstallPlanRequest): KiteResourceInstallPlanPayload? {
            val resourceId = request.resourceId.trim()
            if (resourceId.isBlank()) return null
            val requestKey = buildString {
                append(KiteResourceRequestPolicy.installPlanKey(resourceId))
                append(":r=")
                append(request.registeredResourceIds.sorted().joinToString(","))
                append(":c=")
                append(request.registeredCapabilities.sorted().joinToString(","))
            }
            return synchronized(lock) {
                requestedPlans[requestKey]?.let { return@synchronized it }
                val root = requestManifest(KiteResourceManifestRequest(resourceId))
                    ?: return@synchronized null.also { requestedPlans[requestKey] = null }
                val ordered = linkedSetOf<String>()
                val missing = mutableListOf<KiteResourcePlanMissingRequirement>()
                val visiting = mutableSetOf<String>()
                val visited = mutableSetOf<String>()

                fun isSatisfied(requirement: String, providerIds: List<String>): Boolean {
                    if (providerIds.any { it in request.registeredResourceIds }) return true
                    return request.registeredCapabilities.any { capability ->
                        capabilitySatisfies(requirement, capability)
                    }
                }

                fun visit(manifest: KiteResourceManifest) {
                    if (manifest.id in visited) return
                    if (!visiting.add(manifest.id)) return
                    val dependencies = (manifest.baseRequirements + manifest.defaultRequirements).distinct()
                    dependencies.forEach { requirement ->
                        val providerIds = requestProviderIds(KiteResourceProviderRequest(requirement))
                        if (isSatisfied(requirement, providerIds)) return@forEach
                        val provider = providerIds
                            .asSequence()
                            .mapNotNull { providerId -> requestManifest(KiteResourceManifestRequest(providerId)) }
                            .firstOrNull()
                        if (provider == null) {
                            missing.add(
                                KiteResourcePlanMissingRequirement(
                                    requirement = requirement,
                                    requestedByResourceId = manifest.id
                                )
                            )
                        } else {
                            visit(provider)
                        }
                    }
                    visiting.remove(manifest.id)
                    visited.add(manifest.id)
                    if (manifest.id !in request.registeredResourceIds) {
                        ordered.add(manifest.id)
                    }
                }

                visit(root)
                val revisionSource = buildString {
                    append(resourceId)
                    append('|')
                    append(ordered.joinToString(","))
                    append('|')
                    append(missing.joinToString(",") { "${it.requestedByResourceId}:${it.requirement}" })
                }
                val revision = "plan-${revisionSource.hashCode().toUInt().toString(16)}"
                val rawJson = JSONObject()
                    .put("surface", KiteResourceRequestPolicy.SURFACE_INSTALL_PLAN)
                    .put("targetResourceId", resourceId)
                    .put("revision", revision)
                    .put("resources", JSONArray().apply { ordered.forEach { put(it) } })
                    .put(
                        "missing",
                        JSONArray().apply {
                            missing.forEach { item ->
                                put(
                                    JSONObject()
                                        .put("requirement", item.requirement)
                                        .put("requestedByResourceId", item.requestedByResourceId)
                                )
                            }
                        }
                    )
                KiteResourceInstallPlanPayload(
                    targetResourceId = resourceId,
                    revision = revision,
                    resourceIds = ordered.toList(),
                    missing = missing.distinct(),
                    rawJson = rawJson
                ).also { requestedPlans[requestKey] = it }
            }
        }

        override fun requestAllManifests(): Map<String, KiteResourceManifest> {
            return synchronized(lock) {
                allManifestCache?.let { return@synchronized it }
                val loaded = linkedMapOf<String, KiteResourceManifest>()
                definitionSnapshot().manifests.forEach { (declaredId, manifestJson) ->
                    parseManifestDocument(declaredId, manifestJson)?.let { manifest ->
                        if (manifest.id == declaredId && manifest.id.isNotBlank() && isAvailable(manifest)) {
                            loaded.putIfAbsent(manifest.id, manifest)
                            requestedManifests[manifest.id] = manifest
                        } else if (manifest.id != declaredId) {
                            Log.w(TAG, "Resource definition id mismatch: key=$declaredId manifest=${manifest.id}")
                        }
                    }
                }
                allManifestCache = loaded
                loaded
            }
        }

        override fun requestHomeLayout(): KiteResourceHomeLayout? {
            return synchronized(lock) {
                if (homeLayoutLoaded) return@synchronized homeLayoutCache
                val layout = definitionSnapshot().homeLayoutJson?.let(::parseHomeLayoutDocument)
                homeLayoutCache = layout?.let { parsed ->
                    KiteResourceCatalogProjector.project(parsed, requestAllManifests().values)
                }
                homeLayoutLoaded = true
                homeLayoutCache
            }
        }

        override fun invalidate() {
            synchronized(lock) {
                requestedManifests.clear()
                requestedProviders.clear()
                requestedPlans.clear()
                definitionSnapshotCache = null
                allManifestCache = null
                homeLayoutCache = null
                homeLayoutLoaded = false
                definitionSource.invalidate()
            }
        }

        private fun definitionSnapshot(): KiteResourceDefinitionSnapshot =
            definitionSnapshotCache ?: definitionSource.snapshot().also { definitionSnapshotCache = it }
    }

    private fun parseManifestDocument(resourceId: String, rawJson: String): KiteResourceManifest? =
        runCatching {
            parseManifest(JSONObject(rawJson))
        }.onFailure { error ->
            Log.w(TAG, "Failed to parse resource manifest: $resourceId", error)
        }.getOrNull()

    private fun parseHomeLayoutDocument(rawJson: String): KiteResourceHomeLayout? =
        runCatching {
            parseHomeLayout(JSONObject(rawJson))
        }.onFailure { error ->
            Log.w(TAG, "Failed to parse resource home layout", error)
        }.getOrNull()

    private fun parseHomeLayout(json: JSONObject): KiteResourceHomeLayout {
        val sections = parseHomeSections(json.optJSONArray("sections") ?: JSONArray())
        val heroJson = json.optJSONObject("hero")
        val hero = heroJson?.let {
            val resourceId = it.optString("resourceId").trim()
                .ifBlank { it.optString("id").trim() }
            val imageAsset = it.optString("imageAsset").trim()
                .ifBlank { it.optString("asset").trim() }
            if (resourceId.isBlank() || imageAsset.isBlank()) {
                null
            } else {
                KiteResourceHomeHero(
                    resourceId = resourceId,
                    imageAsset = imageAsset,
                    contentDescription = it.optString("contentDescription").trim()
                )
            }
        }
        return KiteResourceHomeLayout(
            sections = sections,
            hero = hero,
            tabs = parseHomeTabs(json.optJSONArray("tabs")),
            chips = json.optJSONArray("chips").toStringList(),
            rawJson = json.deepCopy()
        )
    }

    private fun parseHomeSections(sectionsJson: JSONArray): List<KiteResourceHomeSection> =
        buildList {
            for (index in 0 until sectionsJson.length()) {
                val section = sectionsJson.optJSONObject(index) ?: continue
                val id = section.optString("id").trim()
                val title = section.optString("title").trim()
                val style = section.optString("style").trim()
                val items = section.optJSONArray("items").toStringList()
                if (id.isNotBlank() && title.isNotBlank()) {
                    add(KiteResourceHomeSection(id = id, title = title, style = style, items = items))
                }
            }
        }

    private fun parseHomeTabs(tabsJson: JSONArray?): List<KiteResourceHomeTab> {
        if (tabsJson == null) return emptyList()
        return buildList {
            for (index in 0 until tabsJson.length()) {
                val tab = tabsJson.optJSONObject(index) ?: continue
                val label = tab.optString("label").trim()
                if (label.isBlank()) continue
                add(
                    KiteResourceHomeTab(
                        id = tab.optString("id").trim().ifBlank { label },
                        label = label,
                        sections = parseHomeSections(tab.optJSONArray("sections") ?: JSONArray())
                    )
                )
            }
        }
    }

    private fun parseManifest(json: JSONObject): KiteResourceManifest {
        val base = json.optJSONObject("base") ?: JSONObject()
        val icon = base.optJSONObject("icon")
        val display = json.optJSONObject("display") ?: JSONObject()
        val relations = json.optJSONObject("relations") ?: JSONObject()
        val source = json.optJSONObject("source") ?: JSONObject()
        val management = json.optJSONObject("management") ?: JSONObject()
        val actions = json.optJSONObject("actions") ?: JSONObject()
        val agentProfiles = parseAgentProfiles(
            agents = json.optJSONArray("agents"),
            legacyAgent = json.optJSONObject("agent")
        )
        val open = actions.optJSONObject("open")
        val openRecipe = open
            ?.takeIf { it.optString("runtime") == "kite_recipe" }
            ?.optJSONObject("recipe")
            ?.deepCopy()

        val installActions = parseInstallActions(actions.optJSONArray("install"))
        val updateActions = parseInstallActions(actions.optJSONArray("update"))
        val uninstallActions = parseShellActions(actions.optJSONArray("uninstall"))
        val sourceSpec = parseSource(source)
        val managementSpec = parseManagement(
            management,
            fallbackManagedCommands = (installActions + updateActions + uninstallActions)
                .flatMap(KiteResourceShellAction::managedCommands)
                .distinct()
        )

        return KiteResourceManifest(
            id = json.optString("id"),
            availability = KiteResourceAvailability.parse(json.optString("availability")),
            name = base.optString("name"),
            description = base.optString("description"),
            version = base.optString("version"),
            iconText = when (icon?.optString("type")) {
                "text" -> icon.optString("value")
                "asset" -> icon.optString("fallbackText")
                else -> ""
            },
            iconAsset = when (icon?.optString("type")) {
                "asset" -> icon.optString("value")
                    .ifBlank { icon.optString("asset") }
                    .trim()
                else -> ""
            },
            iconFit = icon?.optString("fit").orEmpty().trim(),
            displayCategory = display.optString("category").trim(),
            displayAccent = display.optString("accent").trim(),
            displaySizeLabel = display.optString("sizeLabel").trim(),
            displayLongDescription = display.optString("longDescription").trim(),
            displayBadge = parseBadge(display.optJSONObject("badge")),
            displayMedia = parseMedia(display.optJSONObject("media")),
            displayPreviewCards = parsePreviewCards(display.optJSONArray("previewCards")),
            displayRequirementRows = parseDisplayRows(display.optJSONArray("requirementRows")),
            displayRecommendations = parseRecommendations(display.optJSONArray("recommendations")),
            sections = display.optJSONArray("sections").toStringList(),
            tags = display.optJSONArray("tags").toStringList(),
            provides = relations.optJSONArray("provides").toStringList(),
            baseRequirements = relations.optJSONArray("base").toStringList(),
            defaultRequirements = relations.optJSONArray("defaults").toStringList(),
            extensions = relations.optJSONArray("extensions").toStringList(),
            management = managementSpec,
            source = sourceSpec,
            sourceType = sourceSpec.type,
            installActions = installActions,
            updateActions = updateActions,
            uninstallActions = uninstallActions,
            agentProfiles = agentProfiles,
            openRecipe = openRecipe,
            homeCards = parseHomeCards(json.optJSONArray("homeCards")),
            rawJson = json.deepCopy(),
            installRoot = json.optJSONObject("paths")?.optString("installRoot").orEmpty(),
            catalogTabs = display.optJSONArray("tabs").toStringList(),
            catalogOrder = display.optInt("order", Int.MAX_VALUE).coerceAtLeast(0)
        )
    }

    private fun parseAgentProfiles(
        agents: JSONArray?,
        legacyAgent: JSONObject?
    ): List<KiteResourceAgentProfile> {
        if (agents != null) {
            return buildList {
                for (index in 0 until agents.length()) {
                    parseAgentProfile(agents.optJSONObject(index), legacy = false)?.let(::add)
                }
            }
        }
        return listOfNotNull(parseAgentProfile(legacyAgent, legacy = true))
    }

    private fun parseAgentProfile(json: JSONObject?, legacy: Boolean): KiteResourceAgentProfile? {
        if (json == null) return null
        val launch = if (legacy) json else json.optJSONObject("launch") ?: return null
        val providerId = launch.optString("providerId").trim()
        val protocol = launch.optString("protocol").trim().lowercase()
        val transport = launch.optString("transport").trim().lowercase()
        val launchMode = launch.optString("mode").trim().lowercase().ifBlank { "managed" }
        val argv = launch.optJSONArray("argv").toStringList()
        val runtimeGuarantees = RuntimeExecutionGuaranteeCodec.normalize(
            launch.optJSONArray("runtimeGuarantees").toStringList()
        ) ?: return null
        val runtimeDependencies = parseAgentRuntimeDependencies(
            launch.optJSONArray("runtimeDependencies")
        ) ?: return null
        val connectionReference = launch.optString("connectionReference").trim()
        val agentId = if (legacy) providerId else json.optString("id").trim()
        val validLaunch = when (launchMode) {
            "managed" -> argv.isNotEmpty()
            "attach" -> connectionReference.isNotBlank()
            else -> false
        }
        if (
            agentId.isBlank() || providerId.isBlank() || protocol.isBlank() ||
            transport.isBlank() || !validLaunch
        ) return null
        val title = if (legacy) json.optString("title").trim() else launch.optString("title").trim()
        val configuration = if (legacy) null else json.optJSONObject("configuration")
        val sessions = if (legacy) null else json.optJSONObject("sessions")
        return KiteResourceAgentProfile(
            agentId = agentId,
            displayName = if (legacy) title.ifBlank { agentId } else json.optString("name").trim()
                .ifBlank { title.ifBlank { agentId } },
            description = if (legacy) "" else json.optString("description").trim(),
            launchMode = launchMode,
            providerId = providerId,
            protocol = protocol,
            transport = transport,
            argv = argv,
            runtimeGuarantees = runtimeGuarantees,
            environmentFiles = launch.optJSONObject("environmentFiles").toStringMap(),
            runtimeDependencies = runtimeDependencies,
            connectionReference = connectionReference,
            configurationRequired = configuration?.optBoolean("required", false) == true,
            configAdapterId = configuration?.optString("adapter")?.trim().orEmpty(),
            sessionAdapterId = sessions?.optString("adapter")?.trim().orEmpty(),
            title = title
        )
    }

    private fun parseAgentRuntimeDependencies(
        dependencies: JSONArray?
    ): List<KiteResourceAgentRuntimeDependency>? {
        if (dependencies == null) return emptyList()
        val parsed = mutableListOf<KiteResourceAgentRuntimeDependency>()
        for (index in 0 until dependencies.length()) {
            val dependency = dependencies.optJSONObject(index) ?: continue
            val id = dependency.optString("id").trim()
            val argv = dependency.optJSONArray("argv").toStringList()
            if (!STABLE_RUNTIME_DEPENDENCY_ID.matches(id) || argv.isEmpty()) continue
            val runtimeGuarantees = RuntimeExecutionGuaranteeCodec.normalize(
                dependency.optJSONArray("runtimeGuarantees").toStringList()
            ) ?: return null
            val port = dependency.optInt("bindPort", 0).takeIf { it in 1..65_535 }
            parsed +=
                KiteResourceAgentRuntimeDependency(
                    id = id,
                    title = dependency.optString("title").trim().ifBlank { id },
                    argv = argv,
                    workdir = dependency.optString("workdir").trim().ifBlank { "/workspace" },
                    environment = dependency.optJSONObject("environment").toStringMap(),
                    runtimeGuarantees = runtimeGuarantees,
                    environmentFiles = dependency.optJSONObject("environmentFiles").toStringMap(),
                    bindAddress = dependency.optString("bindAddress").trim(),
                    bindPort = port,
                    healthHttpPath = dependency.optString("healthHttpPath").trim(),
                    startupTimeoutMs = dependency.optLong("startupTimeoutMs", 30_000L)
                        .coerceIn(1_000L, 120_000L),
                    healthCheckStartupDelayMs = dependency
                        .optLong("healthCheckStartupDelayMs", 0L)
                        .coerceIn(0L, 120_000L),
                    restartPolicy = dependency.optString("restartPolicy").trim()
                        .ifBlank { "on_failure" },
                    retentionClass = dependency.optString("retentionClass").trim()
                        .ifBlank { "resident" },
                )
        }
        return parsed
    }

    private fun parseManagement(
        managementJson: JSONObject,
        fallbackManagedCommands: List<String>
    ): KiteResourceManagementSpec {
        val declaredCommands = managementJson.optJSONArray("managedCommands").toStringList()
        return KiteResourceManagementSpec(
            mode = KiteResourceManagementMode.parse(managementJson.optString("mode")),
            managedCommands = declaredCommands.ifEmpty { fallbackManagedCommands },
            versionProbe = parseVersionProbe(managementJson.optJSONObject("versionProbe")),
            latestVersionProbe = parseVersionProbe(managementJson.optJSONObject("latestVersionProbe")),
            preservePaths = managementJson.optJSONArray("preservePaths").toStringList()
        )
    }

    private fun parseVersionProbe(probeJson: JSONObject?): KiteResourceVersionProbeSpec? {
        val command = probeJson?.optString("command").orEmpty().trim()
        return command.takeIf(String::isNotBlank)?.let {
            KiteResourceVersionProbeSpec(
                command = it,
                pattern = probeJson?.optString("pattern").orEmpty(),
                group = probeJson?.optInt("group", 1)?.coerceAtLeast(0) ?: 1
            )
        }
    }

    private fun parseSource(sourceJson: JSONObject): KiteResourceSourceSpec {
        val type = sourceJson.optString("type").trim()
        return KiteResourceSourceSpec(
            type = type,
            packageName = sourceJson.optString("package").trim(),
            companionPackages = sourceJson.optJSONArray("companionPackages").toStringList(),
            repository = sourceJson.optString("repository").trim(),
            url = sourceJson.optString("url").trim(),
            asset = sourceJson.optString("asset").trim(),
            assetPattern = sourceJson.optString("assetPattern").trim(),
            channel = sourceJson.optString("channel").trim().ifBlank { "stable" },
            tag = sourceJson.optString("tag").trim().ifBlank {
                if (type == "npm") "latest" else ""
            },
            releaseTagTemplate = sourceJson.optString("releaseTagTemplate").trim(),
            archiveType = sourceJson.optString("archiveType").trim(),
            binaryPath = sourceJson.optString("binaryPath").trim(),
            architectures = sourceJson.optJSONObject("architectures").toStringMap(),
            latestUrl = sourceJson.optString("latestUrl").trim(),
            latestFormat = sourceJson.optString("latestFormat").trim().ifBlank { "json" },
            latestJsonField = sourceJson.optString("latestJsonField").trim(),
            latestStripPrefix = sourceJson.optString("latestStripPrefix").trim(),
            installArguments = sourceJson.optJSONArray("installArguments").toStringList(),
            versionArguments = sourceJson.optJSONArray("versionArguments").toStringList(),
            environment = sourceJson.optJSONObject("environment").toStringMap(),
            profile = sourceJson.optString("profile").trim(),
            interpreter = sourceJson.optString("interpreter").trim(),
            entry = sourceJson.optString("entry").trim()
        )
    }

    private fun parseBadge(badgeJson: JSONObject?): KiteResourceBadgeSpec? {
        if (badgeJson == null) return null
        val label = badgeJson.optString("label").trim()
        if (label.isBlank()) return null
        return KiteResourceBadgeSpec(
            label = label,
            iconText = badgeJson.optString("iconText").trim()
                .ifBlank { badgeJson.optString("icon").trim() },
            accent = badgeJson.optString("accent").trim()
        )
    }

    private fun parseMedia(mediaJson: JSONObject?): KiteResourceMediaSpec? {
        if (mediaJson == null) return null
        val type = mediaJson.optString("type").trim()
        val asset = mediaJson.optString("asset").trim()
        if (type.isBlank() || asset.isBlank()) return null
        return KiteResourceMediaSpec(
            type = type,
            asset = asset,
            contentDescription = mediaJson.optString("contentDescription").trim()
        )
    }

    private fun parsePreviewCards(previewJson: JSONArray?): List<KiteResourcePreviewSpec> {
        if (previewJson == null) return emptyList()
        return buildList {
            for (index in 0 until previewJson.length()) {
                val preview = previewJson.optJSONObject(index) ?: continue
                val title = preview.optString("title").trim()
                if (title.isBlank()) continue
                val icon = preview.optJSONObject("icon")
                add(
                    KiteResourcePreviewSpec(
                        title = title,
                        subtitle = preview.optString("subtitle").trim(),
                        symbol = when (icon?.optString("type")) {
                            "text" -> icon.optString("value")
                            "asset" -> icon.optString("fallbackText")
                            else -> preview.optString("symbol")
                        }.trim(),
                        accent = preview.optString("accent").trim(),
                        iconAsset = when (icon?.optString("type")) {
                            "asset" -> icon.optString("value").ifBlank { icon.optString("asset") }
                            else -> preview.optString("iconAsset")
                        }.trim(),
                        iconFit = icon?.optString("fit").orEmpty().trim()
                    )
                )
            }
        }
    }

    private fun parseDisplayRows(rowsJson: JSONArray?): List<KiteResourceDisplayRowSpec> {
        if (rowsJson == null) return emptyList()
        return buildList {
            for (index in 0 until rowsJson.length()) {
                val row = rowsJson.optJSONObject(index) ?: continue
                val label = row.optString("label").trim()
                val value = row.optString("value").trim()
                if (label.isNotBlank() && value.isNotBlank()) {
                    add(KiteResourceDisplayRowSpec(label = label, value = value))
                }
            }
        }
    }

    private fun parseRecommendations(recommendationsJson: JSONArray?): List<KiteResourceRecommendationSpec> {
        if (recommendationsJson == null) return emptyList()
        return buildList {
            for (index in 0 until recommendationsJson.length()) {
                val recommendation = recommendationsJson.optJSONObject(index) ?: continue
                val resourceId = recommendation.optString("resourceId").trim()
                    .ifBlank { recommendation.optString("id").trim() }
                if (resourceId.isBlank()) continue
                add(
                    KiteResourceRecommendationSpec(
                        resourceId = resourceId,
                        label = recommendation.optString("label").trim()
                    )
                )
            }
        }
    }

    private fun parseInstallActions(actionsJson: JSONArray?): List<KiteResourceShellAction> {
        if (actionsJson == null) return emptyList()
        return buildList {
            for (index in 0 until actionsJson.length()) {
                val action = actionsJson.optJSONObject(index) ?: continue
                val type = action.optString("type").trim()
                val cmd = action.optString("cmd")
                val installSteps = parseInstallSteps(action.optJSONArray("steps"))
                val supported = (type == "shell" && cmd.isNotBlank()) ||
                    (type == "managed" && installSteps.isNotEmpty())
                if (!supported) continue
                add(parseAction(action, type, cmd, installSteps))
            }
        }
    }

    private fun parseShellActions(actionsJson: JSONArray?): List<KiteResourceShellAction> {
        if (actionsJson == null) return emptyList()
        return buildList {
            for (index in 0 until actionsJson.length()) {
                val action = actionsJson.optJSONObject(index) ?: continue
                val type = action.optString("type")
                val cmd = action.optString("cmd")
                if (type != "shell" || cmd.isBlank()) continue
                add(parseAction(action, type, cmd))
            }
        }
    }

    private fun parseAction(
        action: JSONObject,
        type: String,
        cmd: String,
        installSteps: List<KiteResourceInstallStep> = emptyList()
    ): KiteResourceShellAction {
        val managedCommands = (
            action.optJSONArray("managedCommands").toStringList() +
                listOf(action.optString("managedCommand")).filter { it.isNotBlank() }
            ).distinct()
        val npmUninstallPackages = (
            action.optJSONArray("npmUninstallPackages").toStringList() +
                listOf(action.optString("npmUninstallPackage")).filter { it.isNotBlank() }
            ).distinct()
        return KiteResourceShellAction(
            type = type,
            cmd = cmd,
            surfaceMode = action.optString("surfaceMode", "panel"),
            workdir = action.optString("workdir", "/workspace"),
            timeoutMs = action.optLong("timeoutMs", 1_800_000L),
            managedCommands = managedCommands,
            cleanInstallRoot = action.optBoolean("cleanInstallRoot", false),
            npmUninstallPackages = npmUninstallPackages,
            installSteps = installSteps,
            verifications = parseInstallVerifications(action.optJSONArray("verify"))
        )
    }

    private fun parseInstallSteps(stepsJson: JSONArray?): List<KiteResourceInstallStep> {
        if (stepsJson == null) return emptyList()
        return buildList {
            for (index in 0 until stepsJson.length()) {
                val step = stepsJson.optJSONObject(index) ?: continue
                val type = step.optString("type").trim()
                if (type.isBlank()) continue
                val urls = (
                    listOf(step.optString("url")).filter { it.isNotBlank() } +
                        step.optJSONArray("urls").toStringList()
                    ).distinct()
                add(
                    KiteResourceInstallStep(
                        id = step.optString("id").trim().ifBlank { "${type}_${index + 1}" },
                        type = type,
                        cmd = step.optString("cmd"),
                        urls = urls,
                        destination = step.optString("destination"),
                        sha256 = step.optString("sha256").trim(),
                        interpreter = step.optString("interpreter").trim(),
                        path = step.optString("path"),
                        arguments = step.optJSONArray("args").toStringList(),
                        environment = step.optJSONObject("env").toStringMap(),
                        packages = step.optJSONArray("packages").toStringList(),
                        updateIndex = step.optBoolean("update", false),
                        repository = step.optString("repository").ifBlank { step.optString("url") },
                        ref = step.optString("ref").trim(),
                        depth = step.optInt("depth", 1).coerceIn(0, 1000),
                        retryAttempts = step.optInt("retryAttempts", 4).coerceIn(1, 10),
                        retryDelaySeconds = step.optInt("retryDelaySeconds", 2).coerceIn(0, 60)
                    )
                )
            }
        }
    }

    private fun parseInstallVerifications(verifyJson: JSONArray?): List<KiteResourceInstallVerification> {
        if (verifyJson == null) return emptyList()
        return buildList {
            for (index in 0 until verifyJson.length()) {
                val verification = verifyJson.optJSONObject(index) ?: continue
                val cmd = verification.optString("cmd")
                if (cmd.isBlank()) continue
                add(
                    KiteResourceInstallVerification(
                        id = verification.optString("id").trim().ifBlank { "verify_${index + 1}" },
                        cmd = cmd
                    )
                )
            }
        }
    }

    private fun parseHomeCards(cardsJson: JSONArray?): List<KiteResourceHomeCard> {
        if (cardsJson == null) return emptyList()
        return buildList {
            for (index in 0 until cardsJson.length()) {
                val card = cardsJson.optJSONObject(index) ?: continue
                val recipe = card.optJSONObject("recipe") ?: continue
                add(
                    KiteResourceHomeCard(
                        label = card.optString("label"),
                        policy = card.optString("policy", "manual"),
                        recipe = recipe.deepCopy()
                    )
                )
            }
        }
    }

    private fun KiteResourceManifest.isVisibleResourceCard(): Boolean =
        sections.isNotEmpty()

    private fun isAvailable(manifest: KiteResourceManifest): Boolean =
        manifest.availability != KiteResourceAvailability.DEBUG_ONLY || isDebugBuild

    private fun JSONObject.deepCopy(): JSONObject =
        JSONObject(toString())

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return buildMap {
            keys().forEach { key ->
                val value = optString(key)
                if (key.isNotBlank() && value.isNotBlank()) put(key, value)
            }
        }
    }

    private fun List<String>.resolveRequirements(): List<KiteResourceRequirementTarget> =
        map { requirement ->
            KiteResourceRequirementTarget(
                requirement = requirement,
                providerIds = requestProviderIdsFor(requirement)
            )
        }

    private fun capabilitySatisfies(requirement: String, provided: String): Boolean {
        val need = requirement.trim()
        val have = provided.trim()
        if (need.equals(have, ignoreCase = true)) return true
        val needVersion = capabilityMinVersion(need) ?: return false
        val haveVersion = capabilityMinVersion(have) ?: return false
        return needVersion.first.equals(haveVersion.first, ignoreCase = true) &&
            compareCapabilityVersions(haveVersion.second, needVersion.second) >= 0
    }

    private fun capabilityMinVersion(value: String): Pair<String, List<Int>>? {
        val match = Regex("^([A-Za-z0-9_.-]+)>=(\\d+(?:\\.\\d+)*)(?:<\\d+(?:\\.\\d+)*)?$")
            .matchEntire(value.trim())
            ?: return null
        val name = match.groupValues[1]
        val version = match.groupValues[2]
            .split('.')
            .mapNotNull { it.toIntOrNull() }
        if (version.isEmpty()) return null
        return name to version
    }

    private fun compareCapabilityVersions(left: List<Int>, right: List<Int>): Int {
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    companion object {
        private const val TAG = "KiteResourceManifest"
        private val STABLE_RUNTIME_DEPENDENCY_ID = Regex("[a-z0-9](?:[a-z0-9._-]*[a-z0-9])?")
    }
}
