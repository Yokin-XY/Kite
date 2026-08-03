package com.kite.app.bridge

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteBridgeExecutionHostResidencyContractTest {
    private val bridge = File(
        "src/main/java/com/kite/app/bridge/KiteBridgeClient.kt"
    ).readText()
    private val service = File(
        "src/main/kotlin/com/kite/app/foundation/service/KFShellService.kt"
    ).readText()

    @Test
    fun `直接执行先取得前台宿主再启动 Linux 配方`() {
        val residency = bridge.indexOf("KFShellService.ensureExecutionHostResident(context)")
        val execution = bridge.indexOf("runDirectRecipe(context, recipe, requestId")

        assertTrue(residency >= 0)
        assertTrue(execution > residency)
    }

    @Test
    fun `执行宿主驻留动作不触发默认 runtime 暖启动`() {
        assertTrue(service.contains("ACTION_ENSURE_EXECUTION_HOST_RESIDENT"))
        assertTrue(service.contains("直接执行宿主已取得前台驻留保障"))
        assertTrue(service.contains("ContextCompat.startForegroundService(appContext, intent)"))
        val handlerStart = service.indexOf("ACTION_ENSURE_EXECUTION_HOST_RESIDENT -> {")
        val handlerEnd = service.indexOf("ACTION_START_BACKGROUND_RUNTIME -> {", handlerStart)
        val handler = service.substring(handlerStart, handlerEnd)
        assertFalse(handler.contains("ensureOperationalState"))
        assertFalse(handler.contains("BackgroundRuntimeHost"))
    }
}
