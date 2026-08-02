package com.kite.app.feature.runsurface

import android.content.Context
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.contract.AgentSessionSummary
import com.kite.app.agent.store.AgentArchivedSessionMetadata
import com.kite.app.agent.store.AgentProject

/**
 * 会话页面内部的导航和展示状态。
 *
 * 这些类型只描述 Kite 的固定显示结构，不承载任何具体 Agent 的配置规则。
 */
internal enum class ComposerExtensionRoute {
    Main,
    Modes,
    Permissions,
}

internal enum class AgentNavigationScreen {
    Main,
    Drawer,
    SessionSearch,
    Settings,
    DefaultPermission,
    ProviderList,
    ProviderEditor,
    ProviderPresetPicker,
    ProviderModelEditor,
    SkillList,
    McpList,
    McpEditor,
    CoreDocumentList,
    CoreDocumentEditor,
    ArchivedContent,
}

internal data class AgentSessionProjectGroup(
    val cwd: String,
    val name: String,
    val sessions: List<AgentSessionSummary>,
)

internal data class ComposerPresentation(
    val phase: AgentSessionPhase?,
    val cancelling: Boolean,
    val canSend: Boolean,
)

internal data class ComposerModelTextStyle(
    val textSizeSp: Float,
    val maximumWidthDp: Int,
)

internal data class AgentSessionGrouping(
    val defaultCwd: String,
    val defaultSessions: List<AgentSessionSummary>,
    val projects: List<AgentSessionProjectGroup>,
)

internal sealed interface AgentDrawerAction {
    data class NewDraft(val cwd: String) : AgentDrawerAction
    data object ChooseProject : AgentDrawerAction
}

internal sealed interface AgentDrawerRow {
    val key: String

    data class SectionHeader(
        val title: String,
        val actionDescription: String,
        val action: AgentDrawerAction,
        override val key: String,
    ) : AgentDrawerRow

    data class ProjectHeader(
        val project: AgentSessionProjectGroup,
        val expanded: Boolean,
    ) : AgentDrawerRow {
        override val key: String = "project:${project.cwd}"
    }

    data class Session(
        val summary: AgentSessionSummary,
        val inProject: Boolean,
    ) : AgentDrawerRow {
        override val key: String = "session:${summary.id}"
    }

    data class Empty(
        val label: String,
        override val key: String,
    ) : AgentDrawerRow
}

internal sealed interface AgentArchivedRow {
    val key: String

    data class GroupHeader(
        val cwd: String,
        val title: String,
        val subtitle: String?,
        val count: Int,
        val expanded: Boolean,
        val archivedProject: AgentProject? = null,
        val selectableSessionIds: Set<String> = emptySet(),
    ) : AgentArchivedRow {
        override val key: String = "group:$cwd"
    }

    data class Session(val summary: AgentSessionSummary) : AgentArchivedRow {
        override val key: String = "session:${summary.id}"
    }

    data class UnavailableSession(val metadata: AgentArchivedSessionMetadata) : AgentArchivedRow {
        override val key: String = "unavailable-session:${metadata.sessionId}"
    }
}

internal sealed interface AgentDrawerSessionMenuAction {
    data object Rename : AgentDrawerSessionMenuAction
    data object Archive : AgentDrawerSessionMenuAction
    data class Delete(val enabled: Boolean) : AgentDrawerSessionMenuAction
}

internal class MaxHeightScrollView(
    context: Context,
    private val maximumHeight: Int,
) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cappedHeightSpec = View.MeasureSpec.makeMeasureSpec(maximumHeight, View.MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, cappedHeightSpec)
    }
}

internal fun TextView.setTextIfChanged(value: CharSequence?) {
    if (text?.toString() != value?.toString()) text = value
}
