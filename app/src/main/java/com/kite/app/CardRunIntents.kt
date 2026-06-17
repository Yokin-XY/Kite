package com.kite.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.UUID

object CardRunIntents {
    const val ACTION_OPEN = "com.kite.app.action.OPEN_CARD_RUN"
    const val EXTRA_RECIPE_ID = "com.kite.app.extra.RECIPE_ID"
    const val EXTRA_INSTANCE_ID = "com.kite.app.extra.INSTANCE_ID"
    const val EXTRA_LAUNCH_SOURCE = "com.kite.app.extra.LAUNCH_SOURCE"
    const val EXTRA_AUTO_START = "com.kite.app.extra.AUTO_START"
    const val EXTRA_TEMP_URL = "com.kite.app.extra.TEMP_URL"
    const val EXTRA_TEMP_TITLE = "com.kite.app.extra.TEMP_TITLE"
    const val EXTRA_RESOURCE_INSTALL_TARGET_ID = "com.kite.app.extra.RESOURCE_INSTALL_TARGET_ID"
    const val EXTRA_RESOURCE_INSTALL_PLAN_IDS = "com.kite.app.extra.RESOURCE_INSTALL_PLAN_IDS"

    const val SOURCE_CARD = "card"
    const val SOURCE_SHORTCUT = "shortcut"
    const val SOURCE_NOTIFICATION = "notification"
    const val SOURCE_BROWSER_PROXY = "browser_proxy"
    const val SOURCE_RESOURCE_INSTALL = "resource_install"

    fun newInstanceId(recipeId: String): String =
        "run_${recipeId}_${UUID.randomUUID().toString().replace("-", "")}"

    fun instanceDataUri(instanceId: String): Uri =
        Uri.parse("kite://card-run/${Uri.encode(instanceId)}")

    fun launchIntent(
        context: Context,
        recipeId: String,
        instanceId: String? = null,
        launchSource: String = SOURCE_CARD,
        autoStart: Boolean = true
    ): Intent {
        val resolvedInstanceId = instanceId?.takeIf { it.isNotBlank() } ?: recipeId
        return Intent(context, CardRunActivity::class.java)
            .setAction(ACTION_OPEN)
            .setData(instanceDataUri(resolvedInstanceId))
            .putExtra(EXTRA_RECIPE_ID, recipeId)
            .putExtra(EXTRA_INSTANCE_ID, resolvedInstanceId)
            .putExtra(EXTRA_LAUNCH_SOURCE, launchSource)
            .putExtra(EXTRA_AUTO_START, autoStart)
            .apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            }
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

    fun resourceInstallWizardIntent(
        context: Context,
        recipeId: String,
        instanceId: String,
        targetResourceId: String,
        planResourceIds: List<String>
    ): Intent =
        launchIntent(
            context = context,
            recipeId = recipeId,
            instanceId = instanceId,
            launchSource = SOURCE_RESOURCE_INSTALL,
            autoStart = false
        )
            .putExtra(EXTRA_RESOURCE_INSTALL_TARGET_ID, targetResourceId)
            .putStringArrayListExtra(
                EXTRA_RESOURCE_INSTALL_PLAN_IDS,
                java.util.ArrayList(planResourceIds)
            )

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
