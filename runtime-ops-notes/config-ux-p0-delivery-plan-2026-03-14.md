# 配置体验 P0 落地任务清单

日期：2026-03-14

目标：

- 降低服务节点和分支节点的配置门槛
- 让用户在不理解运行时细节的前提下完成 80% 常见配置
- 不引入运行时边界漂移，不让 `code snapshot` 反向成为基础配置前置条件

## 1. 交付范围

P0 只做三件事：

1. 自动生成入参映射草稿
2. 路径选择器替代主要手填路径
3. 分支条件构建器默认化

不在 P0 内的内容：

- 模板市场
- 示例数据预演
- 并行执行能力
- 复杂列表编排重构
- 脚本编辑器类型系统升级

## 2. 任务拆分

### 任务 A：自动生成入参映射草稿

#### 目标

用户进入服务节点配置面板后，平台自动给出一版初始 `argPlans`，而不是空白表单。

#### 匹配优先级

1. 同名 `request.path`
2. 同名 `request.query`
3. 同名 `request.body`
4. 同名上游输出字段
5. 同名 temp 变量

#### 需要的数据源

- 服务签名元数据
- entrypoint request schema
- 当前 flow 中已定义的 `tempPlans`
- 上游节点输出 `as/contextKey`

#### 前端改动点

- [ServiceNodeConfig.vue](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/ServiceNodeConfig.vue)
- [ParamPlanBuilder.vue](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/ParamPlanBuilder.vue)

#### 后端改动建议

优先不改后端，先由前端基于现有数据源做本地草稿推断。

如果前端缺上游上下文信息，再补一个轻量接口返回：

- 当前 flow 可见上下文变量摘要
- 上游节点输出摘要

#### 验收标准

- 新建服务节点时，如果存在签名和 request schema，自动至少生成一条 `argPlan`
- 同名参数在常见 REST 场景下自动命中率达到可用水平
- 用户仍可手动覆盖所有自动生成结果

### 任务 B：路径选择器

#### 目标

把 `ctx.xxx`、`request.body.xxx`、`temp.xxx`、上游输出路径从“以手填为主”改为“以点选为主”。

#### 数据树结构

1. `request`
   - `path`
   - `query`
   - `headers`
   - `body`
2. `temp`
3. `upstream`
4. `ctx`

#### 改动方式

新增一个通用组件，例如：

- `ContextPathPicker.vue`

然后在以下场景复用：

- `ParamPlanBuilder` 的 `ctx` 来源路径
- 分支条件左值/右值路径
- HTTP Header / Query 中来源为 `ctx` 的路径

#### 前端改动点

- [ParamPlanBuilder.vue](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/ParamPlanBuilder.vue)
- 分支节点配置组件
- 可能还包括脚本辅助面板

#### 验收标准

- 常见路径都能通过树形选择生成
- 仍保留手填模式作为高级入口
- 用户不需要记忆系统路径格式即可完成基本映射

### 任务 C：分支条件构建器默认化

#### 目标

用户默认使用结构化条件构建器，而不是直接进入表达式模式。

#### 支持能力

- 左值选择
- 运算符选择
- 右值选择或常量输入
- `AND / OR` 组合
- 条件组嵌套

#### 数据来源

左值候选应来自：

- `request`
- `temp`
- 上游输出
- `ctx`

#### 保留能力

复杂用户仍然允许切换到表达式模式。

#### 改动点

如果当前分支节点已存在结构化条件模型，则优先补齐 UI；
如果当前仍存在多处原始结构混用，P0 不重构运行时，只统一配置面。

#### 验收标准

- 常见审批/金额/状态分支场景可纯 UI 配置
- 不需要先写脚本或表达式才能完成简单分支

## 3. 建议实现顺序

### 第一步

先做任务 A。

原因：

- 成本最低
- 用户感知最直接
- 不会牵动运行时

### 第二步

做任务 B。

原因：

- 能显著降低手填路径的错误率
- 可复用于入参和分支

### 第三步

做任务 C。

原因：

- 交互面更大
- 容易和现有分支配置结构耦合
- 应放在路径选择器之后实现

## 4. 风险控制

### 风险 1：自动草稿误判

处理方式：

- 自动生成后明确标记“系统建议”
- 允许一键清空和重建
- 不把草稿当作强约束

### 风险 2：路径选择器和真实运行时上下文不一致

处理方式：

- 路径树必须完全基于 metadata/schema/flow 上下文生成
- 不允许 UI 构造出运行时不存在的虚假路径

### 风险 3：分支 UI 过度封装导致复杂场景不可表达

处理方式：

- 默认结构化条件构建器
- 高级模式保留表达式

## 5. 最小验收用例

### 用例 1：简单服务入参

接口：

- `POST /orders/create`

服务：

- `create(String userId, Long skuId, Integer quantity)`

要求：

- 打开服务节点后自动生成 3 条映射草稿
- 用户只需要少量调整即可保存

### 用例 2：对象字段路径选择

请求体：

```json
{
  "user": {
    "id": "u001"
  },
  "order": {
    "amount": 1200
  }
}
```

要求：

- 用户可通过树形选择 `request.body.user.id`
- 用户可通过树形选择 `request.body.order.amount`

### 用例 3：审批分支

条件：

- `request.body.amount > 1000`
- `temp.riskLevel == 'HIGH'`

要求：

- 无需手写表达式即可配置
- 最终分支结构可正确保存

## 6. 交付建议

P0 最适合拆成 2 个迭代：

### 迭代 1

- 自动生成入参映射草稿
- 参数卡片补充说明、必填、类型提示

### 迭代 2

- 路径选择器
- 分支条件构建器默认化

## 7. 最终目标

P0 完成后，项目应达到以下用户体验：

- 常见服务节点配置不再是空白开始
- 用户不需要记忆运行时路径格式
- 简单分支不需要写表达式
- 分支和动态入参仍然只依赖 metadata/schema/flow 上下文，不依赖完整代码快照
