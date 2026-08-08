package com.kite.app.agent.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentConfigValue
import com.kite.app.agent.contract.AgentModelSource
import com.kite.app.agent.contract.AgentMode
import com.kite.app.agent.contract.AgentPermissionLevel
import com.kite.app.agent.contract.AgentReasoningLevel
import com.kite.app.agent.contract.AgentReasoningMode
import com.kite.app.agent.contract.AgentReasoningSemantics
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Kite 统一 Provider 目录的更新归属。 */
enum class AgentProviderCatalogPolicy {
    UserManaged,
    FreeScan,
    OfficialLoginVersion,
}

data class AgentCatalogModel(
    val id: String,
    val displayName: String = id,
)

/**
 * Kite 自己保存的 Provider 事实。API Key 不在这个公开对象中，只暴露是否已经安全保存。
 */
data class AgentCatalogProvider(
    val id: String,
    val displayName: String,
    val baseUrl: String? = null,
    val models: List<AgentCatalogModel>,
    val source: AgentModelSource,
    val policy: AgentProviderCatalogPolicy,
    val ownerId: String? = null,
    val sourceVersion: String? = null,
    val credentialPresent: Boolean = false,
)

data class AgentProviderCatalogSnapshot(
    val revision: Long = 0L,
    val selectedProviderId: String? = null,
    val selectedModelId: String? = null,
    val providers: List<AgentCatalogProvider> = emptyList(),
    val controls: List<AgentConfigOption> = emptyList(),
    val workModes: List<AgentMode> = emptyList(),
    val selectedWorkModeId: String? = null,
    val completedImports: Set<String> = emptySet(),
    val pendingNativeProviderIds: Set<String> = emptySet(),
) {
    fun selectedProvider(): AgentCatalogProvider? = providers.firstOrNull { provider ->
        provider.id == selectedProviderId && provider.models.any { it.id == selectedModelId }
    }
}

/** API Key 变更只在一次 Store 调用中短暂存在。 */
sealed interface AgentCatalogCredentialChange {
    data object Keep : AgentCatalogCredentialChange
    data object Remove : AgentCatalogCredentialChange

    class Replace internal constructor(internal val secret: String) : AgentCatalogCredentialChange {
        override fun toString(): String = "Replace([REDACTED])"
    }

    companion object {
        fun replace(secret: String): AgentCatalogCredentialChange = Replace(secret)
    }
}

/** 只允许发送准备层读取；字符串化始终脱敏。 */
class AgentCatalogCredential internal constructor(internal val secret: String) {
    override fun toString(): String = "AgentCatalogCredential([REDACTED])"
}

/**
 * Kite 自有的 Provider、模型、权限、推理与工作模式能力事实源。
 *
 * 普通目录不保存 API Key；凭据由 [AgentProviderCredentialVault] 加密保存。免费目录通过
 * [syncFreeProviders] 增量替换，官方目录只通过 [saveOfficialVersion] 保存登录成功后的版本。
 */
