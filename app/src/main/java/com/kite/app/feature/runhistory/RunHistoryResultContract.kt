package com.kite.app.feature.runhistory

import android.os.Bundle
import androidx.fragment.app.Fragment

internal object RunHistoryResultContract {
    const val REQUEST_KEY = "kite.run-history.request"
    private const val KEY_BACK = "back"

    fun sendBack(fragment: Fragment) {
        fragment.parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            Bundle().apply { putBoolean(KEY_BACK, true) }
        )
    }

    fun isBack(bundle: Bundle): Boolean = bundle.getBoolean(KEY_BACK, false)
}
