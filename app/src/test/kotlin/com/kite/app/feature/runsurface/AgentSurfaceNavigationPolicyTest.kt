package com.kite.app.feature.runsurface

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.kite.app.agent.contract.AgentSessionSummary
import com.kite.app.agent.contract.AgentConfigCategory
import com.kite.app.agent.contract.AgentConfigChoice
import com.kite.app.agent.contract.AgentConfigOption
import com.kite.app.agent.contract.AgentCommand
import com.kite.app.agent.contract.AgentSessionPhase
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.config.AgentLiveConfigSnapshot
import com.kite.app.agent.config.AgentProviderCredentialChange
import com.kite.app.agent.config.AgentConfigScope
import com.kite.app.agent.config.AgentCoreDocumentDescriptor
import com.kite.app.agent.config.AgentCoreDocumentSemantics
import com.kite.app.agent.auth.AgentOfficialAccountCommandResult
import com.kite.app.agent.auth.AgentOfficialAccountManager
import com.kite.app.agent.runtime.AgentDraftModelSelection
import com.kite.app.agent.store.AgentProject
import com.kite.app.agent.store.AgentArchivedSessionMetadata
import com.kite.app.agent.store.AgentArchivedSessionSourceState
import com.kite.app.agent.store.AgentModelLibraryProviderPreference
import com.kite.app.agent.store.AgentModelLibrarySnapshot
import com.kite.app.agent.registration.KiteAgentRegistry
import com.kite.app.agent.registration.AgentOfficialAccountCommand
import com.kite.app.agent.registration.AgentOfficialAccountSpec
import com.kite.app.agent.config.AgentConfigAdapterRegistry
import com.kite.app.theme.KiteTheme
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@RunWith(RobolectricTestRunner::class)
class AgentSurfaceNavigationPolicyTest {
    @Test
    fun `会话读取失败隐藏底层英文并区分登录需求`() {
        assertEquals(
            "需要先登录当前 Agent，请在右下角设置中完成登录",
            AgentSurfaceNavigationPolicy.sessionListFailureMessage("Authentication required"),
        )
        assertEquals(
            "暂时无法读取会话，请稍后重试",
            AgentSurfaceNavigationPolicy.sessionListFailureMessage("socket closed unexpectedly"),
        )
    }

    @Test
    fun `输入器固定模型与权限两个公共入口`() {
        assertEquals(
            listOf("模型", "权限"),
            AgentSurfaceNavigationPolicy.fixedComposerEntries
        )
    }

    @Test
    fun `输入器只显示当前模型并独立显示权限`() {
        val model = AgentConfigOption.Select(
            id = "model",
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = "gpt-5.6-sol",
            choices = listOf(AgentConfigChoice("gpt-5.6-sol", "GPT-5.6-Sol")),
        )
        val thought = AgentConfigOption.Select(
            id = "thought",
            name = "推理强度",
            category = AgentConfigCategory.ThoughtLevel,
            currentValue = "medium",
            choices = listOf(AgentConfigChoice("medium", "中")),
        )
        val permission = AgentConfigOption.Select(
            id = "permission",
            name = "权限",
            category = AgentConfigCategory.Permission,
            currentValue = "full",
            choices = listOf(AgentConfigChoice("full", "完全")),
        )

        assertEquals("GPT-5.6-Sol", AgentSurfaceNavigationPolicy.composerModelLabel(listOf(model, thought)))
        assertEquals("完全", AgentSurfaceNavigationPolicy.composerPermissionLabel(permission))
        assertEquals("模型", AgentSurfaceNavigationPolicy.composerModelLabel(emptyList()))
        assertEquals("权限", AgentSurfaceNavigationPolicy.composerPermissionLabel(null))
        assertEquals(
            "自定义",
            AgentSurfaceNavigationPolicy.composerPermissionLabel(permission.copy(currentValue = "native-custom")),
        )
    }

    @Test
    fun `模型入口按名称长度使用确定性字号档位且保持固定最大宽度`() {
        assertEquals(
            ComposerModelTextStyle(textSizeSp = 13f, maximumWidthDp = 118),
            AgentSurfaceNavigationPolicy.composerModelTextStyle("GLM-5.2"),
        )
        assertEquals(
            ComposerModelTextStyle(textSizeSp = 12f, maximumWidthDp = 118),
            AgentSurfaceNavigationPolicy.composerModelTextStyle("MiMo V2.5 Free"),
        )
        assertEquals(
            ComposerModelTextStyle(textSizeSp = 11f, maximumWidthDp = 118),
            AgentSurfaceNavigationPolicy.composerModelTextStyle("DeepSeek V4 Flash Free (New)"),
        )
    }

