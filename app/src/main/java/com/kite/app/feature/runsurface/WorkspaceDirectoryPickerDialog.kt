package com.kite.app.feature.runsurface

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kite.app.R
import com.kite.app.foundation.workspace.KiteStorageContract
import com.kite.app.foundation.workspace.WorkspaceDirectoryBrowser
import com.kite.app.foundation.workspace.WorkspaceDirectoryEntry
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiActionRole
import com.kite.app.ui.UiKit
import com.kite.app.ui.UiMenuItem
import com.kite.app.ui.UiTextRole
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Kite 自有的 Agent 项目目录选择页。
 *
 * 页面只投影 `/workspace`，没有安卓存储入口。文件读取和新建均在 IO 调度器执行，
 * RecyclerView 只差量绑定当前一级目录。
 */
internal class WorkspaceDirectoryPickerDialog(
    private val context: Context,
    tokens: ThemeTokens,
    private val scope: CoroutineScope,
    private val hostWorkspaceRoot: File,
    initialContainerPath: String,
    private val pageTitle: String = "工作目录",
    private val selectionLabel: String = "当前目录",
    private val actionLabel: String = "选择",
    private val allowCreateDirectory: Boolean = true,
    private val onSelected: (String) -> Unit,
) {
    private val ui = UiKit(context, tokens)
    private val tokens = tokens
    private val dialog = Dialog(context, R.style.Theme_Kite)
    private val adapter = DirectoryAdapter(context, tokens, ::openDirectory)
    private val list = RecyclerView(context)
    private val status = TextView(context)
    private val breadcrumbScroll = HorizontalScrollView(context)
    private val breadcrumbHost = LinearLayout(context)
    private val selectionPath = TextView(context)
    private val useButton = TextView(context)
    private val sortButton = ImageView(context)
    private var currentEntries: List<WorkspaceDirectoryEntry> = emptyList()
    private var sortField = DirectorySortField.Name
    private var sortAscending = true
    private var sortMenu: PopupWindow? = null
    private var currentPath = initialPath(initialContainerPath)
    private var renderedBreadcrumbPath: String? = null
    private var loadingJob: Job? = null
    private var loadRevision: Long = 0L
    private var directoryLoading: Boolean = false

    fun show() {
        dialog.setContentView(buildContent())
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                navigateBack()
                true
            } else {
                false
            }
        }
        dialog.setOnDismissListener {
            loadingJob?.cancel()
            loadingJob = null
            sortMenu?.dismiss()
            sortMenu = null
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(tokens.pageBackground))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            statusBarColor = tokens.pageBackground
            navigationBarColor = tokens.pageBackground
        }
        loadCurrentDirectory()
    }

    fun dismiss() = dialog.dismiss()

    private fun buildContent(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(tokens.pageBackground)
        addView(buildTopBar())
        addView(buildBreadcrumb())
        addView(buildDirectoryViewport(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
        addView(buildSelectionBar())
    }

    private fun buildTopBar(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(20), ui.dp(14), ui.dp(20), ui.dp(8))

        addView(roundIconButton(
            iconRes = R.drawable.ic_arrow_back_light,
            description = "返回",
            onClick = ::navigateBack,
        ), LinearLayout.LayoutParams(ui.dp(52), ui.dp(52)))

        addView(TextView(context).apply {
            text = pageTitle
            ui.applyTextRole(this, UiTextRole.PageTitle)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }, LinearLayout.LayoutParams(0, ui.dp(52), 1f))

        if (allowCreateDirectory) {
            addView(plainIconButton(
                iconRes = R.drawable.ic_create_folder_outline,
                description = "新建文件夹",
                onClick = { showCreateDirectoryDialog() },
            ), LinearLayout.LayoutParams(ui.dp(48), ui.dp(52)))
        } else {
            addView(View(context), LinearLayout.LayoutParams(ui.dp(48), ui.dp(52)))
        }

        addView(sortButton.apply {
            setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_tune_outline))
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ui.dp(11), ui.dp(11), ui.dp(11), ui.dp(11))
            contentDescription = sortDescription()
            isClickable = true
            isFocusable = true
            setOnClickListener { showSortMenu(this) }
        }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(52)))
    }

    private fun buildBreadcrumb(): View = breadcrumbScroll.apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        clipToPadding = false
        setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(16))
        addView(breadcrumbHost.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ui.dp(42),
        ))
    }

    private fun buildDirectoryViewport(): View = FrameLayout(context).apply {
        list.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WorkspaceDirectoryPickerDialog.adapter
            setHasFixedSize(true)
            clipToPadding = false
            setPadding(0, ui.dp(2), 0, ui.dp(12))
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        addView(list, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        addView(status.apply {
            ui.applyTextRole(this, UiTextRole.Supporting)
            gravity = Gravity.CENTER
            setPadding(ui.dp(32), ui.dp(32), ui.dp(32), ui.dp(32))
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))
    }

    private fun buildSelectionBar(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(16), 0, ui.dp(10), 0)
        background = ui.containerBackground(
            tokens.cardBackground,
            Color.TRANSPARENT,
            ui.components.dialog,
        )
        elevation = ui.dp(ui.components.dialog.elevation).toFloat()

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = selectionLabel
                ui.applyTextRole(this, UiTextRole.Supporting)
            })
            addView(selectionPath.apply {
                ui.applyTextRole(this, UiTextRole.Body)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
                setPadding(0, ui.dp(2), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(ui.dp(2), 0, ui.dp(10), 0)
        })

        addView(useButton.apply {
            text = actionLabel
            ui.applyActionRole(this, UiActionRole.Primary)
            setOnClickListener {
                if (!canSelectCurrentPath()) return@setOnClickListener
                val selected = currentPath
                dialog.dismiss()
                onSelected(selected)
            }
        }, LinearLayout.LayoutParams(ui.dp(88), ui.dp(46)))
    }.also { bar ->
        bar.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ui.dp(76),
        ).apply {
            setMargins(ui.dp(24), ui.dp(10), ui.dp(24), ui.dp(20))
        }
    }

    private fun roundIconButton(iconRes: Int, description: String, onClick: () -> Unit): ImageView =
        ImageView(context).apply {
            setImageDrawable(AppCompatResources.getDrawable(context, iconRes))
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14))
            contentDescription = description
            background = ui.roundedBox(
                fill = tokens.cardBackground,
                stroke = Color.TRANSPARENT,
                radius = ui.dp(26).toFloat(),
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun plainIconButton(
        iconRes: Int,
        description: String,
        onClick: (View) -> Unit,
    ): ImageView = ImageView(context).apply {
        setImageDrawable(AppCompatResources.getDrawable(context, iconRes))
        imageTintList = ColorStateList.valueOf(tokens.textPrimary)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(ui.dp(11), ui.dp(11), ui.dp(11), ui.dp(11))
        contentDescription = description
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick(this) }
    }

    private fun openDirectory(entry: WorkspaceDirectoryEntry) {
        if (directoryLoading) return
        currentPath = entry.containerPath
        loadCurrentDirectory()
    }

    private fun navigateBack() {
        val parent = WorkspaceDirectoryBrowser.parentPath(currentPath)
        if (parent == null) {
            dialog.dismiss()
        } else {
            currentPath = parent
            loadCurrentDirectory()
        }
    }

    private fun loadCurrentDirectory() {
        loadingJob?.cancel()
        val revision = ++loadRevision
        val requestedPath = currentPath
        directoryLoading = true
        prepareLoading()
        loadingJob = scope.launch {
            val loadingFeedback = launch {
                delay(LOADING_FEEDBACK_DELAY_MS)
                if (revision == loadRevision && dialog.isShowing) renderLoading()
            }
            try {
                val entries = withContext(Dispatchers.IO) {
                    if (!hostWorkspaceRoot.exists() && !hostWorkspaceRoot.mkdirs()) {
                        error("无法准备 Ubuntu 工作目录")
                    }
                    WorkspaceDirectoryBrowser.listDirectories(hostWorkspaceRoot, requestedPath)
                }
                if (!dialog.isShowing || revision != loadRevision) return@launch
                directoryLoading = false
                renderDirectories(entries)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (!dialog.isShowing || revision != loadRevision) return@launch
                directoryLoading = false
                renderFailure(error)
            } finally {
                loadingFeedback.cancel()
            }
        }
    }

    private fun prepareLoading() {
        status.isVisible = false
        list.isVisible = adapter.itemCount > 0
        updateUseButton()
    }

    private fun renderLoading() {
        renderCurrentPath()
        status.text = "正在读取工作区…"
        status.isVisible = true
        list.isVisible = false
        updateUseButton()
    }

    private fun renderDirectories(entries: List<WorkspaceDirectoryEntry>) {
        renderCurrentPath()
        currentEntries = entries
        adapter.submitList(sortedEntries())
        list.isVisible = entries.isNotEmpty()
        status.isVisible = entries.isEmpty()
        status.text = if (currentPath == KiteStorageContract.CONTAINER_WORKSPACE_ROOT) {
            if (allowCreateDirectory) "还没有可选项目\n点击右上角 + 新建文件夹" else "还没有可选文件夹"
        } else {
            "这个目录中没有子文件夹\n可以直接选择当前目录"
        }
        updateUseButton()
    }

    private fun renderFailure(error: Throwable) {
        renderCurrentPath()
        currentEntries = emptyList()
        adapter.submitList(emptyList())
        list.isVisible = false
        status.isVisible = true
        status.text = error.message ?: "无法读取这个目录"
        updateUseButton()
    }

    private fun renderCurrentPath() {
        renderBreadcrumb()
        selectionPath.text = currentPath
    }

    private fun renderBreadcrumb() {
        if (renderedBreadcrumbPath == currentPath && breadcrumbHost.childCount > 0) return
        breadcrumbHost.removeAllViews()
        val entries = buildList {
            add("Kite Ubuntu" to KiteStorageContract.CONTAINER_WORKSPACE_ROOT)
            var path = KiteStorageContract.CONTAINER_WORKSPACE_ROOT
            currentPath.removePrefix(KiteStorageContract.CONTAINER_WORKSPACE_ROOT)
                .trim('/')
                .split('/')
                .filter(String::isNotBlank)
                .forEach { name ->
                    path = "$path/$name"
                    add(name to path)
                }
        }
        entries.forEachIndexed { index, (label, path) ->
            if (index > 0) {
                breadcrumbHost.addView(ImageView(context).apply {
                    setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_chevron_right_light))
                    imageTintList = ColorStateList.valueOf(tokens.textTertiary)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    contentDescription = null
                }, LinearLayout.LayoutParams(ui.dp(24), ui.dp(42)).apply {
                    setMargins(ui.dp(4), 0, ui.dp(4), 0)
                })
            }
            val current = index == entries.lastIndex
            breadcrumbHost.addView(TextView(context).apply {
                text = label
                ui.applyTextRole(this, UiTextRole.Body)
                setTextColor(if (current) tokens.textPrimary else tokens.textSecondary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = ui.dp(172)
                gravity = Gravity.CENTER
                setPadding(ui.dp(14), 0, ui.dp(14), 0)
                background = ui.roundedBox(
                    fill = tokens.cardBackground,
                    stroke = Color.TRANSPARENT,
                    radius = ui.dp(21).toFloat(),
                )
                isClickable = !current
                isFocusable = !current
                if (!current) {
                    contentDescription = "返回到 $label"
                    setOnClickListener {
                        currentPath = path
                        loadCurrentDirectory()
                    }
                }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ui.dp(42),
            ))
        }
        renderedBreadcrumbPath = currentPath
        breadcrumbScroll.post { breadcrumbScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun updateUseButton() {
        val enabled = !directoryLoading && canSelectCurrentPath()
        useButton.isEnabled = enabled
        useButton.alpha = if (enabled) 1f else 0.34f
        useButton.contentDescription = if (enabled) "$actionLabel $currentPath" else "请先进入工作区文件夹"
    }

    private fun canSelectCurrentPath(): Boolean =
        KiteStorageContract.isSelectableProjectPath(currentPath)

    private fun showSortMenu(anchor: View) {
        sortMenu?.dismiss()
        val popup = ui.showAnchoredMenu(
            context = context,
            anchor = anchor,
            widthDp = 224,
            items = listOf(
                sortFieldItem("名称", DirectorySortField.Name),
                sortFieldItem("修改时间", DirectorySortField.Modified),
                sortFieldItem("项目数量", DirectorySortField.ItemCount),
                sortDirectionItem("正序", ascending = true),
                sortDirectionItem("倒序", ascending = false),
            ),
        )
        sortMenu = popup
        popup.setOnDismissListener {
            if (sortMenu === popup) sortMenu = null
        }
    }

    private fun sortFieldItem(label: String, field: DirectorySortField): UiMenuItem = UiMenuItem(
        label = label,
        selected = sortField == field,
        checkable = true,
        onClick = {
            sortField = field
            applySorting()
        },
    )

    private fun sortDirectionItem(label: String, ascending: Boolean): UiMenuItem = UiMenuItem(
        label = label,
        selected = sortAscending == ascending,
        checkable = true,
        onClick = {
            sortAscending = ascending
            applySorting()
        },
    )

    private fun applySorting() {
        sortButton.contentDescription = sortDescription()
        adapter.submitList(sortedEntries())
    }

    private fun sortedEntries(): List<WorkspaceDirectoryEntry> {
        val nameComparator = compareBy<WorkspaceDirectoryEntry> { it.name.lowercase(Locale.ROOT) }
            .thenBy { it.name }
        val comparator = when (sortField) {
            DirectorySortField.Name -> nameComparator
            DirectorySortField.Modified -> compareBy<WorkspaceDirectoryEntry> { it.lastModified }
                .then(nameComparator)
            DirectorySortField.ItemCount -> compareBy<WorkspaceDirectoryEntry> { it.itemCount }
                .then(nameComparator)
        }
        return currentEntries.sortedWith(if (sortAscending) comparator else comparator.reversed())
    }

    private fun sortDescription(): String {
        val field = when (sortField) {
            DirectorySortField.Name -> "名称"
            DirectorySortField.Modified -> "修改时间"
            DirectorySortField.ItemCount -> "项目数量"
        }
        return "排序：$field，${if (sortAscending) "正序" else "倒序"}"
    }

    private fun showCreateDirectoryDialog() {
        var createPending = false
        ui.showTextInputDialog(
            context = context,
            title = "新建文件夹",
            hint = "文件夹名称",
            dismissLabel = "取消",
            confirmLabel = "新建",
        ) { name, handle ->
            if (createPending) return@showTextInputDialog
            if (name.isBlank()) {
                handle.showError("请输入文件夹名称")
                return@showTextInputDialog
            }
            createPending = true
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        WorkspaceDirectoryBrowser.createDirectory(hostWorkspaceRoot, currentPath, name)
                    }
                }
                result.fold(
                    onSuccess = { created ->
                        handle.dismiss()
                        currentPath = created.containerPath
                        loadCurrentDirectory()
                    },
                    onFailure = { error ->
                        createPending = false
                        handle.showError(error.message ?: "无法新建文件夹")
                    },
                )
            }
        }
    }

    private fun initialPath(rawPath: String): String {
        val normalized = KiteStorageContract.normalizeWorkspacePath(rawPath)
            ?: return KiteStorageContract.CONTAINER_WORKSPACE_ROOT
        val hostDirectory = KiteStorageContract.resolveHostWorkspacePath(hostWorkspaceRoot, normalized)
        return normalized.takeIf {
            it == KiteStorageContract.CONTAINER_WORKSPACE_ROOT ||
                (KiteStorageContract.isSelectableProjectPath(it) && hostDirectory?.isDirectory == true)
        } ?: KiteStorageContract.CONTAINER_WORKSPACE_ROOT
    }

    private companion object {
        const val LOADING_FEEDBACK_DELAY_MS = 140L
    }
}

