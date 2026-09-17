package com.kite.app.foundation.bootstrap

import android.app.Activity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        val failure = StartupTraceStore.readFailure(this)
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
            text = getString(R.string.startup_report_raw_title)
            textSize = 17f
            setTextColor(Color.rgb(24, 28, 36))
            setPadding(0, spacing, 0, 0)
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.startup_report_raw_summary)
            textSize = 13f
            setTextColor(Color.rgb(72, 79, 91))
            setPadding(0, spacing, 0, spacing)
        })
        content.addView(actionButton(getString(R.string.startup_report_generate)) {
            generateReport(report)
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.startup_report_issues_title)
            textSize = 17f
            setTextColor(Color.rgb(24, 28, 36))
            setPadding(0, spacing * 2, 0, 0)
        })
        content.addView(TextView(this).apply {
            text = buildString {
                append(failure?.stage ?: "unknown")
                val reason = failure?.exceptionMessage.orEmpty()
                    .ifBlank { failure?.exceptionClass.orEmpty() }
                if (reason.isNotBlank()) {
                    append('\n')
                    append(reason)
                }
            }
            textSize = 14f
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
        actions.addView(actionButton(getString(R.string.startup_open_app_settings)) { openAppSettings() })
        actions.addView(actionButton(getString(R.string.startup_retry)) {
            StartupTraceStore.clearFailureForRetry(this)
            launchMainActivity()
        })
        content.addView(actions)

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

    private fun generateReport(report: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        runCatching {
            DiagnosticReportFileWriter.write(
                context = this,
                displayName = "kite-startup-failure-$timestamp.txt",
            ) { writer -> writer.write(report) }
        }.onSuccess { generated ->
            Toast.makeText(
                this,
                getString(R.string.startup_report_generated_path, generated.displayPath),
                Toast.LENGTH_LONG,
            ).show()
        }.onFailure { error ->
            Toast.makeText(
                this,
                getString(R.string.startup_report_generate_failed, error.message ?: error.javaClass.simpleName),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        ))
    }
}
