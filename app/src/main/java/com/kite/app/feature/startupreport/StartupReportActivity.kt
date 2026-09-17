package com.kite.app.feature.startupreport

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kite.app.CardRunIntents
import com.kite.app.R
import com.kite.app.application.resources.ResourceActionEffect
import com.kite.app.shell.KiteAppGraph
import com.kite.app.shell.RunNotificationPermissionFragment
import com.kite.app.ui.UiActionRole
import com.kite.app.ui.UiKit
import com.kite.app.ui.UiTextRole
import com.kite.app.ui.theme.kiteThemeEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** 首次运行报告：页面只展示异常检查，完整原文按需生成到下载目录。 */
class StartupReportActivity : AppCompatActivity() {
    private lateinit var ui: UiKit
    private lateinit var issueHost: LinearLayout
    private lateinit var reportStatus: TextView
    private lateinit var generateAction: TextView
    private var report: StartupReportBundle? = null
    private var initialResumeHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = UiKit(this, kiteThemeEnvironment())
        setContentView(buildContent())
        loadReport()
    }

    override fun onResume() {
        super.onResume()
        if (initialResumeHandled) loadReport() else initialResumeHandled = true
    }

    private fun buildContent(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(ui.tokens.pageBackground)
        addView(ui.topBar(
            context = this@StartupReportActivity,
            title = getString(R.string.startup_report_title),
            onBack = ::finish,
        ))
        addView(ScrollView(this@StartupReportActivity).apply {
            isFillViewport = true
            addView(LinearLayout(this@StartupReportActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    ui.dp(ui.foundations.spacing.pageHorizontal),
                    ui.dp(ui.foundations.spacing.sectionGap),
                    ui.dp(ui.foundations.spacing.pageHorizontal),
                    ui.dp(36),
                )
                addView(rawReportCard())
                addView(TextView(this@StartupReportActivity).apply {
                    text = getString(R.string.startup_report_issues_title)
                    ui.applyTextRole(this, UiTextRole.SectionTitle)
                    setPadding(0, ui.dp(24), 0, ui.dp(10))
                })
                issueHost = LinearLayout(this@StartupReportActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(supportingText(getString(R.string.startup_report_loading)))
                }
                addView(issueHost)
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun rawReportCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16))
        background = ui.containerBackground(
            ui.tokens.cardBackground,
            ui.tokens.border,
            ui.components.card,
        )
        elevation = ui.dp(ui.components.card.elevation).toFloat()
        addView(TextView(this@StartupReportActivity).apply {
            text = getString(R.string.startup_report_raw_title)
            ui.applyTextRole(this, UiTextRole.CardTitle)
        })
        addView(supportingText(getString(R.string.startup_report_raw_summary)).apply {
            setPadding(0, ui.dp(7), 0, 0)
        })
        addView(supportingText(getString(R.string.startup_report_raw_privacy_note)).apply {
            setTextColor(ui.tokens.warning)
            setPadding(0, ui.dp(8), 0, 0)
        })
        generateAction = TextView(this@StartupReportActivity).apply {
            text = getString(R.string.startup_report_generate)
            ui.applyActionRole(this, UiActionRole.Primary)
            isEnabled = false
            alpha = 0.55f
            setOnClickListener { generateReport() }
        }
        addView(generateAction, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ui.dp(48),
        ).apply { setMargins(0, ui.dp(16), 0, 0) })
        reportStatus = supportingText("").apply {
            visibility = View.GONE
            setPadding(0, ui.dp(10), 0, 0)
        }
        addView(reportStatus)
    }

    private fun loadReport() {
        lifecycleScope.launch {
            val loaded = runCatching {
                withContext(Dispatchers.IO) { StartupReportCollector.collect(applicationContext) }
            }
            loaded.onSuccess { bundle ->
                report = bundle
                generateAction.isEnabled = true
                generateAction.alpha = 1f
                bindIssues(bundle.issues)
            }.onFailure { error ->
                bindLoadFailure(error)
            }
        }
    }

    private fun bindIssues(issues: List<StartupReportCheck>) {
        issueHost.removeAllViews()
        if (issues.isEmpty()) {
            issueHost.addView(emptyIssuesCard())
            return
        }
        issues.forEachIndexed { index, issue ->
            issueHost.addView(issueCard(issue), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { if (index > 0) setMargins(0, ui.dp(10), 0, 0) })
        }
    }

    private fun issueCard(issue: StartupReportCheck): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
        background = ui.containerBackground(
            ui.tokens.cardBackground,
            ui.tokens.dangerBorder,
            ui.components.card,
        )
        addView(TextView(this@StartupReportActivity).apply {
            text = issue.title
            ui.applyTextRole(this, UiTextRole.CardTitle)
            setTextColor(ui.tokens.danger)
        })
        addView(supportingText(issue.source).apply {
            setPadding(0, ui.dp(5), 0, 0)
        })
        addView(TextView(this@StartupReportActivity).apply {
            text = issue.reason.ifBlank { getString(R.string.startup_report_reason_missing) }
            ui.applyTextRole(this, UiTextRole.Body)
            setPadding(0, ui.dp(8), 0, 0)
            setTextIsSelectable(true)
        })
        if (issue.updatedAt > 0L) {
            addView(supportingText(getString(
                R.string.startup_report_recorded_at,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(issue.updatedAt)),
            )).apply { setPadding(0, ui.dp(8), 0, 0) })
        }
        StartupReportProjector.retryTarget(issue)?.let { resourceId ->
            addView(TextView(this@StartupReportActivity).apply {
                text = getString(R.string.startup_report_retry_item)
                ui.applyActionRole(this, UiActionRole.Primary)
                setOnClickListener { retryResourceIssue(resourceId, this) }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(46),
            ).apply { setMargins(0, ui.dp(12), 0, 0) })
        }
    }

    private fun retryResourceIssue(resourceId: String, action: TextView) {
        action.isEnabled = false
        action.alpha = 0.55f
        action.text = getString(R.string.startup_report_retrying)
        lifecycleScope.launch {
            runCatching {
                KiteAppGraph.from(applicationContext).resourceActionWorkflowCoordinator
                    .recoverFailedInstall(resourceId, parentInstanceId = null)
            }.onSuccess { effects ->
                applyRetryEffects(resourceId, action, effects)
            }.onFailure { error ->
                resetRetryAction(action)
                Toast.makeText(
                    this@StartupReportActivity,
                    getString(
                        R.string.startup_report_retry_failed,
                        error.message ?: error.javaClass.simpleName,
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun applyRetryEffects(
        resourceId: String,
        action: TextView,
        effects: List<ResourceActionEffect>,
    ) {
        effects.forEach { effect ->
            when (effect) {
                is ResourceActionEffect.OpenRun -> startActivity(
                    CardRunIntents.launchIntent(
                        context = this,
                        recipeId = effect.recipeId,
                        instanceId = effect.instanceId,
                        launchSource = CardRunIntents.SOURCE_RESOURCE_INSTALL,
                        autoStart = effect.autoStart,
                        generation = effect.generation,
                    )
                )
                is ResourceActionEffect.OpenInstallWizard -> startActivity(
                    CardRunIntents.resourceInstallWizardIntent(
                        context = this,
                        recipeId = effect.recipeId,
                        instanceId = effect.instanceId,
                        targetResourceId = effect.targetResourceId,
                        planResourceIds = effect.planResourceIds,
                        generation = effect.generation,
                    )
                )
                is ResourceActionEffect.Message -> Toast.makeText(
                    this,
                    effect.text,
                    Toast.LENGTH_SHORT,
                ).show()
                ResourceActionEffect.RequireNotifications ->
                    RunNotificationPermissionFragment.request(
                        fragmentManager = supportFragmentManager,
                        title = getString(R.string.startup_report_retry_item),
                        key = "startup-report-retry:$resourceId",
                        retry = { retryResourceIssue(resourceId, action) },
                        onCancelled = { resetRetryAction(action) },
                    )
            }
        }
        if (effects.none { it == ResourceActionEffect.RequireNotifications }) {
            resetRetryAction(action)
        }
    }

    private fun resetRetryAction(action: TextView) {
        action.isEnabled = true
        action.alpha = 1f
        action.text = getString(R.string.startup_report_retry_item)
    }

    private fun emptyIssuesCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(16), ui.dp(15), ui.dp(16), ui.dp(15))
        background = ui.containerBackground(
            ui.tokens.cardBackground,
            ui.tokens.border,
            ui.components.card,
        )
        addView(TextView(this@StartupReportActivity).apply {
            text = getString(R.string.startup_report_no_issues_title)
            ui.applyTextRole(this, UiTextRole.CardTitle)
        })
        addView(supportingText(getString(R.string.startup_report_no_issues_summary)).apply {
            setPadding(0, ui.dp(6), 0, 0)
        })
    }

    private fun bindLoadFailure(error: Throwable) {
        issueHost.removeAllViews()
        issueHost.addView(issueCard(StartupReportCheck(
            id = "report-load",
            title = getString(R.string.startup_report_load_failed),
            source = getString(R.string.startup_report_itself),
            status = StartupReportCheckStatus.Failed,
            reason = error.message ?: error.javaClass.simpleName,
            kind = StartupReportCheckKind.AppStartup,
        )))
    }

    private fun generateReport() {
        val current = report ?: return
        generateAction.isEnabled = false
        generateAction.alpha = 0.55f
        generateAction.text = getString(R.string.startup_report_generating)
        lifecycleScope.launch {
            val generated = runCatching {
                withContext(Dispatchers.IO) {
                    StartupReportExporter.generate(applicationContext, current)
                }
            }
            generateAction.isEnabled = true
            generateAction.alpha = 1f
            generateAction.text = getString(R.string.startup_report_generate)
            generated.onSuccess { result ->
                reportStatus.text = getString(R.string.startup_report_generated_path, result.displayPath)
                reportStatus.setTextColor(ui.tokens.success)
                reportStatus.visibility = View.VISIBLE
                Toast.makeText(
                    this@StartupReportActivity,
                    getString(R.string.startup_report_generated_toast),
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure { error ->
                reportStatus.text = getString(
                    R.string.startup_report_generate_failed,
                    error.message ?: error.javaClass.simpleName,
                )
                reportStatus.setTextColor(ui.tokens.danger)
                reportStatus.visibility = View.VISIBLE
            }
        }
    }

    private fun supportingText(value: String): TextView = TextView(this).apply {
        text = value
        ui.applyTextRole(this, UiTextRole.Supporting)
        setLineSpacing(ui.dp(3).toFloat(), 1f)
    }
}
