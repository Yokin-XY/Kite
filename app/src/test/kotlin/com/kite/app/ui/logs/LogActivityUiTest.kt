package com.kite.app.ui.logs

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.kite.app.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LogActivityUiTest {
    @Test
    fun `日志页使用标准页头和分层动作且保留日志路径`() {
        val activity = Robolectric.buildActivity(LogActivity::class.java).setup().get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val texts = content.allTexts()
        val descriptions = content.allDescriptions()

        assertTrue(texts.contains(activity.getString(R.string.log_title)))
        assertTrue(texts.contains(activity.getString(R.string.log_file_title)))
        assertTrue(texts.contains(activity.getString(R.string.log_recent_title)))
        assertTrue(texts.contains(activity.getString(R.string.log_share)))
        assertTrue(texts.contains(activity.getString(R.string.log_clear)))
        assertTrue(texts.any { it.contains("kite", ignoreCase = true) && it.contains("log", ignoreCase = true) })
        assertTrue(descriptions.contains(activity.getString(R.string.log_refresh)))
    }

    private fun View.allTexts(): List<String> = buildList {
        if (this@allTexts is TextView) add(text.toString())
        if (this@allTexts is ViewGroup) {
            repeat(childCount) { addAll(getChildAt(it).allTexts()) }
        }
    }

    private fun View.allDescriptions(): List<String> = buildList {
        contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(::add)
        if (this@allDescriptions is ViewGroup) {
            repeat(childCount) { addAll(getChildAt(it).allDescriptions()) }
        }
    }
}
