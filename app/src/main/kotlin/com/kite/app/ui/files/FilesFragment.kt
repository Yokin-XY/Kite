package com.kite.app.ui.files

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kite.app.R
import com.kite.app.foundation.runtime.FileWatchStore
import com.kite.app.foundation.runtime.ContainerRecord
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FilesFragment : Fragment() {

    private lateinit var tvPath: TextView
    private lateinit var tvFiles: TextView

    private var currentPath: String = ""
    private var lastObservedWatchGeneration: Long = 0L
    private var currentContainer: ContainerRecord? = null
    private var browseJob: Job? = null
    private var browseGeneration: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_files, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
        observeFileWatch()
        browseWorkspace()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        browseJob?.cancel()
        browseJob = null
        FileWatchStore.stopWatching()
    }

    private fun setupViews(view: View) {
        tvPath = view.findViewById(R.id.tvCurrentPath)
        tvFiles = view.findViewById(R.id.tvFileList)

        view.findViewById<View>(R.id.btnWorkspace).setOnClickListener {
            browseWorkspace()
        }

        view.findViewById<View>(R.id.btnContainerRootfs).setOnClickListener {
            browseContainerRootfs()
        }

        view.findViewById<View>(R.id.btnExchange).setOnClickListener {
            browseExchange()
        }

        view.findViewById<View>(R.id.btnLogs).setOnClickListener {
            browseLogs()
        }

        view.findViewById<View>(R.id.btnGoUp).setOnClickListener {
            goUp()
        }

        view.findViewById<View>(R.id.btnRefresh).setOnClickListener {
            browseTo(currentPath)
        }
    }

    private fun browseWorkspace() {
        lifecycleScope.launch {
            val container = withContext(Dispatchers.IO) {
                WorkSurfaceRuntimeBridge.ensureDefaultContainer(requireContext().applicationContext)
            }
            currentContainer = container
            browseTo(container.workspacePath)
        }
    }

    private fun browseContainerRootfs() {
        lifecycleScope.launch {
            val container = withContext(Dispatchers.IO) {
                WorkSurfaceRuntimeBridge.ensureDefaultContainer(requireContext().applicationContext)
            }
            currentContainer = container
            browseTo(container.rootfsPath)
        }
    }

    private fun browseExchange() {
        lifecycleScope.launch {
            val exchangeDir = withContext(Dispatchers.IO) {
                WorkSurfaceRuntimeBridge.ensureExchangeDir(requireContext().applicationContext)
            }
            browseTo(exchangeDir.absolutePath)
        }
    }

    private fun browseLogs() {
        browseTo(WorkSurfaceRuntimeBridge.getLogsDir(requireContext().applicationContext).absolutePath)
    }

    private fun browseTo(path: String) {
        if (path.isBlank()) {
            return
        }

        val generation = ++browseGeneration
        val appContext = requireContext().applicationContext
        val appFilesDir = requireContext().filesDir.absolutePath
        val container = currentContainer
        currentPath = path
        tvPath.text = path
        tvFiles.text = "[读取中]\n"

        browseJob?.cancel()
        browseJob = lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                FileWatchStore.watchPath(path)
                buildDirectoryContent(
                    path = path,
                    appFilesDir = appFilesDir,
                    appContext = appContext,
                    container = container
                )
            }
            if (generation == browseGeneration && currentPath == path) {
                tvFiles.text = content
            }
        }
    }

    private fun buildDirectoryContent(
        path: String,
        appFilesDir: String,
        appContext: android.content.Context,
        container: ContainerRecord?
    ): String {
        val directory = File(path)
        val content = StringBuilder()

        if (directory.parent != null && path != appFilesDir) {
            content.append("[..] 返回上一级目录\n\n")
        }

        if (!directory.exists() || !directory.isDirectory) {
            content.append("[错误] 当前目录不存在或不可访问\n")
            return content.toString()
        }

        val entries = directory.listFiles()
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .orEmpty()
        val visibleEntries = entries.take(MAX_VISIBLE_FILE_ROWS)

        visibleEntries.forEach { file ->
            val prefix = if (file.isDirectory) "[目录]" else "[文件]"
            val sizeInfo = if (file.isFile) "  ${formatSize(file.length())}" else ""
            content.append("$prefix ${file.name}$sizeInfo\n")
        }
        if (entries.size > visibleEntries.size) {
            content.append("[已省略] 还有 ${entries.size - visibleEntries.size} 项，请进入子目录或刷新查看\n")
        }

        val isEmptyDirectory = entries.isEmpty()
        if (isEmptyDirectory) {
            content.append("[空目录]\n")
        }

        content.append("\n\n")
        content.append(
            WorkSurfaceRuntimeBridge.buildPathHint(
                context = appContext,
                path = path,
                isEmptyDirectory = isEmptyDirectory,
                container = container
            )
        )
        return content.toString()
    }

    private fun observeFileWatch() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                FileWatchStore.snapshot.collectLatest { latest ->
                    if (!latest.isWatching || latest.rootPath != currentPath) {
                        return@collectLatest
                    }
                    if (latest.generation <= lastObservedWatchGeneration) {
                        return@collectLatest
                    }
                    lastObservedWatchGeneration = latest.generation
                    browseTo(currentPath)
                }
            }
        }
    }

    private fun goUp() {
        val parent = File(currentPath).parent
        if (!parent.isNullOrBlank()) {
            browseTo(parent)
        }
    }

    private fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${size / 1024}KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)}MB"
            else -> "${size / (1024 * 1024 * 1024)}GB"
        }
    }

    companion object {
        private const val MAX_VISIBLE_FILE_ROWS = 400
    }
}
