package com.kite.app.agent.contract

/**
 * Kite 对外固定的权限语义。
 *
 * Adapter 只能公布 Agent 原生能力能够真实兑现的子集；[Custom] 目前只表示读取并保留用户的
 * 原生设置，不承诺 Kite 已提供编辑入口。
 */
enum class AgentPermissionLevel(
    val displayName: String,
    val description: String,
    val order: Int,
) {
    ReadOnly("只读", "只允许读取内容，不执行写入或高风险操作", 1),
    Restricted("受限", "只允许已明确放行的操作，其余请求被阻止", 2),
    Approval("审批", "敏感操作会先请求你确认", 3),
    Lenient("宽松", "常规操作直接执行，高风险操作仍受控制", 4),
    Smart("智能", "自动判断风险，不确定时再请求你确认", 5),
    Full("完全", "关闭普通审批，以 Agent 可用的最高权限运行", 6),
    Custom("自定义", "读取并保留用户在 Agent 原生配置中的权限规则", 7),
}
