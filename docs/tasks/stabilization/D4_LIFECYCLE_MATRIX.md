# D4 生命周期与资源预算矩阵

| 事件 | 显示面 | 运行实例 | 底层进程 | 允许动作 |
| --- | --- | --- | --- | --- |
| 页面切换 | detach/hide | 保留 | 保留 | 解除可见绑定、停止 UI ticker |
| Activity 销毁 | destroy Activity 级 WebView，注销路由 | 保留 | 保留 | 释放显示资源，不宣布失败 |
| Fragment View 销毁 | detach UI | 保留终端 session | 保留 | flush transcript、解除 callback |
| 用户点击停止 | 可显示停止中 | 进入 Stopping | 请求终止 | 通过统一动作与 Store 确认 |
| 系统 trim memory | 回收可恢复缓存 | 按策略保留/回收空闲项 | 仅经 RuntimeReclaimer | 不由页面直接杀进程 |
| low memory | 同上，压力等级更高 | 同上 | 同上 | 仍遵守租约、owner 和保留等级 |

红线：页面离开、Activity 销毁和内存压力均不等于用户点击停止。
