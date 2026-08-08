package com.kite.app.platform.runs

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kite.app.CardRunIntents
import com.kite.app.R
import com.kite.app.application.runs.RUN_NOTIFICATIONS_REQUIRED
import com.kite.app.application.runs.RunCommandResult
import com.kite.app.application.runs.RunNotificationAction
import com.kite.app.application.runs.RunNotificationProjector
import com.kite.app.application.runs.RunNotificationUiState
import com.kite.app.application.runs.RunStepCompletionCommand
import com.kite.app.foundation.bootstrap.KFApplication
import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import com.kite.app.run.CardRunStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal object RunNotificationIntentContract {
    const val RECEIVER_CLASS = "com.kite.app.shell.RunNotificationActionReceiver"
    const val ACTION_COMPLETE_STEP = "com.kite.app.action.COMPLETE_RUN_STEP"
    const val ACTION_CLOSE_RUN = "com.kite.app.action.CLOSE_RUN"
    const val ACTION_RESTART_RUN = "com.kite.app.action.RESTART_RUN"
    const val ACTION_DISMISS_RESULT = "com.kite.app.action.DISMISS_RUN_RESULT"
    const val EXTRA_RECIPE_ID = "com.kite.app.extra.RUN_RECIPE_ID"
    const val EXTRA_INSTANCE_ID = "com.kite.app.extra.RUN_INSTANCE_ID"
    const val EXTRA_GENERATION = "com.kite.app.extra.RUN_GENERATION"
    const val EXTRA_STEP_INDEX = "com.kite.app.extra.RUN_STEP_INDEX"
    const val EXTRA_STEP_ID = "com.kite.app.extra.RUN_STEP_ID"
    const val EXTRA_OUTPUT = "com.kite.app.extra.RUN_STEP_OUTPUT"
}

internal data class RunNotificationRequirement(
    val instanceId: String,
    val generation: Long,
    val stepIndex: Int,
    val title: String
) {
    val key: String = "$instanceId@$generation:$stepIndex"
}

internal fun interface RunNotificationViewBinder {
    fun bind(
        builder: NotificationCompat.Builder,
        model: RunNotificationUiState,
        actionPendingIntent: (RunNotificationAction) -> PendingIntent
    )
}

