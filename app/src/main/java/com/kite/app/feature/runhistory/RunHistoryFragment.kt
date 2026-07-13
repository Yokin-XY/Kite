package com.kite.app.feature.runhistory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kite.app.application.runs.RunHistoryDependenciesOwner
import com.kite.app.application.runs.RunHistoryGateway
import com.kite.app.theme.ThemeConfig
import kotlinx.coroutines.launch

internal class RunHistoryFragment : Fragment() {
    private val gateway: RunHistoryGateway by lazy(LazyThreadSafetyMode.NONE) {
        val owner = requireContext().applicationContext as? RunHistoryDependenciesOwner
            ?: error("Application 必须提供 RunHistoryGateway")
        owner.runHistoryGateway
    }
    private val controller: RunHistoryController by lazy(LazyThreadSafetyMode.NONE) {
        RunHistoryController(recipeId(), initialHistoryId(), gateway)
    }
    private var screen: RunHistoryScreen? = null
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = handleBack()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, backCallback)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = RunHistoryScreen(
        context = requireContext(),
        theme = themeConfig(),
        listTitle = requireArguments().getString(ARG_LIST_TITLE).orEmpty().ifBlank { "运行历史" },
        emptyTitle = requireArguments().getString(ARG_EMPTY_TITLE).orEmpty().ifBlank { "还没有运行记录" },
        emptyDetail = requireArguments().getString(ARG_EMPTY_DETAIL).orEmpty().ifBlank {
            "启动一次卡片后，这里会出现本次流程的时间、步骤和自动执行内容。"
        },
        onBack = ::handleBack,
        onOpenEntry = controller::openEntry,
        onOpenReport = controller::openReport
    ).also { screen = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { controller.state.collect { screen?.render(it) } }
                launch { gateway.changes.collect { controller.refresh() } }
                controller.refresh()
            }
        }
    }

    override fun onDestroyView() {
        screen = null
        super.onDestroyView()
    }

    private fun handleBack() {
        if (!controller.back()) RunHistoryResultContract.sendBack(this)
    }

    private fun recipeId(): String = requireArguments().getString(ARG_RECIPE_ID).orEmpty()

    private fun initialHistoryId(): String? =
        requireArguments().getString(ARG_INITIAL_HISTORY_ID)?.takeIf(String::isNotBlank)

    private fun themeConfig(): ThemeConfig = ThemeConfig(
        requireArguments().getInt(ARG_THEME_COLOR),
        requireArguments().getInt(ARG_BACKGROUND_COLOR)
    )

    companion object {
        private const val ARG_RECIPE_ID = "recipe_id"
        private const val ARG_INITIAL_HISTORY_ID = "initial_history_id"
        private const val ARG_LIST_TITLE = "list_title"
        private const val ARG_EMPTY_TITLE = "empty_title"
        private const val ARG_EMPTY_DETAIL = "empty_detail"
        private const val ARG_THEME_COLOR = "theme_color"
        private const val ARG_BACKGROUND_COLOR = "background_color"

        fun newInstance(
            recipeId: String,
            theme: ThemeConfig,
            listTitle: String,
            emptyTitle: String,
            emptyDetail: String,
            initialHistoryId: String? = null
        ): RunHistoryFragment = RunHistoryFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_RECIPE_ID, recipeId)
                putString(ARG_INITIAL_HISTORY_ID, initialHistoryId)
                putString(ARG_LIST_TITLE, listTitle)
                putString(ARG_EMPTY_TITLE, emptyTitle)
                putString(ARG_EMPTY_DETAIL, emptyDetail)
                putInt(ARG_THEME_COLOR, theme.themeColor)
                putInt(ARG_BACKGROUND_COLOR, theme.backgroundColor)
            }
        }
    }
}
