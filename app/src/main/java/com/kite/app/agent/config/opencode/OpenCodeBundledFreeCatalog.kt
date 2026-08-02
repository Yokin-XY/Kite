package com.kite.app.agent.config.opencode

import com.kite.app.agent.config.AgentCredentialPresence
import com.kite.app.agent.config.AgentFreeProviderCatalog
import com.kite.app.agent.config.AgentProviderModelSummary
import com.kite.app.agent.config.AgentProviderSummary
import com.kite.app.agent.contract.AgentModelSource

/**
 * 随当前 Kite 版本发布的 OpenCode 无凭据免费目录。
 *
 * 只在本地还没有 OpenCode 免费来源时初始化；以后模型上下线由用户在模型库下拉刷新确认。
 * 目录依据：https://opencode.ai/docs/zen
 */
internal fun openCodeBundledFreeProviderCatalog(): AgentFreeProviderCatalog = AgentFreeProviderCatalog(
    sourceId = "opencode-public",
    sourceVersion = "bundled-2026-08-02",
    providers = listOf(
        AgentProviderSummary(
            id = "opencode",
            displayName = "OpenCode",
            models = listOf(
                AgentProviderModelSummary("big-pickle", "Big Pickle"),
                AgentProviderModelSummary("deepseek-v4-flash-free", "DeepSeek V4 Flash Free"),
                AgentProviderModelSummary("mimo-v2.5-free", "MiMo-V2.5 Free"),
                AgentProviderModelSummary("laguna-s-2.1-free", "Laguna S 2.1 Free"),
                AgentProviderModelSummary("ling-3.0-flash-free", "Ling-3.0-flash Free"),
                AgentProviderModelSummary("north-mini-code-free", "North Mini Code Free"),
                AgentProviderModelSummary("nemotron-3-ultra-free", "Nemotron 3 Ultra Free"),
            ),
            credentialPresence = AgentCredentialPresence.NotApplicable,
            source = AgentModelSource.Free,
        ),
    ),
)
