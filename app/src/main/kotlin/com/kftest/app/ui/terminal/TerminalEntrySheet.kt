package com.kftest.app.ui.terminal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.kftest.app.R

class TerminalEntrySheet : BottomSheetDialogFragment() {

    enum class Action {
        NEW_SESSION,
        OPEN_CLAUDE,
        OPEN_CODEX,
        OPEN_OPENCLAW,
        SEND_ENTER
    }

    interface Listener {
        fun onTerminalEntrySelected(action: Action)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_terminal_entries, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind(view, R.id.btnEntryNewSession, Action.NEW_SESSION)
        bind(view, R.id.btnEntryClaude, Action.OPEN_CLAUDE)
        bind(view, R.id.btnEntryCodex, Action.OPEN_CODEX)
        bind(view, R.id.btnEntryOpenClaw, Action.OPEN_OPENCLAW)
        bind(view, R.id.btnEntrySendEnter, Action.SEND_ENTER)
    }

    private fun bind(view: View, buttonId: Int, action: Action) {
        view.findViewById<MaterialButton>(buttonId).setOnClickListener {
            (parentFragment as? Listener)?.onTerminalEntrySelected(action)
            dismissAllowingStateLoss()
        }
    }
}
