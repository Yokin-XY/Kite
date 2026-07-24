package com.kite.app.feature.settings

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kite.app.R
import com.kite.app.theme.KiteTheme
import com.kite.app.theme.ThemeEnvironment
import com.kite.app.ui.UiKit
import com.kite.app.ui.UiTextRole
import com.kite.app.ui.theme.isSystemDarkTheme

/** 声明型内容使用文档排布，不复用交互卡片。 */
internal class SettingsAboutDetailScreen(
    private val context: Context,
    initialPage: SettingsAboutPage,
    initialState: SettingsUiState,
    private val onBack: () -> Unit,
) {
    private val environment: ThemeEnvironment = KiteTheme.resolve(
        initialState.theme,
        context.isSystemDarkTheme(),
    )
    private val ui = UiKit(context, environment)
    private val tokens = environment.tokens

    var currentPage: SettingsAboutPage = initialPage
        private set

    val root: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(tokens.pageBackground)
    }

    private fun showPage(page: SettingsAboutPage) {
        currentPage = page
        root.removeAllViews()
        root.addView(ui.topBar(
            context = context,
            title = context.getString(page.titleRes()),
            onBack = {
                if (currentPage == SettingsAboutPage.FullThirdPartyNotices) {
                    showPage(SettingsAboutPage.OpenSourceComponents)
                } else {
                    onBack()
                }
            },
        ))
        root.addView(ScrollView(context).apply {
            isFillViewport = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(22), ui.dp(8), ui.dp(22), ui.dp(96))
                when (page) {
                    SettingsAboutPage.OpenSourceComponents -> renderOpenSourcePage(this)
                    SettingsAboutPage.KiteLicense -> renderLegalDocument(
                        this,
                        readLegalAsset(ASSET_LICENSE),
                        skipFirstHeading = false,
                    )
                    SettingsAboutPage.Diagnostics -> renderDiagnosticsPage(this)
                    SettingsAboutPage.FullThirdPartyNotices -> renderLegalDocument(
                        this,
                        readLegalAsset(ASSET_THIRD_PARTY_NOTICES),
                        skipFirstHeading = true,
                    )
                }
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun renderOpenSourcePage(host: LinearLayout) {
        host.addView(body(context.getString(R.string.settings_about_open_source_intro)).apply {
            setPadding(0, 0, 0, ui.dp(14))
        })
        host.addView(sectionTitle(context.getString(R.string.settings_about_runtime_foundation_section)))
        runtimeComponents.forEach { component -> host.addView(componentRow(component)) }
        host.addView(sectionTitle(context.getString(R.string.settings_about_app_dependencies_section)).apply {
            setPadding(0, ui.dp(20), 0, ui.dp(6))
        })
        appDependencies.forEach { component -> host.addView(componentRow(component)) }
        host.addView(TextView(context).apply {
            text = context.getString(R.string.settings_about_full_notices_action)
            ui.applyTextRole(this, UiTextRole.Action)
            setTextColor(tokens.primaryText)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ui.dp(14), 0, ui.dp(8))
            minHeight = ui.dp(48)
            isClickable = true
            isFocusable = true
            setOnClickListener { showPage(SettingsAboutPage.FullThirdPartyNotices) }
        })
        host.addView(supporting(context.getString(R.string.settings_about_license_original_note)))
    }

    private fun renderDiagnosticsPage(host: LinearLayout) {
        host.addView(body(context.getString(R.string.settings_about_diagnostics_intro)).apply {
            setPadding(0, 0, 0, ui.dp(14))
        })
        diagnosticSections.forEachIndexed { index, section ->
            host.addView(sectionTitle(context.getString(section.titleRes)).apply {
                if (index > 0) setPadding(0, ui.dp(20), 0, ui.dp(8))
            })
            host.addView(body(context.getString(section.bodyRes)))
        }
    }

    private fun renderLegalDocument(
        host: LinearLayout,
        raw: String,
        skipFirstHeading: Boolean,
    ) {
        val blocks = LegalDocumentParser.parse(raw).let { parsed ->
            if (skipFirstHeading && parsed.firstOrNull() is LegalDocumentBlock.Heading) parsed.drop(1)
            else parsed
        }
        blocks.forEachIndexed { index, block ->
            val view = when (block) {
                is LegalDocumentBlock.Heading -> sectionTitle(block.text)
                is LegalDocumentBlock.Paragraph -> body(block.text)
                is LegalDocumentBlock.Bullet -> body("• ${block.text}").apply {
                    setPadding(ui.dp(10), 0, 0, 0)
                }
            }
            host.addView(view.apply {
                if (index > 0) setPadding(paddingLeft, ui.dp(12), paddingRight, paddingBottom)
            })
        }
        if (blocks.isEmpty()) {
            host.addView(body(context.getString(R.string.settings_about_document_unavailable)))
        }
    }

    private fun componentRow(component: ComponentSpec): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ui.dp(9), 0, ui.dp(9))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(body(component.name).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(supporting(context.getString(component.summaryRes)).apply {
                    setPadding(0, ui.dp(2), ui.dp(10), 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(supporting(component.license).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        addView(View(context).apply { setBackgroundColor(tokens.border) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ui.dp(1),
        ))
    }

    private fun sectionTitle(value: String): TextView = TextView(context).apply {
        text = value
        ui.applyTextRole(this, UiTextRole.SectionTitle)
        setPadding(0, 0, 0, ui.dp(6))
    }

    private fun body(value: String): TextView = TextView(context).apply {
        text = value
        ui.applyTextRole(this, UiTextRole.Body)
        setLineSpacing(0f, 1.18f)
    }

    private fun supporting(value: String): TextView = TextView(context).apply {
        text = value
        ui.applyTextRole(this, UiTextRole.Supporting)
        setLineSpacing(0f, 1.16f)
    }

    private fun readLegalAsset(path: String): String = runCatching {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }.getOrElse { context.getString(R.string.settings_about_document_unavailable) }

    private fun SettingsAboutPage.titleRes(): Int = when (this) {
        SettingsAboutPage.KiteLicense -> R.string.settings_about_license_title
        SettingsAboutPage.OpenSourceComponents -> R.string.settings_about_open_source_title
        SettingsAboutPage.Diagnostics -> R.string.settings_about_diagnostics_title
        SettingsAboutPage.FullThirdPartyNotices -> R.string.settings_about_full_notices_title
    }

    private data class ComponentSpec(
        val name: String,
        val summaryRes: Int,
        val license: String,
    )

    private data class DiagnosticSection(val titleRes: Int, val bodyRes: Int)

    private val runtimeComponents = listOf(
        ComponentSpec("Ubuntu", R.string.settings_about_component_ubuntu, context.getString(R.string.settings_about_license_multiple)),
        ComponentSpec("PRoot", R.string.settings_about_component_proot, "GPL-2.0"),
        ComponentSpec("Termux Terminal View", R.string.settings_about_component_terminal_view, "GPL-3.0"),
        ComponentSpec("Node.js", R.string.settings_about_component_node, "MIT"),
        ComponentSpec("Python", R.string.settings_about_component_python, "PSF-2.0"),
        ComponentSpec("uv", R.string.settings_about_component_uv, "Apache-2.0 / MIT"),
    )

    private val appDependencies = listOf(
        ComponentSpec("AndroidX", R.string.settings_about_component_androidx, "Apache-2.0"),
        ComponentSpec("Material Components", R.string.settings_about_component_material, "Apache-2.0"),
        ComponentSpec("Shizuku", R.string.settings_about_component_shizuku, "Apache-2.0"),
        ComponentSpec("Kotlin Coroutines", R.string.settings_about_component_coroutines, "Apache-2.0"),
        ComponentSpec("Apache Commons Compress", R.string.settings_about_component_compress, "Apache-2.0"),
    )

    private val diagnosticSections = listOf(
        DiagnosticSection(R.string.settings_about_diagnostics_reads_title, R.string.settings_about_diagnostics_reads_body),
        DiagnosticSection(R.string.settings_about_diagnostics_not_reads_title, R.string.settings_about_diagnostics_not_reads_body),
        DiagnosticSection(R.string.settings_about_diagnostics_share_title, R.string.settings_about_diagnostics_share_body),
    )

    init {
        showPage(initialPage)
    }

    private companion object {
        const val ASSET_LICENSE = "legal/LICENSE.txt"
        const val ASSET_THIRD_PARTY_NOTICES = "legal/THIRD_PARTY_NOTICES.md"
    }
}

internal sealed interface LegalDocumentBlock {
    data class Heading(val text: String) : LegalDocumentBlock
    data class Paragraph(val text: String) : LegalDocumentBlock
    data class Bullet(val text: String) : LegalDocumentBlock
}

internal object LegalDocumentParser {
    fun parse(raw: String): List<LegalDocumentBlock> {
        val blocks = mutableListOf<LegalDocumentBlock>()
        val paragraph = mutableListOf<String>()
        val bullet = mutableListOf<String>()
        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += LegalDocumentBlock.Paragraph(paragraph.joinToString(" ").cleanMarkdown())
                paragraph.clear()
            }
        }
        fun flushBullet() {
            if (bullet.isNotEmpty()) {
                blocks += LegalDocumentBlock.Bullet(bullet.joinToString(" ").cleanMarkdown())
                bullet.clear()
            }
        }
        raw.replace("\r\n", "\n").lineSequence().forEach { original ->
            val line = original.trim()
            when {
                line.isBlank() -> {
                    flushParagraph()
                    flushBullet()
                }
                line.startsWith("#") -> {
                    flushParagraph()
                    flushBullet()
                    blocks += LegalDocumentBlock.Heading(line.trimStart('#').trim().cleanMarkdown())
                }
                line.startsWith("- ") -> {
                    flushParagraph()
                    flushBullet()
                    bullet += line.removePrefix("- ")
                }
                bullet.isNotEmpty() -> bullet += line.removePrefix("> ")
                line.startsWith("> ") -> paragraph += line.removePrefix("> ")
                else -> paragraph += line
            }
        }
        flushParagraph()
        flushBullet()
        return blocks
    }

    private fun String.cleanMarkdown(): String = replace("`", "")
}
