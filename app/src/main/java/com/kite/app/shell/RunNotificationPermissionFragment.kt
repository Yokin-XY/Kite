package com.kite.app.shell

import android.Manifest
import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kite.app.R
import com.kite.app.platform.runs.AndroidRunNotificationAccess
import com.kite.app.ui.UiActionRole
import com.kite.app.ui.UiDialogAction
import com.kite.app.ui.UiKit
import com.kite.app.ui.theme.kiteThemeEnvironment
import kotlinx.coroutines.launch

/**
 * 无界面的通知权限宿主。Activity 只提交“需要通知后重试”的意图，权限、设置回返和
 * 等待步骤提醒都由这里统一处理。
 */
internal class RunNotificationPermissionFragment : Fragment() {
    private val coordinator by lazy { KiteAppGraph.from(requireContext()).runNotificationCoordinator }
    private var pendingRetry: (() -> Unit)? = null
    private var shownRequirementKey: String? = null
    private var dialog: Dialog? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(requireContext(), R.string.run_notification_permission_denied, Toast.LENGTH_SHORT).show()
        }
        coordinator.refresh()
        resumePendingAction()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                coordinator.requirement.collect { requirement ->
                    if (requirement == null) {
                        if (AndroidRunNotificationAccess.isAvailable(requireContext())) {
                            shownRequirementKey = null
                        }
                    } else {
                        showRequirement(requirement.title, requirement.key, explicit = false)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        coordinator.refresh()
        resumePendingAction()
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        pendingRetry = null
        super.onDestroy()
    }

    fun request(title: String, key: String, retry: (() -> Unit)? = null) {
        pendingRetry = retry
        showRequirement(title, key, explicit = true)
    }

    private fun showRequirement(title: String, key: String, explicit: Boolean) {
        if (AndroidRunNotificationAccess.isAvailable(requireContext())) {
            resumePendingAction()
            return
        }
        if (dialog?.isShowing == true) return
        if (!explicit && shownRequirementKey == key) return
        shownRequirementKey = key
        val context = requireContext()
        dialog = UiKit(context, context.kiteThemeEnvironment()).showConfirmDialog(
            context = context,
            title = getString(R.string.run_notification_permission_title),
            message = getString(R.string.run_notification_permission_summary, title),
            dismissLabel = getString(R.string.run_notification_permission_later),
            primaryAction = UiDialogAction(
                label = getString(R.string.run_notification_permission_open),
                role = UiActionRole.Primary,
                onClick = ::requestAccess,
            ),
        ).also { nextDialog ->
            nextDialog.setOnDismissListener { dialog = null }
        }
    }

    private fun requestAccess() {
        val context = requireContext()
        if (AndroidRunNotificationAccess.isAvailable(context)) {
            resumePendingAction()
        } else if (AndroidRunNotificationAccess.needsRuntimePermission(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching { startActivity(AndroidRunNotificationAccess.runChannelSettingsIntent(context)) }
                .onFailure {
                    Toast.makeText(context, R.string.run_notification_permission_settings_failed, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun resumePendingAction() {
        if (!AndroidRunNotificationAccess.isAvailable(requireContext())) return
        coordinator.refresh()
        shownRequirementKey = null
        dialog?.dismiss()
        val retry = pendingRetry ?: return
        pendingRetry = null
        retry()
    }

    companion object {
        private const val TAG = "kite-run-notification-permission"

        fun install(fragmentManager: FragmentManager): RunNotificationPermissionFragment =
            fragmentManager.findFragmentByTag(TAG) as? RunNotificationPermissionFragment
                ?: RunNotificationPermissionFragment().also { fragment ->
                    fragmentManager.beginTransaction()
                        .add(fragment, TAG)
                        .commitNowAllowingStateLoss()
                }

        fun request(
            fragmentManager: FragmentManager,
            title: String,
            key: String,
            retry: (() -> Unit)? = null
        ) {
            install(fragmentManager).request(title, key, retry)
        }
    }
}
