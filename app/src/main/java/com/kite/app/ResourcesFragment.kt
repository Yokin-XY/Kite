package com.kite.app

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

/**
 * T7:资源首页 Screen 抽成 Fragment(渐进策略,壳 + 复用 Activity 渲染)。
 * 通过 ResourcesHost 让 Activity 渲染(复用 ensureResourcePage/Nav + 分节刷新)。
 *
 * 根容器必须是纵向 LinearLayout —— Activity 的 renderResourcesInto 用
 * LinearLayout.LayoutParams(MATCH_PARENT, 0, weight) 挂主体页和底栏,依赖 weight 分配高度。
 * 若用 FrameLayout,weight 失效、height=0 保留,主体内容高度变 0 → 页面空白。
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
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.TOP
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 同步渲染(不用 post):post 会在 Fragment 销毁后仍可能执行,导致渲染到已废弃视图。
        // onViewCreated 时视图已 attached,直接渲染更安全。
        if (isAdded && view is ViewGroup) {
            host.renderResourcesInto(view)
        }
    }
}