    @Test
    fun `核心设定按真实作用域分组并明确完整覆盖`() {
        val global = AgentCoreDocumentDescriptor(
            id = "system",
            displayName = "主 Agent 系统提示",
            fileName = "SYSTEM.md",
            displayLocation = "/root/.kimi-code/SYSTEM.md",
            scope = AgentConfigScope.User,
            semantics = AgentCoreDocumentSemantics.FullSystemPromptReplacement,
            exists = false,
            writable = true,
            priorityDescription = "替换内置主 Agent 提示",
            warning = "完整替换",
        )
        val project = global.copy(
            id = "project-agents",
            displayName = "项目说明",
            fileName = "AGENTS.md",
            displayLocation = "/workspace/AGENTS.md",
            scope = AgentConfigScope.Project,
            semantics = AgentCoreDocumentSemantics.SupplementalInstructions,
            exists = true,
        )

        assertEquals("全局设定", AgentCoreDocumentUiPolicy.sectionTitle(global))
        assertEquals("当前工作区", AgentCoreDocumentUiPolicy.sectionTitle(project))
        assertEquals("完整替换主 Agent 系统提示", AgentCoreDocumentUiPolicy.semanticsLabel(global.semantics))
        assertTrue(AgentCoreDocumentUiPolicy.summary(global).contains("尚未创建"))
        assertTrue(AgentCoreDocumentUiPolicy.summary(project).contains("已存在"))
    }

    @Test
    fun `会话搜索只过滤当前 Agent 已返回的列表`() {
        val sessions = listOf(
            AgentSessionSummary(id = "session-a", cwd = "/workspace/kite", title = "修复安装流程"),
            AgentSessionSummary(id = "session-b", cwd = "/workspace/nomo", title = "整理记忆协议")
        )

        assertEquals(listOf("session-a"), AgentSurfaceNavigationPolicy
            .filterSessions(sessions, "KITE")
            .map(AgentSessionSummary::id))
        assertEquals(listOf("session-b"), AgentSurfaceNavigationPolicy
            .filterSessions(sessions, "记忆")
            .map(AgentSessionSummary::id))
        assertEquals(sessions, AgentSurfaceNavigationPolicy.filterSessions(sessions, "  "))
    }

    @Test
    fun `默认会话直接显示而项目只在展开后显示子会话`() {
        val sessions = listOf(
            AgentSessionSummary(id = "default-1", cwd = "/workspace", title = "普通会话"),
            AgentSessionSummary(id = "kite-1", cwd = "/workspace/Kite", title = "修复入口"),
            AgentSessionSummary(id = "kite-2", cwd = "/workspace/Kite/", title = "整理页面"),
            AgentSessionSummary(id = "wechat-1", cwd = "/workspace/微信", title = "接入会话")
        )

        val grouping = AgentSurfaceNavigationPolicy.groupSessions(sessions, "/workspace/")
        val collapsed = AgentSurfaceNavigationPolicy.drawerRows(grouping, emptySet())
        val expanded = AgentSurfaceNavigationPolicy.drawerRows(grouping, setOf("/workspace/Kite"))

        assertEquals(listOf("default-1"), grouping.defaultSessions.map(AgentSessionSummary::id))
        assertEquals(listOf("Kite", "微信"), grouping.projects.map(AgentSessionProjectGroup::name))
        assertEquals(
            AgentDrawerAction.NewDraft("/workspace"),
            (collapsed.first() as AgentDrawerRow.SectionHeader).action
        )
        assertEquals(
            listOf("default-1"),
            collapsed.filterIsInstance<AgentDrawerRow.Session>().map { it.summary.id }
        )
        assertEquals(
            listOf("default-1", "kite-1", "kite-2"),
            expanded.filterIsInstance<AgentDrawerRow.Session>().map { it.summary.id }
        )
        assertTrue(collapsed.filterIsInstance<AgentDrawerRow.ProjectHeader>().none { it.expanded })
        assertTrue(expanded.filterIsInstance<AgentDrawerRow.ProjectHeader>().single { it.project.name == "Kite" }.expanded)
    }

    @Test
    fun `registered project keeps its own name before the first session exists`() {
        val grouping = AgentSurfaceNavigationPolicy.groupSessions(
            sessions = emptyList(),
            defaultCwd = "/workspace",
            registeredProjects = listOf(
                AgentProject("opencode", "微信项目", "/workspace/client/wechat", 1L),
            ),
        )

        assertEquals(listOf("微信项目"), grouping.projects.map(AgentSessionProjectGroup::name))
        assertTrue(grouping.projects.single().sessions.isEmpty())
        assertEquals(
            listOf("微信项目"),
            AgentSurfaceNavigationPolicy.drawerRows(grouping, emptySet())
                .filterIsInstance<AgentDrawerRow.ProjectHeader>()
                .map { it.project.name },
        )
    }

    @Test
    fun `archived project hides both registration and sessions grouped by its directory`() {
        val grouping = AgentSurfaceNavigationPolicy.groupSessions(
            sessions = listOf(
                AgentSessionSummary(id = "default", cwd = "/workspace", title = "默认会话"),
                AgentSessionSummary(id = "kite", cwd = "/workspace/Kite", title = "项目会话"),
            ),
            defaultCwd = "/workspace",
            registeredProjects = listOf(
                AgentProject("opencode", "Kite", "/workspace/Kite", 1L),
            ),
            archivedProjectCwds = setOf("/workspace/Kite/"),
        )

        assertEquals(listOf("default"), grouping.defaultSessions.map(AgentSessionSummary::id))
        assertTrue(grouping.projects.isEmpty())
        assertEquals(
            listOf("default"),
            AgentSurfaceNavigationPolicy.drawerRows(grouping, emptySet())
                .filterIsInstance<AgentDrawerRow.Session>()
                .map { it.summary.id },
        )
    }

