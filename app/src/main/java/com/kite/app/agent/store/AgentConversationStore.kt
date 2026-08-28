package com.kite.app.agent.store

import com.kite.app.agent.contract.AgentCommand
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentContent
import com.kite.app.agent.contract.AgentMessageRole
import com.kite.app.agent.contract.AgentPermissionRequest
import com.kite.app.agent.contract.AgentPlanEntry
import com.kite.app.agent.contract.AgentSessionEvent
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.contract.AgentToolCall
import com.kite.app.agent.contract.AgentToolCallPatch
import com.kite.app.agent.contract.AgentToolContent
import com.kite.app.agent.sdk.skill.AgentSkillPromptComposer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class AgentConversationKey(
    val providerId: String,
    val sessionId: String
)

data class AgentConversationSnapshot(
    val key: AgentConversationKey,
    val instanceId: String,
    val phase: AgentSessionPhase,
    val timeline: List<AgentConversationItem> = emptyList(),
    val turns: List<AgentConversationTurn> = emptyList(),
    val plan: List<AgentPlanEntry> = emptyList(),
    val commands: List<AgentCommand> = emptyList(),
    val configuration: List<AgentConfigOption> = emptyList(),
    val currentModeId: String? = null,
    val pendingPermission: AgentPermissionRequest? = null,
    val title: String? = null,
    val updatedAt: String? = null,
    val usage: AgentSessionEvent.UsageChanged? = null,
    val lastError: String? = null,
    val extensions: List<AgentSessionEvent.Extension> = emptyList(),
    val history: AgentConversationHistory = AgentConversationHistory(),
    val revision: Long = 0L
)

enum class AgentConversationTurnState {
    Running,
    Completed,
    Failed,
    Cancelled,
    Historical
}

data class AgentConversationTurn(
    val ordinal: Long,
    val state: AgentConversationTurnState,
    val startedAtMillis: Long? = null,
    val endedAtMillis: Long? = null,
    val errorMessage: String? = null,
) {
    val durationMillis: Long?
        get() = if (startedAtMillis != null && endedAtMillis != null) {
            (endedAtMillis - startedAtMillis).coerceAtLeast(0L)
        } else {
            null
        }
}

enum class AgentConversationHistoryStatus {
    Live,
    Loading,
    Loaded,
    Unavailable
}

data class AgentConversationHistory(
    val status: AgentConversationHistoryStatus = AgentConversationHistoryStatus.Live,
    val visibleItems: Int = 0,
    val totalItems: Int = 0,
    val hasEarlierItems: Boolean = false,
    val truncatedItems: Int = 0
)

sealed interface AgentConversationItem {
    val id: String
    val turnOrdinal: Long

    data class Message(
        override val id: String,
        val role: AgentMessageRole,
        val messageId: String? = null,
        val content: List<AgentContent>,
        override val turnOrdinal: Long = 0L,
    ) : AgentConversationItem

    data class Tool(
        override val id: String,
        val call: AgentToolCall,
        override val turnOrdinal: Long = 0L,
    ) : AgentConversationItem

    data class Plan(
        override val id: String,
        val entries: List<AgentPlanEntry>,
        override val turnOrdinal: Long = 0L,
    ) : AgentConversationItem
}

/**
 * 进程内的高频会话投影拥有者。
 *
 * 它不复制 CardRunStore 的 PID、运行状态或进程所有权，也不把消息写进 SharedPreferences。
 * 页面重建直接重新订阅这里；进程重建后由 CardRunStore 的 Disconnected 绑定和 provider 的
 * load/resume 决定恢复，不能把旧内存内容伪装成已连接会话。
 */
