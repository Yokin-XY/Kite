package com.kite.app.ui.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kite.app.R
import com.kite.app.foundation.toolchain.ToolchainInstallPhase
import com.kite.app.foundation.toolchain.ToolchainInstallState
import com.kite.app.foundation.toolchain.ToolchainPackInstaller
import kotlinx.coroutines.launch

class BridgeFragment : Fragment() {

    private lateinit var tvBridgeStatus: TextView
    private lateinit var tvToolchainStatus: TextView
    private lateinit var tvToolchainInventory: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_bridge, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
        observeInstallerState()
        ToolchainPackInstaller.refreshState(requireContext())
    }

    override fun onResume() {
        super.onResume()
        ToolchainPackInstaller.refreshState(requireContext())
    }

    private fun setupViews(view: View) {
        tvBridgeStatus = view.findViewById(R.id.tvBridgeStatus)
        tvToolchainStatus = view.findViewById(R.id.tvToolchainStatus)
        tvToolchainInventory = view.findViewById(R.id.tvToolchainInventory)

        tvBridgeStatus.text = "环境工具"
        view.findViewById<Button>(R.id.btnPrepareAiEnv).setOnClickListener {
            ToolchainPackInstaller.prepareAiEnv(requireContext())
            Toast.makeText(requireContext(), "已开始检查并修复 KF 工具环境", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.btnToolchainDoctor).setOnClickListener {
            ToolchainPackInstaller.doctor(requireContext())
            Toast.makeText(requireContext(), "已开始环境诊断", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.btnCopyPrepareAction).setOnClickListener {
            copyCommand(adbCommand("prepare_ai_env"))
        }
        view.findViewById<Button>(R.id.btnCopyDoctorAction).setOnClickListener {
            copyCommand(adbCommand("doctor"))
        }
        view.findViewById<Button>(R.id.btnRefreshStatus).setOnClickListener {
            ToolchainPackInstaller.refreshState(requireContext())
        }
    }

    private fun observeInstallerState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                ToolchainPackInstaller.state.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: ToolchainInstallState) {
        val phaseLabel = when (state.phase) {
            ToolchainInstallPhase.IDLE -> "未安装 / 未诊断"
            ToolchainInstallPhase.RUNNING -> "执行中"
            ToolchainInstallPhase.SUCCEEDED -> "完成"
            ToolchainInstallPhase.FAILED -> "失败"
        }
        tvToolchainStatus.text = buildString {
            appendLine("KF 工具环境：$phaseLabel")
            appendLine("动作：${state.action.ifBlank { "--" }}")
            appendLine("摘要：${state.summary}")
            appendLine("退出码：${state.exitCode?.toString() ?: "--"} timeout=${state.timedOut}")
            appendLine("日志：${state.logPath.ifBlank { ToolchainPackInstaller.logFile(requireContext()).absolutePath }}")
        }
        tvToolchainInventory.text = state.outputPreview.ifBlank {
            "点击“检查并修复”会补齐或修复 Node 24 LTS、uv、pnpm、Python venv/pip 兼容包和常用 CLI；点击“环境诊断”只读取当前状态。"
        }
    }

    private fun adbCommand(action: String): String {
        // 指向真实注册的 com.kite.app.MainActivity(它处理 toolchain_action automation intent)。
        // 原 .ui.main.MainActivity 是未注册的死代码(T10 已删),该 adb 命令本就跑不通。
        return "adb shell am start-activity -n ${requireContext().packageName}/.MainActivity " +
            "--es toolchain_action $action"
    }

    private fun copyCommand(command: String) {
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("kfshell-toolchain-command", command))
        Toast.makeText(requireContext(), "已复制命令", Toast.LENGTH_SHORT).show()
    }
}