    @Test
    fun `归档列表保持分组到会话两级结构`() {
        val sessions = listOf(
            AgentSessionSummary(id = "default-1", cwd = "/workspace"),
            AgentSessionSummary(id = "kite-1", cwd = "/workspace/Kite"),
            AgentSessionSummary(id = "wechat-1", cwd = "/workspace/微信")
        )
        val grouping = AgentSurfaceNavigationPolicy.groupSessions(sessions, "/workspace")

        val rows = AgentSurfaceNavigationPolicy.archivedRows(
            grouping,
            expandedCwds = setOf("/workspace", "/workspace/微信")
        )

        assertEquals(
            listOf("会话", "Kite", "微信"),
            rows.filterIsInstance<AgentArchivedRow.GroupHeader>().map(AgentArchivedRow.GroupHeader::title)
        )
        assertEquals(
            listOf("default-1", "wechat-1"),
            rows.filterIsInstance<AgentArchivedRow.Session>().map { it.summary.id }
        )
        val headers = rows.filterIsInstance<AgentArchivedRow.GroupHeader>()
        assertTrue(headers.single { it.title == "会话" }.selectableSessionIds.isEmpty())
        assertEquals(setOf("kite-1"), headers.single { it.title == "Kite" }.selectableSessionIds)
        assertEquals(setOf("wechat-1"), headers.single { it.title == "微信" }.selectableSessionIds)
    }

    @Test
    fun `归档项目与归档会话在同一列表且空项目仍可恢复`() {
        val archivedProject = AgentProject(
            agentId = "opencode",
            name = "空项目",
            cwd = "/workspace/empty",
            createdAtMillis = 1L,
            archivedAtMillis = 2L,
        )
        val grouping = AgentSurfaceNavigationPolicy.groupSessions(
            sessions = listOf(AgentSessionSummary(id = "kite-1", cwd = "/workspace/Kite")),
            defaultCwd = "/workspace",
            registeredProjects = listOf(
                AgentProject("opencode", "Kite", "/workspace/Kite", 1L),
                archivedProject,
            ),
        )

        val rows = AgentSurfaceNavigationPolicy.archivedRows(
            grouping = grouping,
            expandedCwds = emptySet(),
            archivedProjects = listOf(archivedProject),
        )
        val headers = rows.filterIsInstance<AgentArchivedRow.GroupHeader>()

        assertEquals(listOf("Kite", "空项目"), headers.map(AgentArchivedRow.GroupHeader::title))
        assertEquals(archivedProject, headers.single { it.title == "空项目" }.archivedProject)
        assertEquals(0, headers.single { it.title == "空项目" }.count)
        assertTrue(headers.single { it.title == "空项目" }.selectableSessionIds.isEmpty())
        assertTrue(rows.none { it is AgentArchivedRow.Session })
    }

    @Test
    fun `归档投影不会静默丢弃 Agent 当前未返回的会话`() {
        val projection = AgentSurfaceNavigationPolicy.archivedSessionProjection(
            sessions = listOf(
                AgentSessionSummary(id = "available", cwd = "/workspace"),
                AgentSessionSummary(id = "not-archived", cwd = "/workspace"),
            ),
            archivedSessionIds = linkedSetOf("available", "missing"),
        )

        assertEquals(listOf("available"), projection.sessions.map(AgentSessionSummary::id))
        assertEquals(listOf("missing"), projection.unavailableSessionIds)
    }

    @Test
    fun `已删除与尚未确认的归档会话分组展示`() {
        val sessions = listOf(
            AgentArchivedSessionMetadata(
                sessionId = "session-a",
                archivedAtMillis = 1L,
                sourceState = AgentArchivedSessionSourceState.Deleted,
                sourceCheckedAtMillis = 2L,
            ),
            AgentArchivedSessionMetadata(
                sessionId = "session-b",
                archivedAtMillis = 1L,
                sourceState = AgentArchivedSessionSourceState.Unknown,
                sourceCheckedAtMillis = 0L,
            ),
        )
        val collapsed = AgentSurfaceNavigationPolicy.unavailableArchivedRows(
            sessions,
            emptySet(),
        )
        val expanded = AgentSurfaceNavigationPolicy.unavailableArchivedRows(
            sessions,
            setOf(
                AgentSurfaceNavigationPolicy.DELETED_ARCHIVE_GROUP_CWD,
                AgentSurfaceNavigationPolicy.UNCONFIRMED_ARCHIVE_GROUP_CWD,
            ),
        )

        assertEquals(
            listOf("源会话已删除", "尚未确认"),
            collapsed.filterIsInstance<AgentArchivedRow.GroupHeader>().map { it.title },
        )
        assertEquals(
            listOf("session-a", "session-b"),
            expanded.filterIsInstance<AgentArchivedRow.UnavailableSession>().map { it.metadata.sessionId },
        )
    }

