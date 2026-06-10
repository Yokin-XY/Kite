package com.kftest.app.foundation.runtime

import android.os.FileObserver
import com.kftest.app.foundation.logging.Logger
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class FileWatchSnapshot(
    val rootPath: String? = null,
    val isWatching: Boolean = false,
    val generation: Long = 0L,
    val lastEventAt: Long = 0L,
    val lastEventPath: String? = null,
    val lastEventLabel: String? = null
)

object FileWatchStore {

    private const val LOG_TAG = "FileWatchStore"
    private const val EVENT_SETTLE_DELAY_MS = 220L
    private const val MAX_DIRECT_CHILD_DIRECTORY_OBSERVERS = 64
    private val GENERATED_RUNTIME_FILE_NAMES = setOf(
        "runtime-pressure.env",
        "runtime-process-table.tsv",
        "runtime-resource-event-ledger.json",
        "runtime-lifecycle-action-inbox.json",
        "proot-launch-contract.json",
        "proot-pool-tuning.jsonl"
    )
    private val GENERATED_RUNTIME_PATH_SEGMENTS = listOf(
        "/.kf/system/",
        "/.kf/proc/"
    )
    private const val WATCH_MASK =
        FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.DELETE_SELF or
            FileObserver.MODIFY or
            FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or
            FileObserver.MOVE_SELF or
            FileObserver.CLOSE_WRITE or
            FileObserver.ATTRIB

    private data class WatchSession(
        val rootPath: String,
        val observers: MutableMap<String, FileObserver>
    ) {
        fun stopAll() {
            observers.values.forEach { observer ->
                runCatching { observer.stopWatching() }
            }
            observers.clear()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _snapshot = MutableStateFlow(FileWatchSnapshot())
    val snapshot: StateFlow<FileWatchSnapshot> = _snapshot

    @Volatile
    private var session: WatchSession? = null

    @Volatile
    private var pendingDispatchJob: Job? = null

    @Synchronized
    fun watchPath(path: String) {
        val directory = File(path)
        val canonicalPath = runCatching { directory.canonicalPath }.getOrElse { directory.absolutePath }
        if (!directory.exists() || !directory.isDirectory) {
            Logger.i(LOG_TAG, "目录不可监听，已停止: $canonicalPath")
            stopWatching()
            return
        }

        val existing = session
        if (existing?.rootPath == canonicalPath) {
            existing?.let { syncObservers(it, directory) }
            return
        }

        existing?.stopAll()
        val newSession = WatchSession(
            rootPath = canonicalPath,
            observers = linkedMapOf()
        )
        session = newSession
        syncObservers(newSession, directory)
        _snapshot.value = _snapshot.value.copy(
            rootPath = canonicalPath,
            isWatching = true
        )
        Logger.i(LOG_TAG, "已开始监听目录: $canonicalPath")
    }

    @Synchronized
    fun stopWatching() {
        pendingDispatchJob?.cancel()
        pendingDispatchJob = null
        session?.stopAll()
        session = null
        _snapshot.value = FileWatchSnapshot()
    }

    @Synchronized
    private fun syncObservers(currentSession: WatchSession, rootDirectory: File) {
        val directories = buildSet {
            add(runCatching { rootDirectory.canonicalPath }.getOrElse { rootDirectory.absolutePath })
            rootDirectory.listFiles()
                ?.asSequence()
                ?.filter { child -> child.isDirectory }
                ?.take(MAX_DIRECT_CHILD_DIRECTORY_OBSERVERS)
                ?.forEach { directory ->
                    add(runCatching { directory.canonicalPath }.getOrElse { directory.absolutePath })
                }
        }

        val removed = currentSession.observers.keys - directories
        removed.forEach { path ->
            currentSession.observers.remove(path)?.stopWatching()
        }

        val added = directories - currentSession.observers.keys
        added.forEach { path ->
            currentSession.observers[path] = buildObserver(currentSession, File(path)).also {
                it.startWatching()
            }
        }
    }

    private fun buildObserver(
        currentSession: WatchSession,
        directory: File
    ): FileObserver {
        return object : FileObserver(directory.absolutePath, WATCH_MASK) {
            override fun onEvent(event: Int, path: String?) {
                if (event == 0) {
                    return
                }
                val eventPath = path
                    ?.takeIf { it.isNotBlank() }
                    ?.let { child -> File(directory, child).absolutePath }
                    ?: directory.absolutePath
                val eventLabel = describeEvent(event)
                scheduleDispatch(currentSession, eventPath, eventLabel)
                if (affectsDirectoryGraph(event)) {
                    scope.launch {
                        synchronized(this@FileWatchStore) {
                            if (session?.rootPath == currentSession.rootPath) {
                                syncObservers(currentSession, File(currentSession.rootPath))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun affectsDirectoryGraph(event: Int): Boolean {
        val masked = event and FileObserver.ALL_EVENTS
        return masked == FileObserver.CREATE ||
            masked == FileObserver.DELETE ||
            masked == FileObserver.DELETE_SELF ||
            masked == FileObserver.MOVED_FROM ||
            masked == FileObserver.MOVED_TO ||
            masked == FileObserver.MOVE_SELF
    }

    @Synchronized
    private fun scheduleDispatch(
        currentSession: WatchSession,
        eventPath: String,
        eventLabel: String
    ) {
        pendingDispatchJob?.cancel()
        pendingDispatchJob = scope.launch {
            delay(EVENT_SETTLE_DELAY_MS)
            if (session?.rootPath != currentSession.rootPath) {
                return@launch
            }
            if (isGeneratedRuntimeEvent(eventPath)) {
                return@launch
            }
            val now = System.currentTimeMillis()
            _snapshot.value = _snapshot.value.copy(
                rootPath = currentSession.rootPath,
                isWatching = true,
                generation = _snapshot.value.generation + 1,
                lastEventAt = now,
                lastEventPath = eventPath,
                lastEventLabel = eventLabel
            )
            Logger.i(LOG_TAG, "目录变更: root=${currentSession.rootPath} event=$eventLabel path=$eventPath")
        }
    }

    private fun isGeneratedRuntimeEvent(eventPath: String): Boolean {
        val normalized = eventPath.replace('\\', '/')
        return GENERATED_RUNTIME_FILE_NAMES.any { fileName ->
            normalized.endsWith("/.kf/$fileName")
        } || normalized.endsWith("/.kf/system") ||
            normalized.endsWith("/.kf/proc") ||
            GENERATED_RUNTIME_PATH_SEGMENTS.any { segment ->
                normalized.contains(segment)
        }
    }

    private fun describeEvent(event: Int): String {
        return when (event and FileObserver.ALL_EVENTS) {
            FileObserver.CREATE -> "create"
            FileObserver.DELETE -> "delete"
            FileObserver.DELETE_SELF -> "delete_self"
            FileObserver.MODIFY -> "modify"
            FileObserver.MOVED_FROM -> "moved_from"
            FileObserver.MOVED_TO -> "moved_to"
            FileObserver.MOVE_SELF -> "move_self"
            FileObserver.CLOSE_WRITE -> "close_write"
            FileObserver.ATTRIB -> "attrib"
            else -> "unknown"
        }
    }
}