/** 每实例通知的进程级投影器。它不保存运行事实，只持有已发布 tag 和短期动作反馈。 */
internal class AndroidRunNotificationCoordinator(
    context: Context,
    private val scope: CoroutineScope,
    private val recipeResolver: (String) -> KiteRecipe?,
    private val restartRecipeResolver: (String) -> KiteRecipe?,
    private val completeStep: (RunStepCompletionCommand) -> RunCommandResult,
    private val closeRun: (KiteRecipe, CardRunState) -> RunCommandResult,
    private val restartRun: (KiteRecipe, CardRunState) -> RunCommandResult,
    private val closeRunTask: (String, Long) -> Unit,
    private val viewBinder: RunNotificationViewBinder,
    private val environmentIdProvider: () -> String = { CardRunState.DEFAULT_ENVIRONMENT_ID }
) {
    private data class PendingPresentation(val generation: Long, val detail: String)

    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)
    private val dismissalStore = RunNotificationDismissalStore(appContext)
    private val publishedInstances = linkedSetOf<String>()
    private val pendingActions = mutableMapOf<String, PendingPresentation>()
    private val _requirement = MutableStateFlow<RunNotificationRequirement?>(null)
    val requirement: StateFlow<RunNotificationRequirement?> = _requirement.asStateFlow()
    @Volatile private var started = false

    fun start() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        scope.launch {
            CardRunStore.runs.collect(::render)
        }
    }

    fun startRejectionReason(): String? =
        if (AndroidRunNotificationAccess.isAvailable(appContext)) null else RUN_NOTIFICATIONS_REQUIRED

    fun refresh() {
        scope.launch { render(CardRunStore.snapshot()) }
    }

    fun handleCompletion(command: RunStepCompletionCommand, onFinished: () -> Unit = {}) {
        launchAction(onFinished) {
            var ownsPending = false
            try {
                val resolved = resolveExact(command.instanceId, command.expectedGeneration)
                    ?: return@launchAction refresh()
                val (recipe, state) = resolved
                val action = RunNotificationProjector.project(recipe, state)
                    ?.expandedActions
                    ?.filterIsInstance<RunNotificationAction.CompleteStep>()
                    ?.firstOrNull { it.command == command }
                    ?: return@launchAction refresh()
                if (!beginPending(state, "正在处理当前步骤")) return@launchAction
                ownsPending = true
                publishPending(recipe, state)
                val result = completeStep(action.command)
                if (result is RunCommandResult.Ignored) publishIgnored(state.instanceId, result.reason)
            } finally {
                if (ownsPending) endPending(command.instanceId)
            }
        }
    }

    fun handleClose(instanceId: String, generation: Long, onFinished: () -> Unit = {}) {
        launchAction(onFinished) {
            var ownsPending = false
            try {
                val resolved = resolveExact(instanceId, generation) ?: return@launchAction refresh()
                val (recipe, state) = resolved
                val allowed = RunNotificationProjector.project(recipe, state)
                    ?.expandedActions
                    ?.any { action ->
                        action is RunNotificationAction.Close &&
                            action.instanceId == instanceId &&
                            action.expectedGeneration == generation
                } == true
                if (!allowed || !beginPending(state, "正在关闭")) return@launchAction refresh()
                ownsPending = true
                publishPending(recipe, state)
                val result = closeRun(recipe, state)
                if (result is RunCommandResult.Accepted) {
                    closeRunTask(instanceId, generation)
                } else if (result is RunCommandResult.Ignored) {
                    publishIgnored(instanceId, result.reason)
                }
            } finally {
                if (ownsPending) endPending(instanceId)
            }
        }
    }

    fun handleRestart(
        recipeId: String,
        instanceId: String,
        generation: Long,
        onFinished: () -> Unit = {}
    ) {
        launchAction(onFinished) {
            var ownsPending = false
            try {
                val resolved = resolveExact(instanceId, generation) ?: return@launchAction refresh()
                val (recipe, state) = resolved
                if (recipe.id != recipeId) return@launchAction refresh()
                val allowed = RunNotificationProjector.project(recipe, state)
                    ?.expandedActions
                    ?.any { action ->
                        action is RunNotificationAction.Restart &&
                            action.recipeId == recipeId &&
                            action.instanceId == instanceId &&
                        action.expectedGeneration == generation
                    } == true
                val restartRecipe = restartRecipeResolver(recipeId) ?: return@launchAction refresh()
                if (!allowed || !beginPending(state, "正在启动")) return@launchAction refresh()
                ownsPending = true
                publishPending(recipe, state)
                val result = restartRun(restartRecipe, state)
                if (result is RunCommandResult.Accepted) {
                    dismissalStore.clear(instanceId)
                } else if (result is RunCommandResult.Ignored) {
                    publishIgnored(instanceId, result.reason)
                }
            } finally {
                if (ownsPending) endPending(instanceId)
            }
        }
    }

    fun handleDismiss(instanceId: String, generation: Long, onFinished: () -> Unit = {}) {
        launchAction(onFinished) {
            val resolved = resolveExact(instanceId, generation) ?: return@launchAction
            val (recipe, state) = resolved
            val model = RunNotificationProjector.project(recipe, state) ?: return@launchAction
            if (model.ongoing) return@launchAction
            dismissalStore.dismiss(instanceId, generation)
            synchronized(publishedInstances) { publishedInstances.remove(instanceId) }
            cancel(instanceId)
            render(CardRunStore.snapshot())
        }
    }

    private fun launchAction(onFinished: () -> Unit, action: suspend () -> Unit) {
        scope.launch { action() }.invokeOnCompletion { onFinished() }
    }

    private fun resolveExact(instanceId: String, generation: Long): Pair<KiteRecipe, CardRunState>? {
        val state = CardRunStore.get(instanceId)
            ?.takeIf {
                it.ownerKind == CardRunState.OWNER_KIND_CARD &&
                    it.createdAt == generation &&
                    it.environmentId == environmentIdProvider()
            }
            ?: return null
        val recipe = recipeResolver(state.recipeId) ?: return null
        return recipe to state
    }

    private fun beginPending(state: CardRunState, detail: String): Boolean = synchronized(pendingActions) {
        if (pendingActions.containsKey(state.instanceId)) return@synchronized false
        pendingActions[state.instanceId] = PendingPresentation(state.createdAt, detail)
        true
    }

    private fun endPending(instanceId: String) {
        synchronized(pendingActions) { pendingActions.remove(instanceId) }
    }

    private fun publishPending(recipe: KiteRecipe, state: CardRunState) {
        val pending = synchronized(pendingActions) { pendingActions[state.instanceId] } ?: return
        if (pending.generation != state.createdAt) return
        RunNotificationProjector.project(recipe, state)?.let { model ->
            publish(
                model.copy(
                    detail = pending.detail,
                    compactAction = null,
                    expandedActions = emptyList()
                )
            )
        }
    }

    private fun publishIgnored(instanceId: String, reason: String) {
        val latest = CardRunStore.get(instanceId, environmentIdProvider()) ?: return
        val recipe = recipeResolver(latest.recipeId) ?: return
        RunNotificationProjector.project(recipe, latest)?.let { model ->
            publish(model.copy(detail = ignoredMessage(reason)))
        }
    }

    private fun render(runs: List<CardRunState>) {
        val available = AndroidRunNotificationAccess.isAvailable(appContext)
        val cardRuns = runs.filter {
            it.ownerKind == CardRunState.OWNER_KIND_CARD &&
                it.environmentId == environmentIdProvider()
        }
        dismissalStore.prune(cardRuns.mapTo(linkedSetOf(), CardRunState::instanceId))
        val models = cardRuns.mapNotNull { state ->
            val recipe = recipeResolver(state.recipeId) ?: return@mapNotNull null
            val model = RunNotificationProjector.project(recipe, state) ?: return@mapNotNull null
            if (!model.ongoing && dismissalStore.isDismissed(model.instanceId, model.generation)) {
                return@mapNotNull null
            }
            val pending = synchronized(pendingActions) { pendingActions[model.instanceId] }
            if (pending != null && pending.generation == model.generation) {
                model.copy(
                    detail = pending.detail,
                    compactAction = null,
                    expandedActions = emptyList()
                )
            } else {
                if (pending != null) endPending(model.instanceId)
                model
            }
        }
        _requirement.value = if (available) {
            null
        } else {
            models.firstNotNullOfOrNull { model ->
                model.completionCommand?.let { command ->
                    RunNotificationRequirement(
                        instanceId = command.instanceId,
                        generation = command.expectedGeneration,
                        stepIndex = command.expectedStepIndex,
                        title = model.title
                    )
                }
            }
        }
        if (!available) return

        val currentIds = models.mapTo(linkedSetOf(), RunNotificationUiState::instanceId)
        synchronized(publishedInstances) {
            (publishedInstances - currentIds).forEach(::cancel)
            publishedInstances.clear()
            publishedInstances.addAll(currentIds)
        }
        models.forEach(::publish)
        publishSummary(models)
    }

    private fun publish(model: RunNotificationUiState) {
        val content = CardRunIntents.pendingIntent(
            context = appContext,
            recipeId = model.recipeId,
            instanceId = model.instanceId,
            generation = model.generation,
        )
        val builder = NotificationCompat.Builder(appContext, KFApplication.CHANNEL_RUNS)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle(model.title)
            .setContentText(model.detail)
            .setSubText(model.stepLabel)
            .setContentIntent(content)
            .setWhen(model.updatedAt)
            .setShowWhen(true)
            .setOnlyAlertOnce(true)
            .setOngoing(model.ongoing)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (model.ongoing) Notification.CATEGORY_PROGRESS else Notification.CATEGORY_STATUS)
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .apply {
                if (!model.ongoing) setDeleteIntent(dismissPendingIntent(model))
            }
        viewBinder.bind(builder, model, ::actionPendingIntent)
        val notification = builder.build()
        AndroidRunNotificationAccess.postSafely(
            context = appContext,
            manager = manager,
            tag = notificationTag(model.instanceId),
            id = INSTANCE_NOTIFICATION_ID,
            notification = notification
        )
    }

    private fun publishSummary(models: List<RunNotificationUiState>) {
        if (models.size <= 1) {
            manager.cancel(SUMMARY_NOTIFICATION_ID)
            return
        }
        val active = models.count(RunNotificationUiState::ongoing)
        val summary = NotificationCompat.Builder(appContext, KFApplication.CHANNEL_RUNS)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle("Kite 运行实例")
            .setContentText("${models.size} 个实例，$active 个正在运行")
            .setOnlyAlertOnce(true)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOngoing(active > 0)
            .build()
        AndroidRunNotificationAccess.postSafely(
            context = appContext,
            manager = manager,
            tag = null,
            id = SUMMARY_NOTIFICATION_ID,
            notification = summary
        )
    }

    private fun actionPendingIntent(action: RunNotificationAction): PendingIntent = when (action) {
        is RunNotificationAction.CompleteStep -> completionPendingIntent(action.command)
        is RunNotificationAction.Close -> identityPendingIntent(
            action = RunNotificationIntentContract.ACTION_CLOSE_RUN,
            route = "close",
            instanceId = action.instanceId,
            generation = action.expectedGeneration
        )
        is RunNotificationAction.Restart -> identityPendingIntent(
            action = RunNotificationIntentContract.ACTION_RESTART_RUN,
            route = "restart",
            recipeId = action.recipeId,
            instanceId = action.instanceId,
            generation = action.expectedGeneration
        )
    }

    private fun completionPendingIntent(command: RunStepCompletionCommand): PendingIntent {
        val uri = Uri.Builder()
            .scheme("kite")
            .authority("run-notification")
            .appendPath("complete")
            .appendPath(command.instanceId)
            .appendPath(command.expectedGeneration.toString())
            .appendPath(command.expectedStepIndex.toString())
            .appendPath(command.expectedStepId)
            .build()
        val intent = Intent(RunNotificationIntentContract.ACTION_COMPLETE_STEP)
            .setClassName(appContext, RunNotificationIntentContract.RECEIVER_CLASS)
            .setData(uri)
            .putExtra(RunNotificationIntentContract.EXTRA_INSTANCE_ID, command.instanceId)
            .putExtra(RunNotificationIntentContract.EXTRA_GENERATION, command.expectedGeneration)
            .putExtra(RunNotificationIntentContract.EXTRA_STEP_INDEX, command.expectedStepIndex)
            .putExtra(RunNotificationIntentContract.EXTRA_STEP_ID, command.expectedStepId)
            .putExtra(RunNotificationIntentContract.EXTRA_OUTPUT, command.output)
        return broadcastPendingIntent(intent)
    }

    private fun identityPendingIntent(
        action: String,
        route: String,
        instanceId: String,
        generation: Long,
        recipeId: String? = null
    ): PendingIntent {
        val uri = Uri.Builder()
            .scheme("kite")
            .authority("run-notification")
            .appendPath(route)
            .appendPath(instanceId)
            .appendPath(generation.toString())
            .build()
        val intent = Intent(action)
            .setClassName(appContext, RunNotificationIntentContract.RECEIVER_CLASS)
            .setData(uri)
            .putExtra(RunNotificationIntentContract.EXTRA_INSTANCE_ID, instanceId)
            .putExtra(RunNotificationIntentContract.EXTRA_GENERATION, generation)
        recipeId?.let { intent.putExtra(RunNotificationIntentContract.EXTRA_RECIPE_ID, it) }
        return broadcastPendingIntent(intent)
    }

    private fun dismissPendingIntent(model: RunNotificationUiState): PendingIntent = identityPendingIntent(
        action = RunNotificationIntentContract.ACTION_DISMISS_RESULT,
        route = "dismiss",
        instanceId = model.instanceId,
        generation = model.generation
    )

    private fun broadcastPendingIntent(intent: Intent): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun cancel(instanceId: String) {
        manager.cancel(notificationTag(instanceId), INSTANCE_NOTIFICATION_ID)
    }

    private fun ignoredMessage(reason: String): String = when (reason) {
        "generation_mismatch", "step_index_mismatch", "step_id_mismatch" -> "状态已经变化，已刷新到当前进度"
        "completion_in_flight", "busy", "instance_already_active" -> "当前操作正在处理"
        RUN_NOTIFICATIONS_REQUIRED -> "请先恢复运行通知"
        else -> "当前状态暂时不能执行这个操作"
    }

    private fun notificationTag(instanceId: String): String = "kite-run:$instanceId"

    private companion object {
        const val GROUP_KEY = "kite-runs"
        const val INSTANCE_NOTIFICATION_ID = 1
        const val SUMMARY_NOTIFICATION_ID = 27031
    }
}
