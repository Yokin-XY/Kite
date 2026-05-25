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
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.kite.app.bridge.BridgeResult
import com.kite.app.bridge.KiteBridgeClient
import com.kite.app.bridge.KiteLocalServer
import com.kite.app.diagnostics.KiteDiagnostics
import com.kite.app.recipe.KiteRecipe
import com.kite.app.recipe.KiteRecipeLoader
import com.kite.app.recipe.NewRecipeInput
import com.kite.app.web.KiteWebShell

class MainActivity : Activity() {
    private lateinit var diagnostics: KiteDiagnostics
    private lateinit var recipeLoader: KiteRecipeLoader
    private lateinit var bridgeClient: KiteBridgeClient
    private lateinit var webShell: KiteWebShell
    private lateinit var localServer: KiteLocalServer
    private lateinit var root: LinearLayout
    private lateinit var webView: WebView

    private val recipeStates = mutableMapOf<String, RecipeRunState>()
    private var currentScreen: Screen = Screen.Console
    private var selectedType = KiteRecipe.TYPE_OPEN_URL
    private var currentRecipes: List<KiteRecipe> = emptyList()

    private lateinit var nameInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var commandInput: EditText
    private lateinit var shortcutSwitch: Switch
    private lateinit var commandFieldContainer: View
    private lateinit var createTypeContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnostics = KiteDiagnostics(this)
        diagnostics.writeCapabilityReport()
        recipeLoader = KiteRecipeLoader(this, diagnostics)
        bridgeClient = KiteBridgeClient(diagnostics)
        webView = WebView(this)
        webShell = KiteWebShell(
            activity = this,
            webView = webView,
            diagnostics = diagnostics,
            onStatus = { /* Diagnostics own the persisted state. */ }
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

    @Deprecated("Use OnBackPressedDispatcher in a future AndroidX Activity migration.")
    override fun onBackPressed() {
        if (currentScreen != Screen.Console) showConsole() else super.onBackPressed()
    }

    private fun showConsole() {
        currentScreen = Screen.Console
        currentRecipes = recipeLoader.loadAllRecipes()
        currentRecipes.forEach { recipeStates.putIfAbsent(it.id, RecipeRunState.Unknown) }
        root.removeAllViews()
        root.addView(consoleHeader())
        root.addView(recipeGrid(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation())
    }

    private fun consoleHeader(): View = LinearLayout(this).apply {
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
                val opened = recipeStates.values.count { it == RecipeRunState.Opened }
                val stopped = recipeStates.values.count { it == RecipeRunState.Stopped || it == RecipeRunState.BridgeUnavailable }
                addView(chip("▦  全部 ${currentRecipes.size}", true))
                addView(chip("▶  已打开 $opened", false))
                addView(chip("■  已停止 $stopped", false))
            })
        })
    }

    private fun recipeGrid(): View {
        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply {
            columnCount = 2
            setPadding(dp(18), dp(8), dp(18), dp(92))
            clipToPadding = false
        }
        currentRecipes.forEach { recipe ->
            grid.addView(recipeCard(recipe), GridLayout.LayoutParams().apply {
                width = 0
                height = dp(188)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(6), dp(6), dp(6), dp(10))
            })
        }
        scroll.addView(grid)
        return scroll
    }

    private fun recipeCard(recipe: KiteRecipe): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(10))
        background = roundedBox(Color.WHITE, BORDER, dp(24).toFloat())
        elevation = dp(2).toFloat()

        addView(row {
            gravity = Gravity.TOP
            addView(iconTile(iconText(recipe), accentFor(recipe), tintBackground(accentFor(recipe))))
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(stateTag(recipeStates[recipe.id] ?: RecipeRunState.Unknown))
        })
        addView(cardTitle(recipe.name))
        addView(cardDescription(recipe.description.ifBlank { "打开本地工作台" }))
        addView(urlPill(recipe.defaultUrl, recipe.type == KiteRecipe.TYPE_COMMAND_WEB))
        addView(row {
            addView(primaryAction(primaryLabel(recipe), recipe.type == KiteRecipe.TYPE_COMMAND_WEB) {
                handleRecipeAction(recipe)
            })
            addView(editAction {
                showRecipeDetail(recipe)
            })
        })
    }

    private fun handleRecipeAction(recipe: KiteRecipe) {
        diagnostics.logRecipeAction(recipe, "card_click", mapOf("type" to recipe.type))
        when (recipe.type) {
            KiteRecipe.TYPE_OPEN_URL, KiteRecipe.TYPE_TEMPLATE -> {
                recipeStates[recipe.id] = RecipeRunState.Opened
                openWeb(recipe.openWebUrl(), "recipe_card", recipe)
            }

            KiteRecipe.TYPE_COMMAND_WEB, KiteRecipe.TYPE_START_SERVICE -> {
                if (recipe.hasShellStep()) {
                    recipeStates[recipe.id] = RecipeRunState.Starting
                    showConsole()
                    bridgeClient.runRecipe(recipe) { result ->
                        runOnUiThread { handleBridgeResult(recipe, result) }
                    }
                } else {
                    recipeStates[recipe.id] = RecipeRunState.Opened
                    openWeb(recipe.openWebUrl(), "recipe_card", recipe)
                }
            }

            else -> {
                recipeStates[recipe.id] = RecipeRunState.Stopped
                Toast.makeText(this, "暂不支持的配置类型", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleBridgeResult(recipe: KiteRecipe, result: BridgeResult) {
        if (result.ok || result.accepted) {
            recipeStates[recipe.id] = RecipeRunState.Opened
            diagnostics.logRecipeAction(recipe, "bridge_ok_open_web")
            openWeb(recipe.openWebUrl(), "recipe_card", recipe)
        } else {
            recipeStates[recipe.id] = RecipeRunState.BridgeUnavailable
            diagnostics.logRecipeAction(
                recipe,
                "bridge_unavailable",
                mapOf("message" to result.message.take(500))
            )
            Toast.makeText(this, "桥接不可用，未执行命令", Toast.LENGTH_SHORT).show()
            showConsole()
        }
    }

    private fun showCreateConfig() {
        currentScreen = Screen.CreateConfig
        selectedType = KiteRecipe.TYPE_OPEN_URL
        root.removeAllViews()
        root.addView(createTopBar())
        root.addView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(8), dp(24), dp(132))
                addView(sectionTitle("1. 类型"))
                createTypeContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipToPadding = false
                    clipChildren = false
                }
                addView(createTypeContainer)
                renderTypeOptions()
                addView(sectionTitle("2. 基础信息"))
                addView(formPanel())
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomActions())
    }

    private fun renderTypeOptions() {
        createTypeContainer.removeAllViews()
        val options = listOf(
            TypeOption(KiteRecipe.TYPE_OPEN_URL, "◎", "打开网页"),
            TypeOption(KiteRecipe.TYPE_START_SERVICE, "▷", "启动服务"),
            TypeOption(KiteRecipe.TYPE_COMMAND_WEB, ">_", "命令+网页"),
            TypeOption(KiteRecipe.TYPE_TEMPLATE, "▦", "模板")
        )
        options.forEachIndexed { index, option ->
            createTypeContainer.addView(optionCard(option, selectedType == option.type, index != options.lastIndex))
        }
    }

    private fun formPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(24), dp(24), dp(20))
        background = roundedBox(Color.WHITE, BORDER, dp(28).toFloat())
        elevation = dp(1).toFloat()

        nameInput = editInput("例如：Hermes WebUI")
        urlInput = editInput("例如：http://127.0.0.1:8648")
        commandInput = editInput("例如：hermes-web-ui start --port 8648")

        addView(labeledField("名称", nameInput))
        commandFieldContainer = labeledField("命令", commandInput).apply {
            visibility = if (selectedType == KiteRecipe.TYPE_COMMAND_WEB) View.VISIBLE else View.GONE
        }
        addView(commandFieldContainer)
        addView(labeledField("地址", urlInput))
        addView(toggleRow())
        addView(divider())
        addView(navigationRow("高级设置（可选）"))
    }

    private fun saveNewRecipe() {
        val name = nameInput.text?.toString().orEmpty().trim()
        val url = urlInput.text?.toString().orEmpty().trim()
        val command = commandInput.text?.toString().orEmpty().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "请输入名称", Toast.LENGTH_SHORT).show()
            return
        }
        if (url.isBlank()) {
            Toast.makeText(this, "请输入地址", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedType == KiteRecipe.TYPE_COMMAND_WEB && command.isBlank()) {
            Toast.makeText(this, "请输入命令", Toast.LENGTH_SHORT).show()
            return
        }

        runCatching {
            recipeLoader.saveUserRecipe(
                NewRecipeInput(
                    type = selectedType,
                    name = name,
                    url = url,
                    command = command,
                    shortcut = shortcutSwitch.isChecked
                )
            )
        }.onSuccess {
            Toast.makeText(this, "已保存配置", Toast.LENGTH_SHORT).show()
            showConsole()
        }.onFailure {
            Toast.makeText(this, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRecipeDetail(recipe: KiteRecipe) {
        currentScreen = Screen.RecipeDetail
        root.removeAllViews()
        root.addView(topBar("配置详情") { showConsole() })
        root.addView(ScrollView(this).apply {
            addView(TextView(context).apply {
                text = recipe.toJson().toString(2)
                textSize = 14f
                setTextColor(TEXT_DARK)
                setPadding(dp(24), dp(20), dp(24), dp(28))
                typeface = Typeface.MONOSPACE
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun showWorkbench(url: String, source: String, recipe: KiteRecipe?) {
        currentScreen = Screen.Workbench
        root.removeAllViews()
        root.addView(topBar("Kite 工作台") { showConsole() })
        val parent = webView.parent
        if (parent is ViewGroup) parent.removeView(webView)
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        webShell.open(url, recipeId = recipe?.id, recipeName = recipe?.name, openSource = source)
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
        showWorkbench(target, source, recipe)
    }

    private fun createTopBar(): View = row {
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
        addView(iconButton("✓", dp(44), Color.TRANSPARENT, PURPLE, dp(16)) { saveNewRecipe() })
    }

    private fun topBar(title: String, onBack: () -> Unit): View = row {
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
        addView(View(context), LinearLayout.LayoutParams(dp(44), dp(44)))
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 19f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(TEXT_DARK)
        setPadding(0, dp(22), 0, dp(14))
    }

    private fun optionCard(option: TypeOption, selected: Boolean, rightMargin: Boolean): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(7))
            background = roundedBox(
                fill = Color.WHITE,
                stroke = if (selected) PURPLE else BORDER,
                radius = dp(18).toFloat(),
                strokeWidth = if (selected) dp(2) else dp(1)
            )
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(0, dp(78), 1f).apply {
                setMargins(0, dp(2), if (rightMargin) dp(8) else 0, dp(2))
            }
            addView(TextView(context).apply {
                text = option.icon
                textSize = if (option.icon.length > 1) 15f else 20f
                includeFontPadding = false
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (selected) PURPLE else TEXT_MUTED)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = option.label
                textSize = 12f
                includeFontPadding = false
                setTextColor(if (selected) PURPLE else TEXT_DARK)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(7), 0, 0)
            })
            setOnClickListener {
                selectedType = option.type
                renderTypeOptions()
                if (::commandFieldContainer.isInitialized) {
                    commandFieldContainer.visibility =
                        if (selectedType == KiteRecipe.TYPE_COMMAND_WEB) View.VISIBLE else View.GONE
                }
            }
        }

    private fun labeledField(label: String, input: EditText): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(20))
        addView(TextView(context).apply {
            text = label
            textSize = 15f
            setTextColor(TEXT_DARK)
        })
        addView(input)
    }

    private fun editInput(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 16f
        setSingleLine(true)
        setTextColor(TEXT_DARK)
        setHintTextColor(Color.rgb(148, 163, 184))
        setPadding(dp(14), 0, dp(14), 0)
        background = roundedBox(Color.WHITE, BORDER, dp(16).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply {
            setMargins(0, dp(8), 0, 0)
        }
    }

    private fun toggleRow(): View = row {
        setPadding(0, dp(10), 0, dp(16))
        addView(TextView(context).apply {
            text = "创建快捷方式到桌面"
            textSize = 16f
            setTextColor(TEXT_DARK)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        shortcutSwitch = Switch(context).apply { isChecked = false }
        addView(shortcutSwitch)
    }

    private fun navigationRow(label: String): View = row {
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

    private fun bottomActions(): View = row {
        setPadding(dp(24), dp(16), dp(24), dp(24))
        setBackgroundColor(Color.WHITE)
        addView(TextView(context).apply {
            text = "取消"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(TEXT_DARK)
            background = roundedBox(Color.rgb(241, 245, 249), Color.rgb(241, 245, 249), dp(19).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, dp(62), 0.9f).apply { setMargins(0, 0, dp(18), 0) }
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
            setOnClickListener { saveNewRecipe() }
        })
    }

    private fun bottomNavigation(): View = row {
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setBackgroundColor(Color.WHITE)
        addView(navItem("▦", "配置", true))
        addView(navItem("▤", "模板", false))
        addView(navItem("⌁", "活动", false))
        addView(navItem("⚙", "设置", false))
    }

    private fun navItem(icon: String, label: String, selected: Boolean): View = LinearLayout(this).apply {
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

    private fun row(content: LinearLayout.() -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        content()
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

    private fun iconTile(text: String, tint: Int, fill: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = if (text.length > 1) 12f else 20f
        includeFontPadding = false
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(tint)
        background = roundedBox(fill, tintBackgroundBorder(tint), dp(14).toFloat())
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
    }

    private fun stateTag(state: RecipeRunState): TextView = TextView(this).apply {
        text = state.label
        textSize = 10.5f
        includeFontPadding = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(state.textColor)
        setPadding(dp(7), dp(4), dp(7), dp(4))
        background = roundedBox(state.bgColor, state.borderColor, dp(14).toFloat())
    }

    private fun cardTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        includeFontPadding = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(TEXT_DARK)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(8), 0, 0)
    }

    private fun cardDescription(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 11.5f
        includeFontPadding = false
        setTextColor(TEXT_MUTED)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(5), 0, 0)
    }

    private fun urlPill(url: String, active: Boolean): TextView = TextView(this).apply {
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
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, dp(6), 0, dp(7)) }
    }

    private fun primaryAction(text: String, green: Boolean, onClick: () -> Unit): View = TextView(this).apply {
        this.text = text
        textSize = 11.5f
        includeFontPadding = false
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = roundedBox(if (green) STATUS_GREEN else BLUE, if (green) STATUS_GREEN else BLUE, dp(12).toFloat())
        layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply { setMargins(0, 0, dp(6), 0) }
        setOnClickListener { onClick() }
    }

    private fun editAction(onClick: () -> Unit): View = TextView(this).apply {
        text = "编辑"
        textSize = 11.5f
        includeFontPadding = false
        gravity = Gravity.CENTER
        setTextColor(TEXT_DARK)
        background = roundedBox(Color.WHITE, BORDER, dp(12).toFloat())
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(30))
        setOnClickListener { onClick() }
    }

    private fun chip(text: String, selected: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(if (selected) Color.WHITE else TEXT_DARK)
        setPadding(dp(18), dp(9), dp(18), dp(9))
        background = roundedBox(if (selected) PURPLE else Color.WHITE, if (selected) PURPLE else BORDER, dp(24).toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, 0, dp(10), 0) }
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(BORDER)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun roundedBox(fill: Int, stroke: Int, radius: Float, strokeWidth: Int = dp(1)): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            setStroke(strokeWidth, stroke)
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

    private fun accentFor(recipe: KiteRecipe): Int = when (recipe.type) {
        KiteRecipe.TYPE_COMMAND_WEB -> STATUS_GREEN
        KiteRecipe.TYPE_START_SERVICE -> BLUE
        KiteRecipe.TYPE_TEMPLATE -> PURPLE
        else -> Color.rgb(37, 99, 235)
    }

    private fun iconText(recipe: KiteRecipe): String = when {
        recipe.icon.isNotBlank() && recipe.icon != "hermes" -> recipe.icon.take(2)
        recipe.id.contains("hermes", ignoreCase = true) -> "羽"
        recipe.type == KiteRecipe.TYPE_COMMAND_WEB -> ">_"
        recipe.type == KiteRecipe.TYPE_START_SERVICE -> "▶"
        recipe.type == KiteRecipe.TYPE_TEMPLATE -> "▦"
        else -> "◎"
    }

    private fun primaryLabel(recipe: KiteRecipe): String = when (recipe.type) {
        KiteRecipe.TYPE_OPEN_URL -> "打开"
        KiteRecipe.TYPE_TEMPLATE -> "打开"
        else -> "启动 / 打开"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class TypeOption(val type: String, val icon: String, val label: String)

    private enum class Screen {
        Console,
        Workbench,
        RecipeDetail,
        CreateConfig
    }

    private enum class RecipeRunState(
        val label: String,
        val textColor: Int,
        val bgColor: Int,
        val borderColor: Int
    ) {
        Unknown("未启动", Color.rgb(71, 85, 105), Color.rgb(248, 250, 252), BORDER),
        Starting("启动中", STATUS_GREEN, Color.rgb(232, 248, 238), Color.rgb(190, 234, 205)),
        Opened("已打开", STATUS_GREEN, Color.rgb(232, 248, 238), Color.rgb(190, 234, 205)),
        Stopped("已停止", Color.rgb(71, 85, 105), Color.rgb(248, 250, 252), BORDER),
        BridgeUnavailable("桥接不可用", Color.rgb(185, 28, 28), Color.rgb(254, 242, 242), Color.rgb(254, 202, 202))
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