    @Test
    fun `无法读取记录只在同一 Provider 提供删除且不是当前会话时开放原生删除`() {
        assertTrue(
            AgentSurfaceNavigationPolicy.canDeleteUnavailableSessionNatively(
                targetProviderId = "opencode",
                runtimeProviderId = "opencode",
                deleteSupported = true,
                currentSessionId = "current",
                targetSessionId = "missing",
            ),
        )
        assertFalse(
            AgentSurfaceNavigationPolicy.canDeleteUnavailableSessionNatively(
                targetProviderId = "opencode",
                runtimeProviderId = "hermes",
                deleteSupported = true,
                currentSessionId = null,
                targetSessionId = "missing",
            ),
        )
        assertFalse(
            AgentSurfaceNavigationPolicy.canDeleteUnavailableSessionNatively(
                targetProviderId = "opencode",
                runtimeProviderId = "opencode",
                deleteSupported = false,
                currentSessionId = null,
                targetSessionId = "missing",
            ),
        )
        assertFalse(
            AgentSurfaceNavigationPolicy.canDeleteUnavailableSessionNatively(
                targetProviderId = "opencode",
                runtimeProviderId = "opencode",
                deleteSupported = true,
                currentSessionId = "missing",
                targetSessionId = "missing",
            ),
        )
    }

    @Test
    fun `首字符斜杠触发原生命令过滤而普通消息不触发`() {
        val commands = listOf(
            AgentCommand("model", "切换模型"),
            AgentCommand("mcp", "显示 MCP 服务器状态"),
            AgentCommand("review", "代码审查")
        )

        assertEquals("", AgentSurfaceNavigationPolicy.slashCommandQuery("/"))
        assertEquals("mo", AgentSurfaceNavigationPolicy.slashCommandQuery("/Mo"))
        assertEquals(null, AgentSurfaceNavigationPolicy.slashCommandQuery("你好 /model"))
        assertEquals(null, AgentSurfaceNavigationPolicy.slashCommandQuery("/model glm"))
        assertEquals(
            listOf("model"),
            AgentSurfaceNavigationPolicy.filterCommands(commands, "mo").map(AgentCommand::name)
        )
        assertEquals(
            listOf("mcp"),
            AgentSurfaceNavigationPolicy.filterCommands(commands, "服务器").map(AgentCommand::name)
        )
    }

    @Test
    fun `模型选项按 Agent 返回的供应商分组而不是页面猜测`() {
        val option = AgentConfigOption.Select(
            id = "model",
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = "zhipu/glm-5.2",
            choices = listOf(
                AgentConfigChoice("zhipu/glm-5.2", "GLM-5.2", groupId = "zhipu", groupName = "智谱 GLM"),
                AgentConfigChoice("mimo/mimo-v2-pro", "MiMo V2 Pro", groupId = "mimo", groupName = "小米 MiMo"),
                AgentConfigChoice("mimo/mimo-v2-flash", "MiMo V2 Flash", groupId = "mimo", groupName = "小米 MiMo")
            )
        )

        val groups = AgentSurfaceNavigationPolicy.modelChoiceGroups(option)

        assertEquals(listOf("智谱 GLM", "小米 MiMo"), groups.map { it.name })
        assertEquals(listOf(1, 2), groups.map { it.choices.size })
        assertTrue(AgentSurfaceNavigationPolicy.modelChoiceGroups(
            option.copy(category = AgentConfigCategory.Mode)
        ).isEmpty())
        assertEquals(
            listOf("智谱 GLM"),
            AgentSurfaceNavigationPolicy.modelChoiceGroups(
                option.copy(choices = option.choices.take(1))
            ).map { it.name }
        )
        assertTrue(AgentSurfaceNavigationPolicy.modelChoiceGroups(
            option.copy(choices = listOf(AgentConfigChoice("plain", "普通模型")))
        ).isEmpty())
    }

    @Test
    fun `弹层有效临时供应商优先于当前生效模型所属供应商`() {
        val groups = listOf(
            AgentModelChoiceGroup(
                id = "zhipu",
                name = "智谱 GLM",
                choices = listOf(AgentConfigChoice("zhipu/glm-5.2", "GLM-5.2")),
            ),
            AgentModelChoiceGroup(
                id = "opencode",
                name = "OpenCode Zen",
                choices = listOf(AgentConfigChoice("opencode/big-pickle", "Big Pickle")),
            ),
        )

        val selected = AgentSurfaceNavigationPolicy.resolveModelChoiceGroup(
            groups = groups,
            currentModelValue = "zhipu/glm-5.2",
            requestedGroupId = "opencode",
        )

        assertEquals("opencode", selected?.id)
    }

    @Test
    fun `弹层无效临时供应商回退到当前模型再回退首项`() {
        val groups = listOf(
            AgentModelChoiceGroup(
                id = "zhipu",
                name = "智谱 GLM",
                choices = listOf(AgentConfigChoice("zhipu/glm-5.2", "GLM-5.2")),
            ),
            AgentModelChoiceGroup(
                id = "opencode",
                name = "OpenCode Zen",
                choices = listOf(AgentConfigChoice("opencode/big-pickle", "Big Pickle")),
            ),
        )

        assertEquals(
            "opencode",
            AgentSurfaceNavigationPolicy.resolveModelChoiceGroup(
                groups = groups,
                currentModelValue = "opencode/big-pickle",
                requestedGroupId = "missing",
            )?.id,
        )
        assertEquals(
            "zhipu",
            AgentSurfaceNavigationPolicy.resolveModelChoiceGroup(
                groups = groups,
                currentModelValue = "missing/model",
                requestedGroupId = null,
            )?.id,
        )
    }

