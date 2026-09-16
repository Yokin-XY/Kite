package com.kite.app.platform.runtimemanagement

import com.kite.app.application.runtimemanagement.ProotViewInspectionGateway
import com.kite.app.application.runtimemanagement.ProotViewInspectionSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * View 层巡检——已退役，空实现。
 */
internal class AndroidProotViewInspectionGateway(
    @Suppress("UNUSED_PARAMETER") context: android.content.Context,
) : ProotViewInspectionGateway {
    private val state = MutableStateFlow(ProotViewInspectionSnapshot())
    override val snapshots: StateFlow<ProotViewInspectionSnapshot> = state
    override fun currentSnapshot(): ProotViewInspectionSnapshot = state.value
    override fun refresh(): ProotViewInspectionSnapshot = state.value
}
