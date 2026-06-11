package com.kite.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.UUID

object CardRunIntents {
    const val ACTION_OPEN = "com.kite.app.action.OPEN_CARD_RUN"
    const val EXTRA_RECIPE_ID = "com.kite.app.extra.RECIPE_ID"
    const val EXTRA_INSTANCE_ID = "com.kite.app.extra.INSTANCE_ID"
    const val EXTRA_LAUNCH_SOURCE = "com.kite.app.extra.LAUNCH_SOURCE"
    const val EXTRA_AUTO_START = "com.kite.app.extra.AUTO_START"
    const val EXTRA_TEMP_URL = "com.kite.app.extra.TEMP_URL"
    const val EXTRA_TEMP_TITLE = "com.kite.app.extra.TEMP_TITLE"

    const val SOURCE_CARD = "card"
    const val SOURCE_SHORTCUT = "shortcut"
    const val SOURCE_NOTIFICATION = "notification"
    const val SOURCE_BROWSER_PROXY = "browser_proxy"

    fun newInstanceId(recipeId: String): String =
        "run_${recipeId}_${UUID.randomUUID().toString().replace("-", "")}"

    fun launchIntent(
        context: Context,
        recipeId: String,
        instanceId: String? = null,
        launchSource: String = SOURCE_CARD,
        autoStart: Boolean = true
    ): Intent =
        Intent(context, CardRunActivity::class.java)
            .setAction(ACTION_OPEN)
            .putExtra(EXTRA_RECIPE_ID, recipeId)
            .putExtra(EXTRA_LAUNCH_SOURCE, launchSource)
            .putExtra(EXTRA_AUTO_START, autoStart)
            .apply {
                if (!instanceId.isNullOrBlank()) putExtra(EXTRA_INSTANCE_ID, instanceId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS)
            }

    fun temporaryWebIntent(
        context: Context,
        url: String,
        launchSource: String = SOURCE_BROWSER_PROXY
    ): Intent {
        val recipeId = "temp_web_${UUID.randomUUID().toString().replace("-", "")}"
        return launchIntent(
            context = context,
            recipeId = recipeId,
            instanceId = newInstanceId(recipeId),
            launchSource = launchSource,
            autoStart = false
        ).putExtra(EXTRA_TEMP_URL, url)
            .putExtra(EXTRA_TEMP_TITLE, "临时网页")
    }

    fun pendingIntent(
        context: Context,
        recipeId: String,
        instanceId: String,
        launchSource: String = SOURCE_NOTIFICATION,
        autoStart: Boolean = false
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            instanceId.hashCode(),
            launchIntent(context, recipeId, instanceId, launchSource, autoStart),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
