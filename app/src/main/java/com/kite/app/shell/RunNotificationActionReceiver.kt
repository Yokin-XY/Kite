package com.kite.app.shell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kite.app.application.runs.RunStepCompletionCommand
import com.kite.app.platform.runs.RunNotificationIntentContract

/** 通知点击的 Shell 入口；只解析精确身份并交给进程组合根。 */
class RunNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        intent ?: return
        val coordinator = KiteAppGraph.from(context).runNotificationCoordinator
        when (intent.action) {
            RunNotificationIntentContract.ACTION_COMPLETE_STEP -> {
                val command = intent.toCompletionCommand() ?: return
                val pending = goAsync()
                coordinator.handleCompletion(command, pending::finish)
            }
            RunNotificationIntentContract.ACTION_CLOSE_RUN -> {
                val (instanceId, generation) = intent.toRunIdentity() ?: return
                val pending = goAsync()
                coordinator.handleClose(instanceId, generation, pending::finish)
            }
            RunNotificationIntentContract.ACTION_RESTART_RUN -> {
                val recipeId = intent.getStringExtra(RunNotificationIntentContract.EXTRA_RECIPE_ID)
                    ?.takeIf(String::isNotBlank) ?: return
                val (instanceId, generation) = intent.toRunIdentity() ?: return
                val pending = goAsync()
                coordinator.handleRestart(recipeId, instanceId, generation, pending::finish)
            }
            RunNotificationIntentContract.ACTION_DISMISS_RESULT -> {
                val (instanceId, generation) = intent.toRunIdentity() ?: return
                val pending = goAsync()
                coordinator.handleDismiss(instanceId, generation, pending::finish)
            }
        }
    }

    private fun Intent.toCompletionCommand(): RunStepCompletionCommand? {
        val instanceId = getStringExtra(RunNotificationIntentContract.EXTRA_INSTANCE_ID)
            ?.takeIf(String::isNotBlank) ?: return null
        val generation = getLongExtra(RunNotificationIntentContract.EXTRA_GENERATION, -1L)
            .takeIf { it > 0L } ?: return null
        val stepIndex = getIntExtra(RunNotificationIntentContract.EXTRA_STEP_INDEX, -1)
            .takeIf { it >= 0 } ?: return null
        val stepId = getStringExtra(RunNotificationIntentContract.EXTRA_STEP_ID)
            ?.takeIf(String::isNotBlank) ?: return null
        val output = getStringExtra(RunNotificationIntentContract.EXTRA_OUTPUT).orEmpty()
        return RunStepCompletionCommand(instanceId, generation, stepIndex, stepId, output)
    }

    private fun Intent.toRunIdentity(): Pair<String, Long>? {
        val instanceId = getStringExtra(RunNotificationIntentContract.EXTRA_INSTANCE_ID)
            ?.takeIf(String::isNotBlank) ?: return null
        val generation = getLongExtra(RunNotificationIntentContract.EXTRA_GENERATION, -1L)
            .takeIf { it > 0L } ?: return null
        return instanceId to generation
    }
}