    @Test
    fun `会话配置入口优先显示模型与推理强度且不复制会话事实`() {
        val options = listOf(
            AgentConfigOption.Select(
                id = "mode",
                name = "工作模式",
                category = AgentConfigCategory.Mode,
                currentValue = "agent",
                choices = listOf(AgentConfigChoice("agent", "Agent"))
            ),
            AgentConfigOption.Select(
                id = "model",
                name = "模型",
                category = AgentConfigCategory.Model,
                currentValue = "mimo/mimo-v2-pro",
                choices = listOf(AgentConfigChoice("mimo/mimo-v2-pro", "MiMo V2 Pro"))
            ),
            AgentConfigOption.Select(
                id = "effort",
                name = "推理强度",
                category = AgentConfigCategory.ThoughtLevel,
                currentValue = "high",
                choices = listOf(AgentConfigChoice("high", "高"))
            )
        )

        assertEquals("MiMo V2 Pro · 高", AgentSurfaceNavigationPolicy.configurationSummary(options))
    }

    @Test
    fun `工作模式不占用模型与推理强度摘要`() {
        val modeAndPermissionOnly = listOf(
            AgentConfigOption.Select(
                id = "mode",
                name = "工作模式",
                category = AgentConfigCategory.Mode,
                currentValue = "plan",
                choices = listOf(AgentConfigChoice("plan", "计划"))
            ),
            AgentConfigOption.Select(
                id = "approval-policy",
                name = "权限",
                category = AgentConfigCategory.Permission,
                currentValue = "ask",
                choices = listOf(AgentConfigChoice("ask", "请求批准"))
            )
        )

        assertEquals("", AgentSurfaceNavigationPolicy.configurationSummary(modeAndPermissionOnly))
        val permission = AgentSurfaceNavigationPolicy.permissionOption(modeAndPermissionOnly)
        assertEquals("approval-policy", permission?.id)
        assertEquals("ask", permission?.currentValue)
        assertEquals(
            null,
            AgentSurfaceNavigationPolicy.permissionOption(modeAndPermissionOnly.filterNot {
                it.category == AgentConfigCategory.Permission
            })
        )
    }

    @Test
    fun `供应商列表可以保留二十个真实分组且面板高度有上限`() {
        val option = AgentConfigOption.Select(
            id = "model",
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = "provider-0/model",
            choices = (0 until 20).map { index ->
                AgentConfigChoice(
                    value = "provider-$index/model",
                    name = "模型 $index",
                    groupId = "provider-$index",
                    groupName = "供应商 $index"
                )
            }
        )

        assertEquals(20, AgentSurfaceNavigationPolicy.modelChoiceGroups(option).size)
        assertEquals(
            430,
            AgentSurfaceNavigationPolicy.sessionPanelMaxHeight(
                viewportHeight = 1000,
                composerHeight = 180,
                topBarHeight = 64,
                preferredHeight = 430,
                minimumHeight = 220,
                outerSpacing = 32
            )
        )
        assertEquals(
            224,
            AgentSurfaceNavigationPolicy.sessionPanelMaxHeight(
                viewportHeight = 500,
                composerHeight = 180,
                topBarHeight = 64,
                preferredHeight = 430,
                minimumHeight = 220,
                outerSpacing = 32
            )
        )
    }

    @Test
    fun `供应商编辑器生成稳定安全 ID 并校验地址和模型`() {
        assertEquals("glm", AgentProviderEditorPolicy.providerIdFromName("智谱 GLM"))
        assertTrue(AgentProviderEditorPolicy.providerIdFromName("智谱").startsWith("custom-"))
        assertEquals(
            null,
            AgentProviderEditorPolicy.validate(
                displayName = "智谱 GLM",
                providerId = "zhipu",
                baseUrl = "https://open.bigmodel.cn/api/paas/v4/",
                models = listOf(AgentProviderModelSummary("glm-5.2", "GLM-5.2"))
            )
        )
        assertEquals(
            "模型 ID 不能重复",
            AgentProviderEditorPolicy.validate(
                displayName = "测试供应商",
                providerId = "test",
                baseUrl = "https://example.com/v1",
                models = listOf(
                    AgentProviderModelSummary("model-a"),
                    AgentProviderModelSummary("model-a")
                )
            )
        )
        assertEquals(
            "请求地址必须是有效的 HTTP 或 HTTPS 地址",
            AgentProviderEditorPolicy.validate(
                displayName = "测试供应商",
                providerId = "test",
                baseUrl = "file:///tmp/config",
                models = listOf(AgentProviderModelSummary("model-a"))
            )
        )
        assertEquals("请输入显示名称", AgentProviderEditorPolicy.validateModel("  ", "model-a"))
        assertEquals(null, AgentProviderEditorPolicy.validateModel("日常模型", "model-a"))
        assertEquals("请输入显示名称", AgentProviderEditorPolicy.validateDisplayName("\t"))
        assertEquals(null, AgentProviderEditorPolicy.validateDisplayName("日常模型"))
    }

