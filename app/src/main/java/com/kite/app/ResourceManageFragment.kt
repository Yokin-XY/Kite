package com.kite.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment

/**
 * T7:资源管理 Screen 抽成 Fragment(首个资源 Fragment,渐进策略 ADR-018)。
 *
 * 策略:Fragment 作为路由壳,通过 ResourceManageHost 接口让 Activity 把已验证的
 * 渲染方法(renderResourceManageInto)挂进 Fragment 的容器 —— 不一次性搬运整套
 * 状态机(resourceManageContentHost/Binding/RequestSerial 等),而是复用 Activity 既有逻辑。
 * 这样每步都编译+测试+真机可验证,且不破坏已合规的 Store→信号→局部更新链路。
 *
 * 后续可逐步把渲染逻辑内化进 Fragment(类似 RecipeRawJsonFragment 的完全自渲染)。
 */
class ResourceManageFragment : Fragment() {

    private lateinit var host: ResourceManageHost

    interface ResourceManageHost {
        /** 宿主把已验证的资源管理渲染挂进给定容器(对应原 showResourceManage 的 body)。 */
        fun renderResourceManageInto(container: ViewGroup)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        host = (activity as? ResourceManageHost) ?: error("宿主 Activity 必须实现 ResourceManageHost")
        // Fragment 提供一个容器,宿主把内容挂进来
        return FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 给宿主一个明确的挂载点 id
            id = View.generateViewId()
            post { host.renderResourceManageInto(this) }
        }
    }
}
