package com.kite.app.ui.logs

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.kite.app.R
import com.kite.app.foundation.logging.Logger
import com.kite.app.ui.UiActionRole
import com.kite.app.ui.UiKit
import com.kite.app.ui.UiTextRole
import com.kite.app.ui.theme.kiteThemeEnvironment

/** 日志查看页。页面只更新既有 adapter，不在绑定或绘制阶段读取其他运行事实。 */
class LogActivity : AppCompatActivity() {
    private lateinit var ui: UiKit
    private lateinit var pathView: TextView
    private lateinit var logsAdapter: LogEntryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val environment = kiteThemeEnvironment()
        ui = UiKit(this, environment)
        logsAdapter = LogEntryAdapter()
        setContentView(buildContent())

        Logger.i("LogActivity", "打开日志页面")
        pathView.text = Logger.getLogFilePath()
        loadLogs()
    }

    private fun buildContent(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(ui.tokens.pageBackground)
        addView(ui.topBar(
            context = this@LogActivity,
            title = getString(R.string.log_title),
            onBack = ::finish,
            trailingAction = ui.imageButton(
                context = this@LogActivity,
                iconRes = R.drawable.ic_refresh_light,
                contentDescription = getString(R.string.log_refresh),
                onClick = {
                    Logger.i("LogActivity", "用户点击刷新日志")
                    loadLogs()
                },
            ),
        ))
        addView(LinearLayout(this@LogActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                ui.dp(ui.foundations.spacing.pageHorizontal),
                ui.dp(ui.foundations.spacing.sectionGap),
                ui.dp(ui.foundations.spacing.pageHorizontal),
                ui.dp(28),
            )
            addView(pathCard())
            addView(sectionTitle(getString(R.string.log_recent_title)))
            addView(logList(), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun pathCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16))
        background = ui.containerBackground(
            ui.tokens.cardBackground,
            ui.tokens.border,
            ui.components.card,
        )
        elevation = ui.dp(ui.components.card.elevation).toFloat()
        addView(TextView(this@LogActivity).apply {
            text = getString(R.string.log_file_title)
            ui.applyTextRole(this, UiTextRole.CardTitle)
        })
        pathView = TextView(this@LogActivity).apply {
            ui.applyTextRole(this, UiTextRole.Supporting)
            typeface = Typeface.MONOSPACE
            setPadding(0, ui.dp(6), 0, 0)
            maxLines = 2
        }
        addView(pathView)
        addView(LinearLayout(this@LogActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(actionButton(
                label = getString(R.string.log_share),
                role = UiActionRole.Primary,
                onClick = {
                    Logger.i("LogActivity", "用户点击分享日志")
                    shareLogs()
                },
            ), LinearLayout.LayoutParams(0, ui.dp(48), 1f))
            addView(actionButton(
                label = getString(R.string.log_clear),
                role = UiActionRole.Danger,
                onClick = {
                    Logger.i("LogActivity", "用户点击清空日志")
                    Logger.clear()
                    loadLogs()
                },
            ), LinearLayout.LayoutParams(0, ui.dp(48), 1f).apply {
                setMargins(ui.dp(ui.foundations.spacing.sectionGap), 0, 0, 0)
            })
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, ui.dp(16), 0, 0) })
    }

    private fun sectionTitle(label: String): TextView = TextView(this).apply {
        text = label
        ui.applyTextRole(this, UiTextRole.SectionTitle)
        setPadding(0, ui.dp(22), 0, ui.dp(10))
    }

    private fun logList(): ListView = ListView(this).apply {
        adapter = logsAdapter
        background = ui.containerBackground(
            ui.tokens.cardBackground,
            ui.tokens.border,
            ui.components.card,
        )
        divider = ColorDrawable(ui.tokens.border)
        dividerHeight = ui.dp(1)
        setPadding(ui.dp(6), ui.dp(4), ui.dp(6), ui.dp(4))
        clipToPadding = false
    }

    private fun actionButton(
        label: String,
        role: UiActionRole,
        onClick: () -> Unit,
    ): TextView = TextView(this).apply {
        text = label
        ui.applyActionRole(this, role)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun loadLogs() {
        val items = Logger.getRecentLogs(200).map { entry ->
            "[${entry.time}] [${entry.level.name}] [${entry.tag}] ${entry.msg}"
        }.ifEmpty {
            listOf(getString(R.string.log_empty))
        }
        logsAdapter.replace(items)
    }

    private fun shareLogs() {
        val logs = Logger.getAllLogs().joinToString("\n") { entry ->
            "[${entry.time}] [${entry.level.name}] [${entry.tag}] ${entry.msg}"
        }
        val shareText = "${getString(R.string.log_share_header)}\n==================\n$logs"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.log_title))
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.log_share_chooser)))
    }

    private inner class LogEntryAdapter : BaseAdapter() {
        private val items = mutableListOf<String>()

        fun replace(next: List<String>) {
            items.clear()
            items.addAll(next)
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): String = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
            (convertView as? TextView ?: TextView(this@LogActivity).apply {
                typeface = Typeface.MONOSPACE
                textSize = 12f
                setTextColor(ui.tokens.textPrimary)
                setLineSpacing(ui.dp(3).toFloat(), 1f)
                setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            }).apply {
                text = getItem(position)
            }
    }
}
