package com.kite.app.application.runs

import com.kite.app.recipe.KiteRecipe
import com.kite.app.run.CardRunState
import java.util.concurrent.CopyOnWriteArrayList

internal data class RunLifecycleEvent(
    val recipe: KiteRecipe,
    val state: CardRunState
)

internal fun interface RunLifecycleSink {
    fun onStateCommitted(event: RunLifecycleEvent)
}

/**
 * 进程内事实提交通知。CardRunStore 已经写完后才触发；这里不保存第二份状态。
 */
internal class RunLifecycleEventHub : RunLifecycleSink {
    private val sinks = CopyOnWriteArrayList<RunLifecycleSink>()

    fun register(sink: RunLifecycleSink) {
        sinks += sink
    }

    fun unregister(sink: RunLifecycleSink) {
        sinks -= sink
    }

    override fun onStateCommitted(event: RunLifecycleEvent) {
        sinks.forEach { sink -> sink.onStateCommitted(event) }
    }
}
