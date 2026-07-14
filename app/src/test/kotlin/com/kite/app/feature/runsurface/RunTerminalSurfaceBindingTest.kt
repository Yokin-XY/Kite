package com.kite.app.feature.runsurface

import android.os.Looper
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.kite.app.R
import com.kite.app.run.CardRunStatus
import com.kite.app.run.CardRunSurface
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class RunTerminalSurfaceBindingTest {
    @Test
    fun `终端挂载和分离在容器移除前同步完成`() {
        val fixture = fixture()

        assertEquals(R.id.kite_run_terminal_container, fixture.binding.root.id)
        fixture.binding.render(terminalState())
        shadowOf(Looper.getMainLooper()).idle()

        val fragment = fixture.activity.supportFragmentManager
            .findFragmentByTag(fixture.binding.fragmentTagForTesting())
        assertNotNull(fragment)
        assertTrue(fragment!!.isAdded)
        assertFalse(fragment.isDetached)
        assertNotNull(fragment.view)

        fixture.binding.dispose()
        fixture.host.removeView(fixture.binding.root)

        assertTrue(fragment.isDetached)
        assertNull(fragment.view)
        fixture.activity.supportFragmentManager.executePendingTransactions()
    }

    @Test
    fun `容器挂载前销毁会取消待执行的终端事务`() {
        val fixture = fixture()

        fixture.binding.render(terminalState())
        fixture.binding.dispose()
        fixture.host.removeView(fixture.binding.root)
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(
            fixture.activity.supportFragmentManager
                .findFragmentByTag(fixture.binding.fragmentTagForTesting())
        )
    }

    @Test
    fun `同一稳定编号的旧 Fragment 未挂在当前容器时会重新绑定`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val manager = activity.supportFragmentManager
        val staleContainer = FrameLayout(activity).apply { id = R.id.kite_run_terminal_container }
        activity.setContentView(staleContainer)
        val tag = "${RunTerminalSurfaceBinding.FRAGMENT_TAG_PREFIX}${"instance-1".hashCode()}"
        val staleFragment = TestFragment()
        manager.beginTransaction().add(staleContainer.id, staleFragment, tag).commitNow()

        val host = FrameLayout(activity)
        activity.setContentView(host)
        val tokens = KiteTheme.resolve(
            ThemeConfig(KiteTheme.defaultThemeColor, KiteTheme.defaultBackgroundColor)
        )
        val binding = RunTerminalSurfaceBinding(
            context = activity,
            fragmentManager = manager,
            instanceId = "instance-1",
            tokens = tokens,
            fragmentFactory = { TestFragment() }
        )
        host.addView(binding.root)

        binding.render(terminalState())
        shadowOf(Looper.getMainLooper()).idle()

        val rebound = manager.findFragmentByTag(tag)
        assertNotNull(rebound)
        assertTrue(rebound !== staleFragment)
        assertSame(binding.root, rebound!!.view?.parent)
    }

    @Test
    fun `旧动态容器 Fragment 会被清理而稳定容器 Fragment 会保留`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val manager = activity.supportFragmentManager
        val legacyContainer = FrameLayout(activity).apply { id = View.generateViewId() }
        activity.setContentView(legacyContainer)
        val legacyTag = "${RunTerminalSurfaceBinding.FRAGMENT_TAG_PREFIX}legacy"
        val legacy = TestFragment()
        manager.beginTransaction().add(legacyContainer.id, legacy, legacyTag).commitNow()

        RunTerminalSurfaceBinding.removeIncompatibleRestoredFragments(manager)

        assertNull(manager.findFragmentByTag(legacyTag))

        val stableContainer = FrameLayout(activity).apply { id = R.id.kite_run_terminal_container }
        activity.setContentView(stableContainer)
        val stableTag = "${RunTerminalSurfaceBinding.FRAGMENT_TAG_PREFIX}stable"
        val stable = TestFragment()
        manager.beginTransaction().add(stableContainer.id, stable, stableTag).commitNow()

        RunTerminalSurfaceBinding.removeIncompatibleRestoredFragments(manager)

        assertSame(stable, manager.findFragmentByTag(stableTag))
    }

    @Test
    fun `Activity 恢复旧动态终端 Fragment 时不会在创建视图阶段崩溃`() {
        val first = Robolectric.buildActivity(LegacyCleanupActivity::class.java).setup()
        val activity = first.get()
        val legacyContainer = FrameLayout(activity).apply { id = View.generateViewId() }
        activity.setContentView(legacyContainer)
        val legacyTag = "${RunTerminalSurfaceBinding.FRAGMENT_TAG_PREFIX}restored"
        activity.supportFragmentManager.beginTransaction()
            .add(legacyContainer.id, TestFragment(), legacyTag)
            .commitNow()
        val savedState = Bundle()

        first.pause().saveInstanceState(savedState).stop().destroy()

        val restored = Robolectric.buildActivity(LegacyCleanupActivity::class.java)
            .create(savedState)
            .start()
            .resume()
            .visible()
            .get()

        assertNull(restored.supportFragmentManager.findFragmentByTag(legacyTag))
        assertNotNull(restored.findViewById<View>(R.id.kite_run_terminal_container))
    }

    private fun fixture(): Fixture {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val host = FrameLayout(activity)
        activity.setContentView(host)
        val tokens = KiteTheme.resolve(
            ThemeConfig(KiteTheme.defaultThemeColor, KiteTheme.defaultBackgroundColor)
        )
        val binding = RunTerminalSurfaceBinding(
            context = activity,
            fragmentManager = activity.supportFragmentManager,
            instanceId = "instance-1",
            tokens = tokens,
            fragmentFactory = { TestFragment() }
        )
        host.addView(
            binding.root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        return Fixture(activity, host, binding)
    }

    private fun terminalState(): RunSurfaceUiState = RunSurfaceUiState(
        target = RunSurfaceTarget("recipe-1", "instance-1"),
        title = "终端",
        status = CardRunStatus.WaitingTerminal,
        statusLabel = CardRunStatus.WaitingTerminal.label,
        surface = CardRunSurface.Terminal,
        content = RunSurfaceContent.Terminal("session-1"),
        structureKey = "terminal:session-1",
        currentStepIndex = 0,
        stepCount = 1,
        createdAt = 1L,
        canCompleteCurrentStep = true,
        canStop = true,
        windows = emptyList(),
        updatedAt = 1L
    )

    private data class Fixture(
        val activity: FragmentActivity,
        val host: FrameLayout,
        val binding: RunTerminalSurfaceBinding
    )

    class TestFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View = FrameLayout(requireContext())
    }

    class LegacyCleanupActivity : FragmentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            RunTerminalSurfaceBinding.removeIncompatibleRestoredFragments(supportFragmentManager)
            setContentView(FrameLayout(this).apply { id = R.id.kite_run_terminal_container })
        }
    }
}
