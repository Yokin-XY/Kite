package com.kftest.app.ui.terminal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.kftest.app.R

class TerminalActionSheet : BottomSheetDialogFragment() {

    enum class Action {
        BUILD_DOCTOR,
        BUILD_FAST_COMPILE,
        BUILD_DEBUG_APK,
        CLEAR_SCREEN,
        KILL_CURRENT_SESSION,
        SEND_CTRL_C,
        COPY_TRANSCRIPT,
        DELETE_CURRENT_SESSION
    }

    interface Listener {
        fun onTerminalActionSelected(action: Action)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_terminal_actions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bind(view, R.id.btnActionBuildDoctor, Action.BUILD_DOCTOR)
        bind(view, R.id.btnActionBuildCompile, Action.BUILD_FAST_COMPILE)
        bind(view, R.id.btnActionBuildAssemble, Action.BUILD_DEBUG_APK)
        bind(view, R.id.btnActionClearScreen, Action.CLEAR_SCREEN)
        bind(view, R.id.btnActionKillSession, Action.KILL_CURRENT_SESSION)
        bind(view, R.id.btnActionCtrlC, Action.SEND_CTRL_C)
        bind(view, R.id.btnActionCopyTranscript, Action.COPY_TRANSCRIPT)
        bind(view, R.id.btnActionDeleteCurrentSession, Action.DELETE_CURRENT_SESSION)
    }

    private fun bind(view: View, buttonId: Int, action: Action) {
        view.findViewById<MaterialButton>(buttonId).setOnClickListener {
            (parentFragment as? Listener)?.onTerminalActionSelected(action)
            dismissAllowingStateLoss()
        }
    }
}
