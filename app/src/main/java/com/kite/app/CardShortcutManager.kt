package com.kite.app

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import com.kite.app.recipe.KiteRecipe

object CardShortcutManager {
    fun iconBitmap(recipe: KiteRecipe): Bitmap = shortcutBitmap(recipe)

    fun hasPinnedShortcut(context: Context, recipeId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
        val id = shortcutId(recipeId)
        return shortcutManager.pinnedShortcuts.any { it.id == id } ||
            shortcutManager.dynamicShortcuts.any { it.id == id }
    }

    fun requestPinnedShortcut(context: Context, recipe: KiteRecipe): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return requestLegacyInstallShortcut(context, recipe)
        }
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!shortcutManager.isRequestPinShortcutSupported) {
            return requestLegacyInstallShortcut(context, recipe)
        }

        val shortcutId = shortcutId(recipe.id)
        val shortcut = ShortcutInfo.Builder(context, shortcutId)
            .setShortLabel(recipe.name.take(10).ifBlank { "Kite" })
            .setLongLabel(recipe.name.ifBlank { "Kite 卡片" })
            .setIcon(Icon.createWithBitmap(shortcutBitmap(recipe)))
            .setActivity(ComponentName(context, CardRunActivity::class.java))
            .setIntent(
                CardRunIntents.launchIntent(
                    context = context,
                    recipeId = recipe.id,
                    launchSource = CardRunIntents.SOURCE_SHORTCUT,
                    autoStart = true
                )
            )
            .build()

        runCatching {
            val exists = shortcutManager.dynamicShortcuts.any { it.id == shortcutId } ||
                shortcutManager.pinnedShortcuts.any { it.id == shortcutId }
            if (exists) {
                shortcutManager.updateShortcuts(listOf(shortcut))
            } else {
                shortcutManager.addDynamicShortcuts(listOf(shortcut))
            }
        }
        if (shortcutManager.pinnedShortcuts.any { it.id == shortcutId }) {
            return true
        }

        val callbackIntent = Intent(context, CardShortcutPinnedReceiver::class.java)
            .putExtra(CardRunIntents.EXTRA_RECIPE_ID, recipe.id)
            .putExtra("shortcutId", shortcutId)
        val callback = PendingIntent.getBroadcast(
            context,
            shortcutId.hashCode(),
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return shortcutManager.requestPinShortcut(shortcut, callback.intentSender)
    }

    private fun requestLegacyInstallShortcut(context: Context, recipe: KiteRecipe): Boolean {
        val launchIntent = CardRunIntents.launchIntent(
            context = context,
            recipeId = recipe.id,
            launchSource = CardRunIntents.SOURCE_SHORTCUT,
            autoStart = true
        ).apply {
            removeFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            removeFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            removeFlags(Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val shortcutIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT")
            .putExtra(Intent.EXTRA_SHORTCUT_NAME, recipe.name.ifBlank { "Kite 卡片" })
            .putExtra(Intent.EXTRA_SHORTCUT_ICON, shortcutBitmap(recipe))
            .putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
            .putExtra("duplicate", false)
        return runCatching {
            context.sendBroadcast(shortcutIntent)
            true
        }.getOrDefault(false)
    }

    private fun shortcutId(recipeId: String): String =
        "kite_card_$recipeId".replace(Regex("[^a-zA-Z0-9_.-]"), "_")

    private fun shortcutBitmap(recipe: KiteRecipe): Bitmap {
        val size = 144
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val iconName = recipe.icon.name
        val backgroundColor = when (iconName) {
            "terminal", "code", "server" -> Color.rgb(29, 78, 216)
            "bot", "tools" -> Color.rgb(126, 34, 206)
            "file" -> Color.rgb(180, 83, 9)
            else -> Color.rgb(5, 150, 105)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        val bounds = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(bounds, 34f, 34f, paint)

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = if (iconName == "terminal" || iconName == "code") 38f else 44f
        val glyph = shortcutGlyph(iconName)
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(glyph, size / 2f, y, paint)
        return bitmap
    }

    private fun shortcutGlyph(iconName: String): String = when (iconName) {
        "terminal" -> ">_"
        "web" -> "◎"
        "bot" -> "AI"
        "file" -> "文"
        "music" -> "♪"
        "shopping" -> "购"
        "logs" -> "日"
        "tools" -> "⚙"
        "code" -> "{ }"
        "server" -> "▷"
        else -> "◎"
    }
}
