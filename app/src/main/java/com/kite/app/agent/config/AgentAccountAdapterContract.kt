package com.kite.app.agent.config

import com.kite.app.agent.sdk.account.AgentOfficialAccountAdapter

/** 只有具体原生配置 Adapter 才能声明官方账号凭据能力。 */
internal interface AgentOfficialAccountAdapterProvider {
    fun officialAccountAdapter(): AgentOfficialAccountAdapter?
}
