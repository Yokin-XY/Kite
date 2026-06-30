package com.kite.app.ui.logs

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.kite.app.R
import com.kite.app.foundation.logging.Logger

/**
 * KFShell 日志查看页。
 *
 * 用途：
 * - 查看 App 侧运行日志
 * - 快速确认容器准备、会话启动、命令发送等关键动作
 * - 在需要排障时导出完整日志
 */
class LogActivity : AppCompatActivity() {

    private lateinit var tvLogPath: TextView
    private lateinit var lvLogs: ListView
    private lateinit var btnRefresh: Button
    private lateinit var btnClear: Button
    private lateinit var btnShare: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        Logger.i("LogActivity", "打开日志页面")

        initViews()
        loadLogs()
    }

    private fun initViews() {
        tvLogPath = findViewById(R.id.tvLogPath)
        lvLogs = findViewById(R.id.lvLogs)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnClear = findViewById(R.id.btnClear)
        btnShare = findViewById(R.id.btnShare)

        tvLogPath.text = getString(R.string.log_path_prefix) + Logger.getLogFilePath()

        btnRefresh.setOnClickListener {
            Logger.i("LogActivity", "用户点击刷新日志")
            loadLogs()
        }

        btnClear.setOnClickListener {
            Logger.i("LogActivity", "用户点击清空日志")
            Logger.clear()
            loadLogs()
        }

        btnShare.setOnClickListener {
            Logger.i("LogActivity", "用户点击分享日志")
            shareLogs()
        }
    }

    private fun loadLogs() {
        val items = Logger.getRecentLogs(200).map { entry ->
            "[${entry.time}] [${entry.level.name}] [${entry.tag}] ${entry.msg}"
        }.ifEmpty {
            listOf(getString(R.string.log_empty))
        }

        val adapter = ArrayAdapter(
            this,
            R.layout.item_log_entry,
            R.id.tvLogEntry,
            items
        )
        lvLogs.adapter = adapter
    }

    private fun shareLogs() {
        val logs = Logger.getAllLogs().joinToString("\n") { entry ->
            "[${entry.time}] [${entry.level.name}] [${entry.tag}] ${entry.msg}"
        }

        val shareText = """
            KFShell 运行日志
            ==================
            $logs
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.log_title))
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

        startActivity(Intent.createChooser(intent, getString(R.string.log_share_chooser)))
    }
}
