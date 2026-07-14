package com.kite.app.feature.runsurface

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.kite.app.R
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit
import com.kite.app.ui.terminal.TerminalFragment

/** 终端显示绑定只管理 Fragment 的可见生命周期，不拥有 shell 会话。 */
internal class RunTerminalSurfaceBinding(
    context: Context,
    private val fragmentManager: FragmentManager,
    private val instanceId: String,
    private val tokens: ThemeTokens,
    private val fragmentFactory: (String) -> Fragment = { sessionId ->
        TerminalFragment.detailOnly(sessionId)
    }
) : RunSurfaceBinding {
    private val ui = UiKit(context, tokens)
    private val containerId = R.id.kite_run_terminal_container
    private val fragmentTag = "$FRAGMENT_TAG_PREFIX${instanceId.hashCode()}"
    private var sessionId: String? = null
    private var disposed = false
    private var pendingAttach: Runnable? = null

    override val root: FrameLayout = FrameLayout(context).apply {
        id = containerId
        setBackgroundColor(tokens.pageBackground)
    }

    override fun render(state: RunSurfaceUiState) {
        val nextSessionId = (state.content as? RunSurfaceContent.Terminal)
            ?.sessionId
            ?.takeIf { it.isNotBlank() }
        if (nextSessionId == null) {
            detachFragmentNow()
            sessionId = null
            showLoading()
            return
        }
        sessionId = nextSessionId
        scheduleAttach(nextSessionId)
    }

    override fun reconcile(): Boolean {
        val activeSessionId = sessionId ?: return false
        return scheduleAttach(activeSessionId)
    }

    private fun scheduleAttach(nextSessionId: String): Boolean {
        if (isFragmentMounted(fragmentManager.findFragmentByTag(fragmentTag))) return false
        val attach = Runnable {
            pendingAttach = null
            if (disposed || sessionId != nextSessionId || fragmentManager.isStateSaved ||
                fragmentManager.isDestroyed || root.parent == null
            ) return@Runnable
            var existing = fragmentManager.findFragmentByTag(fragmentTag)
            if (isFragmentMounted(existing)) return@Runnable
            root.removeAllViews()
            val transaction = fragmentManager.beginTransaction()
                .setReorderingAllowed(true)
            if (existing?.isDetached == true && existing.id == containerId) {
                transaction.attach(existing)
            } else {
                existing?.let(transaction::remove)
                transaction.add(containerId, fragmentFactory(nextSessionId), fragmentTag)
            }
            transaction.commitNowAllowingStateLoss()
        }
        pendingAttach?.let(root::removeCallbacks)
        pendingAttach = attach
        root.post(attach)
        return true
    }

    override fun dispose() {
        disposed = true
        pendingAttach?.let(root::removeCallbacks)
        pendingAttach = null
        detachFragmentNow()
    }

    private fun detachFragmentNow() {
        val fragment = fragmentManager.findFragmentByTag(fragmentTag) ?: return
        if (!fragmentManager.isDestroyed && fragment.isAdded && !fragment.isDetached) {
            fragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .detach(fragment)
                .commitNowAllowingStateLoss()
        }
    }

    private fun isFragmentMounted(fragment: Fragment?): Boolean =
        fragment?.isAdded == true &&
            !fragment.isDetached &&
            fragment.view?.parent === root

    internal fun fragmentTagForTesting(): String = fragmentTag

    private fun showLoading() {
        if (root.childCount > 0 && root.getChildAt(0).contentDescription == LOADING_DESCRIPTION) return
        root.removeAllViews()
        root.addView(LinearLayout(root.context).apply {
            contentDescription = LOADING_DESCRIPTION
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(ui.dp(24), ui.dp(28), ui.dp(24), ui.dp(28))
            addView(ProgressBar(context).apply { isIndeterminate = true })
            addView(TextView(context).apply {
                text = "正在准备终端"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(0, ui.dp(18), 0, 0)
            })
            addView(TextView(context).apply {
                text = "正在连接运行环境，请稍候"
                textSize = 12f
                setTextColor(tokens.textSecondary)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(0, ui.dp(8), 0, 0)
            })
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
    }

    internal companion object {
        private const val LOADING_DESCRIPTION = "运行终端准备中"
        internal const val FRAGMENT_TAG_PREFIX = "kite-run-terminal-"

        fun removeIncompatibleRestoredFragments(fragmentManager: FragmentManager) {
            if (fragmentManager.isDestroyed) return
            val incompatible = fragmentManager.fragments.filter { fragment ->
                fragment.tag?.startsWith(FRAGMENT_TAG_PREFIX) == true &&
                    fragment.id != R.id.kite_run_terminal_container
            }
            if (incompatible.isEmpty()) return
            val transaction = fragmentManager.beginTransaction().setReorderingAllowed(true)
            incompatible.forEach(transaction::remove)
            transaction.commitNowAllowingStateLoss()
        }
    }
}
