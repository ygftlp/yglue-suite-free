# 服务调用配置 V3 设计（下拉选服务 + 自动入参）

## 1. 目标
- 把当前 `fn + args` 自由输入，升级为强约束配置。
- 用户只做“选服务、配每个参数来源”，不手写方法名。
- 支持多入参、对象入参、List 入参。
- 保持“单项目内编排”，不引入跨项目目录。
- 继续零入侵业务代码，不要求新增业务服务类。

## 2. 当前问题
- `fn` 文本输入容易拼错，发布前难发现。
- 参数个数/顺序/类型由用户“猜”，误配率高。
- 对象和 List 参数只能靠文本约定，缺少结构化约束。
- 分支条件与参数构造中的 `serviceCall` 交互不一致。

## 3. V3 核心交互
## 3.1 参数编辑入口
- 侧栏仅显示参数摘要卡：`参数名 / 类型 / 来源 / 校验状态`。
- 点击参数值，打开“参数编辑弹框”。

## 3.2 弹框中的服务调用配置
1. 选择服务
- 控件：可搜索下拉（按组件分组）。
- 选项示例：`BUSINESS / userService.queryProfile(String,Long,QueryOption)`。

2. 选择方法重载
- 当同名方法有多个签名时，展示重载列表。
- 选择后固定当前签名 `methodSignature`。

3. 自动带出入参槽位
- 根据签名自动生成参数行，参数数量固定，不允许随意增删。
- 每行显示：`参数名`、`声明类型`、`是否必填`、`默认值策略`。

4. 为每个参数选择来源
- 来源类型：`ctx`、`const`、`tempVar`、`serviceCall`、`transform`、`listPipeline`。
- 对象/List 参数默认使用 JSON 编辑器或“字段映射子弹框”。

5. 实时校验与预览
- 校验：参数个数、必填、类型兼容、JSON 格式、临时变量可达性。
- 预览：展示“运行时最终入参快照（示例值）”。

## 4. 数据模型（替代自由 `fn`）
```json
{
  "kind": "serviceCall",
  "serviceRef": {
    "endpointId": 1024,
    "serviceKey": "BUSINESS:userService",
    "methodName": "queryProfile",
    "methodSignature": "queryProfile(java.lang.String,java.lang.Long,com.example.QueryOption)",
    "returnType": "com.example.UserProfile"
  },
  "argBindings": [
    { "paramName": "tenantId", "paramType": "java.lang.String", "source": { "kind": "ctx", "path": "request.headers.tenantId" } },
    { "paramName": "userId", "paramType": "java.lang.Long", "source": { "kind": "tempVar", "tempKey": "userId" } },
    { "paramName": "options", "paramType": "com.example.QueryOption", "source": { "kind": "const", "constValue": "{\"withScore\":true}" } }
  ]
}
```

## 5. 校验规则
1. 方法签名校验
- `serviceRef.methodSignature` 必须存在于当前项目函数目录。

2. 参数结构校验
- `argBindings.length` 必须等于方法参数个数。
- `argBindings[i].paramName` 必须与签名参数一一对应。

3. 类型校验
- 基础类型强校验（String/Number/Boolean）。
- 对象/List 允许“兼容告警 + 发布拦截策略可配置”。

4. 来源校验
- `ctx` 路径必须可解析。
- `tempVar` 必须在当前节点执行顺序上已定义。
- `const` 对象/List 必须是合法 JSON。

## 6. API 设计
## 6.1 读取函数目录（新增）
- `GET /api/projects/{projectKey}/function-catalog`
- 返回：服务分组、方法签名、参数元数据、返回类型、是否可用于条件表达式。

## 6.2 复用现有元数据
- 已有：`GET /api/projects/{projectKey}/endpoints/components`
- 已有：`GET /api/projects/{projectKey}/endpoints/{endpointId}`
- 已有：`GET /api/projects/{projectKey}/code-snapshots/class-aggregate`

建议：先用现有接口拼装目录，后续收敛为 `function-catalog` 单接口，降低前端拼装复杂度。

## 7. 前端实现要点
1. 新组件
- `ServiceCallPickerModal.vue`：服务选择 + 方法重载选择。
- `ServiceArgBindingEditor.vue`：参数行编辑（按类型渲染控件）。

2. 替换逻辑
- 在 `ParamPlanBuilder.vue` 与 `BranchConditionBuilder.vue` 中，移除 `fn` 文本输入入口。
- `serviceCall` 一律走选择器弹框。

3. 一致性
- 参数构造与分支条件共享同一套 `serviceRef + argBindings` 编辑器与校验器。

## 8. 运行时与性能
1. 发布编译期
- 把 `serviceRef.methodSignature` 编译为稳定绑定（如 `MethodHandle` 索引）。
- 预编译参数取值路径，运行时不做脚本解释。

2. 运行时
- 仅执行编译后的绑定计划。
- 请求级去重缓存（相同服务 + 相同参数调用只执行一次）。
- `listPipeline.enrich` 优先走批量调用，避免 N+1。

## 9. 迁移策略
- 按你的要求，`不兼容旧数据`：
1. 新建流程强制使用 V3 结构。
2. 旧字段 `fn/argsText/argsMode` 不再写入。
3. 发布时若检测到旧结构直接拦截并提示“请重新配置服务调用”。

## 10. 验收标准
- 用户无法手写不存在的方法名。
- 方法签名变更后，编排配置能被自动标记为失效并阻止发布。
- 多入参/对象/List 场景可在弹框内完成，无需脚本。
- 参数构造与分支条件两处交互一致。
