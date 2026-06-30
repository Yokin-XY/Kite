package com.kite.app

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.kite.app.theme.ThemeTokens
import com.kite.app.ui.UiKit

/**
 * T6b 样板:把 Screen.RecipeDetail 中的"原始 JSON"只读页抽成 Fragment。
 *
 * 设计原则(抽屉式,ADR-003):本 Fragment 自包含,不复刻 MainActivity 的命令式 UI 工具链
 * (topBar/tokens/dp 等),而是用最朴素的方式自己渲染 —— 这样它真正独立,
 * 验证"一个 Screen 能走 Fragment 路径"的整套机制(Fragment 创建、挂到 rootHost 容器、
 * 与老的命令式 root 共存、返回处理)。
 *
 * 数据:接收 recipeId 参数,自己通过宿主提供的 RecipeProvider 加载最新 recipe。
 * 返回:顶栏返回按钮回调宿主 onExit(),由宿主决定回退目标(通常回编辑器)。
 *
 * 这是 P2 拆 God Activity 的第一个真实 Fragment,后续 T7/T8 按此模式逐个迁移。
 */
class RecipeRawJsonFragment : Fragment() {

    private lateinit var provider: RecipeProvider
    private var recipeId: String? = null

    interface RecipeProvider {
        /** 按 id 加载最新 recipe(找不到时返回 null,由 Fragment 显示提示)。 */
        fun latestRecipeFor(recipeId: String): com.kite.app.recipe.KiteRecipe?
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recipeId = arguments?.getString(ARG_RECIPE_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        provider = (activity as? RecipeProvider) ?: error("宿主 Activity 必须实现 RecipeProvider")
        return buildContent()
    }

    private fun buildContent(): View {
        val ctx = requireContext()
        // 复用公共 UiKit(T7.0):不再自己写 dp/顶栏/配色,与 Activity 保持视觉一致。
        // 宿主 Activity 必须提供 UiKit(含正确主题 tokens)。
        val ui = (activity as? UiKitProvider)?.provideUiKit()
            ?: error("宿主 Activity 必须实现 UiKitProvider")
        val host = activity as? RecipeRawJsonHost

        val recipe = recipeId?.let { provider.latestRecipeFor(it) }
        val jsonText = recipe?.toJson(includeLocalIdentity = true)?.toString(2)
            ?: "无法加载配方(recipeId=$recipeId)"

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F1115"))
            // 顶栏:复用 UiKit.topBar
            addView(ui.topBar(ctx, "原始 JSON") { host?.onExitRecipeRawJson() })
            // JSON 文本
            addView(ScrollView(ctx).apply {
                addView(TextView(ctx).apply {
                    text = jsonText
                    textSize = 13f
                    setTextColor(Color.parseColor("#C8CDD6"))
                    setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(28))
                    typeface = Typeface.MONOSPACE
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    /** 宿主可提供共享的 UiKit(含正确主题 tokens);不提供则用默认深色。 */
    interface UiKitProvider {
        fun provideUiKit(): UiKit
    }

    interface RecipeRawJsonHost {
        /** 用户点了返回,由宿主决定回退到哪个 Screen(通常编辑器)。 */
        fun onExitRecipeRawJson()
    }

    companion object {
        private const val ARG_RECIPE_ID = "recipe_id"
        fun newInstance(recipeId: String): RecipeRawJsonFragment =
            RecipeRawJsonFragment().apply {
                arguments = Bundle().apply { putString(ARG_RECIPE_ID, recipeId) }
            }
    }
}
