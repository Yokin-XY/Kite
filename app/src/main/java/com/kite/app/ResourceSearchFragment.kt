package com.kite.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment

/**
 * T7:资源搜索 Screen 抽成 Fragment(渐进策略,壳 + 复用 Activity 渲染)。
 * 接收 initialQuery 参数,通过 ResourceSearchHost 让 Activity 渲染。
 */
class ResourceSearchFragment : Fragment() {

    private lateinit var host: ResourceSearchHost
    private var initialQuery: String = ""

    interface ResourceSearchHost {
        fun renderResourceSearchInto(container: ViewGroup, initialQuery: String)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialQuery = arguments?.getString(ARG_QUERY).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        host = (activity as? ResourceSearchHost) ?: error("宿主必须实现 ResourceSearchHost")
        return FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            id = View.generateViewId()
            post { host.renderResourceSearchInto(this, initialQuery) }
        }
    }

    companion object {
        private const val ARG_QUERY = "initial_query"
        fun newInstance(initialQuery: String): ResourceSearchFragment =
            ResourceSearchFragment().apply {
                arguments = Bundle().apply { putString(ARG_QUERY, initialQuery) }
            }
    }
}