    @Test
    fun `API Key 输入保持可见并安全清理剪贴板首尾空白`() {
        assertEquals(
            InputType.TYPE_TEXT_VARIATION_NORMAL,
            AgentProviderCredentialInputPolicy.inputType and InputType.TYPE_MASK_VARIATION
        )
        assertEquals("sk-example-value", AgentProviderCredentialInputPolicy.clipboardValue("  sk-example-value\n"))
        assertEquals(null, AgentProviderCredentialInputPolicy.clipboardValue("  \n"))
        assertEquals(
            "••••••••••••",
            AgentProviderCredentialInputPolicy.displayHint(
                credentialPresent = true,
                removeRequested = false,
                emptyHint = "粘贴供应商 API Key"
            )
        )
        assertEquals(
            "保存后移除 API Key",
            AgentProviderCredentialInputPolicy.displayHint(
                credentialPresent = true,
                removeRequested = true,
                emptyHint = "粘贴供应商 API Key"
            )
        )
        assertEquals(
            AgentProviderCredentialChange.Keep,
            AgentProviderCredentialInputPolicy.credentialChange(removeRequested = false, value = "")
        )
        assertTrue(
            AgentProviderCredentialInputPolicy.credentialChange(
                removeRequested = false,
                value = "  sk-replacement  "
            ) is AgentProviderCredentialChange.Replace
        )
        assertEquals(
            AgentProviderCredentialChange.Remove,
            AgentProviderCredentialInputPolicy.credentialChange(removeRequested = true, value = "")
        )
    }

    @Test
    fun `归档项目选择只是已归档子会话集合`() {
        val selectedA = AgentArchivedSelectionPolicy.toggleSession(emptySet(), "session-a")
        assertEquals(setOf(AgentArchivedSelectionKey.Session("session-a")), selectedA)
        assertEquals(
            emptySet<AgentArchivedSelectionKey>(),
            AgentArchivedSelectionPolicy.toggleSession(selectedA, "session-a"),
        )

        val selectedProject = AgentArchivedSelectionPolicy.toggleProject(
            current = emptySet(),
            childSessionIds = listOf("session-a", "session-b"),
        )
        assertEquals(
            setOf(
                AgentArchivedSelectionKey.Session("session-a"),
                AgentArchivedSelectionKey.Session("session-b"),
            ),
            selectedProject,
        )
        assertEquals(
            emptySet<AgentArchivedSelectionKey>(),
            AgentArchivedSelectionPolicy.toggleProject(
                selectedProject,
                listOf("session-a", "session-b"),
            ),
        )

        assertEquals(
            AgentArchivedProjectSelectionState.Unchecked,
            AgentArchivedSelectionPolicy.projectSelectionState(emptySet(), listOf("session-a", "session-b")),
        )
        assertEquals(
            AgentArchivedProjectSelectionState.Partial,
            AgentArchivedSelectionPolicy.projectSelectionState(selectedA, listOf("session-a", "session-b")),
        )
        assertEquals(
            AgentArchivedProjectSelectionState.Checked,
            AgentArchivedSelectionPolicy.projectSelectionState(selectedProject, listOf("session-a", "session-b")),
        )

        val all = AgentArchivedSelectionPolicy.selectAll(
            sessionIds = listOf("session-a", "session-b", "missing"),
        )
        assertEquals(setOf("session-a", "session-b", "missing"), AgentArchivedSelectionPolicy.selectedSessionIds(all))
        val sessionIds = AgentArchivedSelectionPolicy.selectedSessionIds(selectedA)
        assertTrue(AgentArchivedSelectionPolicy.canDelete(sessionIds, currentSessionId = "session-b", deleteSupported = true))
        assertFalse(AgentArchivedSelectionPolicy.canDelete(sessionIds, currentSessionId = "session-a", deleteSupported = true))
        assertFalse(AgentArchivedSelectionPolicy.canDelete(sessionIds, currentSessionId = null, deleteSupported = false))
    }

    @Test
    fun `Agent 视觉投影使用中性灰阶且不改强调色`() {
        val source = KiteTheme.resolve(KiteTheme.defaultSelection, systemDark = false).tokens
        val light = AgentSurfaceThemePolicy.project(source, isDark = false)
        val dark = AgentSurfaceThemePolicy.project(source, isDark = true)

        assertEquals(android.graphics.Color.WHITE, light.pageBackground)
        assertEquals(android.graphics.Color.rgb(247, 247, 247), light.cardBackground)
        assertEquals(android.graphics.Color.rgb(17, 17, 17), light.textPrimary)
        assertEquals(android.graphics.Color.rgb(102, 102, 102), light.textSecondary)
        assertEquals(android.graphics.Color.BLACK, dark.pageBackground)
        assertEquals(android.graphics.Color.rgb(36, 36, 36), dark.cardBackground)
        assertEquals(source.primaryStrong, light.primaryStrong)
        assertEquals(source.primaryStrong, dark.primaryStrong)
    }

