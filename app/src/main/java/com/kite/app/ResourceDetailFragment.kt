package com.kite.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment

/**
 * T7:资源详情 Screen 抽成 Fragment(渐进策略,壳 + 复用 Activity 渲染)。
 * 接收 resourceId,通过 ResourceDetailHost 让 Activity 渲染。
 * 注意:同屏去重(currentScreen==ResourceDetail && 同 id)逻辑保留在 Activity 的
 * showResourceDetail 前置判断里,只有需要重建时才走 Fragment。
 */
class ResourceDetailFragment : Fragment() {

    private lateinit var host: ResourceDetailHost
    private var resourceId: String = ""

    interface ResourceDetailHost {
        fun renderResourceDetailInto(container: ViewGroup, resourceId: String)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resourceId = arguments?.getString(ARG_RESOURCE_ID).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        host = (activity as? ResourceDetailHost) ?: error("宿主必须实现 ResourceDetailHost")
        return FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            id = View.generateViewId()
            post { host.renderResourceDetailInto(this, resourceId) }
        }
    }

    companion object {
        private const val ARG_RESOURCE_ID = "resource_id"
        fun newInstance(resourceId: String): ResourceDetailFragment =
            ResourceDetailFragment().apply {
                arguments = Bundle().apply { putString(ARG_RESOURCE_ID, resourceId) }
            }
    }
}
