package com.kite.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment

/**
 * T7:资源首页 Screen 抽成 Fragment(渐进策略,壳 + 复用 Activity 渲染)。
 * 通过 ResourcesHost 让 Activity 渲染(复用 ensureResourcePage/Nav + 分节刷新)。
 */
class ResourcesFragment : Fragment() {

    private lateinit var host: ResourcesHost

    interface ResourcesHost {
        fun renderResourcesInto(container: ViewGroup)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        host = (activity as? ResourcesHost) ?: error("宿主必须实现 ResourcesHost")
        return FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            id = View.generateViewId()
            post { host.renderResourcesInto(this) }
        }
    }
}
