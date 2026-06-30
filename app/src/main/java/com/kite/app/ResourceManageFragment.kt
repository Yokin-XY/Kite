package com.kite.app

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

/**
 * T7:资源管理 Screen 抽成 Fragment(首个资源 Fragment,渐进策略)。
 *
 * 策略:Fragment 作为路由壳,通过 ResourceManageHost 接口让 Activity 把已验证的
 * 渲染方法(renderResourceManageInto)挂进 Fragment 的容器 —— 不一次性搬运整套
 * 状态机(resourceManageContentHost/Binding/RequestSerial 等),而是复用 Activity 既有逻辑。
 *
 * 根容器为纵向 LinearLayout(与 Activity render 方法的 LayoutParams 假设一致)。
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
        if (isAdded && view is ViewGroup) {
            host.renderResourceManageInto(view)
        }
    }
}
