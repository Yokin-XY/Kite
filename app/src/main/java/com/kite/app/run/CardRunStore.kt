package com.kite.app.run

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.kite.app.browser.BrowserHandoffPolicy
import com.kite.app.recipe.KiteRecipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object CardRunStore {
    private val _runs = MutableStateFlow<List<CardRunState>>(emptyList())
    private val registeredRecipes = linkedMapOf<String, KiteRecipe>()
    private val runsByInstance = linkedMapOf<String, CardRunState>()
    private val historiesByRecipe = linkedMapOf<String, MutableList<CardRunHistoryEntry>>()
    private val recentlyConfirmedRuntimeOwners = linkedMapOf<String, Long>()
    private val persistExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "KiteCardRunStorePersist").apply { isDaemon = true }
    }
    private val persistScheduleLock = Any()
    private var prefs: SharedPreferences? = null
    private var initialized = false
    private var persistScheduled = false
    private var historyPersistScheduled = false
    val runs: StateFlow<List<CardRunState>> = _runs

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val loaded = loadPersistedRuns()
        val normalized = loaded
            .map { it.normalizedAfterProcessRestore() }
            .filterNot { it.shouldDropCurrentAfterProcessRestore() }
        runsByInstance.clear()
        normalized.forEach { runsByInstance[it.instanceId] = it }
        historiesByRecipe.clear()
        recentlyConfirmedRuntimeOwners.clear()
        loadPersistedHistory().forEach { entry ->
            historiesByRecipe.getOrPut(entry.recipeId) { mutableListOf() }.add(entry)
        }
        trimAllHistory()
        _runs.value = sortedRuns()
        initialized = true
        if (normalized != loaded) {
            persistRuns()
        }
    }

    /**
     * 仅用于单元测试:把单例内存状态清空回未初始化,使各测试互不污染。
     * 不改动磁盘 SharedPreferences 内容(进程恢复类测试需要预先 seed 磁盘数据)。
     * 生产代码不应调用,因此标注 @VisibleForTesting。
     */
    @VisibleForTesting
    @Synchronized
    fun resetForTest() {
        synchronized(persistScheduleLock) {
            persistScheduled = false
            historyPersistScheduled = false
        }
        registeredRecipes.clear()
        runsByInstance.clear()
        historiesByRecipe.clear()
        recentlyConfirmedRuntimeOwners.clear()
        _runs.value = emptyList()
        prefs = null
        initialized = false
    }

    @Synchronized
    fun registerRecipe(recipe: KiteRecipe) {
        registeredRecipes[recipe.id] = recipe
    }

    @Synchronized
    fun registeredRecipe(recipeId: String): KiteRecipe? =
        registeredRecipes[recipeId]

    @Synchronized
    fun start(
        recipe: KiteRecipe,
        instanceId: String = recipe.id,
        parentInstanceId: String? = null,
        ownerKind: String = CardRunState.OWNER_KIND_CARD,
        stepId: String? = null,
        agentId: String? = null,
        environmentId: String = CardRunState.DEFAULT_ENVIRONMENT_ID
    ): CardRunState {
        registerRecipe(recipe)
        val now = System.currentTimeMillis()
        val resolvedEnvironmentId = environmentId.normalizedEnvironmentId()
        val existing = runsByInstance[instanceId]
        if (
            existing != null &&
            existing.environmentId == resolvedEnvironmentId &&
            existing.recipeId == recipe.id &&
            existing.status == CardRunStatus.Starting &&
            !existing.hasRunBinding()
        ) {
            // Resource preparation and recipe start can touch the same instance; keep that as one lifecycle.
            val run = existing.copy(
                recipeName = recipe.name,
                parentInstanceId = parentInstanceId ?: existing.parentInstanceId,
                ownerKind = ownerKind,
                stepId = stepId ?: existing.stepId,
                agentId = existing.agentId ?: agentId.normalizedAgentId(),
                stepCount = recipe.steps.size,
                updatedAt = now
            )
            upsert(run)
            return run
        }
        val nextAgentId = existing?.agentId ?: agentId.normalizedAgentId()
        val resumableAgentBinding = existing?.agentBinding?.takeIf {
            existing.recipeId == recipe.id &&
                nextAgentId != null &&
                existing.agentId == nextAgentId
        }
        val run = CardRunState(
            instanceId = instanceId,
            recipeId = recipe.id,
            recipeName = recipe.name,
            parentInstanceId = parentInstanceId,
            ownerKind = ownerKind,
            stepId = stepId,
            agentId = nextAgentId,
            agentBinding = resumableAgentBinding,
            status = CardRunStatus.Starting,
            stepCount = recipe.steps.size,
            createdAt = now,
            updatedAt = now,
            environmentId = resolvedEnvironmentId
        )
        upsert(run)
        recordHistoryStart(recipe, run, now)
        return run
    }

    @Synchronized
    fun update(
        recipe: KiteRecipe,
        status: CardRunStatus,
        instanceId: String? = null,
        parentInstanceId: String? = null,
        ownerKind: String? = null,
        stepId: String? = null,
        surface: CardRunSurface? = null,
        currentStepIndex: Int? = null,
        runtimeRootOwnerId: String? = null,
        runtimeOwnerId: String? = null,
        runtimeUnitId: String? = null,
        ownedRuntimeOwnerIds: List<String>? = null,
        runId: String? = null,
        terminalSessionId: String? = null,
        pid: String? = null,
        rootPid: String? = null,
        processGroupId: String? = null,
        systemSessionId: String? = null,
        runtimeLane: String? = null,
        runtimeFallbackReason: String? = null,
        lastMeaningfulOutput: String? = null,
        lastError: String? = null,
        shellReportText: String? = null,
        nextActionUrl: String? = null,
        x11Display: String? = null,
        x11SocketPath: String? = null,
        agentId: String? = null,
        agentBinding: CardRunAgentBinding? = null,
        clearRunBinding: Boolean = false,
        clearTerminalSession: Boolean = false,
        clearNextActionUrl: Boolean = false,
        clearAgentBinding: Boolean = false,
        environmentId: String? = null
    ): CardRunState {
        val now = System.currentTimeMillis()
        val resolvedEnvironmentId = environmentId?.normalizedEnvironmentId()
        val existing = instanceId
            ?.takeIf { it.isNotBlank() }
            ?.let { id ->
                get(id)?.takeIf { resolvedEnvironmentId == null || it.environmentId == resolvedEnvironmentId }
                    ?: start(
                        recipe = recipe,
                        instanceId = id,
                        environmentId = resolvedEnvironmentId ?: CardRunState.DEFAULT_ENVIRONMENT_ID
                    )
            }
            ?: currentForRecipe(recipe.id, resolvedEnvironmentId)
            ?: start(
                recipe = recipe,
                environmentId = resolvedEnvironmentId ?: CardRunState.DEFAULT_ENVIRONMENT_ID
            )
        if (existing.shouldIgnoreStoppedRuntimeWrite(
                status = status,
                runId = runId,
                terminalSessionId = terminalSessionId,
                x11Display = x11Display,
                x11SocketPath = x11SocketPath,
                pid = pid,
                rootPid = rootPid,
                processGroupId = processGroupId,
                systemSessionId = systemSessionId,
                lastMeaningfulOutput = lastMeaningfulOutput,
                lastError = lastError,
                shellReportText = shellReportText
            )
        ) {
            return existing
        }
        val beginsNewHistory = existing.status.endsHistoryEntry() &&
            (status.startsHistoryEntry() || status.isImmediateHistoryEnd())
        val resolvedSurface = surface ?: when {
            !nextActionUrl.isNullOrBlank() -> CardRunSurface.Web
            !terminalSessionId.isNullOrBlank() -> CardRunSurface.Terminal
            !x11Display.isNullOrBlank() -> CardRunSurface.X11
            !lastError.isNullOrBlank() || !lastMeaningfulOutput.isNullOrBlank() || !shellReportText.isNullOrBlank() -> CardRunSurface.Report
            else -> existing.surface
        }
        val nextOwnedRuntimeOwnerIds = if (clearRunBinding) {
            emptyList()
        } else {
            (ownedRuntimeOwnerIds.orEmpty().ifEmpty { existing.ownedRuntimeOwnerIds } +
                listOfNotNull(runtimeOwnerId?.takeIf { it.isNotBlank() }))
                .distinct()
        }
        val nextAgentId = existing.agentId ?: agentId.normalizedAgentId()
        val next = existing.copy(
            recipeName = recipe.name,
            parentInstanceId = parentInstanceId ?: existing.parentInstanceId,
            ownerKind = ownerKind ?: existing.ownerKind,
            stepId = stepId ?: existing.stepId,
            status = status,
            surface = resolvedSurface,
            currentStepIndex = currentStepIndex ?: existing.currentStepIndex,
            stepCount = recipe.steps.size,
            runtimeRootOwnerId = if (clearRunBinding) null else runtimeRootOwnerId ?: existing.runtimeRootOwnerId,
            runtimeOwnerId = if (clearRunBinding) null else runtimeOwnerId ?: existing.runtimeOwnerId,
            runtimeUnitId = if (clearRunBinding) null else runtimeUnitId ?: existing.runtimeUnitId,
            ownedRuntimeOwnerIds = nextOwnedRuntimeOwnerIds,
            runId = if (clearRunBinding) null else runId ?: existing.runId,
            terminalSessionId = if (clearRunBinding || clearTerminalSession) null else terminalSessionId ?: existing.terminalSessionId,
            pid = if (clearRunBinding) null else pid ?: existing.pid,
            rootPid = if (clearRunBinding) null else rootPid ?: existing.rootPid,
            processGroupId = if (clearRunBinding) null else processGroupId ?: existing.processGroupId,
            systemSessionId = if (clearRunBinding) null else systemSessionId ?: existing.systemSessionId,
            runtimeLane = runtimeLane ?: existing.runtimeLane,
            runtimeFallbackReason = runtimeFallbackReason ?: existing.runtimeFallbackReason,
            lastMeaningfulOutput = lastMeaningfulOutput ?: existing.lastMeaningfulOutput,
            lastError = lastError,
            shellReportText = shellReportText ?: existing.shellReportText,
            nextActionUrl = if (clearRunBinding || clearNextActionUrl) null else nextActionUrl ?: existing.nextActionUrl,
            x11Display = if (clearRunBinding) null else x11Display ?: existing.x11Display,
            x11SocketPath = if (clearRunBinding) null else x11SocketPath ?: existing.x11SocketPath,
            agentId = nextAgentId,
            agentBinding = when {
                clearAgentBinding -> null
                agentBinding != null -> agentBinding
                clearRunBinding -> null
                else -> existing.agentBinding
            },
            createdAt = if (beginsNewHistory) now else existing.createdAt,
            updatedAt = now
        )
        upsert(next)
        if (beginsNewHistory) {
            recordHistoryStart(recipe, next, now)
        } else {
            recordHistoryUpdate(recipe, next, now)
        }
        if (next.status == CardRunStatus.CleanupPending) {
            reconcileCleanupPendingOwners(recentConfirmedOwnerIds(now))
        }
        return get(next.instanceId) ?: next
    }

    @Synchronized
    fun selectSurface(instanceId: String, surface: CardRunSurface): CardRunState? {
        val existing = get(instanceId) ?: return null
        val next = existing.copy(
            surface = surface,
            selectedWindowId = null,
            updatedAt = System.currentTimeMillis()
        )
        upsert(next)
        return next
    }

    @Synchronized
    fun selectWindow(instanceId: String, windowId: String, surface: CardRunSurface): CardRunState? {
        val existing = get(instanceId) ?: return null
        val next = existing.copy(
            surface = surface,
            selectedWindowId = windowId,
            updatedAt = System.currentTimeMillis()
        )
        upsert(next)
        return next
    }

    /**
     * 只更新低频 Agent 运行绑定。调用方必须提交它观察到的运行代次，迟到连接不得改写新实例。
     */
    @Synchronized
    fun updateAgentBinding(
        instanceId: String,
        expectedGeneration: Long,
        status: CardRunAgentConnectionStatus,
        providerId: String? = null,
        sessionId: String? = null,
        statusMessage: String? = null,
        clear: Boolean = false
    ): CardRunState? {
        val existing = runsByInstance[instanceId] ?: return null
        if (expectedGeneration <= 0L || existing.createdAt != expectedGeneration) return null
        val previous = existing.agentBinding
        val resolvedProviderId = providerId?.trim()?.takeIf(String::isNotBlank) ?: previous?.providerId
        if (!clear && resolvedProviderId == null) return null
        val now = System.currentTimeMillis()
        val binding = if (clear) {
            null
        } else {
            CardRunAgentBinding(
                providerId = resolvedProviderId!!,
                sessionId = sessionId?.trim()?.takeIf(String::isNotBlank) ?: previous?.sessionId,
                status = status,
                statusMessage = statusMessage?.trim()?.takeIf(String::isNotBlank),
                updatedAt = now
            )
        }
        val next = existing.copy(agentBinding = binding, updatedAt = now)
        upsert(next)
        return next
    }

    @Synchronized
    fun removeRun(instanceId: String): CardRunState? {
        val removed = runsByInstance.remove(instanceId) ?: return null
        _runs.value = sortedRuns()
        schedulePersistRuns()
        return removed
    }

    @Synchronized
    fun currentForRecipe(recipeId: String, environmentId: String? = null): CardRunState? =
        _runs.value
            .filter { it.recipeId == recipeId }
            .filter { environmentId == null || it.environmentId == environmentId.normalizedEnvironmentId() }
            .filter { it.parentInstanceId.isNullOrBlank() }
            .maxByOrNull { it.updatedAt }
            ?: _runs.value
                .filter { it.recipeId == recipeId }
                .filter { environmentId == null || it.environmentId == environmentId.normalizedEnvironmentId() }
                .maxByOrNull { it.updatedAt }

    @Synchronized
    fun get(instanceId: String): CardRunState? =
        runsByInstance[instanceId]

    @Synchronized
    fun get(instanceId: String, environmentId: String): CardRunState? =
        runsByInstance[instanceId]
            ?.takeIf { it.environmentId == environmentId.normalizedEnvironmentId() }

    @Synchronized
    fun snapshot(): List<CardRunState> = _runs.value

    /**
     * 环境切换已经由 PRoot 进程守卫确认旧 View 退出，此处只收敛对应环境的运行事实。
     * 不触碰其他环境，也不重新扫描进程。
     */
    @Synchronized
    fun confirmEnvironmentStopped(environmentId: String): Set<String> {
        val target = environmentId.normalizedEnvironmentId()
        val now = System.currentTimeMillis()
        val settled = linkedSetOf<String>()
        runsByInstance.values.toList().forEach { run ->
            if (run.environmentId != target || !run.requiresEnvironmentStopSettlement()) return@forEach
            val next = run.copy(
                status = CardRunStatus.Stopped,
                surface = CardRunSurface.Summary,
                selectedWindowId = null,
                runtimeRootOwnerId = null,
                runtimeOwnerId = null,
                runtimeUnitId = null,
                ownedRuntimeOwnerIds = emptyList(),
                runId = null,
                terminalSessionId = null,
                pid = null,
                rootPid = null,
                processGroupId = null,
                systemSessionId = null,
                lastMeaningfulOutput = "环境已切换，原环境运行已结束",
                lastError = null,
                nextActionUrl = null,
                x11Display = null,
                x11SocketPath = null,
                updatedAt = now
            )
            upsert(next)
            registeredRecipes[next.recipeId]?.let { recipe -> recordHistoryUpdate(recipe, next, now) }
            settled += next.instanceId
        }
        return settled
    }

    /**
     * 终止层已核验 owner 退出时，只收敛对应的“停止待确认”实例。
     * owner id 携带运行代次，因此旧代确认不会误停新代实例。
     */
    @Synchronized
    fun confirmRuntimeOwnersStopped(ownerIds: Collection<String>): Set<String> {
        val confirmedOwners = ownerIds.map(String::trim).filter(String::isNotBlank).toSet()
        if (confirmedOwners.isEmpty()) return emptySet()

        val now = System.currentTimeMillis()
        pruneRecentOwnerConfirmations(now)
        confirmedOwners.forEach { ownerId ->
            recentlyConfirmedRuntimeOwners[ownerId] = now + OWNER_CONFIRMATION_RETENTION_MS
        }
        return reconcileCleanupPendingOwners(recentConfirmedOwnerIds(now))
    }

    private fun reconcileCleanupPendingOwners(confirmedOwners: Set<String>): Set<String> {
        if (confirmedOwners.isEmpty()) return emptySet()

        val settledInstances = linkedSetOf<String>()
        runsByInstance.values.toList().forEach { run ->
            if (run.status != CardRunStatus.CleanupPending) return@forEach
            val knownOwners = (
                run.ownedRuntimeOwnerIds + listOfNotNull(run.runtimeRootOwnerId, run.runtimeOwnerId)
            ).map(String::trim).filter(String::isNotBlank).toSet()
            if (knownOwners.none(confirmedOwners::contains)) return@forEach

            val remainingOwners = knownOwners - confirmedOwners
            val now = System.currentTimeMillis()
            val next = if (remainingOwners.isEmpty()) {
                settledInstances += run.instanceId
                run.copy(
                    status = CardRunStatus.Stopped,
                    surface = CardRunSurface.Summary,
                    runtimeRootOwnerId = null,
                    runtimeOwnerId = null,
                    runtimeUnitId = null,
                    ownedRuntimeOwnerIds = emptyList(),
                    runId = null,
                    terminalSessionId = null,
                    pid = null,
                    rootPid = null,
                    processGroupId = null,
                    systemSessionId = null,
                    lastMeaningfulOutput = "已关闭",
                    lastError = null,
                    nextActionUrl = null,
                    x11Display = null,
                    x11SocketPath = null,
                    agentBinding = null,
                    updatedAt = now,
                )
            } else {
                run.copy(
                    runtimeRootOwnerId = run.runtimeRootOwnerId?.takeUnless(confirmedOwners::contains),
                    runtimeOwnerId = run.runtimeOwnerId?.takeUnless(confirmedOwners::contains),
                    ownedRuntimeOwnerIds = run.ownedRuntimeOwnerIds.filterNot(confirmedOwners::contains),
                    updatedAt = now,
                )
            }
            upsert(next)
            registeredRecipes[next.recipeId]?.let { recipe -> recordHistoryUpdate(recipe, next, now) }
        }
        return settledInstances
    }

    private fun recentConfirmedOwnerIds(now: Long): Set<String> {
        pruneRecentOwnerConfirmations(now)
        return recentlyConfirmedRuntimeOwners.keys.toSet()
    }

    private fun pruneRecentOwnerConfirmations(now: Long) {
        recentlyConfirmedRuntimeOwners.entries.removeAll { (_, expiresAt) -> expiresAt <= now }
        while (recentlyConfirmedRuntimeOwners.size > MAX_RECENT_OWNER_CONFIRMATIONS) {
            recentlyConfirmedRuntimeOwners.entries.firstOrNull()?.key?.let(recentlyConfirmedRuntimeOwners::remove)
        }
    }

    @Synchronized
    fun childrenOf(parentInstanceId: String, environmentId: String? = null): List<CardRunState> =
        _runs.value
            .filter { it.parentInstanceId == parentInstanceId }
            .filter { environmentId == null || it.environmentId == environmentId.normalizedEnvironmentId() }
            .sortedByDescending { it.updatedAt }

    @Synchronized
    fun historyForRecipe(
        recipeId: String,
        limit: Int = MAX_HISTORY_PER_RECIPE,
        environmentId: String? = null
    ): List<CardRunHistoryEntry> =
        historiesByRecipe[recipeId]
            ?.withoutStaleOpenEntries()
            ?.filter { environmentId == null || it.environmentId == environmentId.normalizedEnvironmentId() }
            ?.sortedByDescending { it.startedAt }
            ?.take(limit.coerceAtLeast(0))
            .orEmpty()

    @Synchronized
    fun removeRecipes(recipeIds: Collection<String>) {
        val ids = recipeIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        if (ids.isEmpty()) return
        val beforeSize = runsByInstance.size
        runsByInstance.entries.removeAll { (_, run) -> run.recipeId in ids || run.instanceId in ids }
        val historyChanged = ids.any { historiesByRecipe.remove(it) != null }
        if (runsByInstance.size == beforeSize && !historyChanged) return
        _runs.value = sortedRuns()
        schedulePersistRuns()
        if (historyChanged) schedulePersistHistory()
    }

    @Synchronized
    fun removeRunStatesForRecipes(
        recipeIds: Collection<String>,
        removeOpenHistory: Boolean = false,
        environmentId: String? = null
    ) {
        val ids = recipeIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        if (ids.isEmpty()) return
        val removedInstanceIds = runsByInstance.values
            .filter { it.recipeId in ids || it.instanceId in ids }
            .filter { environmentId == null || it.environmentId == environmentId.normalizedEnvironmentId() }
            .mapTo(mutableSetOf()) { it.instanceId }
        if (removedInstanceIds.isEmpty()) return
        runsByInstance.entries.removeAll { (_, run) -> run.instanceId in removedInstanceIds }
        var historyChanged = false
        if (removeOpenHistory) {
            ids.forEach { recipeId ->
                val entries = historiesByRecipe[recipeId] ?: return@forEach
                val before = entries.size
                entries.removeAll {
                    it.instanceId in removedInstanceIds &&
                        (environmentId == null || it.environmentId == environmentId.normalizedEnvironmentId()) &&
                        !it.isClosed()
                }
                if (entries.size != before) {
                    historyChanged = true
                    if (entries.isEmpty()) historiesByRecipe.remove(recipeId)
                }
            }
        }
        _runs.value = sortedRuns()
        schedulePersistRuns()
        if (historyChanged) schedulePersistHistory()
    }

    @Synchronized
    fun removeClosedRunStatesForRecipes(recipeIds: Collection<String>): Set<String> {
        val ids = recipeIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        if (ids.isEmpty()) return emptySet()
        val activeInstanceIds = runsByInstance.values
            .filter { (it.recipeId in ids || it.instanceId in ids) && !it.status.endsHistoryEntry() }
            .mapTo(mutableSetOf()) { it.instanceId }
        val closedRunInstanceIds = runsByInstance.values
            .filter { (it.recipeId in ids || it.instanceId in ids) && it.status.endsHistoryEntry() }
            .mapTo(linkedSetOf()) { it.instanceId }
        val closedHistoryInstanceIds = linkedSetOf<String>()
        var historyChanged = false
        ids.forEach { recipeId ->
            val entries = historiesByRecipe[recipeId] ?: return@forEach
            val before = entries.size
            entries.removeAll { entry ->
                if (entry.isClosed()) {
                    closedHistoryInstanceIds.add(entry.instanceId)
                    true
                } else {
                    false
                }
            }
            if (entries.size != before) {
                historyChanged = true
                if (entries.isEmpty()) historiesByRecipe.remove(recipeId)
            }
        }
        if (closedRunInstanceIds.isNotEmpty()) {
            runsByInstance.entries.removeAll { (_, run) -> run.instanceId in closedRunInstanceIds }
            _runs.value = sortedRuns()
            schedulePersistRuns()
        }
        if (historyChanged) schedulePersistHistory()
        return (closedRunInstanceIds + closedHistoryInstanceIds)
            .filterTo(linkedSetOf()) { it !in activeInstanceIds }
    }

    @Synchronized
    private fun upsert(run: CardRunState) {
        runsByInstance[run.instanceId] = run
        _runs.value = sortedRuns()
        schedulePersistRuns()
    }

    private fun sortedRuns(): List<CardRunState> =
        runsByInstance.values.sortedByDescending { it.updatedAt }

    private fun schedulePersistRuns() {
        if (prefs == null) return
        synchronized(persistScheduleLock) {
            if (persistScheduled) return
            persistScheduled = true
        }
        persistExecutor.schedule(
            {
                synchronized(persistScheduleLock) {
                    persistScheduled = false
                }
                persistRuns()
            },
            PERSIST_DEBOUNCE_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun persistRuns() {
        val store = prefs ?: return
        val payload = JSONArray()
        _runs.value
            .sortedByDescending { it.updatedAt }
            .filterNot { it.isTransientResourceRunState() }
            .take(MAX_STORED_RUNS)
            .forEach { payload.put(it.toJson()) }
        store.edit().putString(KEY_RUNS, payload.toString()).apply()
    }

    private fun schedulePersistHistory() {
        if (prefs == null) return
        synchronized(persistScheduleLock) {
            if (historyPersistScheduled) return
            historyPersistScheduled = true
        }
        persistExecutor.schedule(
            {
                synchronized(persistScheduleLock) {
                    historyPersistScheduled = false
                }
                persistHistory()
            },
            PERSIST_DEBOUNCE_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun persistHistory() {
        val store = prefs ?: return
        val entries = synchronized(this) {
            historiesByRecipe.values.flatMap { it.toList() }
        }
        val payload = JSONArray()
        entries
            .sortedByDescending { it.updatedAt }
            .take(MAX_STORED_HISTORY_ENTRIES)
            .forEach { payload.put(it.toJson()) }
        store.edit().putString(KEY_HISTORY, payload.toString()).apply()
    }

    private fun loadPersistedRuns(): List<CardRunState> {
        val raw = prefs?.getString(KEY_RUNS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toCardRunStateOrNull()?.let { add(it) }
                }
            }.sortedByDescending { it.updatedAt }
                .filterNot { it.isTransientResourceRunState() }
        }.getOrDefault(emptyList())
    }

    private fun loadPersistedHistory(): List<CardRunHistoryEntry> {
        val raw = prefs?.getString(KEY_HISTORY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toCardRunHistoryEntryOrNull()?.let { add(it.normalizedHistoryAfterProcessRestore()) }
                }
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    private fun CardRunState.normalizedAfterProcessRestore(): CardRunState {
        if (agentBinding?.isActive() == true || (agentBinding != null && status.shouldResetAfterProcessRestore())) {
            val now = System.currentTimeMillis()
            return copy(
                status = CardRunStatus.Failed,
                surface = CardRunSurface.Summary,
                runtimeRootOwnerId = null,
                runtimeOwnerId = null,
                runtimeUnitId = null,
                ownedRuntimeOwnerIds = emptyList(),
                runId = null,
                terminalSessionId = null,
                pid = null,
                rootPid = null,
                processGroupId = null,
                systemSessionId = null,
                lastMeaningfulOutput = AGENT_RESTORE_DISCONNECTED_MESSAGE,
                lastError = AGENT_RESTORE_DISCONNECTED_MESSAGE,
                nextActionUrl = null,
                x11Display = null,
                x11SocketPath = null,
                agentBinding = agentBinding.copy(
                    status = CardRunAgentConnectionStatus.Disconnected,
                    statusMessage = AGENT_RESTORE_DISCONNECTED_MESSAGE,
                    updatedAt = now
                )
            )
        }
        if (!status.shouldResetAfterProcessRestore()) return this
        return copy(
            status = CardRunStatus.Failed,
            surface = when {
                !shellReportText.isNullOrBlank() || !lastMeaningfulOutput.isNullOrBlank() -> CardRunSurface.Report
                else -> CardRunSurface.Summary
            },
            runId = null,
            terminalSessionId = null,
            pid = null,
            rootPid = null,
            processGroupId = null,
            systemSessionId = null,
            lastMeaningfulOutput = lastMeaningfulOutput ?: PROCESS_RESTORE_ABORTED_MESSAGE,
            lastError = lastError ?: PROCESS_RESTORE_ABORTED_MESSAGE,
            nextActionUrl = null,
            x11Display = null,
            x11SocketPath = null
        )
    }

    private fun CardRunState.shouldDropCurrentAfterProcessRestore(): Boolean {
        if (agentBinding != null && agentBinding.status != CardRunAgentConnectionStatus.Stopped) return false
        return status.endsHistoryEntry() ||
            status == CardRunStatus.CleanupPending ||
            status.shouldResetAfterProcessRestore() ||
            hasRunBinding() ||
            !parentInstanceId.isNullOrBlank() ||
            !nextActionUrl.isNullOrBlank()
    }

    private fun CardRunStatus.shouldResetAfterProcessRestore(): Boolean =
        this == CardRunStatus.Starting ||
            this == CardRunStatus.Running ||
            this == CardRunStatus.WaitingTerminal ||
            this == CardRunStatus.AlreadyRunning ||
            this == CardRunStatus.Opened ||
            this == CardRunStatus.Stopping

    private fun CardRunState.isResourceOperationRun(): Boolean =
        recipeId.startsWith("resource-") && (recipeId.endsWith("-install") || recipeId.endsWith("-uninstall"))

    private fun CardRunState.isInstallWizardRun(): Boolean =
        recipeId.startsWith("resource-install-wizard-")

    private fun CardRunState.isTransientResourceRunState(): Boolean =
        isInstallWizardRun() || isResourceOperationRun()

    private fun CardRunState.skipsHistory(): Boolean =
        recipeId.startsWith("resource-install-wizard-") ||
            recipeId.startsWith("tmp-") ||
            (!parentInstanceId.isNullOrBlank() && ownerKind in WINDOW_OWNER_KINDS)

    private fun recordHistoryStart(recipe: KiteRecipe, state: CardRunState, now: Long = state.createdAt) {
        if (state.skipsHistory()) return
        val entry = CardRunHistoryEntry(
            historyId = "${state.instanceId}@$now",
            recipeId = recipe.id,
            recipeName = recipe.name,
            instanceId = state.instanceId,
            ownerKind = state.ownerKind,
            environmentId = state.environmentId,
            status = state.status,
            currentStepIndex = state.currentStepIndex,
            stepCount = state.stepCount.takeIf { it > 0 } ?: recipe.steps.size,
            startedAt = now,
            endedAt = if (state.status.endsHistoryEntry()) now else null,
            updatedAt = now,
            summary = state.lastMeaningfulOutput.orEmpty().take(MAX_HISTORY_SUMMARY_CHARS),
            error = state.lastError.orEmpty().take(MAX_HISTORY_SUMMARY_CHARS),
            shellReportText = state.shellReportText.orEmpty().takeLast(MAX_HISTORY_REPORT_CHARS),
            steps = recipe.toHistorySteps(state)
        )
        val entries = historiesByRecipe.getOrPut(recipe.id) { mutableListOf() }
        entries.removeAll {
            it.instanceId == state.instanceId &&
                it.environmentId == state.environmentId &&
                !it.isClosed()
        }
        entries.removeAll { it.historyId == entry.historyId }
        entries.add(0, entry)
        trimHistory(recipe.id)
        schedulePersistHistory()
    }

    private fun recordHistoryUpdate(recipe: KiteRecipe, state: CardRunState, now: Long = state.updatedAt) {
        if (state.skipsHistory()) return
        val entries = historiesByRecipe.getOrPut(recipe.id) { mutableListOf() }
        val current = entries.firstOrNull {
            it.instanceId == state.instanceId &&
                it.environmentId == state.environmentId &&
                !it.isClosed()
        } ?: entries.firstOrNull { it.environmentId == state.environmentId }
            ?: run {
                recordHistoryStart(recipe, state, now)
                return
            }
        if (current.isClosed() && (state.status.startsHistoryEntry() || state.status.isImmediateHistoryEnd())) {
            recordHistoryStart(recipe, state, now)
            return
        }
        val nextStepCount = state.stepCount.takeIf { it > 0 } ?: recipe.steps.size
        val reportTargetNeedsSnapshot = state.shellReportText.orEmpty().isNotBlank() &&
            recipe.shellReportTargetStepIndex(state.currentStepIndex)?.let { targetIndex ->
                current.steps.firstOrNull { it.index == targetIndex }?.reportText.isNullOrBlank()
            } == true
        if (!state.status.endsHistoryEntry() &&
            current.status == state.status &&
            current.currentStepIndex == state.currentStepIndex &&
            current.stepCount == nextStepCount &&
            state.nextActionUrl.isNullOrBlank() &&
            state.terminalSessionId.isNullOrBlank() &&
            state.lastError.isNullOrBlank() &&
            !reportTargetNeedsSnapshot
        ) {
            return
        }
        val nextSteps = recipe.toHistorySteps(
            state = state,
            previousSteps = current.steps,
            fallbackReportText = current.shellReportText
        )
        val next = current.copy(
            recipeName = recipe.name,
            ownerKind = state.ownerKind,
            status = state.status,
            currentStepIndex = state.currentStepIndex,
            stepCount = nextStepCount,
            endedAt = if (state.status.endsHistoryEntry()) now else current.endedAt,
            updatedAt = now,
            summary = state.lastMeaningfulOutput.orEmpty().take(MAX_HISTORY_SUMMARY_CHARS),
            error = state.lastError.orEmpty().take(MAX_HISTORY_SUMMARY_CHARS),
            shellReportText = state.shellReportText.orEmpty()
                .ifBlank { current.shellReportText }
                .takeLast(MAX_HISTORY_REPORT_CHARS),
            steps = nextSteps
        )
        if (!current.meaningfullyDiffersFrom(next)) return
        val index = entries.indexOfFirst { it.historyId == current.historyId }
        if (index >= 0) entries[index] = next else entries.add(0, next)
        trimHistory(recipe.id)
        schedulePersistHistory()
    }

    private fun KiteRecipe.toHistorySteps(
        state: CardRunState,
        previousSteps: List<CardRunHistoryStep> = emptyList(),
        fallbackReportText: String = ""
    ): List<CardRunHistoryStep> {
        val previousReportsByIndex = previousSteps.associateBy { it.index }
        val shellReport = state.shellReportText.orEmpty().takeLast(MAX_HISTORY_REPORT_CHARS)
        val reportTargetIndex = shellReport
            .takeIf { it.isNotBlank() }
            ?.let { shellReportTargetStepIndex(state.currentStepIndex) }
        val rows = steps.mapIndexed { index, step ->
            val previousReport = previousReportsByIndex[index]?.reportText.orEmpty()
            val reportText = if (index == reportTargetIndex) {
                shellReport.ifBlank { previousReport }
            } else {
                previousReport
            }.takeLast(MAX_HISTORY_REPORT_CHARS)
            step.toHistoryStep(
                index = index,
                resolvedUrl = if (index == state.currentStepIndex) state.nextActionUrl else null,
                defaultUrl = defaultUrl,
                reportText = reportText
            )
        }.toMutableList()
        val nextUrl = state.nextActionUrl?.takeIf { it.isNotBlank() }
        val persistedNextUrl = nextUrl?.redactedUrlForPersistence()
        if (persistedNextUrl != null && rows.none { it.type == KiteRecipe.STEP_OPEN_WEB && it.detail == persistedNextUrl }) {
            rows.add(
                CardRunHistoryStep(
                    index = rows.size,
                    type = KiteRecipe.STEP_OPEN_WEB,
                    label = "网页",
                    detail = persistedNextUrl.take(MAX_HISTORY_DETAIL_CHARS)
                )
            )
        }
        return rows.attachLegacyReportIfNeeded(fallbackReportText, state.currentStepIndex)
    }

    private fun KiteRecipe.shellReportTargetStepIndex(currentStepIndex: Int): Int? {
        if (steps.isEmpty()) return null
        val cappedIndex = when {
            currentStepIndex < 0 -> 0
            currentStepIndex > steps.lastIndex -> steps.lastIndex
            else -> currentStepIndex
        }
        return (cappedIndex downTo 0).firstOrNull { steps[it].type == KiteRecipe.STEP_SHELL }
    }

    private fun com.kite.app.recipe.KiteRecipeStep.toHistoryStep(
        index: Int,
        resolvedUrl: String?,
        defaultUrl: String,
        reportText: String
    ): CardRunHistoryStep {
        val detail = when (type) {
            KiteRecipe.STEP_SHELL -> cmd ?: text
            KiteRecipe.STEP_TERMINAL -> text ?: cmd
            KiteRecipe.STEP_OPEN_WEB -> resolvedUrl ?: url ?: defaultUrl
            KiteRecipe.STEP_X11 -> cmd ?: text
            KiteRecipe.STEP_ANDROID_ACTION -> action
            else -> cmd ?: text ?: url ?: action
        }.orEmpty().let { value ->
            if (type == KiteRecipe.STEP_OPEN_WEB) value.redactedUrlForPersistence() else value
        }.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .replace(Regex("\\s+"), " ")
            .take(MAX_HISTORY_DETAIL_CHARS)
        return CardRunHistoryStep(
            index = index,
            type = type,
            label = when (type) {
                KiteRecipe.STEP_SHELL -> "SH"
                KiteRecipe.STEP_TERMINAL -> "终端"
                KiteRecipe.STEP_OPEN_WEB -> "网页"
                KiteRecipe.STEP_X11 -> "X11"
                KiteRecipe.STEP_ANDROID_ACTION -> "本机"
                else -> type.ifBlank { "步骤" }
            },
            detail = detail,
            reportText = reportText.takeLast(MAX_HISTORY_REPORT_CHARS)
        )
    }

    private fun List<CardRunHistoryStep>.attachLegacyReportIfNeeded(
        reportText: String,
        currentStepIndex: Int
    ): List<CardRunHistoryStep> {
        val report = reportText.takeLast(MAX_HISTORY_REPORT_CHARS).takeIf { it.isNotBlank() } ?: return this
        if (any { it.reportText.isNotBlank() }) return this
        val cappedPosition = when {
            isEmpty() -> return this
            currentStepIndex < 0 -> 0
            currentStepIndex > lastIndex -> lastIndex
            else -> currentStepIndex
        }
        val targetPosition = (cappedPosition downTo 0).firstOrNull { getOrNull(it)?.type == KiteRecipe.STEP_SHELL }
            ?: indexOfLast { it.type == KiteRecipe.STEP_SHELL }
        if (targetPosition < 0) return this
        return mapIndexed { position, step ->
            if (position == targetPosition) step.copy(reportText = report) else step
        }
    }

    private fun CardRunHistoryEntry.meaningfullyDiffersFrom(next: CardRunHistoryEntry): Boolean =
        status != next.status ||
            currentStepIndex != next.currentStepIndex ||
            stepCount != next.stepCount ||
            endedAt != next.endedAt ||
            error != next.error ||
            shellReportText != next.shellReportText && (next.status.endsHistoryEntry() || currentStepIndex != next.currentStepIndex) ||
            steps != next.steps ||
            ((next.status.endsHistoryEntry() || next.error.isNotBlank()) && summary != next.summary)

    private fun trimAllHistory() {
        historiesByRecipe.keys.toList().forEach { trimHistory(it) }
    }

    private fun trimHistory(recipeId: String) {
        val entries = historiesByRecipe[recipeId] ?: return
        val trimmed = entries
            .withoutStaleOpenEntries()
            .sortedByDescending { it.startedAt }
            .take(MAX_HISTORY_PER_RECIPE)
        entries.clear()
        entries.addAll(trimmed)
    }

    private fun List<CardRunHistoryEntry>.withoutStaleOpenEntries(): List<CardRunHistoryEntry> =
        filterNot { candidate ->
            // Older open entries are stale once any newer lifecycle exists for the same recipe.
            !candidate.isClosed() && any { other ->
                other.updatedAt > candidate.updatedAt ||
                    other.startedAt > candidate.startedAt
            }
        }

    private fun CardRunStatus.startsHistoryEntry(): Boolean =
        this == CardRunStatus.Starting ||
            this == CardRunStatus.Running ||
            this == CardRunStatus.WaitingTerminal ||
            this == CardRunStatus.AlreadyRunning ||
            this == CardRunStatus.Opened

    private fun CardRunStatus.endsHistoryEntry(): Boolean =
        this == CardRunStatus.Completed ||
            this == CardRunStatus.Failed ||
            this == CardRunStatus.Stopped ||
            this == CardRunStatus.BridgeUnavailable ||
            this == CardRunStatus.Unknown

    private fun CardRunStatus.isImmediateHistoryEnd(): Boolean =
        this == CardRunStatus.Failed ||
            this == CardRunStatus.BridgeUnavailable ||
            this == CardRunStatus.Stopped

    private fun CardRunState.shouldIgnoreStoppedRuntimeWrite(
        status: CardRunStatus,
        runId: String?,
        terminalSessionId: String?,
        x11Display: String?,
        x11SocketPath: String?,
        pid: String?,
        rootPid: String?,
        processGroupId: String?,
        systemSessionId: String?,
        lastMeaningfulOutput: String?,
        lastError: String?,
        shellReportText: String?
    ): Boolean {
        if (this.status != CardRunStatus.Stopped) return false
        if (status == CardRunStatus.Stopped || status == CardRunStatus.Starting) return false
        val carriesRuntimePayload = listOf(
            runId,
            terminalSessionId,
            x11Display,
            x11SocketPath,
            pid,
            rootPid,
            processGroupId,
            systemSessionId,
            lastMeaningfulOutput,
            lastError,
            shellReportText
        ).any { !it.isNullOrBlank() }
        if (status == CardRunStatus.Opened && !carriesRuntimePayload) return false
        return status == CardRunStatus.Running ||
            status == CardRunStatus.WaitingTerminal ||
            status == CardRunStatus.AlreadyRunning ||
            status == CardRunStatus.Opened ||
            status == CardRunStatus.Completed ||
            status == CardRunStatus.Failed ||
            status == CardRunStatus.CleanupPending ||
            status == CardRunStatus.BridgeUnavailable
    }

    private fun CardRunHistoryEntry.normalizedHistoryAfterProcessRestore(): CardRunHistoryEntry =
        if (status == CardRunStatus.CleanupPending && endedAt == null) {
            copy(endedAt = updatedAt)
        } else if (!status.shouldResetAfterProcessRestore() || endedAt != null) {
            this
        } else {
            copy(
                status = CardRunStatus.Failed,
                endedAt = updatedAt,
                summary = summary.ifBlank { PROCESS_RESTORE_ABORTED_MESSAGE },
                error = error.ifBlank { PROCESS_RESTORE_ABORTED_MESSAGE }
            )
        }

    private fun CardRunState.toJson(): JSONObject =
        JSONObject()
            .put("instanceId", instanceId)
            .put("cardInstanceId", cardInstanceId)
            .put("recipeId", recipeId)
            .put("recipeName", recipeName)
            .put("environmentId", environmentId)
            .put("parentInstanceId", parentInstanceId.orEmpty())
            .put("ownerKind", ownerKind)
            .put("stepId", stepId.orEmpty())
            .put("status", status.name)
            .put("surface", surface.name)
            .put("selectedWindowId", selectedWindowId.orEmpty())
            .put("currentStepIndex", currentStepIndex)
            .put("stepCount", stepCount)
            .put("runtimeRootOwnerId", runtimeRootOwnerId.orEmpty())
            .put("runtimeOwnerId", runtimeOwnerId.orEmpty())
            .put("runtimeUnitId", runtimeUnitId.orEmpty())
            .put("ownedRuntimeOwnerIds", JSONArray().apply { ownedRuntimeOwnerIds.forEach(::put) })
            .put("runId", runId.orEmpty())
            .put("terminalSessionId", terminalSessionId.orEmpty())
            .put("pid", pid.orEmpty())
            .put("rootPid", rootPid.orEmpty())
            .put("processGroupId", processGroupId.orEmpty())
            .put("systemSessionId", systemSessionId.orEmpty())
            .put("runtimeLane", runtimeLane.orEmpty())
            .put("runtimeFallbackReason", runtimeFallbackReason.orEmpty())
            .put("lastMeaningfulOutput", lastMeaningfulOutput.orEmpty().take(MAX_STORED_TEXT_CHARS))
            .put("lastError", lastError.orEmpty().take(MAX_STORED_TEXT_CHARS))
            .put("shellReportText", shellReportText.orEmpty().takeLast(MAX_STORED_TEXT_CHARS))
            .put("nextActionUrl", nextActionUrl.orEmpty().redactedUrlForPersistence())
            .put("x11Display", x11Display.orEmpty())
            .put("x11SocketPath", x11SocketPath.orEmpty())
            .put("agentId", agentId.orEmpty())
            .put("agentBinding", agentBinding?.toJson() ?: JSONObject.NULL)
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)

    private fun CardRunAgentBinding.toJson(): JSONObject =
        JSONObject()
            .put("providerId", providerId)
            .put("sessionId", sessionId.orEmpty())
            .put("status", status.name)
            .put("statusMessage", statusMessage.orEmpty().take(MAX_AGENT_STATUS_MESSAGE_CHARS))
            .put("updatedAt", updatedAt)

    private fun CardRunHistoryEntry.toJson(): JSONObject =
        JSONObject()
            .put("historyId", historyId)
            .put("recipeId", recipeId)
            .put("recipeName", recipeName)
            .put("environmentId", environmentId)
            .put("instanceId", instanceId)
            .put("ownerKind", ownerKind)
            .put("status", status.name)
            .put("currentStepIndex", currentStepIndex)
            .put("stepCount", stepCount)
            .put("startedAt", startedAt)
            .put("endedAt", endedAt ?: 0L)
            .put("updatedAt", updatedAt)
            .put("summary", summary.take(MAX_HISTORY_SUMMARY_CHARS))
            .put("error", error.take(MAX_HISTORY_SUMMARY_CHARS))
            .put("shellReportText", shellReportText.takeLast(MAX_HISTORY_REPORT_CHARS))
            .put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })

    private fun CardRunHistoryStep.toJson(): JSONObject =
        JSONObject()
            .put("index", index)
            .put("type", type)
            .put("label", label)
            .put("detail", detail.redactedUrlForPersistence().take(MAX_HISTORY_DETAIL_CHARS))
            .put("reportText", reportText.takeLast(MAX_HISTORY_REPORT_CHARS))

    private fun JSONObject.toCardRunStateOrNull(): CardRunState? {
        val instanceId = optString("instanceId").takeIf { it.isNotBlank() }
            ?: optString("cardInstanceId").takeIf { it.isNotBlank() }
            ?: return null
        val recipeId = optString("recipeId").takeIf { it.isNotBlank() } ?: return null
        val status = enumValueOrDefault(optString("status"), CardRunStatus.Unknown)
        val surface = enumValueOrDefault(optString("surface"), CardRunSurface.Summary)
        val updatedAt = optLong("updatedAt").takeIf { it > 0L } ?: System.currentTimeMillis()
        val createdAt = optLong("createdAt").takeIf { it > 0L } ?: updatedAt
        return CardRunState(
            instanceId = instanceId,
            recipeId = recipeId,
            recipeName = optString("recipeName"),
            environmentId = optString("environmentId").normalizedEnvironmentId(),
            parentInstanceId = optString("parentInstanceId").takeIf { it.isNotBlank() },
            ownerKind = optString("ownerKind").ifBlank { CardRunState.OWNER_KIND_CARD },
            stepId = optString("stepId").takeIf { it.isNotBlank() },
            status = status,
            surface = surface,
            selectedWindowId = optString("selectedWindowId").takeIf { it.isNotBlank() },
            currentStepIndex = optInt("currentStepIndex", -1),
            stepCount = optInt("stepCount", 0),
            runtimeRootOwnerId = optString("runtimeRootOwnerId").takeIf { it.isNotBlank() },
            runtimeOwnerId = optString("runtimeOwnerId").takeIf { it.isNotBlank() },
            runtimeUnitId = optString("runtimeUnitId").takeIf { it.isNotBlank() },
            ownedRuntimeOwnerIds = optJSONArray("ownedRuntimeOwnerIds")
                ?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }
                .orEmpty(),
            runId = optString("runId").takeIf { it.isNotBlank() },
            terminalSessionId = optString("terminalSessionId").takeIf { it.isNotBlank() },
            pid = optString("pid").takeIf { it.isNotBlank() },
            rootPid = optString("rootPid").takeIf { it.isNotBlank() },
            processGroupId = optString("processGroupId").takeIf { it.isNotBlank() },
            systemSessionId = optString("systemSessionId").takeIf { it.isNotBlank() },
            runtimeLane = optString("runtimeLane").takeIf { it.isNotBlank() },
            runtimeFallbackReason = optString("runtimeFallbackReason").takeIf { it.isNotBlank() },
            lastMeaningfulOutput = optString("lastMeaningfulOutput").takeIf { it.isNotBlank() },
            lastError = optString("lastError").takeIf { it.isNotBlank() },
            shellReportText = optString("shellReportText").takeIf { it.isNotBlank() },
            nextActionUrl = optString("nextActionUrl").takeIf { it.isNotBlank() },
            x11Display = optString("x11Display").takeIf { it.isNotBlank() },
            x11SocketPath = optString("x11SocketPath").takeIf { it.isNotBlank() },
            agentId = optString("agentId").trim().takeIf(String::isNotBlank),
            agentBinding = optJSONObject("agentBinding")?.toCardRunAgentBindingOrNull(),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun JSONObject.toCardRunAgentBindingOrNull(): CardRunAgentBinding? {
        val providerId = optString("providerId").trim().takeIf(String::isNotBlank) ?: return null
        return CardRunAgentBinding(
            providerId = providerId,
            sessionId = optString("sessionId").trim().takeIf(String::isNotBlank),
            status = enumValueOrDefault(optString("status"), CardRunAgentConnectionStatus.Disconnected),
            statusMessage = optString("statusMessage").trim().takeIf(String::isNotBlank),
            updatedAt = optLong("updatedAt").takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    private fun JSONObject.toCardRunHistoryEntryOrNull(): CardRunHistoryEntry? {
        val recipeId = optString("recipeId").takeIf { it.isNotBlank() } ?: return null
        val startedAt = optLong("startedAt").takeIf { it > 0L } ?: return null
        val instanceId = optString("instanceId").ifBlank { recipeId }
        val historyId = optString("historyId").ifBlank { "$instanceId@$startedAt" }
        val status = enumValueOrDefault(optString("status"), CardRunStatus.Unknown)
        val stepsJson = optJSONArray("steps") ?: JSONArray()
        val legacyReportText = optString("shellReportText").takeLast(MAX_HISTORY_REPORT_CHARS)
        val steps = buildList {
            for (index in 0 until stepsJson.length()) {
                stepsJson.optJSONObject(index)?.toCardRunHistoryStepOrNull()?.let { add(it) }
            }
        }.attachLegacyReportIfNeeded(legacyReportText, optInt("currentStepIndex", -1))
        return CardRunHistoryEntry(
            historyId = historyId,
            recipeId = recipeId,
            recipeName = optString("recipeName"),
            environmentId = optString("environmentId").normalizedEnvironmentId(),
            instanceId = instanceId,
            ownerKind = optString("ownerKind").ifBlank { CardRunState.OWNER_KIND_CARD },
            status = status,
            currentStepIndex = optInt("currentStepIndex", -1),
            stepCount = optInt("stepCount", steps.size),
            startedAt = startedAt,
            endedAt = optLong("endedAt").takeIf { it > 0L },
            updatedAt = optLong("updatedAt").takeIf { it > 0L } ?: startedAt,
            summary = optString("summary").take(MAX_HISTORY_SUMMARY_CHARS),
            error = optString("error").take(MAX_HISTORY_SUMMARY_CHARS),
            shellReportText = legacyReportText,
            steps = steps
        )
    }

    private fun JSONObject.toCardRunHistoryStepOrNull(): CardRunHistoryStep? =
        CardRunHistoryStep(
            index = optInt("index", 0),
            type = optString("type"),
            label = optString("label").ifBlank { optString("type").ifBlank { "步骤" } },
            detail = optString("detail").redactedUrlForPersistence().take(MAX_HISTORY_DETAIL_CHARS),
            reportText = optString("reportText").takeLast(MAX_HISTORY_REPORT_CHARS)
        )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(default)

    private fun String?.normalizedAgentId(): String? =
        this?.trim()?.takeIf(String::isNotBlank)

    private fun String.normalizedEnvironmentId(): String =
        trim().ifBlank { CardRunState.DEFAULT_ENVIRONMENT_ID }

    private fun CardRunState.requiresEnvironmentStopSettlement(): Boolean =
        isBusy() || isActive() || isInterruptible() || hasRunBinding() || status == CardRunStatus.CleanupPending

    private fun String.redactedUrlForPersistence(): String =
        if (startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)) {
            BrowserHandoffPolicy.redactedUrlForDiagnostics(this)
        } else {
            this
        }

    private const val PREFS_NAME = "kite_card_run_store"
    private const val KEY_RUNS = "runs_v1"
    private const val KEY_HISTORY = "history_v1"
    private const val MAX_STORED_RUNS = 80
    private const val MAX_STORED_TEXT_CHARS = 4000
    private const val MAX_AGENT_STATUS_MESSAGE_CHARS = 500
    private const val MAX_HISTORY_PER_RECIPE = 5
    private const val MAX_STORED_HISTORY_ENTRIES = 200
    private const val MAX_HISTORY_DETAIL_CHARS = 260
    private const val MAX_HISTORY_SUMMARY_CHARS = 500
    private const val MAX_HISTORY_REPORT_CHARS = 4000
    private const val PROCESS_RESTORE_ABORTED_MESSAGE = "Kite 重新启动，上次运行未确认正常结束"
    private const val AGENT_RESTORE_DISCONNECTED_MESSAGE = "Kite 重新启动，需要重新连接 Agent 会话"
    private const val OWNER_CONFIRMATION_RETENTION_MS = 60_000L
    private const val MAX_RECENT_OWNER_CONFIRMATIONS = 512
    private const val PERSIST_DEBOUNCE_MS = 300L
    private val WINDOW_OWNER_KINDS = setOf(
        CardRunState.OWNER_KIND_TERMINAL,
        CardRunState.OWNER_KIND_WEB,
        CardRunState.OWNER_KIND_STEP_REPLAY
    )

}
