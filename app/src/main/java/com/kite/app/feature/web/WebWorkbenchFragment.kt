package com.kite.app.feature.web

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.kite.app.browser.BrowserHandoffLauncher

/** 普通工作台 Feature。Fragment 生命周期只控制可见 Web 资源，不控制后台运行或认证会话。 */
internal class WebWorkbenchFragment : Fragment() {
    private val dependencies: WebWorkbenchDependenciesOwner by lazy(LazyThreadSafetyMode.NONE) {
        requireContext().applicationContext as? WebWorkbenchDependenciesOwner
            ?: error("Application 必须提供 WebWorkbenchDependenciesOwner")
    }
    private var screen: WebWorkbenchScreen? = null
    private var restoredUrl: String? = null
    private var displayReleased = false
    private lateinit var backCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredUrl = savedInstanceState?.getString(STATE_URL)?.takeIf(String::isNotBlank)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        displayReleased = false
        val target = target().copy(url = restoredUrl ?: target().url)
        return WebWorkbenchScreen(
            activity = requireActivity(),
            pageBackground = requireArguments().getInt(ARG_PAGE_BACKGROUND),
            textPrimary = requireArguments().getInt(ARG_TEXT_PRIMARY),
            diagnostics = dependencies.webWorkbenchDiagnostics,
            automationSessions = dependencies.webWorkbenchAutomationSessions,
            onExit = ::leaveWorkbench,
            onLaunchHandoff = BrowserHandoffLauncher { request, decision ->
                dependencies.launchWebWorkbenchHandoff(request, decision)
            }
        ).also { created ->
            screen = created
            created.open(target)
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (screen?.handleBack() == true) return
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
    }

    override fun onResume() {
        super.onResume()
        screen?.onResume()
    }

    override fun onPause() {
        screen?.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        currentUrlForState()?.let { outState.putString(STATE_URL, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        restoredUrl = currentUrlForState()
        screen?.dispose()
        screen = null
        displayReleased = true
        super.onDestroyView()
    }

    internal fun currentUrlForState(): String? =
        screen?.currentUrl()?.takeIf(String::isNotBlank)
            ?: restoredUrl
            ?: arguments?.getString(ARG_URL)?.takeIf(String::isNotBlank)

    internal fun displayReleasedForTest(): Boolean = displayReleased

    private fun leaveWorkbench() {
        backCallback.isEnabled = false
        requireActivity().onBackPressedDispatcher.onBackPressed()
        backCallback.isEnabled = true
    }

    private fun target(): WebWorkbenchTarget = WebWorkbenchTarget(
        url = requireArguments().getString(ARG_URL).orEmpty(),
        source = requireArguments().getString(ARG_SOURCE).orEmpty(),
        recipeId = requireArguments().getString(ARG_RECIPE_ID),
        recipeName = requireArguments().getString(ARG_RECIPE_NAME),
        instanceId = requireArguments().getString(ARG_INSTANCE_ID),
        automationEnabled = requireArguments().getBoolean(ARG_AUTOMATION_ENABLED)
    )

    companion object {
        private const val ARG_URL = "url"
        private const val ARG_SOURCE = "source"
        private const val ARG_RECIPE_ID = "recipe_id"
        private const val ARG_RECIPE_NAME = "recipe_name"
        private const val ARG_INSTANCE_ID = "instance_id"
        private const val ARG_AUTOMATION_ENABLED = "automation_enabled"
        private const val ARG_PAGE_BACKGROUND = "page_background"
        private const val ARG_TEXT_PRIMARY = "text_primary"
        private const val STATE_URL = "current_url"

        fun newInstance(
            target: WebWorkbenchTarget,
            pageBackground: Int,
            textPrimary: Int
        ): WebWorkbenchFragment = WebWorkbenchFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_URL, target.url)
                putString(ARG_SOURCE, target.source)
                putString(ARG_RECIPE_ID, target.recipeId)
                putString(ARG_RECIPE_NAME, target.recipeName)
                putString(ARG_INSTANCE_ID, target.instanceId)
                putBoolean(ARG_AUTOMATION_ENABLED, target.automationEnabled)
                putInt(ARG_PAGE_BACKGROUND, pageBackground)
                putInt(ARG_TEXT_PRIMARY, textPrimary)
            }
        }
    }
}
