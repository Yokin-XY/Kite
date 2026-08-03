package com.kite.app.agent.config

/**
 * Kite Agent SDK 的供应商预置目录入口。
 *
 * CC Switch 的供应商预置以应用类型为作用域，同一供应商会针对 Claude、Codex、OpenCode 等工具
 * 使用不同的协议端点。Kite 保持同样的事实边界：页面只接收当前 Adapter 能原生写入的预置，
 * 具体端点差异留在兼容目录中；预置仍只是可编辑起点，不会在选择时直接改变 Agent 状态。
 */
object AgentProviderPresetCatalog {
    fun presetsFor(adapterId: String?): List<AgentProviderPreset> = adapterId
        ?.let(CcSwitchProviderPresetDefinitions::presetsFor)
        .orEmpty()
}
