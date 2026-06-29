package com.kftest.app.foundation.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeBootstrapProgressTest {
    @Test
    fun stageUpdatesAreIgnoredOutsideBootstrapRun() {
        RuntimeBootstrapProgress.resetForTesting()

        RuntimeBootstrapProgress.stageStarted("ensureExternalExchange(buildContainerExecConfig)")

        assertEquals(false, RuntimeBootstrapProgress.snapshot.value.active)
        assertEquals(null, RuntimeBootstrapProgress.snapshot.value.percent)
    }

    @Test
    fun activeBootstrapProgressDoesNotMoveBackward() {
        RuntimeBootstrapProgress.resetForTesting()
        RuntimeBootstrapProgress.beginBootstrapRun()

        try {
            RuntimeBootstrapProgress.stageStarted("installBundledToolchain")
            assertEquals(80, RuntimeBootstrapProgress.snapshot.value.percent)

            RuntimeBootstrapProgress.stageStarted("ensureRuntimeOperational")
            assertEquals(80, RuntimeBootstrapProgress.snapshot.value.percent)
        } finally {
            RuntimeBootstrapProgress.endBootstrapRun()
        }
    }

    @Test
    fun readyClearsActiveBootstrapRun() {
        RuntimeBootstrapProgress.resetForTesting()
        RuntimeBootstrapProgress.beginBootstrapRun()

        try {
            RuntimeBootstrapProgress.stageStarted("installBundledToolchain")
            RuntimeBootstrapProgress.ready()

            assertEquals(false, RuntimeBootstrapProgress.snapshot.value.active)
            assertEquals(100, RuntimeBootstrapProgress.snapshot.value.percent)
        } finally {
            RuntimeBootstrapProgress.endBootstrapRun()
        }
    }

    @Test
    fun readyCanClearStaleProgressOutsideBootstrapRun() {
        RuntimeBootstrapProgress.resetForTesting()
        RuntimeBootstrapProgress.ready()

        assertEquals(false, RuntimeBootstrapProgress.snapshot.value.active)
        assertEquals(100, RuntimeBootstrapProgress.snapshot.value.percent)
    }
}
