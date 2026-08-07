package com.kite.app.feature.runsurface

import android.content.Context
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class SingleShotWarmup(
    private val executor: Executor,
) {
    private val scheduled = AtomicBoolean(false)

    fun schedule(preload: () -> Unit): Boolean {
        if (!scheduled.compareAndSet(false, true)) return false
        executor.execute(preload)
        return true
    }
}

/** Main 首帧完成后只预校验 Agent 显示类，不构造 View 或读取会话事实。 */
internal object AgentSurfaceWarmup {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KiteAgentSurfaceWarmup").apply { isDaemon = true }
    }
    private val warmup = SingleShotWarmup(executor)

    fun schedule(context: Context) {
        val classLoader = context.applicationContext.classLoader
        warmup.schedule {
            runCatching {
                Class.forName(
                    RunAgentSurfaceBinding::class.java.name,
                    true,
                    classLoader,
                )
            }
        }
    }
}