class AgentProviderCatalogStore private constructor(
    context: Context,
    private val credentialVault: AgentProviderCredentialVault,
) {
    constructor(context: Context) : this(
        context.applicationContext,
        AndroidKeystoreAgentProviderCredentialVault(context.applicationContext),
    )

    internal constructor(context: Context, credentialVault: AgentProviderCredentialVault, testing: Unit = Unit) :
        this(context.applicationContext, credentialVault)

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val snapshotCache = mutableMapOf<String, AgentProviderCatalogSnapshot>()
    private var snapshotCachePayload: String? = null
    private var snapshotCachePayloadKnown = false

    internal fun cachedSnapshot(agentId: String): AgentProviderCatalogSnapshot? = synchronized(LOCK) {
        snapshotCache[agentId]
    }

    fun snapshot(agentId: String): AgentProviderCatalogSnapshot = synchronized(LOCK) {
        if (agentId.isBlank()) return@synchronized AgentProviderCatalogSnapshot()
        val agents = readAgents()
        snapshotCache.getOrPut(agentId) {
            agents.optJSONObject(agentId)?.toSnapshot(agentId) ?: AgentProviderCatalogSnapshot()
        }
    }

    fun saveUserProvider(
        agentId: String,
        provider: AgentCatalogProvider,
        credential: AgentCatalogCredentialChange,
    ): AgentCatalogProvider? {
        val normalized = provider.normalized(
            source = AgentModelSource.UserConfigured,
            policy = AgentProviderCatalogPolicy.UserManaged,
            ownerId = null,
            sourceVersion = null,
        ) ?: return null
        val occupied = snapshot(agentId).providers.firstOrNull { it.id == normalized.id }
        if (occupied != null && occupied.policy != AgentProviderCatalogPolicy.UserManaged) return null
        val credentialKey = credentialKey(agentId, normalized.id)
        when (credential) {
            AgentCatalogCredentialChange.Keep -> Unit
            AgentCatalogCredentialChange.Remove -> credentialVault.remove(credentialKey)
            is AgentCatalogCredentialChange.Replace -> {
                if (credential.secret.isBlank()) return null
                credentialVault.put(credentialKey, credential.secret)
            }
        }
        return update(agentId) { current ->
            val stored = normalized.copy(credentialPresent = credentialVault.contains(credentialKey))
            val nextProviders = current.providers.filterNot { it.id == stored.id } + stored
            current.copy(
                providers = nextProviders.sortedBy(AgentCatalogProvider::id),
                pendingNativeProviderIds = current.pendingNativeProviderIds + stored.id,
            ) to stored
        }
    }

    fun removeUserProvider(agentId: String, providerId: String): Boolean {
        val current = snapshot(agentId)
        val provider = current.providers.firstOrNull { it.id == providerId } ?: return false
        if (provider.policy != AgentProviderCatalogPolicy.UserManaged) return false
        credentialVault.remove(credentialKey(agentId, providerId))
        return update(agentId) { snapshot ->
            val providers = snapshot.providers.filterNot { it.id == providerId }
            snapshot.copy(
                providers = providers,
                selectedProviderId = snapshot.selectedProviderId.takeUnless { it == providerId },
                selectedModelId = snapshot.selectedModelId.takeUnless { snapshot.selectedProviderId == providerId },
                pendingNativeProviderIds = snapshot.pendingNativeProviderIds - providerId,
            ) to true
        }
    }

    /** 只替换同一扫描源拥有的免费目录；本次未出现的旧项会被删除。 */
    fun syncFreeProviders(
        agentId: String,
        scanSourceId: String,
        sourceVersion: String,
        providers: List<AgentCatalogProvider>,
    ): AgentProviderCatalogSnapshot {
        update(agentId) { current ->
            val scanned = providers.mapNotNull { candidate ->
                candidate.normalized(
                    source = AgentModelSource.Free,
                    policy = AgentProviderCatalogPolicy.FreeScan,
                    ownerId = scanSourceId,
                    sourceVersion = sourceVersion,
                )
            }.distinctBy(AgentCatalogProvider::id)
            val next = current.copy(
                providers = (
                    current.providers.filterNot {
                        it.policy == AgentProviderCatalogPolicy.FreeScan && it.ownerId == scanSourceId
                    } + scanned
                    ).sortedBy(AgentCatalogProvider::id),
            ).dropInvalidSelections()
            next to Unit
        }
        return snapshot(agentId)
    }

    /** 首次仅在该免费来源完全不存在时写入随应用发布的快照，并给空目录选定可直接使用的默认模型。 */
    fun seedFreeProvidersIfAbsent(
        agentId: String,
        sourceId: String,
        sourceVersion: String,
        providers: List<AgentCatalogProvider>,
    ): AgentProviderCatalogSnapshot {
        update(agentId) { current ->
            if (current.providers.any {
                    it.policy == AgentProviderCatalogPolicy.FreeScan && it.ownerId == sourceId
                }) {
                return@update current to Unit
            }
            val seeded = providers.mapNotNull { candidate ->
                candidate.normalized(
                    source = AgentModelSource.Free,
                    policy = AgentProviderCatalogPolicy.FreeScan,
                    ownerId = sourceId,
                    sourceVersion = sourceVersion,
                )
            }.filterNot { candidate -> current.providers.any { it.id == candidate.id } }
                .distinctBy(AgentCatalogProvider::id)
            if (seeded.isEmpty()) return@update current to Unit
            val first = seeded.first()
            val firstModel = first.models.first()
            current.copy(
                providers = (current.providers + seeded).distinctBy(AgentCatalogProvider::id)
                    .sortedBy(AgentCatalogProvider::id),
                selectedProviderId = current.selectedProviderId ?: first.id,
                selectedModelId = current.selectedModelId ?: firstModel.id,
            ) to Unit
        }
        return snapshot(agentId)
    }

    /** 登录成功后保存一份官方目录版本；不会被免费扫描或普通页面刷新改写。 */
    fun saveOfficialVersion(
        agentId: String,
        accountId: String,
        sourceVersion: String,
        providers: List<AgentCatalogProvider>,
    ): AgentProviderCatalogSnapshot {
        update(agentId) { current ->
            val official = providers.mapNotNull { candidate ->
                candidate.normalized(
                    source = AgentModelSource.OfficialLogin,
                    policy = AgentProviderCatalogPolicy.OfficialLoginVersion,
                    ownerId = accountId,
                    sourceVersion = sourceVersion,
                )
            }.distinctBy(AgentCatalogProvider::id)
            val next = current.copy(
                providers = (
                    current.providers.filterNot {
                        it.policy == AgentProviderCatalogPolicy.OfficialLoginVersion && it.ownerId == accountId
                    } + official
                    ).sortedBy(AgentCatalogProvider::id),
            ).dropInvalidSelections()
            next to Unit
        }
        return snapshot(agentId)
    }

    fun select(agentId: String, providerId: String, modelId: String): Boolean = update(agentId) { current ->
        val accepted = current.providers.any { provider ->
            provider.id == providerId && provider.models.any { it.id == modelId }
        }
        if (!accepted) current to false
        else current.copy(selectedProviderId = providerId, selectedModelId = modelId) to true
    }

    /** 保存某个已映射控件的最近选择；只更新 Kite 草稿默认值，不触碰 Agent。 */
    fun selectControl(agentId: String, configId: String, value: AgentConfigValue): Boolean =
        update(agentId) { current ->
            val index = current.controls.indexOfFirst { it.id == configId }
            val selected = current.controls.getOrNull(index)?.withSelectedValue(value)
            if (selected == null) {
                current to false
            } else {
                current.copy(
                    controls = current.controls.toMutableList().apply { this[index] = selected },
                ) to true
            }
        }

    /** 首次只写入 Adapter 随应用发布的已核验工作模式，并建立可用的草稿默认值。 */
    fun seedWorkModesIfAbsent(
        agentId: String,
        modes: List<AgentMode>,
        defaultModeId: String?,
    ): AgentProviderCatalogSnapshot {
        update(agentId) { current ->
            if (current.workModes.isNotEmpty()) return@update current to Unit
            val normalized = modes.mapNotNull { it.normalized() }.distinctBy(AgentMode::id)
            if (normalized.isEmpty()) return@update current to Unit
            val selected = defaultModeId?.takeIf { id -> normalized.any { it.id == id } }
                ?: normalized.first().id
            current.copy(workModes = normalized, selectedWorkModeId = selected) to Unit
        }
        return snapshot(agentId)
    }

    /** 保存 Adapter 已映射的真实工作模式目录；保留仍有效的用户草稿选择。 */
    fun replaceWorkModes(
        agentId: String,
        modes: List<AgentMode>,
        currentModeId: String?,
    ): AgentProviderCatalogSnapshot = update(agentId) { current ->
        val normalized = modes.mapNotNull { it.normalized() }.distinctBy(AgentMode::id)
        val selected = current.selectedWorkModeId?.takeIf { id -> normalized.any { it.id == id } }
            ?: currentModeId?.takeIf { id -> normalized.any { it.id == id } }
        val next = current.copy(workModes = normalized, selectedWorkModeId = selected)
        next to next
    }

    /** 只更新 Kite 草稿选择；不接触 Agent、连接或原生会话。 */
    fun selectWorkMode(agentId: String, modeId: String): Boolean = update(agentId) { current ->
        val accepted = current.workModes.any { it.id == modeId }
        if (!accepted) current to false
        else current.copy(selectedWorkModeId = modeId) to true
    }

    /** 保存 Adapter 已映射的权限和推理目录；不接收模型、模式或未分类配置。 */
    fun replaceMappedControls(agentId: String, options: List<AgentConfigOption>): AgentProviderCatalogSnapshot =
        update(agentId) { current ->
            val mapped = options.filter { option ->
                option.category == AgentConfigCategory.Permission ||
                    option.category == AgentConfigCategory.ThoughtLevel
            }
            val next = current.copy(controls = mapped.map { it.keepSelectionFrom(current.controls) })
            next to next
        }

    /** 只替换本次真实公布到的类别，避免一次局部更新误删另一类固定控件。 */
    fun mergeMappedControls(agentId: String, options: List<AgentConfigOption>): AgentProviderCatalogSnapshot {
        val mapped = options.filter { option ->
            option.category == AgentConfigCategory.Permission ||
                option.category == AgentConfigCategory.ThoughtLevel
        }
        if (mapped.isEmpty()) return snapshot(agentId)
        val categories = mapped.mapNotNullTo(linkedSetOf(), AgentConfigOption::category)
        update(agentId) { current ->
            val next = current.copy(
                controls = (current.controls.filterNot { it.category in categories } +
                    mapped.map { it.keepSelectionFrom(current.controls) })
                    .distinctBy(AgentConfigOption::id),
            )
            next to Unit
        }
        return snapshot(agentId)
    }

    fun hasCompletedImport(agentId: String, importId: String): Boolean =
        importId.isNotBlank() && importId in snapshot(agentId).completedImports

    fun markImportCompleted(agentId: String, importId: String): AgentProviderCatalogSnapshot {
        val normalized = importId.trim().take(MAX_ID)
        require(normalized.isNotBlank()) { "导入 ID 不能为空" }
        update(agentId) { current ->
            current.copy(completedImports = current.completedImports + normalized) to Unit
        }
        return snapshot(agentId)
    }

    fun markProviderPrepared(agentId: String, providerId: String): AgentProviderCatalogSnapshot {
        update(agentId) { current ->
            current.copy(pendingNativeProviderIds = current.pendingNativeProviderIds - providerId) to Unit
        }
        return snapshot(agentId)
    }

    internal fun credential(agentId: String, providerId: String): AgentCatalogCredential? =
        credentialVault.read(credentialKey(agentId, providerId))?.let(::AgentCatalogCredential)

    internal fun resetForTest() = synchronized(LOCK) {
        preferences.edit().clear().commit()
        credentialVault.clear()
        snapshotCache.clear()
        snapshotCachePayload = null
        snapshotCachePayloadKnown = false
    }

    private fun <T> update(
        agentId: String,
        transform: (AgentProviderCatalogSnapshot) -> Pair<AgentProviderCatalogSnapshot, T>,
    ): T = synchronized(LOCK) {
        require(agentId.isNotBlank()) { "Agent ID 不能为空" }
        val agents = readAgents()
        val current = snapshotCache[agentId]
            ?: agents.optJSONObject(agentId)?.toSnapshot(agentId)
            ?: AgentProviderCatalogSnapshot()
        val (candidate, result) = transform(current)
        if (candidate != current) {
            val next = candidate.copy(revision = current.revision + 1)
            agents.put(agentId, next.toJson())
            writeAgents(agents)
            snapshotCache[agentId] = next
        } else {
            snapshotCache.putIfAbsent(agentId, current)
        }
        result
    }

    private fun readAgents(): JSONObject {
        val persistedPayload = preferences.getString(KEY_PAYLOAD, null)
        if (!snapshotCachePayloadKnown || snapshotCachePayload != persistedPayload) {
            snapshotCache.clear()
            snapshotCachePayload = persistedPayload
            snapshotCachePayloadKnown = true
        }
        return runCatching {
            val payload = JSONObject(persistedPayload ?: "{}")
            if (payload.optInt(KEY_VERSION, VERSION) != VERSION) JSONObject()
            else payload.optJSONObject(KEY_AGENTS) ?: JSONObject()
        }.getOrElse { JSONObject() }
    }

    private fun writeAgents(agents: JSONObject) {
        val payload = JSONObject().put(KEY_VERSION, VERSION).put(KEY_AGENTS, agents).toString()
        preferences.edit().putString(KEY_PAYLOAD, payload).apply()
        snapshotCachePayload = payload
        snapshotCachePayloadKnown = true
    }

    private fun JSONObject.toSnapshot(agentId: String): AgentProviderCatalogSnapshot {
        val providers = buildList {
            val array = optJSONArray(KEY_PROVIDERS) ?: JSONArray()
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                json.toProvider(agentId)?.let(::add)
            }
        }.distinctBy(AgentCatalogProvider::id).sortedBy(AgentCatalogProvider::id)
        val controls = buildList {
            val array = optJSONArray(KEY_CONTROLS) ?: JSONArray()
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.toControl()?.let(::add)
            }
        }
        val workModes = buildList {
            val array = optJSONArray(KEY_WORK_MODES) ?: JSONArray()
            for (index in 0 until array.length()) {
                val mode = array.optJSONObject(index) ?: continue
                AgentMode(
                    id = mode.optString(KEY_ID),
                    name = mode.optString(KEY_NAME),
                    description = mode.optString(KEY_DESCRIPTION).takeIf(String::isNotBlank),
                ).normalized()?.let(::add)
            }
        }.distinctBy(AgentMode::id)
        return AgentProviderCatalogSnapshot(
            revision = optLong(KEY_REVISION),
            selectedProviderId = optString(KEY_SELECTED_PROVIDER).takeIf(String::isNotBlank),
            selectedModelId = optString(KEY_SELECTED_MODEL).takeIf(String::isNotBlank),
            providers = providers,
            controls = controls,
            workModes = workModes,
            selectedWorkModeId = optString(KEY_SELECTED_WORK_MODE).trim()
                .take(MAX_ID).takeIf(String::isNotBlank),
            completedImports = buildSet {
                val array = optJSONArray(KEY_COMPLETED_IMPORTS) ?: JSONArray()
                for (index in 0 until array.length()) {
                    array.optString(index).trim().take(MAX_ID).takeIf(String::isNotBlank)?.let(::add)
                }
            },
            pendingNativeProviderIds = buildSet {
                val array = optJSONArray(KEY_PENDING_NATIVE_PROVIDERS) ?: JSONArray()
                for (index in 0 until array.length()) {
                    array.optString(index).trim().take(MAX_ID).takeIf(String::isNotBlank)?.let(::add)
                }
            },
        ).dropInvalidSelections()
    }

    private fun AgentProviderCatalogSnapshot.toJson(): JSONObject = JSONObject().apply {
        put(KEY_REVISION, revision)
        selectedProviderId?.let { put(KEY_SELECTED_PROVIDER, it) }
        selectedModelId?.let { put(KEY_SELECTED_MODEL, it) }
        put(KEY_PROVIDERS, JSONArray().apply { providers.forEach { put(it.toJson()) } })
        put(KEY_CONTROLS, JSONArray().apply { controls.forEach { put(it.toJson()) } })
        put(KEY_WORK_MODES, JSONArray().apply {
            workModes.forEach { mode ->
                put(JSONObject().apply {
                    put(KEY_ID, mode.id)
                    put(KEY_NAME, mode.name)
                    mode.description?.let { put(KEY_DESCRIPTION, it) }
                })
            }
        })
        selectedWorkModeId?.let { put(KEY_SELECTED_WORK_MODE, it) }
        put(KEY_COMPLETED_IMPORTS, JSONArray().apply { completedImports.sorted().forEach(::put) })
        put(KEY_PENDING_NATIVE_PROVIDERS, JSONArray().apply {
            pendingNativeProviderIds.sorted().forEach(::put)
        })
    }

    private fun JSONObject.toProvider(agentId: String): AgentCatalogProvider? {
        val id = optString(KEY_ID).trim().take(MAX_ID)
        val name = optString(KEY_NAME).trim().take(MAX_DISPLAY_NAME)
        if (id.isBlank() || name.isBlank()) return null
        val source = enumValueOrNull<AgentModelSource>(optString(KEY_SOURCE)) ?: return null
        val policy = enumValueOrNull<AgentProviderCatalogPolicy>(optString(KEY_POLICY)) ?: return null
        val models = buildList {
            val array = optJSONArray(KEY_MODELS) ?: JSONArray()
            for (index in 0 until array.length()) {
                val model = array.optJSONObject(index) ?: continue
                val modelId = model.optString(KEY_ID).trim().take(MAX_MODEL_ID)
                val displayName = model.optString(KEY_NAME).trim().take(MAX_DISPLAY_NAME)
                if (modelId.isNotBlank()) add(AgentCatalogModel(modelId, displayName.ifBlank { modelId }))
            }
        }.distinctBy(AgentCatalogModel::id)
        if (models.isEmpty()) return null
        return AgentCatalogProvider(
            id = id,
            displayName = name,
            baseUrl = optString(KEY_BASE_URL).trim().take(MAX_URL).takeIf(String::isNotBlank),
            models = models,
            source = source,
            policy = policy,
            ownerId = optString(KEY_OWNER_ID).trim().take(MAX_ID).takeIf(String::isNotBlank),
            sourceVersion = optString(KEY_SOURCE_VERSION).trim().take(MAX_VERSION).takeIf(String::isNotBlank),
            credentialPresent = credentialVault.contains(credentialKey(agentId, id)),
        )
    }

    private fun AgentCatalogProvider.toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_NAME, displayName)
        baseUrl?.let { put(KEY_BASE_URL, it) }
        put(KEY_SOURCE, source.name)
        put(KEY_POLICY, policy.name)
        ownerId?.let { put(KEY_OWNER_ID, it) }
        sourceVersion?.let { put(KEY_SOURCE_VERSION, it) }
        put(KEY_MODELS, JSONArray().apply {
            models.forEach { model -> put(JSONObject().put(KEY_ID, model.id).put(KEY_NAME, model.displayName)) }
        })
    }

    private fun AgentConfigOption.toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_NAME, name)
        description?.let { put(KEY_DESCRIPTION, it) }
        put(KEY_CATEGORY, category?.value)
        when (this@toJson) {
            is AgentConfigOption.Toggle -> {
                put(KEY_TYPE, TYPE_TOGGLE)
                put(KEY_CURRENT, currentValue)
            }
            is AgentConfigOption.Select -> {
                put(KEY_TYPE, TYPE_SELECT)
                put(KEY_CURRENT, currentValue)
                put(KEY_CHOICES, JSONArray().apply {
                    choices.forEach { choice ->
                        put(JSONObject().apply {
                            put(KEY_VALUE, choice.value)
                            put(KEY_NAME, choice.name)
                            choice.description?.let { put(KEY_DESCRIPTION, it) }
                            choice.permission?.let { put(KEY_PERMISSION, it.name) }
                            choice.reasoning?.let { put(KEY_REASONING, it.id) }
                        })
                    }
                })
            }
        }
    }

    private fun JSONObject.toControl(): AgentConfigOption? {
        val id = optString(KEY_ID).trim().take(MAX_ID)
        val name = optString(KEY_NAME).trim().take(MAX_DISPLAY_NAME)
        val category = AgentConfigCategory(optString(KEY_CATEGORY).trim())
        if (id.isBlank() || name.isBlank() || category !in STORED_CONTROL_CATEGORIES) return null
        val description = optString(KEY_DESCRIPTION).trim().take(MAX_DESCRIPTION).takeIf(String::isNotBlank)
        return when (optString(KEY_TYPE)) {
            TYPE_TOGGLE -> AgentConfigOption.Toggle(
                id = id,
                name = name,
                description = description,
                category = category,
                currentValue = optBoolean(KEY_CURRENT),
            )
            TYPE_SELECT -> {
                val choices = buildList {
                    val array = optJSONArray(KEY_CHOICES) ?: JSONArray()
                    for (index in 0 until array.length()) {
                        val choice = array.optJSONObject(index) ?: continue
                        val value = choice.optString(KEY_VALUE).trim().take(MAX_MODEL_ID)
                        val choiceName = choice.optString(KEY_NAME).trim().take(MAX_DISPLAY_NAME)
                        if (value.isBlank() || choiceName.isBlank()) continue
                        add(AgentConfigChoice(
                            value = value,
                            name = choiceName,
                            description = choice.optString(KEY_DESCRIPTION).trim()
                                .take(MAX_DESCRIPTION).takeIf(String::isNotBlank),
                            permission = enumValueOrNull<AgentPermissionLevel>(choice.optString(KEY_PERMISSION)),
                            reasoning = reasoningSemantics(choice.optString(KEY_REASONING)),
                        ))
                    }
                }.distinctBy(AgentConfigChoice::value)
                val current = optString(KEY_CURRENT)
                if (choices.isEmpty() || choices.none { it.value == current }) null
                else AgentConfigOption.Select(
                    id = id,
                    name = name,
                    description = description,
                    category = category,
                    currentValue = current,
                    choices = choices,
                )
            }
            else -> null
        }
    }

    private fun AgentCatalogProvider.normalized(
        source: AgentModelSource,
        policy: AgentProviderCatalogPolicy,
        ownerId: String?,
        sourceVersion: String?,
    ): AgentCatalogProvider? {
        val providerId = id.trim().take(MAX_ID)
        val providerName = displayName.trim().take(MAX_DISPLAY_NAME)
        val normalizedModels = models.mapNotNull { model ->
            val modelId = model.id.trim().take(MAX_MODEL_ID)
            val name = model.displayName.trim().take(MAX_DISPLAY_NAME)
            modelId.takeIf(String::isNotBlank)?.let { AgentCatalogModel(it, name.ifBlank { it }) }
        }.distinctBy(AgentCatalogModel::id)
        if (providerId.isBlank() || providerName.isBlank() || normalizedModels.isEmpty()) return null
        return copy(
            id = providerId,
            displayName = providerName,
            baseUrl = baseUrl?.trim()?.take(MAX_URL)?.takeIf(String::isNotBlank),
            models = normalizedModels,
            source = source,
            policy = policy,
            ownerId = ownerId?.trim()?.take(MAX_ID)?.takeIf(String::isNotBlank),
            sourceVersion = sourceVersion?.trim()?.take(MAX_VERSION)?.takeIf(String::isNotBlank),
            credentialPresent = false,
        )
    }

    private fun AgentMode.normalized(): AgentMode? {
        val modeId = id.trim().take(MAX_ID)
        val modeName = name.trim().take(MAX_DISPLAY_NAME)
        if (modeId.isBlank() || modeName.isBlank()) return null
        return copy(
            id = modeId,
            name = modeName,
            description = description?.trim()?.take(MAX_DESCRIPTION)?.takeIf(String::isNotBlank),
        )
    }

    private fun AgentConfigOption.keepSelectionFrom(
        previous: List<AgentConfigOption>,
    ): AgentConfigOption {
        val stored = previous.firstOrNull { it.id == id && it.category == category } ?: return this
        return when {
            this is AgentConfigOption.Select && stored is AgentConfigOption.Select &&
                choices.any { it.value == stored.currentValue } -> copy(currentValue = stored.currentValue)
            this is AgentConfigOption.Toggle && stored is AgentConfigOption.Toggle ->
                copy(currentValue = stored.currentValue)
            else -> this
        }
    }

    private fun AgentConfigOption.withSelectedValue(value: AgentConfigValue): AgentConfigOption? = when {
        this is AgentConfigOption.Select && value is AgentConfigValue.Select &&
            choices.any { it.value == value.value } -> copy(currentValue = value.value)
        this is AgentConfigOption.Toggle && value is AgentConfigValue.Toggle -> copy(currentValue = value.value)
        else -> null
    }

    private fun AgentProviderCatalogSnapshot.dropInvalidSelections(): AgentProviderCatalogSnapshot {
        val validModel = providers.any { provider ->
            provider.id == selectedProviderId && provider.models.any { it.id == selectedModelId }
        }
        val modelSafe = if (validModel || (selectedProviderId == null && selectedModelId == null)) this
        else copy(selectedProviderId = null, selectedModelId = null)
        val validMode = modelSafe.selectedWorkModeId == null ||
            modelSafe.workModes.any { it.id == modelSafe.selectedWorkModeId }
        return if (validMode) modelSafe else modelSafe.copy(selectedWorkModeId = null)
    }

    private fun credentialKey(agentId: String, providerId: String): String =
        "$agentId\u0000$providerId"

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
        enumValues<T>().firstOrNull { it.name == value }

    private fun reasoningSemantics(id: String): AgentReasoningSemantics? =
        AgentReasoningLevel.entries.firstOrNull { it.id == id }
            ?: AgentReasoningMode.entries.firstOrNull { it.id == id }

    private companion object {
        val LOCK = Any()
        val STORED_CONTROL_CATEGORIES = setOf(AgentConfigCategory.Permission, AgentConfigCategory.ThoughtLevel)
        const val PREFERENCES = "kite_agent_provider_catalog"
        const val KEY_PAYLOAD = "payload"
        const val KEY_VERSION = "version"
        const val KEY_AGENTS = "agents"
        const val KEY_REVISION = "revision"
        const val KEY_SELECTED_PROVIDER = "selectedProviderId"
        const val KEY_SELECTED_MODEL = "selectedModelId"
        const val KEY_PROVIDERS = "providers"
        const val KEY_CONTROLS = "controls"
        const val KEY_WORK_MODES = "workModes"
        const val KEY_SELECTED_WORK_MODE = "selectedWorkModeId"
        const val KEY_COMPLETED_IMPORTS = "completedImports"
        const val KEY_PENDING_NATIVE_PROVIDERS = "pendingNativeProviderIds"
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_BASE_URL = "baseUrl"
        const val KEY_MODELS = "models"
        const val KEY_SOURCE = "source"
        const val KEY_POLICY = "policy"
        const val KEY_OWNER_ID = "ownerId"
        const val KEY_SOURCE_VERSION = "sourceVersion"
        const val KEY_DESCRIPTION = "description"
        const val KEY_CATEGORY = "category"
        const val KEY_TYPE = "type"
        const val KEY_CURRENT = "current"
        const val KEY_CHOICES = "choices"
        const val KEY_VALUE = "value"
        const val KEY_PERMISSION = "permission"
        const val KEY_REASONING = "reasoning"
        const val TYPE_SELECT = "select"
        const val TYPE_TOGGLE = "toggle"
        const val VERSION = 1
        const val MAX_ID = 256
        const val MAX_MODEL_ID = 512
        const val MAX_DISPLAY_NAME = 256
        const val MAX_DESCRIPTION = 2_048
        const val MAX_URL = 4_096
        const val MAX_VERSION = 256
    }
}

