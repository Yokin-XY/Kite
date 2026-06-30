package com.kite.app.foundation.service

import android.content.Context
import android.content.Intent

/**
 * foundation 层对"入口层 Activity"的依赖反转契约。
 *
 * 背景:KFShellService(底层前台服务)原本直接 import com.kite.app.MainActivity /
 * CardRunActivity(上层业务层),用来做 recent-task 过滤和通知 PendingIntent 构造。
 * 这构成"底层依赖上层"的反向依赖,违背分层。
 *
 * 解法:foundation 只定义本接口,业务层(KFApplication)在启动时注入实现,
 * KFShellService 通过接口拿到所需 Activity 的 Class 与类名,不再 hardcode 上层类。
 */
interface KiteTaskContract {
    /** 主控台 Activity 的 Class,用于构造通知 PendingIntent。 */
    val mainActivityClass: Class<*>

    /** 卡片运行窗口 Activity 的类名,用于 recent-task 过滤。 */
    val cardRunActivityClassName: String

    /** 主控台 Activity 的类名,用于判断被移除的 task 是否主任务。 */
    val mainActivityClassName: String
        get() = mainActivityClass.name

    /** 构造一个跳转到主控台的 Intent(带 NEW_TASK 标志)。 */
    fun buildMainActivityIntent(context: Context): Intent =
        Intent(context, mainActivityClass).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * KiteTaskContract 的全局注入点。
 *
 * 由 KFApplication.onCreate 在应用启动时设置,供 KFShellService 等底层组件读取。
 * 默认值会在未注入时抛出明确错误,避免静默失败。
 */
object KiteTaskContractHost {
    @Volatile
    private var contract: KiteTaskContract? = null

    fun install(contract: KiteTaskContract) {
        this.contract = contract
    }

    fun get(): KiteTaskContract =
        contract ?: error("KiteTaskContract 尚未注入;应在 KFApplication.onCreate 中 install。")
}
