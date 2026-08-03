package com.kite.app.agent.config

import com.kite.app.agent.config.native.ClaudeCodeAgentConfigAdapter
import com.kite.app.agent.config.native.CodexAgentConfigAdapter
import com.kite.app.agent.config.native.HermesAgentConfigAdapter
import com.kite.app.agent.config.native.MiMoCodeAgentConfigAdapter
import com.kite.app.agent.config.native.OpenClawAgentConfigAdapter
import com.kite.app.agent.config.opencode.OpenCodeAgentConfigAdapter

/**
 * 依据 CC Switch 按应用拆分的预置结构整理的 Kite 兼容目录。
 *
 * 这里只收录供应商自有渠道和少量具有独立模型目录的平台；不复制 CC Switch 的合作推广代理商。
 * 每条路由必须符合目标 Adapter 当前真实写入协议：OpenAI-compatible、Anthropic-compatible，
 * 或 Codex 经 Kite 本地 Responses -> Chat Completions 转换通道。
 */
internal object CcSwitchProviderPresetDefinitions {
    private val openAiCompatibleAdapters = setOf(
        OpenCodeAgentConfigAdapter.ADAPTER_ID,
        OpenClawAgentConfigAdapter.ADAPTER_ID,
        HermesAgentConfigAdapter.ADAPTER_ID,
        MiMoCodeAgentConfigAdapter.ADAPTER_ID,
    )

    private val definitions = listOf(
        definition(
            id = "zhipu",
            displayName = "智谱 GLM",
            routes = openAiRoutes(
                "https://open.bigmodel.cn/api/paas/v4/",
                model("glm-5.2", "GLM-5.2"),
            ),
        ),
        definition(
            id = "zhipu-coding-plan",
            displayName = "智谱 GLM Coding Plan",
            routes = openAiAndCodexRoutes(
                "https://open.bigmodel.cn/api/coding/paas/v4",
                model("glm-5.2", "GLM-5.2"),
                model("glm-5-turbo", "GLM-5-Turbo"),
                model("glm-4.7", "GLM-4.7"),
            ) + claudeRoute(
                "https://open.bigmodel.cn/api/anthropic",
                model("glm-5.2", "GLM-5.2"),
                model("glm-5-turbo", "GLM-5-Turbo"),
                model("glm-4.7", "GLM-4.7"),
            ),
        ),
        definition(
            id = "kimi",
            displayName = "Kimi",
            routes = openAiAndCodexRoutes(
                "https://api.moonshot.cn/v1",
                model("kimi-k2.7-code", "Kimi K2.7 Code"),
                model("kimi-k3", "Kimi K3"),
            ) + claudeRoute(
                "https://api.moonshot.cn/anthropic",
                model("kimi-k2.7-code", "Kimi K2.7 Code"),
                model("kimi-k3", "Kimi K3"),
            ),
        ),
        definition(
            id = "kimi-coding",
            displayName = "Kimi For Coding",
            routes = codexRoute(
                "https://api.kimi.com/coding/v1",
                model("kimi-for-coding", "Kimi For Coding"),
            ) + claudeRoute(
                "https://api.kimi.com/coding/",
                model("kimi-for-coding", "Kimi For Coding"),
            ),
        ),
        definition(
            id = "deepseek",
            displayName = "DeepSeek",
            routes = openAiRoutes(
                "https://api.deepseek.com/v1",
                model("deepseek-v4-pro", "DeepSeek V4 Pro"),
                model("deepseek-v4-flash", "DeepSeek V4 Flash"),
            ) + claudeRoute(
                "https://api.deepseek.com/anthropic",
                model("deepseek-v4-pro", "DeepSeek V4 Pro"),
                model("deepseek-v4-flash", "DeepSeek V4 Flash"),
            ),
        ),
        definition(
            id = "volcengine-agentplan",
            displayName = "火山 Agent Plan",
            routes = openAiRoutes(
                "https://ark.cn-beijing.volces.com/api/coding/v3",
                model("ark-code-latest", "Ark Code Latest"),
            ),
        ),
        definition(
            id = "bailian",
            displayName = "阿里云百炼",
            routes = openAiRoutes(
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                model("qwen3-coder-plus", "Qwen3 Coder Plus"),
            ),
        ),
        definition(
            id = "stepfun",
            displayName = "阶跃星辰 StepFun",
            routes = openAiRoutes(
                "https://api.stepfun.com/step_plan/v1",
                model("step-3.5-flash-2603", "Step 3.5 Flash 2603"),
                model("step-3.5-flash", "Step 3.5 Flash"),
            ) + codexRoute(
                "https://api.stepfun.com/step_plan/v1",
                model("step-3.7-flash", "Step 3.7 Flash"),
                model("step-3.5-flash-2603", "Step 3.5 Flash 2603"),
                model("step-3.5-flash", "Step 3.5 Flash"),
            ) + claudeRoute(
                "https://api.stepfun.com/step_plan",
                model("step-3.5-flash-2603", "Step 3.5 Flash 2603"),
                model("step-3.5-flash", "Step 3.5 Flash"),
            ),
        ),
        definition(
            id = "modelscope",
            displayName = "魔搭 ModelScope",
            routes = openAiAndCodexRoutes(
                "https://api-inference.modelscope.cn/v1",
                model("ZhipuAI/GLM-5.1", "GLM-5.1"),
            ),
        ),
        definition(
            id = "longcat",
            displayName = "美团 LongCat",
            routes = openAiRoutes(
                "https://api.longcat.chat/openai/v1",
                model("LongCat-2.0", "LongCat 2.0"),
            ) + claudeRoute(
                "https://api.longcat.chat/anthropic",
                model("LongCat-2.0", "LongCat 2.0"),
            ),
        ),
        definition(
            id = "minimax",
            displayName = "MiniMax（中国）",
            routes = openAiRoutes(
                "https://api.minimaxi.com/v1",
                model("MiniMax-M2.7", "MiniMax M2.7"),
            ) + claudeRoute(
                "https://api.minimaxi.com/anthropic",
                model("MiniMax-M2.7", "MiniMax M2.7"),
            ),
        ),
        definition(
            id = "minimax-global",
            displayName = "MiniMax（国际）",
            routes = openAiRoutes(
                "https://api.minimax.io/v1",
                model("MiniMax-M2.7", "MiniMax M2.7"),
            ) + claudeRoute(
                "https://api.minimax.io/anthropic",
                model("MiniMax-M2.7", "MiniMax M2.7"),
            ),
        ),
        definition(
            id = "xiaomi-mimo",
            displayName = "小米 MiMo",
            routes = openAiRoutes(
                "https://api.xiaomimimo.com/v1",
                model("mimo-v2.5-pro", "MiMo V2.5 Pro"),
                model("mimo-v2.5", "MiMo V2.5"),
            ) + claudeRoute(
                "https://api.xiaomimimo.com/anthropic",
                model("mimo-v2.5-pro", "MiMo V2.5 Pro"),
                model("mimo-v2.5", "MiMo V2.5"),
            ),
        ),
        definition(
            id = "xiaomi-mimo-token-plan",
            displayName = "小米 MiMo Token Plan",
            routes = openAiRoutes(
                "https://token-plan-cn.xiaomimimo.com/v1",
                model("mimo-v2.5-pro", "MiMo V2.5 Pro"),
                model("mimo-v2.5", "MiMo V2.5"),
            ) + claudeRoute(
                "https://token-plan-cn.xiaomimimo.com/anthropic",
                model("mimo-v2.5-pro", "MiMo V2.5 Pro"),
                model("mimo-v2.5", "MiMo V2.5"),
            ),
        ),
        definition(
            id = "siliconflow",
            displayName = "硅基流动 SiliconFlow",
            routes = openAiAndCodexRoutes(
                "https://api.siliconflow.cn/v1",
                model("Pro/MiniMaxAI/MiniMax-M2.7", "Pro / MiniMax M2.7"),
            ),
        ),
        definition(
            id = "nvidia",
            displayName = "NVIDIA NIM",
            routes = openAiAndCodexRoutes(
                "https://integrate.api.nvidia.com/v1",
                model("moonshotai/kimi-k2.5", "Kimi K2.5"),
            ),
        ),
        definition(
            id = "opencode-go",
            displayName = "OpenCode Go",
            routes = openAiAndCodexRoutes(
                "https://opencode.ai/zen/go/v1",
                model("glm-5.2", "GLM-5.2"),
                model("kimi-k2.7-code", "Kimi K2.7 Code"),
                model("deepseek-v4-pro", "DeepSeek V4 Pro"),
                model("deepseek-v4-flash", "DeepSeek V4 Flash"),
                model("mimo-v2.5-pro", "MiMo V2.5 Pro"),
            ),
        ),
    )