private enum class DirectorySortField {
    Name,
    Modified,
    ItemCount,
}

private class DirectoryAdapter(
    private val context: Context,
    private val tokens: ThemeTokens,
    private val onClick: (WorkspaceDirectoryEntry) -> Unit,
) : ListAdapter<WorkspaceDirectoryEntry, DirectoryAdapter.Holder>(Diff) {
    private val ui = UiKit(context, tokens)
    private val dayFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val title = TextView(context)
        val metadata = TextView(context)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(24), 0, ui.dp(20), 0)
            background = ui.roundedBox(Color.TRANSPARENT, Color.TRANSPARENT, 0f)
            isClickable = true
            isFocusable = true

            addView(ImageView(context).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_files))
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                background = ui.roundedBox(
                    fill = tokens.cardBackground,
                    stroke = Color.TRANSPARENT,
                    radius = ui.dp(25).toFloat(),
                )
                setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9))
            }, LinearLayout.LayoutParams(ui.dp(50), ui.dp(50)))

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(title.apply {
                    ui.applyTextRole(this, UiTextRole.CardTitle)
                    typeface = Typeface.DEFAULT
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
                addView(metadata.apply {
                    ui.applyTextRole(this, UiTextRole.Supporting)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, ui.dp(3), 0, 0)
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(ui.dp(18), 0, ui.dp(8), 0)
            })

            addView(ImageView(context).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_chevron_right_light))
                imageTintList = ColorStateList.valueOf(tokens.textTertiary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }, LinearLayout.LayoutParams(ui.dp(22), ui.dp(44)))
        }
        row.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ui.dp(84),
        ).apply {
            setMargins(0, ui.dp(1), 0, ui.dp(1))
        }
        return Holder(row, title, metadata)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = getItem(position)
        holder.title.text = entry.name
        holder.metadata.text = "${formatModified(entry.lastModified)}  |  ${entry.itemCount} 项"
        holder.itemView.contentDescription = "打开文件夹 ${entry.name}"
        holder.itemView.setOnClickListener { onClick(entry) }
    }

    private fun formatModified(lastModified: Long): String {
        val date = Date(lastModified)
        return if (android.text.format.DateUtils.isToday(lastModified)) {
            timeFormat.format(date)
        } else {
            dayFormat.format(date)
        }
    }

    class Holder(itemView: View, val title: TextView, val metadata: TextView) :
        RecyclerView.ViewHolder(itemView)

    private object Diff : DiffUtil.ItemCallback<WorkspaceDirectoryEntry>() {
        override fun areItemsTheSame(
            oldItem: WorkspaceDirectoryEntry,
            newItem: WorkspaceDirectoryEntry,
        ): Boolean = oldItem.containerPath == newItem.containerPath

        override fun areContentsTheSame(
            oldItem: WorkspaceDirectoryEntry,
            newItem: WorkspaceDirectoryEntry,
        ): Boolean = oldItem == newItem
    }
}