    @Test
    fun `归档选择视觉使用 ChatGPT 中性尺寸与颜色`() {
        assertEquals(22, AgentSelectionVisualPolicy.INDICATOR_SIZE_DP)
        assertEquals(44, AgentSelectionVisualPolicy.TOUCH_TARGET_DP)
        assertEquals(14, AgentSelectionVisualPolicy.INDICATOR_ICON_SIZE_DP)
        assertEquals(48, AgentSelectionVisualPolicy.ACTION_HEIGHT_DP)

        val light = AgentSelectionVisualPolicy.palette(isDark = false)
        assertEquals(android.graphics.Color.rgb(32, 33, 35), light.selectedIndicator)
        assertEquals(android.graphics.Color.rgb(247, 247, 248), light.selectedRow)
        assertEquals(android.graphics.Color.rgb(252, 235, 235), light.dangerAction)
        assertEquals(android.graphics.Color.rgb(207, 30, 39), light.dangerActionText)
    }

    @Test
    fun `持久默认提示明确不会改变当前会话`() {
        assertEquals(
            "智谱 GLM 已设为默认；正在进行的会话不会改变",
            AgentPersistentDefaultPolicy.savedMessage("智谱 GLM", currentAgent = true)
        )
        assertEquals(
            "供应商资料已保存；下次打开该 Agent 时使用",
            AgentPersistentDefaultPolicy.configurationSavedMessage("供应商资料已保存", currentAgent = false)
        )
    }

    @Test
    fun `空白草稿目录读取原生默认且其他选择不改默认`() {
        val snapshot = AgentLiveConfigSnapshot(
            agentId = "opencode",
            adapterId = "opencode",
            revision = "r1",
            displayLocation = "/root/.config/opencode/opencode.jsonc",
            activeProviderId = "zhipu",
            defaultModel = "zhipu/glm-5.2",
            providers = listOf(
                AgentProviderSummary(
                    id = "zhipu",
                    displayName = "智谱 GLM",
                    models = listOf(AgentProviderModelSummary("glm-5.2", "GLM-5.2"))
                ),
                AgentProviderSummary(
                    id = "mimo",
                    displayName = "小米 MiMo",
                    models = listOf(AgentProviderModelSummary("mimo-v2-pro", "MiMo V2 Pro"))
                )
            )
        )

        val default = AgentDraftModelPolicy.defaultSelection(snapshot)
        val option = AgentDraftModelPolicy.option(
            snapshot,
            default,
            library = AgentModelLibrarySnapshot(
                providers = mapOf(
                    "zhipu" to AgentModelLibraryProviderPreference(
                        modelDisplayNames = mapOf("glm-5.2" to "日常模型")
                    )
                )
            )
        )!!
        val mimoChoice = option.choices.single { it.groupId == "mimo" }
        val mimo = AgentDraftModelPolicy.selection(snapshot, mimoChoice.value)

        assertEquals(AgentDraftModelSelection("zhipu", "glm-5.2", true), default)
        assertEquals("日常模型", option.choices.single { it.value == option.currentValue }.name)
        assertEquals(AgentDraftModelSelection("mimo", "mimo-v2-pro", false), mimo)
        assertEquals(listOf("智谱 GLM", "小米 MiMo"), option.choices.mapNotNull { it.groupName }.distinct())
    }

    @Test
    fun `空白草稿合并Agent发现的免费模型且不写入持久默认`() {
        val snapshot = AgentLiveConfigSnapshot(
            agentId = "opencode",
            adapterId = "opencode",
            revision = "r1",
            displayLocation = "/root/.config/opencode/opencode.jsonc",
            activeProviderId = "zhipu",
            defaultModel = "zhipu/glm-5.2",
            providers = listOf(
                AgentProviderSummary(
                    id = "zhipu",
                    displayName = "智谱 GLM",
                    models = listOf(AgentProviderModelSummary("glm-5.2", "GLM-5.2"))
                )
            )
        )
        val discovered = AgentConfigOption.Select(
            id = "model",
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = "opencode/big-pickle",
            choices = listOf(
                AgentConfigChoice(
                    value = "opencode/big-pickle",
                    name = "Big Pickle",
                    groupId = "opencode",
                    groupName = "OpenCode Zen"
                )
            )
        )

        val option = AgentDraftModelPolicy.option(
            snapshot,
            selected = null,
            discovered = discovered,
            library = AgentModelLibrarySnapshot(
                providers = mapOf(
                    "opencode" to AgentModelLibraryProviderPreference(
                        modelDisplayNames = mapOf("opencode/big-pickle" to "免费轻量")
                    )
                )
            )
        )!!
        val freeChoice = option.choices.single { it.groupId == "opencode" }
        val selection = AgentDraftModelPolicy.selection(snapshot, freeChoice.value, option.choices)

        assertEquals(listOf("智谱 GLM", "OpenCode Zen"), option.choices.mapNotNull { it.groupName }.distinct())
        assertEquals("免费轻量", freeChoice.name)
        assertEquals(AgentDraftModelSelection("opencode", "big-pickle", false), selection)
        assertEquals("GLM-5.2", option.choices.single { it.value == option.currentValue }.name)
    }

