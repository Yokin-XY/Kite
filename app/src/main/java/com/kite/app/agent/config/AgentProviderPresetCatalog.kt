package com.kite.app.agent.config

/**
 * 通用 OpenAI 兼容供应商预置目录。
 *
 * 目录与页面、Agent 产品名解耦；预置只负责减少输入，用户保存前可以修改或删除所有字段。
 * 端点和模型依据供应商官方资料校准，后续可替换为资源下发目录。
 */
object AgentProviderPresetCatalog {
    val presets: List<AgentProviderPreset> = listOf(
        AgentProviderPreset(
            id = "zhipu",
            providerId = "zhipu",
            displayName = "智谱 GLM",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4/",
            models = listOf(
                AgentProviderModelSummary("glm-5.2", "GLM-5.2")
            )
        ),
        AgentProviderPreset(
            id = "zhipu-coding-plan",
            providerId = "zhipu-coding-plan",
            displayName = "智谱 GLM Coding Plan",
            baseUrl = "https://open.bigmodel.cn/api/coding/paas/v4",
            models = listOf(
                AgentProviderModelSummary("glm-5.2", "GLM-5.2"),
                AgentProviderModelSummary("glm-5-turbo", "GLM-5-Turbo"),
                AgentProviderModelSummary("glm-4.7", "GLM-4.7")
            )
        ),
        AgentProviderPreset(
            id = "xiaomi-mimo",
            providerId = "xiaomi-mimo",
            displayName = "小米 MiMo",
            baseUrl = "https://api.xiaomimimo.com/v1",
            models = listOf(
                AgentProviderModelSummary("mimo-v2-pro", "MiMo V2 Pro"),
                AgentProviderModelSummary("mimo-v2-omni", "MiMo V2 Omni"),
                AgentProviderModelSummary("mimo-v2-flash", "MiMo V2 Flash")
            )
        )
    )
}
