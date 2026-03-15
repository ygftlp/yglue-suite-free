# 上线准备度评估（2026-03-12）

## 评估范围

本次评估覆盖以下主链路：

- 编排器中的 flow / version / entrypoint 管理
- IDEA 插件同步规则到本地项目
- `yglue-runtime` 读取本地 `.ygflow` 执行
- `yglue-runtime-spring-boot3` 对真实 REST 接口的接管
- 前端托管开关与后端接口一致性

## 当前结论

按项目既定设计，当前系统已经具备上线基础。

前提是：

- 把 IDEA 插件同步视为正式发布链路的一部分
- 把“插件同步成功”视为强制发布门禁，而不是开发便利功能

如果满足这个前提，我的建议是：

- 可以进入预发或灰度上线
- 经过一次完整发布演练后，可以进入正式生产发布

## 已确认通过的项

- 根项目 `mvn test` 通过
- 前端 `npm run build` 通过
- 分支执行按命中边继续执行
- 入参解析链 `paramPlans -> _resolvedArgs -> service method` 已通过测试
- 样例 flow 可执行
- Spring Boot 3 场景下，真实 REST 接口可被 flow 接管
- 前端 entrypoint 开关已对齐后端真实 API
- Flyway 重复版本号冲突已清理
- 代码生成器不再输出明显不可编译的无效 Java

## 系统边界

这里需要明确一个架构边界：

- `yglue-runtime` 是纯执行层
- `yglue-runtime` 不负责远程同步、心跳、拉取规则或热更新
- 规则同步职责属于 IDEA 插件

这不是缺陷，而是当前系统的既定职责划分。

## 发布门禁建议

每次发布至少执行以下动作：

1. 在编排器中确认目标 flow 已发布，entrypoint 已启用。
2. 通过 IDEA 插件同步目标项目规则。
3. 确认本地 `.ygflow/rules/*.json` 与 `.ygflow/entrypoints.json` 已更新。
4. 运行 `verify-yglue-sync.ps1` 做一致性校验。
5. 执行服务构建与回归测试。
6. 对接管接口做最小 smoke test。

详细步骤见 [plugin-sync-release-checklist.md](/d:/JavaWorkspace/ygflow-suite/runtime-ops-notes/plugin-sync-release-checklist.md)。

## 当前残余风险

- 发布流程仍依赖人工或半人工执行 IDEA 插件同步。
- 如果团队没有把“同步成功”纳入变更门禁，容易出现编排器已发布但本地项目未同步的操作事故。

## 上线判断

如果你们接受“IDEA 插件同步”就是正式发布链路的一环，当前项目可以上线。  
如果你们要求“平台发布后服务端自动拉取并即时生效”，当前系统不满足这个目标。
