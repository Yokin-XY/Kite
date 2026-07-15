package com.kite.app.platform.runs

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunNotificationDismissalStoreTest {
    private val store = RunNotificationDismissalStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `清除只抑制同一实例代次`() {
        val instanceId = "dismiss-generation-test"
        store.clear(instanceId)

        store.dismiss(instanceId, 41L)

        assertTrue(store.isDismissed(instanceId, 41L))
        assertFalse(store.isDismissed(instanceId, 42L))
        assertFalse(store.isDismissed(instanceId, 41L))
    }

    @Test
    fun `实例事实移除后清理展示抑制`() {
        val instanceId = "dismiss-prune-test"
        store.clear(instanceId)
        store.dismiss(instanceId, 51L)

        store.prune(emptySet())

        assertFalse(store.isDismissed(instanceId, 51L))
    }
}
