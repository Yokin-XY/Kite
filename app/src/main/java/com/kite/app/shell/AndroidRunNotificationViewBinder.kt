package com.kite.app.shell

import android.app.PendingIntent
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.kite.app.R
import com.kite.app.application.runs.RunNotificationAction
import com.kite.app.application.runs.RunNotificationUiState
import com.kite.app.platform.runs.RunNotificationViewBinder

/** 只把通知 UiState 绑定成系统 RemoteViews，不读取或修改运行事实。 */
internal class AndroidRunNotificationViewBinder(context: Context) : RunNotificationViewBinder {
    private val appContext = context.applicationContext

    override fun bind(
        builder: NotificationCompat.Builder,
        model: RunNotificationUiState,
        actionPendingIntent: (RunNotificationAction) -> PendingIntent
    ) {
        val compact = remoteViews(
            R.layout.notification_run_compact,
            model,
            expanded = false,
            actionPendingIntent
        )
        val expanded = remoteViews(
            R.layout.notification_run_expanded,
            model,
            expanded = true,
            actionPendingIntent
        )
        builder
            .setCustomContentView(compact)
            .setCustomBigContentView(expanded)
            .setCustomHeadsUpContentView(compact)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
    }

    private fun remoteViews(
        layoutId: Int,
        model: RunNotificationUiState,
        expanded: Boolean,
        actionPendingIntent: (RunNotificationAction) -> PendingIntent
    ): RemoteViews = RemoteViews(appContext.packageName, layoutId).apply {
        setTextViewText(R.id.run_notification_title, model.title)
        setTextViewText(R.id.run_notification_step, model.stepLabel)
        setTextViewText(R.id.run_notification_detail, model.detail)
        setViewVisibility(R.id.run_notification_progress, View.VISIBLE)
        if (model.progressMax > 0) {
            setProgressBar(R.id.run_notification_progress, model.progressMax, model.progress, false)
        } else if (model.indeterminate) {
            setProgressBar(R.id.run_notification_progress, 0, 0, true)
        } else {
            setViewVisibility(R.id.run_notification_progress, View.GONE)
        }
        if (expanded) {
            val actions = model.expandedActions.takeLast(2)
            bindAction(
                R.id.run_notification_action_secondary,
                actions.getOrNull(0).takeIf { actions.size > 1 },
                actionPendingIntent
            )
            bindAction(R.id.run_notification_action_primary, actions.lastOrNull(), actionPendingIntent)
            setViewVisibility(
                R.id.run_notification_actions,
                if (actions.isEmpty()) View.GONE else View.VISIBLE
            )
        } else {
            bindAction(R.id.run_notification_action_primary, model.compactAction, actionPendingIntent)
        }
    }

    private fun RemoteViews.bindAction(
        viewId: Int,
        action: RunNotificationAction?,
        actionPendingIntent: (RunNotificationAction) -> PendingIntent
    ) {
        if (action == null) {
            setViewVisibility(viewId, View.GONE)
            return
        }
        setViewVisibility(viewId, View.VISIBLE)
        setTextViewText(viewId, action.label)
        setOnClickPendingIntent(viewId, actionPendingIntent(action))
    }
}
