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
    private var pendingCancellation: (() -> Unit)? = null
    private var awaitingExternalAccess = false
    private var shownRequirementKey: String? = null
    private var dialog: Dialog? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        awaitingExternalAccess = false
        if (granted) {
            coordinator.refresh()
            resumePendingAction()
        } else {
            Toast.makeText(requireContext(), R.string.run_notification_permission_denied, Toast.LENGTH_SHORT).show()
            cancelPendingAction()
        }
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
        if (awaitingExternalAccess) {
            awaitingExternalAccess = false
            if (AndroidRunNotificationAccess.isAvailable(requireContext())) {
                resumePendingAction()
            } else {
                cancelPendingAction()
            }
        } else {
            resumePendingAction()
        }
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        pendingRetry = null
        pendingCancellation = null
        awaitingExternalAccess = false
        super.onDestroy()
    }

    fun request(
        title: String,
        key: String,
        retry: (() -> Unit)? = null,
        onCancelled: (() -> Unit)? = null,
    ) {
        pendingRetry = retry
        pendingCancellation = onCancelled
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
                dismissOnClick = false,
                onClick = ::requestAccess,
            ),
        ).also { nextDialog ->
            nextDialog.setOnDismissListener {
                dialog = null
                if (!awaitingExternalAccess && pendingRetry != null) cancelPendingAction()
            }
        }
    }

    private fun requestAccess() {
        val context = requireContext()
        if (AndroidRunNotificationAccess.isAvailable(context)) {
            resumePendingAction()
        } else if (AndroidRunNotificationAccess.needsRuntimePermission(context)) {
            awaitingExternalAccess = true
            dialog?.dismiss()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            awaitingExternalAccess = true
            dialog?.dismiss()
            runCatching { startActivity(AndroidRunNotificationAccess.runChannelSettingsIntent(context)) }
                .onFailure {
                    awaitingExternalAccess = false
                    Toast.makeText(context, R.string.run_notification_permission_settings_failed, Toast.LENGTH_SHORT).show()
                    cancelPendingAction()
                }
        }
    }

    private fun resumePendingAction() {
        if (!AndroidRunNotificationAccess.isAvailable(requireContext())) return
        coordinator.refresh()
        shownRequirementKey = null
        val retry = pendingRetry ?: return
        pendingRetry = null
        pendingCancellation = null
        awaitingExternalAccess = false
        dismissDialogWithoutCallback()
        retry()
    }

    private fun cancelPendingAction() {
        shownRequirementKey = null
        awaitingExternalAccess = false
        pendingRetry = null
        val cancellation = pendingCancellation
        pendingCancellation = null
        dismissDialogWithoutCallback()
        cancellation?.invoke()
    }

    private fun dismissDialogWithoutCallback() {
        val current = dialog ?: return
        dialog = null
        current.setOnDismissListener(null)
        current.dismiss()
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
            retry: (() -> Unit)? = null,
            onCancelled: (() -> Unit)? = null,
        ) {
            install(fragmentManager).request(title, key, retry, onCancelled)
        }
    }
}