    fun presetsFor(adapterId: String): List<AgentProviderPreset> = definitions.mapNotNull { definition ->
        val route = definition.routes[adapterId] ?: return@mapNotNull null
        AgentProviderPreset(
            id = definition.id,
            providerId = definition.id,
            displayName = definition.displayName,
            baseUrl = route.baseUrl,
            models = route.models,
        )
    }

    private fun definition(
        id: String,
        displayName: String,
        routes: Map<String, ProviderRoute>,
    ) = ProviderDefinition(id, displayName, routes)

    private fun openAiRoutes(baseUrl: String, vararg models: AgentProviderModelSummary): Map<String, ProviderRoute> =
        openAiCompatibleAdapters.associateWith { ProviderRoute(baseUrl, models.toList()) }

    private fun openAiAndCodexRoutes(
        baseUrl: String,
        vararg models: AgentProviderModelSummary,
    ): Map<String, ProviderRoute> = openAiRoutes(baseUrl, *models) + codexRoute(baseUrl, *models)

    private fun codexRoute(baseUrl: String, vararg models: AgentProviderModelSummary) = mapOf(
        CodexAgentConfigAdapter.ADAPTER_ID to ProviderRoute(baseUrl, models.toList()),
    )

    private fun claudeRoute(baseUrl: String, vararg models: AgentProviderModelSummary) = mapOf(
        ClaudeCodeAgentConfigAdapter.ADAPTER_ID to ProviderRoute(baseUrl, models.toList()),
    )

    private fun model(id: String, displayName: String) = AgentProviderModelSummary(id, displayName)

    private data class ProviderDefinition(
        val id: String,
        val displayName: String,
        val routes: Map<String, ProviderRoute>,
    )

    private data class ProviderRoute(
        val baseUrl: String,
        val models: List<AgentProviderModelSummary>,
    )
}
