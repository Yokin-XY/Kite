package com.kite.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.kite.app.diagnostics.KiteDiagnostics

class CardShortcutPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recipeId = intent.getStringExtra(CardRunIntents.EXTRA_RECIPE_ID).orEmpty()
        val shortcutId = intent.getStringExtra("shortcutId").orEmpty()
        KiteDiagnostics(context).logRecipeEvent(
            "card_shortcut_pin_confirmed",
            null,
            mapOf(
                "recipeId" to recipeId,
                "shortcutId" to shortcutId
            )
        )
        Toast.makeText(context.applicationContext, "桌面快捷方式已创建", Toast.LENGTH_SHORT).show()
    }
}
