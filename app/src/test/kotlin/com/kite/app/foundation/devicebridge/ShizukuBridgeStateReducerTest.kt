package com.kite.app.foundation.devicebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuBridgeStateReducerTest {
    @Test
    fun `installed manager without binder is stopped rather than ready`() {
        val state = reduce(
            ShizukuBridgeSignal.SnapshotObserved(
                managerInstalled = true,
                binderAlive = false,
                permissionGranted = null,
                uid = null,
                serverVersion = null,
                source = "test"
            )
        )

        assertEquals(DeviceBridgeLifecycleStatus.InstalledButStopped, state.lifecycle)
        assertFalse(state.binderAlive)
        assertEquals(ShizukuPermissionState.Unknown, state.permission)
    }

    @Test
    fun `live shell binder without permission requires authorization`() {
        val state = reduce(observed(permissionGranted = false, uid = 2_000))

        assertEquals(DeviceBridgeLifecycleStatus.PermissionRequired, state.lifecycle)
        assertEquals(DeviceBridgeIdentity.Shell, state.identity)
        assertEquals(ShizukuPermissionState.Required, state.permission)
    }

    @Test
    fun `authorization result drives ready and revoked states`() {
        val waiting = reduce(observed(permissionGranted = false, uid = 2_000))
        val requesting = ShizukuBridgeStateReducer.reduce(waiting, ShizukuBridgeSignal.AuthorizationRequested)
        val denied = ShizukuBridgeStateReducer.reduce(requesting, ShizukuBridgeSignal.PermissionResult(false))
        val granted = ShizukuBridgeStateReducer.reduce(requesting, ShizukuBridgeSignal.PermissionResult(true))

        assertEquals(DeviceBridgeLifecycleStatus.Connecting, requesting.lifecycle)
        assertTrue(requesting.requestInFlight)
        assertEquals(DeviceBridgeLifecycleStatus.Revoked, denied.lifecycle)
        assertEquals(ShizukuPermissionState.Denied, denied.permission)
        assertEquals(DeviceBridgeLifecycleStatus.Ready, granted.lifecycle)
        assertEquals(ShizukuPermissionState.Granted, granted.permission)
        assertFalse(granted.requestInFlight)
    }

    @Test
    fun `binder death clears stale privilege identity`() {
        val ready = reduce(observed(permissionGranted = true, uid = 0))
        val dead = ShizukuBridgeStateReducer.reduce(ready, ShizukuBridgeSignal.BinderDied(managerInstalled = true))

        assertEquals(DeviceBridgeLifecycleStatus.InstalledButStopped, dead.lifecycle)
        assertEquals(DeviceBridgeIdentity.Unknown, dead.identity)
        assertNull(dead.uid)
        assertEquals(ShizukuPermissionState.Unknown, dead.permission)
    }

    @Test
    fun `uid zero is represented as root without changing backend contract`() {
        val state = reduce(observed(permissionGranted = true, uid = 0))

        assertEquals(DeviceBridgeLifecycleStatus.Ready, state.lifecycle)
        assertEquals(DeviceBridgeIdentity.Root, state.identity)
    }

    private fun reduce(signal: ShizukuBridgeSignal): ShizukuBridgeState =
        ShizukuBridgeStateReducer.reduce(ShizukuBridgeState(), signal)

    private fun observed(permissionGranted: Boolean, uid: Int) = ShizukuBridgeSignal.SnapshotObserved(
        managerInstalled = true,
        binderAlive = true,
        permissionGranted = permissionGranted,
        uid = uid,
        serverVersion = 13,
        source = "test"
    )
}
