package com.kite.app.foundation.bootstrap

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.kite.app.R

class StartupGuardActivity : Activity() {
    private var readyReceiverRegistered = false
    private val readyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == StartupTraceStore.ACTION_STARTUP_READY) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReadyReceiver()
        StartupTraceStore.markStage(this, "guard.activity_created")
        if (StartupTraceStore.hasFailure(this)) {
            showFailureReport()
        } else {
            launchMainActivity()
        }
    }

    private fun launchMainActivity() {
        StartupTraceStore.markStage(this, "guard.main_launch_requested")
        try {
            startActivity(Intent().setClassName(packageName, "$packageName.MainActivity"))
        } catch (error: Throwable) {
            StartupTraceStore.recordGuardFailure(this, "guard.main_launch_requested", error)
            showFailureReport()
        }
    }

    override fun onDestroy() {
        if (readyReceiverRegistered) {
            unregisterReceiver(readyReceiver)
            readyReceiverRegistered = false
        }
        super.onDestroy()
    }

    private fun registerReadyReceiver() {
        val filter = IntentFilter(StartupTraceStore.ACTION_STARTUP_READY)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(readyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(readyReceiver, filter)
        }
        readyReceiverRegistered = true
    }

    private fun showFailureReport() {
        val report = StartupTraceStore.reportText(this)
        val detailsMarker = "\n\n阶段流水:"
        val detailsIndex = report.indexOf(detailsMarker)
        val summary = if (detailsIndex >= 0) report.substring(0, detailsIndex) else report
        val details = if (detailsIndex >= 0) report.substring(detailsIndex + 2) else ""
        val density = resources.displayMetrics.density
        val padding = (20 * density).toInt()
        val spacing = (10 * density).toInt()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(247, 248, 250))
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.startup_failure_title)
            textSize = 24f
            setTextColor(Color.rgb(24, 28, 36))
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.startup_failure_summary)
            textSize = 15f
            setTextColor(Color.rgb(72, 79, 91))
            setPadding(0, spacing, 0, spacing)
        })
        content.addView(TextView(this).apply {
            text = summary
            textSize = 13f
            setTextColor(Color.rgb(34, 39, 47))
            setTextIsSelectable(true)
            setPadding(0, spacing, 0, spacing)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        actions.addView(actionButton(getString(R.string.startup_copy_diagnostics)) { copyReport(report) })
        actions.addView(actionButton(getString(R.string.startup_share_diagnostics)) { shareReport(report) })
        actions.addView(actionButton(getString(R.string.startup_open_app_settings)) { openAppSettings() })
        actions.addView(actionButton(getString(R.string.startup_retry)) {
            StartupTraceStore.clearFailureForRetry(this)
            launchMainActivity()
        })
        content.addView(actions)
        if (details.isNotBlank()) {
            content.addView(TextView(this).apply {
                text = details
                textSize = 12f
                setTextColor(Color.rgb(72, 79, 91))
                setTextIsSelectable(true)
                setPadding(0, spacing, 0, 0)
            })
        }

        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    private fun copyReport(report: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.startup_diagnostics_clip_label), report))
        Toast.makeText(this, getString(R.string.startup_diagnostics_copied), Toast.LENGTH_SHORT).show()
    }

    private fun shareReport(report: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.startup_diagnostics_clip_label))
            putExtra(Intent.EXTRA_TEXT, report)
        }, getString(R.string.startup_share_chooser)))
    }

    private fun openAppSettings() {
        startActivity(Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        ))
    }
}
