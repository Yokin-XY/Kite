package com.kite.app.foundation.service

import android.content.Context

/**
 * 任务入口层唯一正确动作入口。
 *
 * 只放：入口层向工作面层发动作。
 * 不放：容器参数、工作面实现细节、底层直连。
 */
object WorkstationActionGateway {

    fun createShellSession(context: Context) {
        KFShellService.createShellSession(context.applicationContext)
    }

    fun launchAgentSession(context: Context, runtimeId: String) {
        KFShellService.launchAgentSession(context.applicationContext, runtimeId)
    }

    fun sendTerminalCommand(
        context: Context,
        command: String,
        sessionId: String? = null
    ) {
        KFShellService.sendTerminalCommand(
            context = context.applicationContext,
            command = command,
            sessionId = sessionId
        )
    }

    fun pasteTerminalInput(
        context: Context,
        payload: String
    ) {
        KFShellService.pasteTerminalInput(
            context = context.applicationContext,
            payload = payload
        )
    }

    fun endTerminalSession(context: Context, sessionId: String? = null) {
        KFShellService.endTerminalSession(context.applicationContext, sessionId)
    }

    fun startBackgroundRuntime(context: Context, runtimeId: String) {
        KFShellService.startBackgroundRuntime(context.applicationContext, runtimeId)
    }

    fun stopBackgroundRuntime(context: Context, runtimeId: String) {
        KFShellService.stopBackgroundRuntime(context.applicationContext, runtimeId)
    }

    fun restartBackgroundRuntime(context: Context, runtimeId: String) {
        KFShellService.restartBackgroundRuntime(context.applicationContext, runtimeId)
    }
}
