package com.kite.app.run

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CardRunRuntimeLaneContractTest {
    @Test
    fun `card run store persists lane and fallback reason from state owner`() {
        val store = sourceFile("run/CardRunStore.kt").readText()
        val mutation = sourceFile("application/runs/RunExecutionContract.kt").readText()
        val executor = sourceFile("platform/runs/AndroidRecipeExecutor.kt").readText()

        assertTrue(store.contains("runtimeLane = runtimeLane ?: existing.runtimeLane"))
        assertTrue(store.contains(".put(\"runtimeLane\", runtimeLane.orEmpty())"))
        assertTrue(store.contains("runtimeLane = optString(\"runtimeLane\")"))
        assertTrue(store.contains(".put(\"runtimeFallbackReason\", runtimeFallbackReason.orEmpty())"))
        assertTrue(mutation.contains("val runtimeLane: String? = null"))
        assertTrue(executor.contains("runtimeLane = prepared.runtimeLane"))
        assertTrue(executor.contains("runtimeFallbackReason = prepared.fallbackReason"))
    }

    private fun sourceFile(relative: String): File = listOf(
        File("src/main/java/com/kite/app/$relative"),
        File("app/src/main/java/com/kite/app/$relative"),
    ).first(File::exists)
}