object AgentConversationStore {
    private val mutableConversations = linkedMapOf<AgentConversationKey, MutableConversation>()
    private val replayConversations = linkedMapOf<AgentConversationKey, MutableConversation>()
    private val pendingPublications = linkedSetOf<AgentConversationKey>()
    private val publishExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "KiteAgentConversationPublish").apply { isDaemon = true }
    }
    private val _conversations = MutableStateFlow<Map<AgentConversationKey, AgentConversationSnapshot>>(emptyMap())
    private var publishScheduled = false
    private var publicationCount = 0L
    private var nowMillis: () -> Long = System::currentTimeMillis

    val conversations: StateFlow<Map<AgentConversationKey, AgentConversationSnapshot>> = _conversations

    fun observe(key: AgentConversationKey): Flow<AgentConversationSnapshot?> =
        conversations.map { it[key] }.distinctUntilChanged()

    @Synchronized
    fun bind(
        instanceId: String,
        key: AgentConversationKey,
        phase: AgentSessionPhase = AgentSessionPhase.Preparing
    ): AgentConversationSnapshot {
        val existing = mutableConversations[key]
        val conversation = if (existing == null || existing.instanceId != instanceId) {
            MutableConversation(key = key, instanceId = instanceId, phase = phase).also {
                mutableConversations[key] = it
            }
        } else {
            existing.apply {
                this.phase = phase
                revision++
            }
        }
        publishNow(key)
        return conversation.freeze()
    }

    @Synchronized
    fun applyEvent(key: AgentConversationKey, event: AgentSessionEvent): Boolean {
        val replay = replayConversations[key]
        val conversation = replay ?: mutableConversations[key] ?: return false
        val highFrequency = conversation.apply(event)
        if (replay == null) {
            if (highFrequency) schedulePublish(key) else publishNow(key)
        }
        return true
    }

    /**
     * `session/load` 会在请求返回前重放完整历史。回放先进入影子会话，避免半份历史和数百次
     * 中间发布进入页面；成功后再原子替换当前投影，失败则保留原投影。
     */
    @Synchronized
    fun beginHistoryReplay(instanceId: String, key: AgentConversationKey): AgentConversationSnapshot {
        val current = mutableConversations[key]
            ?: MutableConversation(key, instanceId, AgentSessionPhase.Preparing).also {
                mutableConversations[key] = it
            }
        current.historyStatus = AgentConversationHistoryStatus.Loading
        current.revision++
        replayConversations[key] = MutableConversation(
            key,
            instanceId,
            AgentSessionPhase.Preparing,
            recordsLiveTiming = false,
        ).apply {
            historyStatus = AgentConversationHistoryStatus.Loading
        }
        publishNow(key)
        return current.freeze()
    }

    @Synchronized
    fun completeHistoryReplay(key: AgentConversationKey): AgentConversationSnapshot? {
        val replay = replayConversations.remove(key) ?: return mutableConversations[key]?.freeze()
        replay.finishHistoryReplay()
        replay.historyStatus = AgentConversationHistoryStatus.Loaded
        replay.visibleTimelineItems = minOf(INITIAL_VISIBLE_TIMELINE_ITEMS, replay.timeline.size)
        replay.revision++
        mutableConversations[key] = replay
        publishNow(key)
        return replay.freeze()
    }

    @Synchronized
    fun abortHistoryReplay(key: AgentConversationKey): AgentConversationSnapshot? {
        replayConversations.remove(key)
        val current = mutableConversations[key] ?: return null
        current.historyStatus = AgentConversationHistoryStatus.Live
        current.revision++
        publishNow(key)
        return current.freeze()
    }

    @Synchronized
    fun markHistoryUnavailable(key: AgentConversationKey): AgentConversationSnapshot? {
        val current = mutableConversations[key] ?: return null
        current.historyStatus = AgentConversationHistoryStatus.Unavailable
        current.revision++
        publishNow(key)
        return current.freeze()
    }

    @Synchronized
    fun revealEarlier(key: AgentConversationKey): AgentConversationSnapshot? {
        val current = mutableConversations[key] ?: return null
        if (current.visibleTimelineItems >= current.timeline.size) return current.freeze()
        current.visibleTimelineItems = minOf(
            current.timeline.size,
            current.visibleTimelineItems + VISIBLE_TIMELINE_PAGE_ITEMS
        )
        current.revision++
        publishNow(key)
        return current.freeze()
    }

    @Synchronized
    fun requestPermission(
        key: AgentConversationKey,
        request: AgentPermissionRequest
    ): AgentConversationSnapshot? {
        val conversation = mutableConversations[key] ?: return null
        conversation.phaseBeforePermission = conversation.phase
        conversation.phase = AgentSessionPhase.WaitingPermission
        conversation.pendingPermission = request
        conversation.revision++
        publishNow(key)
        return conversation.freeze()
    }

    @Synchronized
    fun resolvePermission(key: AgentConversationKey): AgentConversationSnapshot? {
        val conversation = mutableConversations[key] ?: return null
        conversation.pendingPermission = null
        conversation.phase = conversation.phaseBeforePermission
            ?.takeUnless { it == AgentSessionPhase.WaitingPermission }
            ?: AgentSessionPhase.Ready
        conversation.phaseBeforePermission = null
        conversation.revision++
        publishNow(key)
        return conversation.freeze()
    }

    @Synchronized
    fun fail(key: AgentConversationKey, message: String): AgentConversationSnapshot? {
        val conversation = mutableConversations[key] ?: return null
        conversation.apply(AgentSessionEvent.LifecycleChanged(AgentSessionPhase.Failed, message))
        publishNow(key)
        return conversation.freeze()
    }

    @Synchronized
    fun snapshot(key: AgentConversationKey): AgentConversationSnapshot? =
        mutableConversations[key]?.freeze()

    /**
     * 同一个 Kite 会话因底层配置切换而获得新原生 session id 时，迁移完整可见时间线。
     * 新 session 已发布的配置事实会被吸收，但不会用一份空投影覆盖用户已有对话。
     */
    @Synchronized
    fun rekey(
        instanceId: String,
        fromKey: AgentConversationKey,
        toKey: AgentConversationKey,
        phase: AgentSessionPhase = AgentSessionPhase.Ready,
    ): AgentConversationSnapshot {
        if (fromKey == toKey) return bind(instanceId, toKey, phase)
        val source = mutableConversations[fromKey]?.takeIf { it.instanceId == instanceId }
            ?: return bind(instanceId, toKey, phase)
        val target = mutableConversations[toKey]?.takeIf { it.instanceId == instanceId }

        mutableConversations.remove(fromKey)
        mutableConversations.remove(toKey)
        replayConversations.remove(fromKey)
        replayConversations.remove(toKey)
        pendingPublications.remove(fromKey)
        pendingPublications.remove(toKey)

        source.key = toKey
        target?.let(source::adoptSessionFacts)
        source.phase = phase
        source.revision++
        mutableConversations[toKey] = source

        val snapshot = source.freeze()
        _conversations.value = _conversations.value.toMutableMap().apply {
            remove(fromKey)
            put(toKey, snapshot)
        }
        publicationCount++
        return snapshot
    }

    /** 首发失败时撤销 Kite 本地乐观消息；仅供明确放弃本地草稿的调用方使用。 */
    @Synchronized
    fun discardLocalMessage(key: AgentConversationKey, messageId: String): Boolean {
        val conversation = mutableConversations[key] ?: return false
        if (!conversation.discardMessage(messageId)) return false
        publishNow(key)
        return true
    }

    /**
     * 首发失败仍保留用户已经看见的消息，只结束这一轮并恢复输入能力。失败不是连接事实，
     * 因此不能把整个 Agent 会话推进到 Failed。
     */
    @Synchronized
    fun failLocalTurn(key: AgentConversationKey, messageId: String, message: String): Boolean {
        val conversation = mutableConversations[key] ?: return false
        if (!conversation.failLocalTurn(messageId, message)) return false
        publishNow(key)
        return true
    }

    /** 相同草稿重试时复用原来的右侧消息，避免产生失败和重发两条重复气泡。 */
    @Synchronized
    fun retryLocalTurn(key: AgentConversationKey, messageId: String): Boolean {
        val conversation = mutableConversations[key] ?: return false
        if (!conversation.retryLocalTurn(messageId)) return false
        publishNow(key)
        return true
    }

    @Synchronized
    fun remove(key: AgentConversationKey): AgentConversationSnapshot? {
        val removed = mutableConversations.remove(key)?.freeze() ?: return null
        replayConversations.remove(key)
        pendingPublications.remove(key)
        val next = _conversations.value.toMutableMap().apply { remove(key) }
        _conversations.value = next
        publicationCount++
        return removed
    }

    @Synchronized
    fun removeInstance(instanceId: String): List<AgentConversationSnapshot> {
        val keys = mutableConversations.filterValues { it.instanceId == instanceId }.keys.toList()
        return keys.mapNotNull(::remove)
    }

    @Synchronized
    internal fun flushForTest() {
        flushPendingPublications()
    }

    @Synchronized
    internal fun publicationCountForTest(): Long = publicationCount

    @Synchronized
    internal fun setNowMillisForTest(clock: () -> Long) {
        nowMillis = clock
    }

    @Synchronized
    internal fun resetForTest() {
        mutableConversations.clear()
        replayConversations.clear()
        pendingPublications.clear()
        publishScheduled = false
        publicationCount = 0L
        nowMillis = System::currentTimeMillis
        _conversations.value = emptyMap()
    }

    private fun schedulePublish(key: AgentConversationKey) {
        pendingPublications += key
        if (publishScheduled) return
        publishScheduled = true
        publishExecutor.schedule(
            {
                synchronized(this) {
                    publishScheduled = false
                    flushPendingPublications()
                }
            },
            PUBLISH_FRAME_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun flushPendingPublications() {
        val keys = pendingPublications.toList()
        pendingPublications.clear()
        keys.forEach(::publishNow)
    }

    private fun publishNow(key: AgentConversationKey) {
        pendingPublications.remove(key)
        val snapshot = mutableConversations[key]?.freeze() ?: return
        _conversations.value = _conversations.value.toMutableMap().apply { put(key, snapshot) }
        publicationCount++
    }

    private class MutableConversation(
        var key: AgentConversationKey,
        val instanceId: String,
        var phase: AgentSessionPhase,
        private val recordsLiveTiming: Boolean = true,
    ) {
        val timeline = mutableListOf<MutableTimelineItem>()
        var plan: List<AgentPlanEntry> = emptyList()
        var commands: List<AgentCommand> = emptyList()
        var configuration: List<AgentConfigOption> = emptyList()
        var currentModeId: String? = null
        var pendingPermission: AgentPermissionRequest? = null
        var phaseBeforePermission: AgentSessionPhase? = null
        var title: String? = null
        var updatedAt: String? = null
        var usage: AgentSessionEvent.UsageChanged? = null
        var lastError: String? = null
        val extensions = mutableListOf<AgentSessionEvent.Extension>()
        var historyStatus: AgentConversationHistoryStatus = AgentConversationHistoryStatus.Live
        var visibleTimelineItems: Int = INITIAL_VISIBLE_TIMELINE_ITEMS
        var truncatedTimelineItems: Int = 0
        var retainedTextChars: Long = 0L
        var retainedInlineBytes: Long = 0L
        var revision: Long = 0L
        var turnOrdinal: Long = 0L
        var nextItemOrdinal: Long = 0L
        var turnActive: Boolean = false
        var currentTurnHasUser: Boolean = false
        val turns = linkedMapOf<Long, MutableTurn>()

        fun adoptSessionFacts(next: MutableConversation) {
            if (next.configuration.isNotEmpty()) configuration = next.configuration
            if (next.commands.isNotEmpty()) commands = next.commands
            next.currentModeId?.let { currentModeId = it }
            next.pendingPermission?.let { pendingPermission = it }
            next.phaseBeforePermission?.let { phaseBeforePermission = it }
            next.title?.let { title = it }
            next.updatedAt?.let { updatedAt = it }
            next.usage?.let { usage = it }
            next.lastError?.let { lastError = it }
            if (next.extensions.isNotEmpty()) {
                extensions += next.extensions
                while (extensions.size > MAX_EXTENSION_EVENTS) extensions.removeAt(0)
            }
        }

        fun apply(event: AgentSessionEvent): Boolean {
            revision++
            return when (event) {
                is AgentSessionEvent.LifecycleChanged -> {
                    when (event.phase) {
                        AgentSessionPhase.Prompting -> ensureTurn()
                        AgentSessionPhase.Ready -> finishTurn(AgentConversationTurnState.Completed)
                        AgentSessionPhase.Failed -> finishTurn(
                            AgentConversationTurnState.Failed,
                            event.message?.trim()?.takeIf(String::isNotBlank),
                        )
                        AgentSessionPhase.Cancelled,
                        AgentSessionPhase.Closed -> finishTurn(AgentConversationTurnState.Cancelled)
                        AgentSessionPhase.Preparing,
                        AgentSessionPhase.WaitingPermission,
                        AgentSessionPhase.Cancelling -> Unit
                    }
                    phase = event.phase
                    if (event.phase == AgentSessionPhase.Failed) {
                        lastError = event.message?.trim()?.takeIf(String::isNotBlank) ?: "Agent 会话失败"
                    } else if (event.phase == AgentSessionPhase.Ready) {
                        lastError = null
                    }
                    false
                }
                is AgentSessionEvent.MessageChunk -> {
                    appendMessage(event)
                    true
                }
                is AgentSessionEvent.ToolCallStarted -> {
                    upsertTool(event.call)
                    true
                }
                is AgentSessionEvent.ToolCallUpdated -> {
                    updateTool(event.update)
                    true
                }
                is AgentSessionEvent.PlanUpdated -> {
                    plan = event.entries
                    upsertPlan(event.entries)
                    false
                }
                is AgentSessionEvent.CommandsUpdated -> {
                    commands = event.commands
                    false
                }
                is AgentSessionEvent.CurrentModeChanged -> {
                    currentModeId = event.modeId
                    false
                }
                is AgentSessionEvent.ConfigurationUpdated -> {
                    configuration = event.options
                    false
                }
                is AgentSessionEvent.SessionInfoChanged -> {
                    title = event.title ?: title
                    updatedAt = event.updatedAt ?: updatedAt
                    false
                }
                is AgentSessionEvent.UsageChanged -> {
                    usage = event
                    true
                }
                is AgentSessionEvent.Extension -> {
                    extensions += event
                    while (extensions.size > MAX_EXTENSION_EVENTS) extensions.removeAt(0)
                    true
                }
            }
        }

        fun freeze(): AgentConversationSnapshot {
            val visibleCount = minOf(visibleTimelineItems, timeline.size)
            val visibleTimeline = timeline
                .subList(timeline.size - visibleCount, timeline.size)
                .map(MutableTimelineItem::freeze)
            val visibleTurnOrdinals = visibleTimeline.mapTo(linkedSetOf()) { it.turnOrdinal }
            return AgentConversationSnapshot(
            key = key,
            instanceId = instanceId,
            phase = phase,
            timeline = visibleTimeline,
            turns = turns.values
                .filter { it.ordinal in visibleTurnOrdinals }
                .map(MutableTurn::freeze),
            plan = plan,
            commands = commands,
            configuration = configuration,
            currentModeId = currentModeId,
            pendingPermission = pendingPermission,
            title = title,
            updatedAt = updatedAt,
            usage = usage,
            lastError = lastError,
            extensions = extensions.toList(),
            history = AgentConversationHistory(
                status = historyStatus,
                visibleItems = visibleCount,
                totalItems = timeline.size,
                hasEarlierItems = visibleCount < timeline.size,
                truncatedItems = truncatedTimelineItems
            ),
            revision = revision
        )
        }

        private fun appendMessage(event: AgentSessionEvent.MessageChunk) {
            prepareTurnForMessage(event)
            val previous = timeline.lastOrNull() as? MutableMessage
            val canAppend = previous != null &&
                previous.role == event.role &&
                previous.turnOrdinal == turnOrdinal &&
                ((event.messageId != null && previous.messageId == event.messageId) ||
                    (event.messageId == null && previous.messageId == null))
            val message = if (canAppend) {
                previous!!
            } else {
                MutableMessage(
                    id = "${key.sessionId}:message:${nextItemOrdinal++}",
                    role = event.role,
                    messageId = event.messageId,
                    turnOrdinal = turnOrdinal
                ).also { addTimelineItem(it) }
            }
            retainedTextChars -= message.retainedTextChars
            retainedInlineBytes -= message.retainedInlineBytes
            val visibleBlocks = if (event.role == AgentMessageRole.User && event.content is AgentContent.Text) {
                AgentSkillPromptComposer.restoreVisibleText(event.content.text) ?: listOf(event.content)
            } else {
                listOf(event.content)
            }
            visibleBlocks.forEach(message::append)
            retainedTextChars += message.retainedTextChars
            retainedInlineBytes += message.retainedInlineBytes
            trimTimeline()
        }

        private fun prepareTurnForMessage(event: AgentSessionEvent.MessageChunk) {
            if (event.role == AgentMessageRole.User) {
                val sameUserMessage = timeline
                    .filterIsInstance<MutableMessage>()
                    .lastOrNull { it.role == AgentMessageRole.User && it.turnOrdinal == turnOrdinal }
                    ?.let { previous ->
                        (event.messageId != null && previous.messageId == event.messageId) ||
                            (event.messageId == null && previous.messageId == null && turnActive)
                    }
                    ?: false
                if (!sameUserMessage) {
                    if (!turnActive || currentTurnHasUser) beginTurn()
                    currentTurnHasUser = true
                }
            } else {
                ensureTurn()
            }
        }

        private fun ensureTurn() {
            if (!turnActive) beginTurn()
        }

        private fun beginTurn() {
            if (turnActive) finishTurn(AgentConversationTurnState.Completed)
            turnOrdinal += 1L
            turnActive = true
            currentTurnHasUser = false
            turns[turnOrdinal] = MutableTurn(
                ordinal = turnOrdinal,
                state = if (recordsLiveTiming) {
                    AgentConversationTurnState.Running
                } else {
                    AgentConversationTurnState.Historical
                },
                startedAtMillis = if (recordsLiveTiming) nowMillis() else null,
            )
        }

        private fun finishTurn(state: AgentConversationTurnState, errorMessage: String? = null) {
            if (!turnActive) return
            turns[turnOrdinal]?.apply {
                if (recordsLiveTiming) {
                    this.state = state
                    endedAtMillis = nowMillis()
                    this.errorMessage = errorMessage
                } else {
                    this.state = AgentConversationTurnState.Historical
                }
            }
            turnActive = false
            currentTurnHasUser = false
        }

        fun finishHistoryReplay() {
            finishTurn(AgentConversationTurnState.Historical)
        }

        fun discardMessage(messageId: String): Boolean {
            val index = timeline.indexOfFirst { item ->
                item is MutableMessage && item.messageId == messageId
            }
            if (index < 0) return false
            val removed = timeline.removeAt(index)
            retainedTextChars = (retainedTextChars - removed.retainedTextChars).coerceAtLeast(0L)
            retainedInlineBytes = (retainedInlineBytes - removed.retainedInlineBytes).coerceAtLeast(0L)
            revision++
            return true
        }

        fun failLocalTurn(messageId: String, message: String): Boolean {
            val localMessage = timeline.filterIsInstance<MutableMessage>()
                .lastOrNull { it.messageId == messageId }
                ?: return false
            val turn = turns[localMessage.turnOrdinal] ?: return false
            turn.state = AgentConversationTurnState.Failed
            turn.endedAtMillis = nowMillis()
            turn.errorMessage = message.trim().takeIf(String::isNotBlank) ?: "本轮未完成"
            if (turn.ordinal == turnOrdinal) {
                turnActive = false
                currentTurnHasUser = false
            }
            phase = AgentSessionPhase.Ready
            revision++
            return true
        }

        fun retryLocalTurn(messageId: String): Boolean {
            val localMessage = timeline.filterIsInstance<MutableMessage>()
                .lastOrNull { it.messageId == messageId }
                ?: return false
            val turn = turns[localMessage.turnOrdinal] ?: return false
            if (turn.state != AgentConversationTurnState.Failed) return false
            turn.state = AgentConversationTurnState.Running
            turn.endedAtMillis = null
            turn.errorMessage = null
            turnOrdinal = turn.ordinal
            turnActive = true
            currentTurnHasUser = true
            phase = AgentSessionPhase.Preparing
            revision++
            return true
        }

        private fun upsertTool(call: AgentToolCall) {
            val existing = timeline.filterIsInstance<MutableTool>().firstOrNull { it.call.id == call.id }
            if (existing != null) {
                retainedTextChars -= existing.retainedTextChars
                retainedInlineBytes -= existing.retainedInlineBytes
                existing.call = call
                retainedTextChars += existing.retainedTextChars
                retainedInlineBytes += existing.retainedInlineBytes
                trimTimeline()
            } else {
                ensureTurn()
                addTimelineItem(MutableTool(call.id, turnOrdinal, call))
            }
        }

        private fun updateTool(patch: AgentToolCallPatch) {
            val existing = timeline.filterIsInstance<MutableTool>().firstOrNull { it.call.id == patch.id }
            if (existing != null) {
                retainedTextChars -= existing.retainedTextChars
                retainedInlineBytes -= existing.retainedInlineBytes
                existing.call = existing.call.merge(patch)
                retainedTextChars += existing.retainedTextChars
                retainedInlineBytes += existing.retainedInlineBytes
                trimTimeline()
            } else {
                ensureTurn()
                addTimelineItem(
                    MutableTool(
                        id = patch.id,
                        turnOrdinal = turnOrdinal,
                        call = AgentToolCall(
                            id = patch.id,
                            title = patch.title.orEmpty().ifBlank { "工具调用" },
                            kind = patch.kind,
                            status = patch.status,
                            content = patch.content.orEmpty(),
                            locations = patch.locations.orEmpty(),
                            rawInput = patch.rawInput,
                            rawOutput = patch.rawOutput
                        )
                    )
                )
            }
        }

        private fun upsertPlan(entries: List<AgentPlanEntry>) {
            if (entries.isNotEmpty()) ensureTurn()
            val existing = timeline.filterIsInstance<MutablePlan>()
                .lastOrNull { it.turnOrdinal == turnOrdinal }
            if (entries.isEmpty()) {
                if (existing != null) removeTimelineItem(existing)
                return
            }
            if (existing != null) {
                retainedTextChars -= existing.retainedTextChars
                existing.entries = entries
                retainedTextChars += existing.retainedTextChars
                trimTimeline()
            } else {
                addTimelineItem(
                    MutablePlan(
                        id = "${key.sessionId}:plan:$turnOrdinal",
                        turnOrdinal = turnOrdinal,
                        entries = entries
                    )
                )
            }
        }

        private fun addTimelineItem(item: MutableTimelineItem) {
            timeline += item
            retainedTextChars += item.retainedTextChars
            retainedInlineBytes += item.retainedInlineBytes
            trimTimeline()
        }

        private fun trimTimeline() {
            while (
                timeline.size > MAX_TIMELINE_ITEMS ||
                (timeline.size > 1 && retainedTextChars > MAX_RETAINED_TEXT_CHARS) ||
                (timeline.size > 1 && retainedInlineBytes > MAX_RETAINED_INLINE_BYTES)
            ) {
                removeTimelineItem(timeline.first())
                truncatedTimelineItems++
            }
        }

        private fun removeTimelineItem(item: MutableTimelineItem) {
            if (!timeline.remove(item)) return
            retainedTextChars = (retainedTextChars - item.retainedTextChars).coerceAtLeast(0L)
            retainedInlineBytes = (retainedInlineBytes - item.retainedInlineBytes).coerceAtLeast(0L)
        }
    }

    private sealed interface MutableTimelineItem {
        val retainedTextChars: Long
        val retainedInlineBytes: Long
        fun freeze(): AgentConversationItem
    }

    private class MutableMessage(
        private val id: String,
        val role: AgentMessageRole,
        val messageId: String?,
        val turnOrdinal: Long
    ) : MutableTimelineItem {
        private val content = mutableListOf<MutableMessageContent>()
        override var retainedTextChars: Long = 0L
            private set
        override var retainedInlineBytes: Long = 0L
            private set

        fun append(block: AgentContent) {
            retainedTextChars += block.retainedTextChars()
            retainedInlineBytes += block.retainedInlineBytes()
            val previous = content.lastOrNull()
            if (block is AgentContent.Text && previous is MutableMessageContent.Text) {
                previous.text.append(block.text)
            } else if (block is AgentContent.Text) {
                content += MutableMessageContent.Text(
                    text = StringBuilder(block.text),
                    annotations = block.annotations,
                    extension = block.extension
                )
            } else {
                content += MutableMessageContent.Other(block)
            }
        }

        override fun freeze(): AgentConversationItem.Message = AgentConversationItem.Message(
            id = id,
            role = role,
            messageId = messageId,
            content = content.map(MutableMessageContent::freeze),
            turnOrdinal = turnOrdinal,
        )
    }

    private class MutableTool(
        private val id: String,
        val turnOrdinal: Long,
        var call: AgentToolCall
    ) : MutableTimelineItem {
        override val retainedTextChars: Long
            get() = call.retainedTextChars()
        override val retainedInlineBytes: Long
            get() = call.retainedInlineBytes()
        override fun freeze(): AgentConversationItem.Tool = AgentConversationItem.Tool(id, call, turnOrdinal)
    }

    private class MutablePlan(
        private val id: String,
        val turnOrdinal: Long,
        var entries: List<AgentPlanEntry>
    ) : MutableTimelineItem {
        override val retainedTextChars: Long
            get() = entries.sumOf { it.content.length.toLong() }
        override val retainedInlineBytes: Long = 0L
        override fun freeze(): AgentConversationItem.Plan = AgentConversationItem.Plan(id, entries, turnOrdinal)
    }

    private data class MutableTurn(
        val ordinal: Long,
        var state: AgentConversationTurnState,
        val startedAtMillis: Long?,
        var endedAtMillis: Long? = null,
        var errorMessage: String? = null,
    ) {
        fun freeze(): AgentConversationTurn = AgentConversationTurn(
            ordinal = ordinal,
            state = state,
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis,
            errorMessage = errorMessage,
        )
    }

    private sealed interface MutableMessageContent {
        fun freeze(): AgentContent

        class Text(
            val text: StringBuilder,
            private val annotations: com.kite.app.agent.contract.AgentContentAnnotations?,
            private val extension: com.kite.app.agent.contract.AgentProtocolExtension?
        ) : MutableMessageContent {
            override fun freeze(): AgentContent = AgentContent.Text(text.toString(), annotations, extension)
        }

        class Other(private val content: AgentContent) : MutableMessageContent {
            override fun freeze(): AgentContent = content
        }
    }

    private fun AgentToolCall.merge(patch: AgentToolCallPatch): AgentToolCall = copy(
        title = patch.title ?: title,
        kind = patch.kind ?: kind,
        status = patch.status ?: status,
        content = patch.content ?: content,
        locations = patch.locations ?: locations,
        rawInput = patch.rawInput ?: rawInput,
        rawOutput = patch.rawOutput ?: rawOutput
    )

    private fun AgentContent.retainedTextChars(): Long = when (this) {
        is AgentContent.Text -> text.length.toLong()
        is AgentContent.SkillReference -> displayName.length.toLong()
        is AgentContent.EmbeddedText -> text.length.toLong()
        else -> 0L
    }

    private fun AgentContent.retainedInlineBytes(): Long = when (this) {
        is AgentContent.Image -> estimatedBase64Bytes(data)
        is AgentContent.Audio -> estimatedBase64Bytes(data)
        is AgentContent.EmbeddedBlob -> estimatedBase64Bytes(data)
        else -> 0L
    }

    private fun AgentToolCall.retainedTextChars(): Long =
        title.length.toLong() +
            rawInput.orEmpty().length +
            rawOutput.orEmpty().length +
            content.sumOf { entry ->
                when (entry) {
                    is AgentToolContent.Content -> entry.content.retainedTextChars()
                    is AgentToolContent.Diff -> entry.path.length + entry.newText.length + entry.oldText.orEmpty().length
                    is AgentToolContent.Terminal -> entry.terminalId.length
                }.toLong()
            }

    private fun AgentToolCall.retainedInlineBytes(): Long = content.sumOf { entry ->
        when (entry) {
            is AgentToolContent.Content -> entry.content.retainedInlineBytes()
            is AgentToolContent.Diff,
            is AgentToolContent.Terminal -> 0L
        }
    }

    private fun estimatedBase64Bytes(value: String): Long {
        val payload = value.substringAfter("base64,", value)
        var useful = 0L
        payload.forEach { char -> if (!char.isWhitespace()) useful++ }
        return useful * 3L / 4L
    }

    private const val PUBLISH_FRAME_MS = 32L
    private const val INITIAL_VISIBLE_TIMELINE_ITEMS = 80
    private const val VISIBLE_TIMELINE_PAGE_ITEMS = 60
    private const val MAX_TIMELINE_ITEMS = 500
    private const val MAX_RETAINED_TEXT_CHARS = 2_000_000L
    private const val MAX_RETAINED_INLINE_BYTES = 24L * 1024L * 1024L
    private const val MAX_EXTENSION_EVENTS = 50
}
