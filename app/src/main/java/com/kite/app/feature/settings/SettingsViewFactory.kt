package com.kite.app.feature.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.kite.app.R
import com.kite.app.application.settings.AppLanguagePreference
import com.kite.app.browser.BrowserRuntimeMode
import com.kite.app.theme.ThemeTokens

internal class SettingsViewFactory(
    private val context: Context,
    val tokens: ThemeTokens
) {
    data class NavigationBinding(
        val root: View,
        val subtitle: TextView
    )

    class SwitchBinding(
        val root: View,
        val subtitle: TextView,
        private val control: Switch
    ) {
        private var binding = false

        fun bind(checked: Boolean, subtitleText: String? = null) {
            binding = true
            if (control.isChecked != checked) control.isChecked = checked
            subtitleText?.let { subtitle.text = it }
            binding = false
        }

        fun shouldDispatch(): Boolean = !binding
    }

    fun topBar(title: String, onBack: () -> Unit): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(10))
        addView(TextView(context).apply {
            text = "‹"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
            background = roundedBox(Color.TRANSPARENT, Color.TRANSPARENT, dp(16).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setOnClickListener { onBack() }
            contentDescription = context.getString(R.string.common_back)
        })
        addView(TextView(context).apply {
            text = title
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(View(context), LinearLayout.LayoutParams(dp(44), dp(44)))
    }

    fun navigationRow(title: String, subtitle: String, onClick: () -> Unit): NavigationBinding {
        val subtitleView = subtitleView(subtitle)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(16), dp(16))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(22).toFloat())
            elevation = dp(1).toFloat()
            addView(labelColumn(title, subtitleView), LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ))
            addView(TextView(context).apply {
                text = "›"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(tokens.textTertiary)
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(42))
            })
            setOnClickListener { onClick() }
        }
        return NavigationBinding(root, subtitleView)
    }

    fun switchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): SwitchBinding {
        val subtitleView = subtitleView(subtitle)
        lateinit var binding: SwitchBinding
        val control = Switch(context).apply { isChecked = checked }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(16), dp(14))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(22).toFloat())
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(12), 0, 0) }
            addView(labelColumn(title, subtitleView, endPadding = dp(8)), LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ))
            addView(control)
            setOnClickListener { control.isChecked = !control.isChecked }
        }
        binding = SwitchBinding(root, subtitleView, control)
        control.setOnCheckedChangeListener { _, value ->
            if (binding.shouldDispatch()) onChanged(value)
        }
        return binding
    }

    fun sectionTitle(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 13.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(tokens.textPrimary)
        setPadding(0, 0, 0, dp(12))
    }

    fun colorPresetRow(
        options: List<Pair<String, Int>>,
        selectedColor: Int,
        onSelect: (Int) -> Unit
    ): View = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            options.forEach { (label, color) ->
                addView(colorPresetChip(label, color, color == selectedColor) { onSelect(color) })
            }
        })
    }

    fun themePreviewCard(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = roundedBox(tokens.cardBackground, tokens.border, dp(24).toFloat())
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(26), 0, 0) }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = ">_"
                textSize = 16f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setTextColor(tokens.primaryStrong)
                background = roundedBox(tokens.primarySoft, tokens.primarySoft, dp(13).toFloat())
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(TextView(context).apply {
                    text = context.getString(R.string.settings_theme_preview_title)
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                })
                addView(subtitleView(context.getString(R.string.settings_theme_preview_summary)))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        addView(TextView(context).apply {
            text = context.getString(R.string.settings_theme_preview_action)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tokens.buttonText)
            background = roundedBox(tokens.primaryStrong, tokens.primaryStrong, dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                setMargins(0, dp(16), 0, 0)
            }
        })
    }

    fun showBrowserModeDialog(
        state: SettingsUiState,
        onSelect: (BrowserRuntimeMode) -> Unit
    ) {
        val dialog = Dialog(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(22).toFloat())
            addView(TextView(context).apply {
                text = context.getString(R.string.settings_browser_mode_title)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(subtitleView(context.getString(R.string.settings_browser_mode_dialog_summary)).apply {
                setPadding(0, dp(6), 0, dp(12))
            })
            BrowserRuntimeMode.values().forEach { mode ->
                addView(browserModeChoice(mode, mode == state.browserRuntimeMode) {
                    onSelect(mode)
                    dialog.dismiss()
                })
            }
            addView(TextView(context).apply {
                text = context.getString(R.string.common_close)
                gravity = Gravity.CENTER
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textSecondary)
                background = roundedBox(tokens.surface, tokens.border, dp(14).toFloat())
                setPadding(0, dp(11), 0, dp(11))
                setOnClickListener { dialog.dismiss() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(12), 0, 0) }
            })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun showLanguageDialog(
        state: SettingsUiState,
        onSelect: (AppLanguagePreference) -> Unit
    ) {
        val dialog = Dialog(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = roundedBox(tokens.cardBackground, tokens.border, dp(22).toFloat())
            addView(TextView(context).apply {
                text = context.getString(R.string.settings_language_dialog_title)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(subtitleView(context.getString(R.string.settings_language_dialog_summary)).apply {
                setPadding(0, dp(6), 0, dp(12))
            })
            AppLanguagePreference.entries.forEach { language ->
                addView(languageChoice(language, language == state.appLanguage) {
                    onSelect(language)
                    dialog.dismiss()
                })
            }
            addView(TextView(context).apply {
                text = context.getString(R.string.common_close)
                gravity = Gravity.CENTER
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textSecondary)
                background = roundedBox(tokens.surface, tokens.border, dp(14).toFloat())
                setPadding(0, dp(11), 0, dp(11))
                setOnClickListener { dialog.dismiss() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(12), 0, 0) }
            })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun roundedBox(fill: Int, stroke: Int, radius: Float, strokeWidth: Int = dp(1)): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            setStroke(strokeWidth, stroke)
        }

    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun labelColumn(title: String, subtitle: TextView, endPadding: Int = 0): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (endPadding > 0) setPadding(0, 0, endPadding, 0)
            addView(TextView(context).apply {
                text = title
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(subtitle)
        }

    private fun subtitleView(value: String): TextView = TextView(context).apply {
        text = value
        textSize = 12.5f
        setTextColor(tokens.textSecondary)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(4), 0, 0)
    }

    private fun colorPresetChip(
        label: String,
        color: Int,
        selected: Boolean,
        onClick: () -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, dp(12), 0)
        background = roundedBox(
            if (selected) tokens.primarySubtle else tokens.surface,
            if (selected) tokens.primaryStrong else tokens.border,
            dp(18).toFloat(),
            dp(if (selected) 2 else 1)
        )
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
            setMargins(0, 0, dp(10), 0)
        }
        addView(View(context).apply {
            background = roundedBox(color, color, dp(9).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                setMargins(0, 0, dp(8), 0)
            }
        })
        addView(TextView(context).apply {
            text = label
            textSize = 12.5f
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(if (selected) tokens.primaryStrong else tokens.textSecondary)
        })
        setOnClickListener { onClick() }
    }

    private fun browserModeChoice(
        mode: BrowserRuntimeMode,
        selected: Boolean,
        onClick: () -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(12), dp(12), dp(12))
        background = roundedBox(
            if (selected) tokens.primarySubtle else tokens.surface,
            if (selected) tokens.primaryStrong else tokens.border,
            dp(16).toFloat()
        )
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(8), 0, 0) }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = context.browserModeTitle(mode)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(subtitleView(context.browserModeSummary(mode)).apply {
                setPadding(0, dp(4), dp(10), 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = if (selected) "✓" else ""
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(40))
        })
        setOnClickListener { onClick() }
    }

    private fun languageChoice(
        language: AppLanguagePreference,
        selected: Boolean,
        onClick: () -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(12), dp(12), dp(12))
        background = roundedBox(
            if (selected) tokens.primarySubtle else tokens.surface,
            if (selected) tokens.primaryStrong else tokens.border,
            dp(16).toFloat()
        )
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(8), 0, 0) }
        addView(TextView(context).apply {
            text = context.appLanguageLabel(language)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = if (selected) "✓" else ""
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(tokens.primaryStrong)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(40))
        })
        setOnClickListener { onClick() }
    }
}
