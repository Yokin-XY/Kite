package com.kite.app.application.runs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 一次性页面呈现请求。事实已先写入 CardRunStore，错过 Effect 的页面仍可从状态恢复。
 */
internal class RunExecutionEffectBus : RunExecutionEffectSink {
    private val mutableEffects = MutableSharedFlow<RunExecutionEffect>(extraBufferCapacity = 16)
    val effects: Flow<RunExecutionEffect> = mutableEffects.asSharedFlow()

    override fun emit(effect: RunExecutionEffect) {
        mutableEffects.tryEmit(effect)
    }
}
