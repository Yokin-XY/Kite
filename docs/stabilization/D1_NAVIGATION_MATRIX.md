# D1 导航与返回验收矩阵

## 返回优先级

同一次返回请求按以下顺序处理：

1. 当前 Fragment 或显示面消费内部返回，例如终端详情回终端列表。
2. WebView 有历史记录时先返回网页历史。
3. `CardRunActivity` 交给运行窗口完成当前步骤或关闭任务。
4. 有上下文返回动作时回到具体来源对象。
5. 普通子页面回合同定义的父页面。
6. `Console` 根页面交给 Android 系统退出或返回上一个任务。

页面顶部返回和系统 back 必须进入同一条优先级链，不能分别维护目标。

## 目标矩阵

| Screen | 类型 | 返回合同 | 恢复合同 | 备注 |
| --- | --- | --- | --- | --- |
| Console | Root | System | None | 应用根页 |
| Terminal | Root | Console | Direct | 终端详情由 Fragment 先消费 |
| Workbench | Child | Console | WorkbenchUrl | Web 历史先消费 |
| CardRun | RunSurface | Console / CardRunTask | None | 独立任务由 CardRun 合同处理 |
| RecipeDetail | Child | Contextual / Console | None | 原始 JSON、历史详情共享 Screen |
| CreateConfig | Editor | Contextual / Console | RecipeDraft | 返回前检查未保存修改 |
| RecipeMore | Child | Contextual / Console | None | 回到当前编辑草稿 |
| Resources | Root | Console | Direct | 底部主页面 |
| ResourceSearch | Child | Resources | AsParent(Resources) | 恢复时不恢复旧查询输入 |
| ResourceManage | Child | Resources | Direct | 独立资源管理页 |
| ResourceDetail | Child | Resources | None | 恢复缺少 resourceId 时不猜测 |
| ResourceMore | Child | Contextual / Resources | AsParent(Resources) | 正常回当前资源详情 |
| ResourceRawJson | Child | Contextual / Resources | None | 正常回当前资源详情 |
| Processes | Child | Console | None | 运行管理入口 |
| Settings | Root | Console | Direct | 底部主页面 |
| ThemeSettings | Child | Settings | Direct | 设置子页 |

## 自动化验收

| 编号 | 合同 | 证据 |
| --- | --- | --- |
| D1-A01 | 每个 Screen 有唯一 Destination | `ScreenRouterContractTest` |
| D1-A02 | 普通页面父级确定 | `ScreenRouterContractTest` |
| D1-A03 | 上下文动作优先且离开后清除 | `ScreenRouterContractTest` |
| D1-A04 | 上下文缺失回安全父页面 | `ScreenRouterContractTest` |
| D1-A05 | Console 交系统、CardRun 交运行任务 | `ScreenRouterContractTest` |
| D1-A06 | 恢复白名单保持现有边界 | `ScreenRouterContractTest` |
| D1-A07 | 路由入口委托现有渲染路径 | `ScreenRouterContractTest` |
| D1-A08 | MainActivity 顶部返回与系统 back 使用同一入口 | 待接入测试 |
| D1-A09 | Web 历史优先于页面返回 | 待接入测试 |
| D1-A10 | 终端详情优先回列表 | 待接入测试 |

## OnePlus 8T 验收

目标设备：`3f8bbaad`。

- 首页 -> 设置 -> 主题：连续返回依次为设置、首页。
- 首页 -> 资源 -> 搜索：顶部返回和系统 back 都回资源首页。
- 资源 -> 详情 -> 更多或原始 JSON：返回到同一资源详情，再回资源首页。
- 首页 -> 运行管理：返回首页，不提前修改进程状态。
- 首页 -> 终端 -> 终端详情：第一次返回列表，第二次返回首页。
- 工作台有网页历史时先退网页；无网页历史时回首页。
- 独立 CardRun 返回仍遵守完成步骤或关闭任务规则。
- 全程页面结构、文字、颜色和布局不发生视觉改造。
