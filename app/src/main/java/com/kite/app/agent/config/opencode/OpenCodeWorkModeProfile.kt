package com.kite.app.agent.config.opencode

import com.kite.app.agent.config.AgentWorkModeCatalog
import com.kite.app.agent.contract.AgentMode

private data class OpenCodeWorkModeDisplay(
    val name: String,
    val description: String,
)

private val OPEN_CODE_WORK_MODE_DISPLAYS = mapOf(
    "build" to OpenCodeWorkModeDisplay(
        name = "执行",
        description = "使用完整工具完成开发、修改和验证任务",
    ),
    "plan" to OpenCodeWorkModeDisplay(
        name = "规划",
        description = "分析问题并制定方案，文件修改和命令默认受限",
    ),
)

internal fun openCodeWorkModeCatalog(): AgentWorkModeCatalog = AgentWorkModeCatalog(
    modes = OPEN_CODE_WORK_MODE_DISPLAYS.map { (id, display) ->
        AgentMode(id = id, name = display.name, description = display.description)
    },
    defaultModeId = "build",
)

internal fun normalizeOpenCodeWorkModes(modes: List<AgentMode>): List<AgentMode> =
    modes.map { mode ->
        val display = OPEN_CODE_WORK_MODE_DISPLAYS[mode.id] ?: return@map mode
        mode.copy(name = display.name, description = display.description)
    }
