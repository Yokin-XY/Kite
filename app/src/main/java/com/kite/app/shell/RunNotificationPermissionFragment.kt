package com.kite.app.shell

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kite.app.platform.runs.AndroidRunNotificationAccess
import kotlinx.coroutines.launch

/**
 * 无界面的通知权限宿主。Activity 只提交“需要通知后重试”的意图，权限、设置回返和
 * 等待步骤提醒都由这里统一处理。
 */
internal class RunNotificationPermissionFragment : Fragment() {
    private val coordinator by lazy { KiteAppGraph.from(requireContext()).runNotificationCoordinator }
    private var pendingRetry: (() -> Unit)? = null
    private var shownRequirementKey: String? = null
    private var dialog: AlertDialog? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(requireContext(), "通知未开启，可在设置中再次授权", Toast.LENGTH_SHORT).show()
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
        dialog = AlertDialog.Builder(requireContext())
            .setTitle("需要通知权限")
            .setMessage(
                "$title 的进度、结果和下一步操作由系统通知承载。" +
                    "请允许通知；顶部提醒由系统里的“首页卡片进度”类别控制。"
            )
            .setPositiveButton("去开启") { _, _ -> requestAccess() }
            .setNegativeButton("暂不", null)
            .create()
            .also { nextDialog ->
                nextDialog.setOnDismissListener { dialog = null }
                nextDialog.show()
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
                    Toast.makeText(context, "无法打开通知设置", Toast.LENGTH_SHORT).show()
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
