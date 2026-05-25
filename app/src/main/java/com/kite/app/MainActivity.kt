package com.kite.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.kite.app.bridge.KiteLocalServer
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeLoader
import com.kite.app.web.KiteWebShell

class MainActivity : Activity() {
    private lateinit var diagnostics: KiteDiagnostics
    private lateinit var recipeLoader: KiteRecipeLoader
    private lateinit var webShell: KiteWebShell
    private lateinit var localServer: KiteLocalServer
    private lateinit var root: LinearLayout
    private lateinit var webView: WebView

    private val recipeStates = mutableMapOf<String, RecipeState>()
    private var currentScreen: Screen = Screen.Console

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        diagnostics = KiteDiagnostics(this)
        diagnostics.writeCapabilityReport()
        recipeLoader = KiteRecipeLoader(this)
        webView = WebView(this)
        webShell = KiteWebShell(
            activity = this,
            webView = webView,
            diagnostics = diagnostics,
            onStatus = { /* Workbench state is represented by the current screen. */ }
        )

        localServer = KiteLocalServer(
            diagnostics = diagnostics,
            openWeb = { url -> runOnUiThread { openWeb(url, "endpoint") } }
        )
        localServer.start()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        setContentView(root)
        showConsole()
    }

    override fun onDestroy() {
        localServer.stop()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android framework; acceptable for this minimal Activity skeleton.")
    override fun onBackPressed() {
        if (currentScreen != Screen.Console) {
            showConsole()
        } else {
            super.onBackPressed()
        }
    }

    private fun showConsole() {
        currentScreen = Screen.Console
        root.removeAllViews()
        root.addView(consoleHeader())
        root.addView(consoleBody(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        root.addView(bottomNavigation())
    }

    private fun consoleHeader(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26), dp(24), dp(26), dp(12))

            addView(row {
                gravity = Gravity.CENTER_VERTICAL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(context).apply {
                        text = "Kite"
                        textSize = 31f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(TEXT_DARK)
                    })
                    addView(TextView(context).apply {
                        text = "配置表控制台"
                        textSize = 14f
                        setTextColor(TEXT_MUTED)
                    })
                })
                addView(iconButton("⌕", dp(48), Color.TRANSPARENT, TEXT_DARK, dp(18)) {
                    Toast.makeText(context, "搜索稍后接入", Toast.LENGTH_SHORT).show()
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                        setMargins(0, 0, dp(12), 0)
                    }
                })
                addView(iconButton("+", dp(58), PURPLE, Color.WHITE, dp(20)) { showCreateConfig() })
            })

            addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(0, dp(20), 0, 0)
                addView(row {
                    addView(chip("▦  全部 6", true))
                    addView(chip("▶  运行中 2", false))
                    addView(chip("■  已停止 1", false))
                    addView(chip("☆  收藏 0", false))
                })
            })
        }

    private fun consoleBody(): View =
        FrameLayout(this).apply {
            addView(recipeList(), FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

    private fun recipeList(): View {
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(112))
        }
        val recipe = recipeLoader.loadSampleRecipe()
        recipeStates.putIfAbsent(recipe.id, RecipeState.Stopped)
        list.addView(gridRow(
            recipeCard(recipe, rightMargin = true),
            serviceCard(
                name = "OCR",
                description = "识别图片和文档中的文字",
                url = "http://127.0.0.1:7861",
                icon = "OCR",
                accent = BLUE,
                state = CardState.Stopped,
                buttonText = "启动",
                buttonGreen = false,
                rightMargin = false,
                enabled = false
            )
        ))
        list.addView(gridRow(
            serviceCard(
                name = "Python Service",
                description = "运行 Python API 和脚本服务",
                url = "http://127.0.0.1:8000",
                icon = "Py",
                accent = Color.rgb(37, 99, 235),
                state = CardState.Ready,
                buttonText = "启动",
                buttonGreen = false,
                rightMargin = true,
                enabled = false
            ),
            serviceCard(
                name = "文件",
                description = "打开本地文件工作台",
                url = "http://127.0.0.1:8080",
                icon = "文",
                accent = PURPLE,
                state = CardState.Ready,
                buttonText = "打开",
                buttonGreen = false,
                rightMargin = false,
                enabled = false
            )
        ))
        list.addView(gridRow(
            serviceCard(
                name = "Codex UI",
                description = "AI 编程助手工作台",
                url = "http://127.0.0.1:3000",
                icon = ">_",
                accent = STATUS_GREEN,
                state = CardState.Running,
                buttonText = "打开",
                buttonGreen = true,
                rightMargin = true,
                enabled = false
            ),
            serviceCard(
                name = "日志",
                description = "查看和搜索应用日志",
                url = "http://127.0.0.1:5601",
                icon = "日",
                accent = Color.rgb(234, 88, 12),
                state = CardState.Stopped,
                buttonText = "启动",
                buttonGreen = false,
                rightMargin = false,
                enabled = false
            )
        ))
        scroll.addView(list)
        return scroll
    }

    private fun recipeCard(recipe: KiteRecipe, rightMargin: Boolean): View =
        gridCard(rightMargin) {
            addView(row {
                gravity = Gravity.TOP
                addView(iconTile("羽", STATUS_GREEN, Color.rgb(232, 248, 238)))
                addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                addView(cardStateTag(CardState.Running))
            })

            addView(gridTitle(recipe.name))
            addView(gridDescription(recipe.description))
            addView(compactUrlPill(recipe.defaultUrl, active = true))

            addView(row {
                addView(gridPrimaryAction("启动 / 打开", green = true) {
                    recipeStates[recipe.id] = RecipeState.Opened
                    openWeb(recipe.defaultUrl, "card", recipe)
                })
                addView(gridEditAction { showRecipeDetail() })
            })

        }

    private fun serviceCard(
        name: String,
        description: String,
        url: String,
        icon: String,
        accent: Int,
        state: CardState,
        buttonText: String,
        buttonGreen: Boolean,
        rightMargin: Boolean,
        enabled: Boolean
    ): View =
        gridCard(rightMargin) {
            addView(row {
                gravity = Gravity.TOP
                addView(iconTile(icon, accent, tintBackground(accent)))
                addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                addView(cardStateTag(state))
            })

            addView(gridTitle(name))
            addView(gridDescription(description))
            addView(compactUrlPill(url, active = false))

            addView(row {
                addView(gridPrimaryAction(buttonText, green = buttonGreen) {
                    if (enabled) {
                        openWeb(url, "card")
                    } else {
                        Toast.makeText(context, "暂未启用", Toast.LENGTH_SHORT).show()
                    }
                })
                addView(gridEditAction {
                    Toast.makeText(context, "编辑稍后接入", Toast.LENGTH_SHORT).show()
                })
            })

        }

    private fun showCreateConfig() {
        currentScreen = Screen.CreateConfig
        root.removeAllViews()
        root.addView(createTopBar())
        root.addView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(8), dp(24), dp(132))
                addView(sectionTitle("1. 类型"))
                addView(optionTypeRow())
                addView(sectionTitle("2. 基础信息"))
                addView(formPanel())
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomActions())
    }

    private fun createTopBar(): View =
        row {
            setPadding(dp(24), dp(22), dp(24), dp(14))
            gravity = Gravity.CENTER_VERTICAL
            addView(iconButton("‹", dp(44), Color.TRANSPARENT, TEXT_DARK, dp(16)) { showConsole() })
            addView(TextView(context).apply {
                text = "新建配置"
                textSize = 25f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(TEXT_DARK)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(iconButton("✓", dp(44), Color.TRANSPARENT, PURPLE, dp(16)) {
                Toast.makeText(context, "保存能力稍后接入", Toast.LENGTH_SHORT).show()
            })
        }

    private fun sectionTitle(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT_DARK)
            setPadding(0, dp(22), 0, dp(14))
        }

    private fun optionTypeRow(): View =
        row {
            setPadding(0, dp(6), 0, dp(10))
            clipToPadding = false
            clipChildren = false
            addView(optionCard("◎", "打开网页", true, rightMargin = true))
            addView(optionCard("▷", "启动服务", false, rightMargin = true))
            addView(optionCard(">_", "命令+网页", false, rightMargin = true))
            addView(optionCard("▦", "模板", false, rightMargin = false))
        }

    private fun optionCard(icon: String, label: String, selected: Boolean, rightMargin: Boolean): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(7))
            clipToPadding = false
            clipChildren = false
            background = roundedBox(
                fill = Color.WHITE,
                stroke = if (selected) PURPLE else BORDER,
                radius = dp(18).toFloat(),
                strokeWidth = if (selected) dp(2) else dp(1)
            )
            elevation = dp(1).toFloat()
            val params = LinearLayout.LayoutParams(0, dp(78), 1f)
            params.setMargins(0, dp(2), if (rightMargin) dp(8) else 0, dp(2))
            layoutParams = params

            addView(TextView(context).apply {
                text = icon
                textSize = if (icon.length > 1) 15f else 20f
                includeFontPadding = false
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (selected) PURPLE else TEXT_MUTED)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = label
                textSize = 12f
                includeFontPadding = false
                setTextColor(if (selected) PURPLE else TEXT_DARK)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(7), 0, 0)
            })
        }

    private fun formPanel(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            background = roundedBox(Color.WHITE, BORDER, dp(28).toFloat())
            elevation = dp(1).toFloat()
            addView(inputField("名称", "例如：Hermes WebUI"))
            addView(inputField("地址", "例如：http://127.0.0.1:8648"))
            addView(toggleRow())
            addView(divider())
            addView(navigationRow("高级设置（可选）"))
        }

    private fun inputField(label: String, hint: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(20))
            addView(TextView(context).apply {
                text = label
                textSize = 15f
                setTextColor(TEXT_DARK)
            })
            addView(EditText(context).apply {
                this.hint = hint
                textSize = 16f
                setSingleLine(true)
                setTextColor(TEXT_DARK)
                setHintTextColor(Color.rgb(148, 163, 184))
                setPadding(dp(14), 0, dp(14), 0)
                background = roundedBox(Color.WHITE, BORDER, dp(16).toFloat())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(58)
                ).apply { setMargins(0, dp(8), 0, 0) }
            })
        }

    private fun toggleRow(): View =
        row {
            setPadding(0, dp(10), 0, dp(16))
            addView(TextView(context).apply {
                text = "创建快捷方式到桌面"
                textSize = 16f
                setTextColor(TEXT_DARK)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Switch(context).apply { isChecked = true })
        }

    private fun navigationRow(label: String): View =
        row {
            setPadding(0, dp(20), 0, 0)
            addView(TextView(context).apply {
                text = label
                textSize = 16f
                setTextColor(TEXT_DARK)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = "›"
                textSize = 28f
                setTextColor(TEXT_MUTED)
            })
        }

    private fun bottomActions(): View =
        row {
            setPadding(dp(24), dp(16), dp(24), dp(24))
            setBackgroundColor(Color.WHITE)
            addView(TextView(context).apply {
                text = "取消"
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(TEXT_DARK)
                background = roundedBox(Color.rgb(241, 245, 249), Color.rgb(241, 245, 249), dp(19).toFloat())
                layoutParams = LinearLayout.LayoutParams(0, dp(62), 0.9f).apply {
                    setMargins(0, 0, dp(18), 0)
                }
                setOnClickListener { showConsole() }
            })
            addView(TextView(context).apply {
                text = "保存"
                textSize = 18f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = roundedBox(PURPLE, PURPLE, dp(19).toFloat())
                layoutParams = LinearLayout.LayoutParams(0, dp(62), 1.1f)
                setOnClickListener { Toast.makeText(context, "保存能力稍后接入", Toast.LENGTH_SHORT).show() }
            })
        }

    private fun showRecipeDetail() {
        currentScreen = Screen.RecipeDetail
        root.removeAllViews()
        root.addView(topBar("配置详情", "返回控制台") { showConsole() })
        root.addView(ScrollView(this).apply {
            addView(TextView(context).apply {
                text = recipeLoader.loadSampleRecipeJson()
                textSize = 14f
                setTextColor(TEXT_DARK)
                setPadding(dp(24), dp(20), dp(24), dp(28))
                typeface = Typeface.MONOSPACE
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun showWorkbench(url: String) {
        currentScreen = Screen.Workbench
        root.removeAllViews()
        root.addView(topBar("Kite 工作台", "返回控制台") { showConsole() })
        root.addView(webView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        webShell.open(url)
    }

    private fun openWeb(url: String, source: String, recipe: KiteRecipe? = null) {
        val target = url.trim().ifBlank { DEFAULT_LOCAL_URL }
        diagnostics.writeWebAppStatus(
            url = target,
            title = recipe?.name,
            state = "opening",
            recipeId = recipe?.id,
            recipeName = recipe?.name,
            openSource = source
        )
        showWorkbench(target)
    }

    private fun topBar(title: String, backText: String, onBack: () -> Unit): View =
        row {
            setPadding(dp(18), dp(14), dp(18), dp(10))
            gravity = Gravity.CENTER_VERTICAL
            addView(iconButton("‹", dp(44), Color.TRANSPARENT, TEXT_DARK, dp(16)) { onBack() })
            addView(TextView(context).apply {
                text = title
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT_DARK)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = backText
                textSize = 12f
                setTextColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            })
        }

    private fun gridRow(left: View, right: View): View =
        row {
            gravity = Gravity.TOP
            addView(left)
            addView(right)
        }

    private fun gridCard(rightMargin: Boolean, content: LinearLayout.() -> Unit): View =
        object : LinearLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                val squareSpec = View.MeasureSpec.makeMeasureSpec(measuredWidth, View.MeasureSpec.EXACTLY)
                super.onMeasure(widthMeasureSpec, squareSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedBox(Color.WHITE, BORDER, dp(24).toFloat())
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, if (rightMargin) dp(10) else 0, dp(10))
            }
            content()
        }

    private fun iconTile(text: String, tint: Int, fill: Int): TextView =
        TextView(this).apply {
            this.text = text
            textSize = if (text.length > 1) 12f else 20f
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(tint)
            background = roundedBox(fill, tintBackgroundBorder(tint), dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        }

    private fun gridTitle(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 16f
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT_DARK)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(8), 0, 0)
        }

    private fun gridDescription(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 11.5f
            includeFontPadding = false
            setTextColor(TEXT_MUTED)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, 0)
        }

    private fun compactUrlPill(url: String, active: Boolean): TextView =
        TextView(this).apply {
            text = url
            textSize = 10.5f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(if (active) STATUS_GREEN else Color.rgb(37, 99, 235))
            setPadding(dp(7), dp(4), dp(7), dp(4))
            background = roundedBox(
                if (active) Color.rgb(232, 248, 238) else Color.rgb(239, 246, 255),
                if (active) Color.rgb(190, 234, 205) else Color.rgb(191, 219, 254),
                dp(10).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(7)) }
        }

    private fun gridPrimaryAction(text: String, green: Boolean, onClick: () -> Unit): View =
        TextView(this).apply {
            this.text = text
            textSize = 11.5f
            includeFontPadding = false
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = roundedBox(if (green) STATUS_GREEN else BLUE, if (green) STATUS_GREEN else BLUE, dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply {
                setMargins(0, 0, dp(6), 0)
            }
            setOnClickListener { onClick() }
        }

    private fun gridEditAction(onClick: () -> Unit): View =
        TextView(this).apply {
            text = "编辑"
            textSize = 11.5f
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(TEXT_DARK)
            background = roundedBox(Color.WHITE, BORDER, dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(30))
            setOnClickListener { onClick() }
        }

    private fun cardFooter(shortcutChecked: Boolean, onEdit: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(divider())
            addView(row {
                setPadding(0, dp(8), 0, dp(8))
                addView(TextView(context).apply {
                    text = "✎  编辑"
                    textSize = 12.5f
                    setTextColor(TEXT_DARK)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f)
                    setOnClickListener { onEdit() }
                })
                addView(TextView(context).apply {
                    text = "|"
                    textSize = 13f
                    setTextColor(BORDER)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(12), ViewGroup.LayoutParams.WRAP_CONTENT)
                })
                addView(TextView(context).apply {
                    text = "⌘  快捷"
                    textSize = 12.5f
                    setTextColor(TEXT_DARK)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(context).apply {
                    text = if (shortcutChecked) "●" else "●"
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setTextColor(if (shortcutChecked) STATUS_GREEN else Color.rgb(180, 180, 180))
                    layoutParams = LinearLayout.LayoutParams(dp(26), dp(24))
                })
            })
        }

    private fun cardStateTag(state: CardState): TextView =
        TextView(this).apply {
            text = state.label
            textSize = 10.5f
            includeFontPadding = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(state.textColor)
            setPadding(dp(7), dp(4), dp(7), dp(4))
            background = roundedBox(state.bgColor, state.borderColor, dp(14).toFloat())
        }

    private fun bottomNavigation(): View =
        row {
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundColor(Color.WHITE)
            addView(navItem("▦", "配置", selected = true))
            addView(navItem("▤", "模板", selected = false))
            addView(navItem("⌁", "活动", selected = false))
            addView(navItem("⚙", "设置", selected = false))
        }

    private fun navItem(icon: String, label: String, selected: Boolean): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(58), 1f)
            addView(TextView(context).apply {
                text = icon
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(if (selected) PURPLE else TEXT_MUTED)
            })
            addView(TextView(context).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (selected) PURPLE else TEXT_MUTED)
            })
        }

    private fun tintBackground(color: Int): Int {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.rgb(
            red + ((255 - red) * 0.88).toInt(),
            green + ((255 - green) * 0.88).toInt(),
            blue + ((255 - blue) * 0.88).toInt()
        )
    }

    private fun tintBackgroundBorder(color: Int): Int {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.rgb(
            red + ((255 - red) * 0.72).toInt(),
            green + ((255 - green) * 0.72).toInt(),
            blue + ((255 - blue) * 0.72).toInt()
        )
    }

    private fun card(content: LinearLayout.() -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(18))
            background = roundedBox(Color.WHITE, BORDER, dp(28).toFloat())
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(18)) }
            content()
        }

    private fun row(content: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            content()
        }

    private fun chip(text: String, selected: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(if (selected) Color.WHITE else TEXT_DARK)
            setPadding(dp(18), dp(9), dp(18), dp(9))
            background = roundedBox(
                if (selected) PURPLE else Color.WHITE,
                if (selected) PURPLE else BORDER,
                dp(24).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dp(10), 0) }
        }

    private fun statusTag(state: RecipeState): TextView =
        TextView(this).apply {
            text = state.label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(state.textColor)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = roundedBox(state.bgColor, state.borderColor, dp(20).toFloat())
        }

    private fun urlPill(url: String, active: Boolean): TextView =
        TextView(this).apply {
            text = url
            textSize = 15f
            setTextColor(if (active) STATUS_GREEN else Color.rgb(37, 99, 235))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedBox(
                if (active) Color.rgb(232, 248, 238) else Color.rgb(239, 246, 255),
                if (active) Color.rgb(190, 234, 205) else Color.rgb(191, 219, 254),
                dp(14).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(16), 0, dp(18)) }
        }

    private fun primaryAction(text: String, green: Boolean, onClick: () -> Unit): View =
        TextView(this).apply {
            this.text = text
            textSize = 18f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = roundedBox(if (green) STATUS_GREEN else BLUE, if (green) STATUS_GREEN else BLUE, dp(20).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, dp(60), 1f).apply {
                setMargins(0, 0, dp(14), 0)
            }
            setOnClickListener { onClick() }
        }

    private fun secondaryAction(text: String, onClick: () -> Unit): View =
        TextView(this).apply {
            this.text = text
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(TEXT_DARK)
            background = roundedBox(Color.WHITE, BORDER, dp(20).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(92), dp(60))
            setOnClickListener { onClick() }
        }

    private fun iconButton(text: String, size: Int, fill: Int, textColor: Int, radius: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = if (text == "+") 30f else 28f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            background = roundedBox(fill, fill, radius.toFloat())
            if (fill != Color.TRANSPARENT) elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener { onClick() }
        }

    private fun divider(): View =
        View(this).apply {
            setBackgroundColor(BORDER)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            )
        }

    private fun roundedBox(
        fill: Int,
        stroke: Int,
        radius: Float,
        strokeWidth: Int = dp(1)
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            setStroke(strokeWidth, stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class Screen {
        Console,
        Workbench,
        RecipeDetail,
        CreateConfig
    }

    private enum class CardState(
        val label: String,
        val textColor: Int,
        val bgColor: Int,
        val borderColor: Int
    ) {
        Running("● 运行中", STATUS_GREEN, Color.rgb(232, 248, 238), Color.rgb(190, 234, 205)),
        Ready("● 就绪", Color.rgb(37, 99, 235), Color.rgb(239, 246, 255), Color.rgb(191, 219, 254)),
        Stopped("■ 已停止", Color.rgb(71, 85, 105), Color.rgb(248, 250, 252), BORDER)
    }

    private enum class RecipeState(
        val label: String,
        val textColor: Int,
        val bgColor: Int,
        val borderColor: Int
    ) {
        Stopped("未启动", Color.rgb(71, 85, 105), Color.rgb(248, 250, 252), BORDER),
        Opened("已打开", STATUS_GREEN, Color.rgb(232, 248, 238), Color.rgb(190, 234, 205)),
        Planned("规划中", Color.rgb(37, 99, 235), Color.rgb(239, 246, 255), Color.rgb(191, 219, 254))
    }

    companion object {
        private const val DEFAULT_LOCAL_URL = "http://127.0.0.1:8648"
        private val BG = Color.rgb(248, 250, 252)
        private val TEXT_DARK = Color.rgb(15, 23, 42)
        private val TEXT_MUTED = Color.rgb(100, 116, 139)
        private val BORDER = Color.rgb(226, 232, 240)
        private val PURPLE = Color.rgb(109, 67, 230)
        private val STATUS_GREEN = Color.rgb(5, 150, 105)
        private val BLUE = Color.rgb(37, 99, 235)
    }
}
