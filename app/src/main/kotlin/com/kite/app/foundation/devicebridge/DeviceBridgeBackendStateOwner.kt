package com.kite.app.foundation.devicebridge

import android.content.Context
import com.kite.app.foundation.logging.Logger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 设置页和执行器共同消费的 Device Bridge 后端状态。 */
data class DeviceBridgeBackendSnapshot(
    val selectedMode: DeviceBridgeBackendMode = DeviceBridgeBackendMode.Shizuku,
    val lifecycle: DeviceBridgeLifecycleStatus = DeviceBridgeLifecycleStatus.Unavailable,
    val identity: DeviceBridgeIdentity = DeviceBridgeIdentity.Unknown,
    val uid: Int? = null,
    val detail: String = "",
    val checking: Boolean = false,
    val managerInstalled: Boolean = false,
    val binderAlive: Boolean = false,
    val serverVersion: Int? = null,
    val revision: Long = 0L,
)

/** 纯投影规则，避免设置页自行解释 Shizuku 或 Root 的底层状态。 */
internal object DeviceBridgeBackendStateProjector {
    fun fromShizuku(state: ShizukuBridgeState): DeviceBridgeBackendSnapshot =
        DeviceBridgeBackendSnapshot(
            selectedMode = DeviceBridgeBackendMode.Shizuku,
            lifecycle = state.lifecycle,
            identity = state.identity,
            uid = state.uid,
            detail = state.error.orEmpty(),
            checking = state.requestInFlight,
            managerInstalled = state.managerInstalled,
            binderAlive = state.binderAlive,
            serverVersion = state.serverVersion,
        )

    fun rootNotChecked(): DeviceBridgeBackendSnapshot = DeviceBridgeBackendSnapshot(
        selectedMode = DeviceBridgeBackendMode.RootExperimental,
        lifecycle = DeviceBridgeLifecycleStatus.Unavailable,
        detail = DETAIL_ROOT_NOT_CHECKED,
    )

    fun rootChecking(): DeviceBridgeBackendSnapshot = DeviceBridgeBackendSnapshot(
        selectedMode = DeviceBridgeBackendMode.RootExperimental,
        lifecycle = DeviceBridgeLifecycleStatus.Connecting,
        detail = DETAIL_ROOT_CHECKING,
        checking = true,
    )

    fun fromRootProbe(probe: RootBridgeProbe): DeviceBridgeBackendSnapshot =
        DeviceBridgeBackendSnapshot(
            selectedMode = DeviceBridgeBackendMode.RootExperimental,
            lifecycle = probe.lifecycle,
            identity = probe.identity,
            uid = probe.uid,
            detail = probe.detail,
        )

    const val DETAIL_ROOT_NOT_CHECKED = "root_not_checked"
    private const val DETAIL_ROOT_CHECKING = "root_checking"
}

/**
 * Device Bridge 模式与实时连接事实的应用级唯一所有者。
 *
 * Root 检测可能触发授权界面，因此只允许由用户选择 Root 或点击检测时显式执行；
 * 应用启动、页面恢复和普通状态刷新都不会运行 su。
 */
object DeviceBridgeBackendStateOwner {
    private const val LOG_TAG = "DeviceBridgeState"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(DeviceBridgeBackendSnapshot())
    private val rootProbeGeneration = AtomicLong(0L)
    private val revision = AtomicLong(0L)

    val state: StateFlow<DeviceBridgeBackendSnapshot> = mutableState.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    @Synchronized
    fun start(context: Context) {
        if (appContext != null) return
        val applicationContext = context.applicationContext
        appContext = applicationContext
        ShizukuBridgeStateOwner.start(applicationContext)
        when (DeviceBridgeBackendModeStore.current(applicationContext)) {
            DeviceBridgeBackendMode.Shizuku -> publish(
                DeviceBridgeBackendStateProjector.fromShizuku(ShizukuBridgeStateOwner.current())
            )
            DeviceBridgeBackendMode.RootExperimental -> publish(
                DeviceBridgeBackendStateProjector.rootNotChecked()
            )
        }
        scope.launch {
            ShizukuBridgeStateOwner.state.collect { shizukuState ->
                publishShizukuIfSelected(shizukuState)
            }
        }
    }

    fun current(): DeviceBridgeBackendSnapshot = mutableState.value

    /** 持久化模式。选择 Root 是显式用户动作，因此随后执行一次 Root 检测。 */
    fun select(mode: DeviceBridgeBackendMode): Boolean {
        val context = appContext ?: return false
        if (!DeviceBridgeBackendModeStore.select(context, mode)) return false
        rootProbeGeneration.incrementAndGet()
        when (mode) {
            DeviceBridgeBackendMode.Shizuku -> {
                publish(DeviceBridgeBackendStateProjector.fromShizuku(ShizukuBridgeStateOwner.current()))
                ShizukuBridgeStateOwner.refresh("backend_selected")
            }
            DeviceBridgeBackendMode.RootExperimental -> {
                publish(DeviceBridgeBackendStateProjector.rootNotChecked())
                probeRoot()
            }
        }
        return true
    }

    /** 普通页面刷新只刷新 Shizuku；Root 必须通过 [probeRoot] 显式检测。 */
    fun refreshSelected() {
        if (mutableState.value.selectedMode == DeviceBridgeBackendMode.Shizuku) {
            ShizukuBridgeStateOwner.refresh("backend_refresh")
        }
    }

    fun probeRoot(): Boolean {
        if (appContext == null ||
            mutableState.value.selectedMode != DeviceBridgeBackendMode.RootExperimental
        ) {
            return false
        }
        val generation = rootProbeGeneration.incrementAndGet()
        publish(DeviceBridgeBackendStateProjector.rootChecking())
        scope.launch {
            val probe = RootDeviceBridgeBackend.probe()
            if (generation != rootProbeGeneration.get()) return@launch
            if (mutableState.value.selectedMode != DeviceBridgeBackendMode.RootExperimental) return@launch
            publish(DeviceBridgeBackendStateProjector.fromRootProbe(probe))
        }
        return true
    }

    @Synchronized
    private fun publishShizukuIfSelected(shizukuState: ShizukuBridgeState) {
        if (mutableState.value.selectedMode != DeviceBridgeBackendMode.Shizuku) return
        publish(DeviceBridgeBackendStateProjector.fromShizuku(shizukuState))
    }

    @Synchronized
    private fun publish(snapshot: DeviceBridgeBackendSnapshot) {
        mutableState.value = snapshot.copy(revision = revision.incrementAndGet())
        Logger.i(
            LOG_TAG,
            "mode=${snapshot.selectedMode.storageValue} lifecycle=${snapshot.lifecycle} " +
                "identity=${snapshot.identity} checking=${snapshot.checking}"
        )
    }
}
