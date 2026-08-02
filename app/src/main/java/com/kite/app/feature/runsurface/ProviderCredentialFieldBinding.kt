package com.kite.app.feature.runsurface

import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import com.kite.app.agent.config.AgentProviderCredentialChange

/** 管理 Provider 凭据输入的显示状态，不持有或解释具体供应商协议。 */
internal class ProviderCredentialFieldBinding(
    val field: EditText,
    val pasteAction: ImageButton,
    private val deleteAction: ImageButton,
    private val credentialPresent: Boolean,
    private val emptyHint: String,
) {
    private var removeRequested: Boolean = false

    fun markForRemoval() {
        removeRequested = true
        field.setText("")
        field.hint = AgentProviderCredentialInputPolicy.displayHint(
            credentialPresent = credentialPresent,
            removeRequested = true,
            emptyHint = emptyHint,
        )
        deleteAction.visibility = View.GONE
    }

    fun onInputChanged(value: CharSequence?) {
        if (!value.isNullOrBlank()) {
            removeRequested = false
        }
        field.hint = AgentProviderCredentialInputPolicy.displayHint(
            credentialPresent = credentialPresent,
            removeRequested = removeRequested,
            emptyHint = emptyHint,
        )
        deleteAction.visibility = if (credentialPresent && !removeRequested) View.VISIBLE else View.GONE
    }

    fun credentialChange(): AgentProviderCredentialChange =
        AgentProviderCredentialInputPolicy.credentialChange(
            removeRequested = removeRequested,
            value = field.text,
        )

    fun setEnabledState(enabled: Boolean) {
        field.isEnabled = enabled
        field.alpha = if (enabled) 1f else 0.45f
        pasteAction.isEnabled = enabled
        pasteAction.alpha = if (enabled) 1f else 0.45f
        deleteAction.isEnabled = enabled
        deleteAction.alpha = if (enabled) 1f else 0.45f
    }
}