internal interface AgentProviderCredentialVault {
    fun contains(key: String): Boolean
    fun put(key: String, secret: String)
    fun read(key: String): String?
    fun remove(key: String)
    fun clear()
}

/** Android Keystore 只保存主密钥；每个 Provider 的 AES-GCM 密文存入私有 SharedPreferences。 */
private class AndroidKeystoreAgentProviderCredentialVault(context: Context) : AgentProviderCredentialVault {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun contains(key: String): Boolean = preferences.contains(storageKey(key))

    override fun put(key: String, secret: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(cipher.iv.size + encrypted.size)
        cipher.iv.copyInto(payload)
        encrypted.copyInto(payload, cipher.iv.size)
        preferences.edit().putString(storageKey(key), Base64.encodeToString(payload, Base64.NO_WRAP)).commit()
    }

    override fun read(key: String): String? = runCatching {
        val payload = preferences.getString(storageKey(key), null)?.let {
            Base64.decode(it, Base64.NO_WRAP)
        } ?: return null
        if (payload.size <= IV_BYTES) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES))
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        cipher.doFinal(payload.copyOfRange(IV_BYTES, payload.size)).toString(Charsets.UTF_8)
    }.getOrNull()

    override fun remove(key: String) {
        preferences.edit().remove(storageKey(key)).commit()
    }

    override fun clear() {
        preferences.edit().clear().commit()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun storageKey(key: String): String = MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val PREFERENCES = "kite_agent_provider_credentials"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "kite.agent.provider.credentials.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