    @Test
    fun `空白草稿显示官方模型别名但选择仍返回真实模型引用`() {
        val snapshot = AgentLiveConfigSnapshot(
            agentId = "codex",
            adapterId = "codex",
            revision = "r1",
            displayLocation = "/root/.codex/config.toml",
            activeProviderId = "custom",
            defaultModel = "custom/default",
            providers = listOf(
                AgentProviderSummary(
                    id = "custom",
                    displayName = "自定义",
                    models = listOf(AgentProviderModelSummary("default", "Default"))
                )
            )
        )
        val account = AgentOfficialAccountSpec(
            id = "chatgpt",
            displayName = "ChatGPT 官方",
            modelGroupIds = listOf("openai"),
            login = AgentOfficialAccountCommand(listOf("codex", "login")),
        )
        val discovered = AgentConfigOption.Select(
            id = "model",
            name = "模型",
            category = AgentConfigCategory.Model,
            currentValue = "openai/gpt-5.6",
            choices = listOf(
                AgentConfigChoice(
                    value = "openai/gpt-5.6",
                    name = "GPT-5.6",
                    groupId = "openai",
                    groupName = "OpenAI",
                )
            )
        )
        val option = AgentDraftModelPolicy.option(
            snapshot,
            selected = null,
            discovered = discovered,
            library = AgentModelLibrarySnapshot(
                providers = mapOf(
                    "__kite_official__:chatgpt" to AgentModelLibraryProviderPreference(
                        modelDisplayNames = mapOf("openai/gpt-5.6" to "日常")
                    )
                )
            ),
            officialAccounts = listOf(account),
        )!!

        val officialChoice = option.choices.single { it.groupId == "openai" }
        val selection = AgentDraftModelPolicy.selection(snapshot, officialChoice.value, option.choices)

        assertEquals("日常", officialChoice.name)
        assertEquals("openai/gpt-5.6", officialChoice.description)
        assertEquals(AgentDraftModelSelection("openai", "gpt-5.6", false), selection)
    }

    @Test
    fun `会话搜索和设置页返回抽屉时复用同一视图树`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val tokens = KiteTheme.resolve(KiteTheme.defaultSelection, systemDark = false).tokens
        val accountJob = SupervisorJob()
        val agentRegistry = KiteAgentRegistry(
            context = activity,
            resourceRegistrationSource = { emptyList() }
        )
        val binding = RunAgentSurfaceBinding(
            context = activity,
            tokens = tokens,
            onCloseInstance = {},
            onPickImages = {},
            onPickFiles = {},
            agentRegistry = agentRegistry,
            officialAccountManager = AgentOfficialAccountManager(
                scope = CoroutineScope(accountJob + Dispatchers.Unconfined),
                registry = agentRegistry,
                commandRunner = { AgentOfficialAccountCommandResult(0, "") },
            ),
            agentConfigAdapters = AgentConfigAdapterRegistry(emptyList())
        )

        binding.showSessionDrawerForTesting()
        binding.showSessionSearchForTesting()

        assertEquals("SessionSearch", binding.navigationScreenForTesting())
        assertTrue(binding.handleBack())
        assertEquals("Drawer", binding.navigationScreenForTesting())

        binding.showSettingsForTesting(returnToDrawer = true)

        assertTrue(binding.handleBack())
        assertEquals("Drawer", binding.navigationScreenForTesting())
        assertTrue(binding.handleBack())
        assertEquals("Main", binding.navigationScreenForTesting())

        binding.dispose()
        accountJob.cancel()
        activity.finish()
    }

    @Test
    fun `重复运行事实只绑定模型与权限状态而不重建输入控件`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val tokens = KiteTheme.resolve(KiteTheme.defaultSelection, systemDark = false).tokens
        val accountJob = SupervisorJob()
        val agentRegistry = KiteAgentRegistry(
            context = activity,
            resourceRegistrationSource = { emptyList() },
        )
        val binding = RunAgentSurfaceBinding(
            context = activity,
            tokens = tokens,
            onCloseInstance = {},
            onPickImages = {},
            onPickFiles = {},
            agentRegistry = agentRegistry,
            officialAccountManager = AgentOfficialAccountManager(
                scope = CoroutineScope(accountJob + Dispatchers.Unconfined),
                registry = agentRegistry,
                commandRunner = { AgentOfficialAccountCommandResult(0, "") },
            ),
            agentConfigAdapters = AgentConfigAdapterRegistry(emptyList()),
        )
        val identities = binding.sessionControlIdentityForTesting()

        repeat(100) { binding.refreshSessionControlsForTesting() }

        assertEquals(identities, binding.sessionControlIdentityForTesting())
        assertEquals(1 to 1, binding.sessionControlChildCountsForTesting())
        val (inputType, imeOptions) = binding.composerInputFlagsForTesting()
        assertTrue(inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
        assertEquals(EditorInfo.IME_FLAG_NO_ENTER_ACTION, imeOptions)

        binding.dispose()
        accountJob.cancel()
        activity.finish()
    }
}
