package com.kite.app.resources

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class KiteResourceManifest(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val iconText: String,
    val iconAsset: String,
    val sections: List<String>,
    val tags: List<String>,
    val provides: List<String>,
    val baseRequirements: List<String>,
    val defaultRequirements: List<String>,
    val extensions: List<String>,
    val sourceType: String,
    val installActions: List<KiteResourceShellAction>,
    val uninstallActions: List<KiteResourceShellAction>,
    val openRecipe: JSONObject?,
    val homeCards: List<KiteResourceHomeCard>,
    val rawJson: JSONObject
)

data class KiteResourceShellAction(
    val type: String,
    val cmd: String,
    val surfaceMode: String,
    val workdir: String,
    val timeoutMs: Long,
    val managedCommands: List<String>,
    val cleanInstallRoot: Boolean,
    val npmUninstallPackages: List<String>
)

data class KiteResourceHomeCard(
    val label: String,
    val policy: String,
    val recipe: JSONObject
)

data class KiteResourceHomeLayout(
    val sections: List<KiteResourceHomeSection>,
    val rawJson: JSONObject
)

data class KiteResourceHomeSection(
    val id: String,
    val title: String,
    val items: List<String>
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

class KiteResourceManifestLoader(private val context: Context) {
    private val supply: KiteResourceManifestSupply = AssetResourceManifestSupply(context)

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

    private inner class AssetResourceManifestSupply(private val context: Context) : KiteResourceManifestSupply {
        private val lock = Any()
        private val requestedManifests = linkedMapOf<String, KiteResourceManifest?>()
        private val requestedProviders = linkedMapOf<String, List<String>>()
        private val requestedPlans = linkedMapOf<String, KiteResourceInstallPlanPayload?>()
        private var knownAssetEntries: List<String>? = null
        private var allManifestCache: Map<String, KiteResourceManifest>? = null
        private var homeLayoutCache: KiteResourceHomeLayout? = null
        private var homeLayoutLoaded = false

        override fun requestManifest(request: KiteResourceManifestRequest): KiteResourceManifest? {
            val resourceId = request.resourceId.trim()
            if (resourceId.isBlank()) return null
            return synchronized(lock) {
                if (requestedManifests.containsKey(resourceId)) return@synchronized requestedManifests[resourceId]
                val direct = readManifest("$ASSET_ROOT/$resourceId/manifest.json")
                    ?.takeIf { it.id == resourceId }
                val manifest = direct ?: requestAllManifests()[resourceId]
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
                assetEntries().forEach { entry ->
                    readManifest("$ASSET_ROOT/$entry/manifest.json")?.let { manifest ->
                        if (manifest.id.isNotBlank()) {
                            loaded[manifest.id] = manifest
                            requestedManifests[manifest.id] = manifest
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
                homeLayoutCache = readHomeLayout("$ASSET_ROOT/home.json")
                homeLayoutLoaded = true
                homeLayoutCache
            }
        }

        override fun invalidate() {
            synchronized(lock) {
                requestedManifests.clear()
                requestedProviders.clear()
                requestedPlans.clear()
                knownAssetEntries = null
                allManifestCache = null
                homeLayoutCache = null
                homeLayoutLoaded = false
            }
        }

        private fun assetEntries(): List<String> {
            knownAssetEntries?.let { return it }
            val entries = context.assets.list(ASSET_ROOT).orEmpty()
                .filterNot { it.endsWith(".json", ignoreCase = true) }
                .sorted()
            knownAssetEntries = entries
            return entries
        }
    }

    private fun readManifest(path: String): KiteResourceManifest? =
        runCatching {
            context.assets.open(path).bufferedReader().use { reader ->
                parseManifest(JSONObject(reader.readText()))
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to read resource manifest: $path", error)
        }.getOrNull()

    private fun readHomeLayout(path: String): KiteResourceHomeLayout? =
        runCatching {
            context.assets.open(path).bufferedReader().use { reader ->
                parseHomeLayout(JSONObject(reader.readText()))
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to read resource home layout: $path", error)
        }.getOrNull()

    private fun parseHomeLayout(json: JSONObject): KiteResourceHomeLayout {
        val sectionsJson = json.optJSONArray("sections") ?: JSONArray()
        val sections = buildList {
            for (index in 0 until sectionsJson.length()) {
                val section = sectionsJson.optJSONObject(index) ?: continue
                val id = section.optString("id").trim()
                val title = section.optString("title").trim()
                val items = section.optJSONArray("items").toStringList()
                if (id.isNotBlank() && title.isNotBlank()) {
                    add(KiteResourceHomeSection(id = id, title = title, items = items))
                }
            }
        }
        return KiteResourceHomeLayout(sections = sections, rawJson = json.deepCopy())
    }

    private fun parseManifest(json: JSONObject): KiteResourceManifest {
        val base = json.optJSONObject("base") ?: JSONObject()
        val icon = base.optJSONObject("icon")
        val display = json.optJSONObject("display") ?: JSONObject()
        val relations = json.optJSONObject("relations") ?: JSONObject()
        val source = json.optJSONObject("source") ?: JSONObject()
        val actions = json.optJSONObject("actions") ?: JSONObject()
        val open = actions.optJSONObject("open")
        val openRecipe = open
            ?.takeIf { it.optString("runtime") == "kite_recipe" }
            ?.optJSONObject("recipe")
            ?.deepCopy()

        return KiteResourceManifest(
            id = json.optString("id"),
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
            sections = display.optJSONArray("sections").toStringList(),
            tags = display.optJSONArray("tags").toStringList(),
            provides = relations.optJSONArray("provides").toStringList(),
            baseRequirements = relations.optJSONArray("base").toStringList(),
            defaultRequirements = relations.optJSONArray("defaults").toStringList(),
            extensions = relations.optJSONArray("extensions").toStringList(),
            sourceType = source.optString("type"),
            installActions = parseShellActions(actions.optJSONArray("install")),
            uninstallActions = parseShellActions(actions.optJSONArray("uninstall")),
            openRecipe = openRecipe,
            homeCards = parseHomeCards(json.optJSONArray("homeCards")),
            rawJson = json.deepCopy()
        )
    }

    private fun parseShellActions(actionsJson: JSONArray?): List<KiteResourceShellAction> {
        if (actionsJson == null) return emptyList()
        return buildList {
            for (index in 0 until actionsJson.length()) {
                val action = actionsJson.optJSONObject(index) ?: continue
                val type = action.optString("type")
                val cmd = action.optString("cmd")
                if (type != "shell" || cmd.isBlank()) continue
                val managedCommands = (
                    action.optJSONArray("managedCommands").toStringList() +
                        listOf(action.optString("managedCommand")).filter { it.isNotBlank() }
                    ).distinct()
                val npmUninstallPackages = (
                    action.optJSONArray("npmUninstallPackages").toStringList() +
                        listOf(action.optString("npmUninstallPackage")).filter { it.isNotBlank() }
                    ).distinct()
                add(
                    KiteResourceShellAction(
                        type = type,
                        cmd = cmd,
                        surfaceMode = action.optString("surfaceMode", "panel"),
                        workdir = action.optString("workdir", "/workspace"),
                        timeoutMs = action.optLong("timeoutMs", 600_000L),
                        managedCommands = managedCommands,
                        cleanInstallRoot = action.optBoolean("cleanInstallRoot", false),
                        npmUninstallPackages = npmUninstallPackages
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
        private const val ASSET_ROOT = "resources"
        private const val TAG = "KiteResourceManifest"
    }
}
