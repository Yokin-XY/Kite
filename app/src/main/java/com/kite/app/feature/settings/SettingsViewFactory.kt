package com.kite.app.feature.settings

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.kite.app.R
import com.kite.app.application.settings.AppLanguagePreference
import com.kite.app.theme.ThemeTokens
import com.kite.app.theme.ThemeEnvironment
import com.kite.app.ui.UiKit
import com.kite.app.ui.UiTextRole
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources

internal class SettingsViewFactory(
    private val context: Context,
    environment: ThemeEnvironment,
) {
    data class PriorityChoice(
        val id: String,
        val title: String,
        val summary: String,
    )

    private val ui = UiKit(context, environment)
    val tokens: ThemeTokens = environment.tokens
    private val foundations = environment.foundations
    private val components = environment.components
    class NavigationBinding(
        val root: View,
        val subtitle: TextView,
        private val title: String,
    ) {
        fun bind(subtitleText: String) {
            subtitle.text = subtitleText
            root.contentDescription = "$title. $subtitleText"
        }
    }

    data class InformationBinding(
        val root: View,
        val subtitle: TextView,
    )

    class SwitchBinding(
        val root: View,
        val subtitle: TextView,
        private val control: Switch,
        private val title: String,
    ) {
        private var binding = false

        fun bind(checked: Boolean, subtitleText: String? = null) {
            binding = true
            if (control.isChecked != checked) control.isChecked = checked
            subtitleText?.let { subtitle.text = it }
            control.contentDescription = "$title. ${subtitle.text}"
            binding = false
        }

        fun shouldDispatch(): Boolean = !binding
    }

    fun topBar(title: String, onBack: () -> Unit): View = ui.topBar(context, title, onBack)

    fun navigationRow(title: String, subtitle: String, onClick: () -> Unit): NavigationBinding {
        val subtitleView = subtitleView(subtitle)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(16), dp(16))
            background = containerBackground(tokens.cardBackground, tokens.border, components.interactiveCard)
            elevation = dp(components.interactiveCard.elevation).toFloat()
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
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            isFocusable = true
            setOnClickListener { onClick() }
        }
        return NavigationBinding(root, subtitleView, title).also { it.bind(subtitle) }
    }

    /** 帮助页的动作卡片使用真实矢量图标区分内部导航与外部链接。 */
    fun navigationRowWithIcon(
        title: String,
        subtitle: String,
        @DrawableRes trailingIcon: Int,
        onClick: () -> Unit,
    ): NavigationBinding {
        val subtitleView = subtitleView(subtitle)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(9), dp(12), dp(9))
            background = containerBackground(tokens.cardBackground, tokens.border, components.interactiveCard)
            elevation = dp(components.interactiveCard.elevation).toFloat()
            addView(labelColumn(title, subtitleView), LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ))
            addView(ImageView(context).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, trailingIcon))
                imageTintList = ColorStateList.valueOf(tokens.textTertiary)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(7), dp(7), dp(7), dp(7))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(34), dp(38)))
            isFocusable = true
            setOnClickListener { onClick() }
        }
        return NavigationBinding(root, subtitleView, title).also { it.bind(subtitle) }
    }

    fun switchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): SwitchBinding {
        val subtitleView = subtitleView(subtitle)
        lateinit var binding: SwitchBinding
        val control = Switch(context).apply {
            isChecked = checked
            contentDescription = "$title. $subtitle"
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(16), dp(14))
            background = containerBackground(tokens.cardBackground, tokens.border, components.interactiveCard)
            elevation = dp(components.interactiveCard.elevation).toFloat()
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
        binding = SwitchBinding(root, subtitleView, control, title)
        control.setOnCheckedChangeListener { _, value ->
            if (binding.shouldDispatch()) onChanged(value)
        }
        return binding
    }

    fun sectionTitle(text: String): TextView = TextView(context).apply {
        this.text = text
        ui.applyTextRole(this, UiTextRole.SectionTitle)
        setPadding(0, 0, 0, dp(12))
    }

    fun textView(text: String, role: UiTextRole): TextView = TextView(context).apply {
        this.text = text
        ui.applyTextRole(this, role)
    }

    fun informationCard(title: String, summary: String): View =
        informationBinding(title, summary).root

    fun informationBinding(title: String, summary: String): InformationBinding {
        val subtitle = subtitleView(summary).apply {
            maxLines = Int.MAX_VALUE
            ellipsize = null
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = containerBackground(tokens.cardBackground, tokens.border, components.card)
            addView(TextView(context).apply {
                text = title
                ui.applyTextRole(this, UiTextRole.CardTitle)
            })
            addView(subtitle)
        }
        return InformationBinding(root, subtitle)
    }

    fun themePreviewCard(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = containerBackground(tokens.cardBackground, tokens.border, components.card)
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

    fun showLanguageDialog(
        state: SettingsUiState,
        onSelect: (AppLanguagePreference) -> Unit
    ) {
        val dialog = Dialog(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = containerBackground(tokens.cardBackground, tokens.border, components.dialog)
            addView(TextView(context).apply {
                text = context.getString(R.string.settings_language_dialog_title)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(subtitleView(context.getString(R.string.settings_language_dialog_summary)).apply {
                maxLines = Int.MAX_VALUE
                ellipsize = null
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

    fun showTextChoiceDialog(
        title: String,
        summary: String,
        options: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
    ) {
        val dialog = Dialog(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = containerBackground(tokens.cardBackground, tokens.border, components.dialog)
            addView(TextView(context).apply {
                text = title
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(subtitleView(summary).apply {
                maxLines = Int.MAX_VALUE
                ellipsize = null
                setPadding(0, dp(6), 0, dp(12))
            })
            options.forEachIndexed { index, label ->
                addView(textChoice(label, index == selectedIndex) {
                    onSelect(index)
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
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, dp(12), 0, 0) }
            })
        }
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    fun showPriorityDialog(
        title: String,
        summary: String,
        choices: List<PriorityChoice>,
        onOrderChanged: (List<String>) -> Unit,
    ) {
        val dialog = Dialog(context)
        val order = choices.map(PriorityChoice::id).toMutableList()
        val byId = choices.associateBy(PriorityChoice::id)
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        fun renderChoices() {
            list.removeAllViews()
            order.forEachIndexed { index, sourceId ->
                val choice = byId.getValue(sourceId)
                list.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(12), dp(8), dp(12))
                    background = roundedBox(tokens.surface, tokens.border, dp(16).toFloat())
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { setMargins(0, dp(8), 0, 0) }
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(TextView(context).apply {
                            text = "${index + 1}. ${choice.title}"
                            ui.applyTextRole(this, UiTextRole.CardTitle)
                        })
                        addView(subtitleView(choice.summary).apply {
                            maxLines = 3
                            ellipsize = null
                        })
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(priorityButton("↑", index > 0) {
                        order.removeAt(index)
                        order.add(index - 1, sourceId)
                        onOrderChanged(order.toList())
                        renderChoices()
                    })
                    addView(priorityButton("↓", index < order.lastIndex) {
                        order.removeAt(index)
                        order.add(index + 1, sourceId)
                        onOrderChanged(order.toList())
                        renderChoices()
                    })
                })
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = containerBackground(tokens.cardBackground, tokens.border, components.dialog)
            addView(TextView(context).apply {
                text = title
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(tokens.textPrimary)
            })
            addView(subtitleView(summary).apply {
                maxLines = Int.MAX_VALUE
                ellipsize = null
                setPadding(0, dp(6), 0, dp(8))
            })
            addView(ScrollView(context).apply { addView(list) }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
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
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, dp(12), 0, 0) }
            })
        }
        renderChoices()
        dialog.setContentView(content)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.8f).toInt(),
        )
    }

    fun roundedBox(fill: Int, stroke: Int, radius: Float, strokeWidth: Int = dp(1)): GradientDrawable =
        ui.roundedBox(fill, stroke, radius, strokeWidth)

    private fun containerBackground(
        fill: Int,
        stroke: Int,
        recipe: com.kite.app.theme.ThemeContainerRecipe,
    ): GradientDrawable = ui.containerBackground(fill, stroke, recipe)

    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun labelColumn(title: String, subtitle: TextView, endPadding: Int = 0): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (endPadding > 0) setPadding(0, 0, endPadding, 0)
            addView(TextView(context).apply {
                text = title
                ui.applyTextRole(this, UiTextRole.CardTitle)
            })
            addView(subtitle)
        }

    private fun subtitleView(value: String): TextView = TextView(context).apply {
        text = value
        ui.applyTextRole(this, UiTextRole.Supporting)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(4), 0, 0)
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
        isFocusable = true
        contentDescription = context.appLanguageLabel(language)
        setOnClickListener { onClick() }
    }

    private fun textChoice(label: String, selected: Boolean, onClick: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(12), dp(12))
            background = roundedBox(
                if (selected) tokens.primarySubtle else tokens.surface,
                if (selected) tokens.primaryStrong else tokens.border,
                dp(16).toFloat(),
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(8), 0, 0) }
            addView(TextView(context).apply {
                text = label
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
            isFocusable = true
            contentDescription = label
            setOnClickListener { onClick() }
        }

    private fun priorityButton(label: String, enabled: Boolean, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 20f
            gravity = Gravity.CENTER
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.28f
            setTextColor(tokens.textSecondary)
            background = roundedBox(tokens.surface, tokens.border, dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                setMargins(dp(6), 0, 0, 0)
            }
            isFocusable = enabled
            contentDescription = label
            if (enabled) setOnClickListener { onClick() }
        }
}
