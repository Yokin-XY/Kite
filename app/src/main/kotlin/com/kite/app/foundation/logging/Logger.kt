package com.kite.app.foundation.logging

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * KFShell 日志系统
 * - 内存缓冲 + 文件持久化
 * - 分级日志 (DEBUG/INFO/ERROR)
 * - 自动管理日志文件大小
 */
object Logger {
    private const val LOG_FILE = "kftest.log"
    private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5MB
    private const val MAX_LINES = 1000

    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile
    private var logDir: File? = null

    enum class Level { DEBUG, INFO, ERROR }

    data class LogEntry(
        val time: String,
        val level: Level,
        val tag: String,
        val msg: String,
        val thread: String
    )

    fun init(context: Context) {
        val dir = File(context.filesDir, "runtime/logs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        logDir = dir
        // 清理旧日志
        cleanOldLogs()
    }

    fun d(tag: String, msg: String) = log(Level.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(Level.INFO, tag, msg)
    fun e(tag: String, msg: String) = log(Level.ERROR, tag, msg)

    private fun log(level: Level, tag: String, msg: String) {
        val entry = LogEntry(
            time = dateFormat.format(Date()),
            level = level,
            tag = tag,
            msg = msg,
            thread = Thread.currentThread().name
        )
        logQueue.offer(entry)

        // 控制队列大小
        while (logQueue.size > MAX_LINES) {
            logQueue.poll()
        }

        // 写入文件
        writeToFile(entry)

        // 打印到 Logcat
        android.util.Log.println(
            when (level) {
                Level.DEBUG -> android.util.Log.DEBUG
                Level.INFO -> android.util.Log.INFO
                Level.ERROR -> android.util.Log.ERROR
            },
            "[KFShell]$tag",
            msg
        )
    }

    private fun writeToFile(entry: LogEntry) {
        try {
            val file = File(requireLogDir(), LOG_FILE)
            if (file.exists() && file.length() > MAX_LOG_SIZE) {
                rotateLog(file)
            }
            FileWriter(file, true).use { fw ->
                fw.write("[${entry.time}] [${entry.level.name}] [${entry.tag}] ${entry.msg}\n")
            }
        } catch (e: Exception) {
            // 静默处理，避免日志写入失败影响主流程
        }
    }

    private fun rotateLog(file: File) {
        val backup = File(requireLogDir(), "kftest.old.log")
        if (backup.exists()) backup.delete()
        file.renameTo(backup)
    }

    private fun cleanOldLogs() {
        val dir = requireLogDir()
        dir.listFiles()?.forEach { f ->
            if (f.name != LOG_FILE && f.name != "kftest.old.log") {
                f.delete()
            }
        }
    }

    fun getRecentLogs(count: Int = 100): List<LogEntry> {
        return logQueue.toList().takeLast(count)
    }

    fun getAllLogs(): List<LogEntry> {
        return logQueue.toList()
    }

    fun getLogFilePath(): String = File(requireLogDir(), LOG_FILE).absolutePath

    fun clear() {
        logQueue.clear()
        File(requireLogDir(), LOG_FILE).delete()
    }

    private fun requireLogDir(): File {
        return logDir ?: File("/data/data/com.kite.app/files/runtime/logs")
    }
}
