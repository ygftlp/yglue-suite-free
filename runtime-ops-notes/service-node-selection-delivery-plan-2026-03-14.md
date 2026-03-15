# 服务节点“选择服务后自动回填”实施清单

日期：2026-03-14

## 1. 目标

把服务节点收敛成统一主交互：

1. 选择服务
2. 选择方法
3. 自动回填入参
4. 自动回填出参
5. 用户只配置参数来源和少量修正

目标不是新增一套新节点，而是在现有服务节点基础上演进。

## 2. 当前基础

当前项目已经具备较多可复用基础：

- [ServiceCallEditor.vue](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/ServiceCallEditor.vue)
  - 已能加载服务目录
  - 已能选择方法
  - 已能维护 `serviceRef`
  - 已能生成 `argBindings`
  - 已能展示对象参数字段结构
- [ServiceNodeConfig.vue](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/ServiceNodeConfig.vue)
  - 已能展示输入签名和输出信息
- [client.ts](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/api/client.ts)
  - 已有 `/projects/{projectKey}/endpoints/components` 调用
- [ExportMetadataAction.java](/d:/JavaWorkspace/ygflow-suite/yglue-idea-plugin/src/main/java/org/yglue/flow/idea/ExportMetadataAction.java)
  - 已能导出服务方法、入参、出参等元数据

结论：

- 这项工作是“收敛和默认化”
- 不是“从零发明能力”

## 3. 交付目标

P0 完成后，服务节点应表现为：

- 默认展示“选择服务方法”入口
- 选择后自动显示：
  - 服务名
  - Bean 名
  - 方法签名
  - 入参名/类型/说明/是否必填
  - 返回类型/返回字段
- 自动初始化参数映射草稿
- 对象类型参数默认推荐“对象构造器模式”

## 4. 前端任务

### FE-1 服务节点入口改为“选择服务方法”

#### 目标

让用户先选服务方法，不再把 `fn/bean/method` 当作主要输入项。

#### 改动点

- [ServiceNodeConfig.vue](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/ServiceNodeConfig.vue)
- [Inspector.vue](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/Inspector.vue)

#### 行为

- 如果当前节点还没有 `comp/serviceRef`
  - 首屏直接展示“选择服务”按钮
- 如果已选择
  - 展示服务摘要卡片
  - 支持“重新选择”

### FE-2 复用 `ServiceCallEditor` 作为主配置器

#### 目标

不要让 `ServiceCallEditor` 只存在于 `serviceCall` 来源场景，而应成为服务节点本身的主配置器。

#### 改动点

- [ServiceCallEditor.vue](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/ServiceCallEditor.vue)
- [ServiceNodeConfig.vue](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/ServiceNodeConfig.vue)

#### 行为

- 选定方法后：
  - 自动生成 `serviceRef`
  - 自动生成 `argBindings`
  - 自动同步到服务节点的 `inputs/output/paramPlans`

### FE-3 自动回填入参草稿

#### 目标

根据参数名和 request schema 自动生成默认来源。

#### 匹配优先级

1. `request.path.<name>`
2. `request.query.<name>`
3. `request.body.<name>`
4. 同名 `temp`
5. 同名上游输出

#### 改动点

- [ServiceCallEditor.vue](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/ServiceCallEditor.vue)
- [ParamPlanBuilder.vue](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/ParamPlanBuilder.vue)

#### 结果

- 选择服务方法后，不再是空白参数列表
- 用户只修正少量不匹配项

### FE-4 输出信息自动回填

#### 目标

根据服务返回类型自动展示：

- 返回值类型
- 对象字段
- 默认 `contextKey`

#### 改动点

- [ServiceNodeConfig.vue](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/ServiceNodeConfig.vue)
- [useFlowState.ts](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/composables/useFlowState.ts)

### FE-5 参数来源选择器

#### 目标

减少用户手写路径。

#### 方案

在服务节点配置中加入统一来源选择：

- request.path
- request.query
- request.body
- temp
- upstream
- const

这项先在服务节点范围内落地，后续再复用到分支和其他节点。

### FE-6 对象参数默认推荐“对象构造器”

#### 目标

如果参数是对象类型，默认进入对象构造器模式，而不是直接把它当字符串或纯路径。

#### 改动点

- [ServiceCallEditor.vue](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/ServiceCallEditor.vue)

#### 行为

- 基础类型：默认 direct
- 对象类型：默认 objectBuilder
- `List<T>`：默认展示 item 类型提示

## 5. 后端任务

### BE-1 服务目录接口字段校准

#### 目标

确保前端选择服务方法时拿到的信息足够完整。

#### 服务目录至少应包含

- `serviceName`
- `serviceBean`
- `serviceClass`
- `methodName`
- `methodSignature`
- `returnType`
- `params`
  - `name`
  - `type`
  - `required`
  - `description`
  - `schema`
- 返回值 schema 或输出字段

#### 现状

前端 `ServiceCallEditor` 已按这类结构消费目录数据，说明后端接口大体已具备。

### BE-2 可达类型展开

#### 目标

服务方法的参数和返回值如果是对象类型，平台需要知道内部字段结构。

#### 要求

- 展开 DTO 属性
- 支持嵌套对象
- 支持 `List<T>`
- 支持枚举和值对象

这项是对象字段补齐的核心。

### BE-3 暴露规则替代强依赖注解

#### 目标

允许平台配置哪些类/方法被视为“可编排服务”。

#### 规则建议

- include annotations
- include packages
- include stereotypes
- exclude patterns

这样不再要求业务方必须额外标注专用注解。

## 6. 插件任务

### PLG-1 元数据模型统一

#### 目标

把当前“metadata”和“code snapshot”两条线收敛成更统一的“编排语义元数据”。

#### 方向

- 基础元数据：服务、REST、模型、schema
- 增强元数据：public 类型语义结构

### PLG-2 服务暴露扫描规则配置

#### 目标

不只按 `@FlowApi/@FlowOperation` 扫描，而是支持规则化扫描。

#### 配置项建议

- includes packages
- excludes packages
- include annotations
- exclude classes
- include Spring stereotypes

## 7. 数据模型建议

服务节点内部建议收敛到以下核心字段：

- `serviceRef`
  - `serviceBean`
  - `serviceClass`
  - `methodName`
  - `methodSignature`
  - `returnType`
- `inputs`
  - 签名定义
- `output`
  - 返回值定义
- `paramPlans`
  - 实际参数来源映射

其中：

- `serviceRef/inputs/output` 是“静态签名层”
- `paramPlans` 是“运行时映射层”

不要再让用户直接维护底层 `fn` 作为主要入口。

## 8. 验收场景

### 场景 1：简单方法

方法：

```java
String greet(String name, Integer age)
```

验收：

- 选中方法后自动出现两个参数
- 默认草稿优先匹配 `request.body.name`、`request.body.age`
- 输出显示 `String`

### 场景 2：对象参数

方法：

```java
OrderResult create(CreateOrderCommand cmd)
```

验收：

- 选中方法后自动识别对象参数
- 默认进入对象构造器模式
- 可展示 `cmd` 的字段树
- 输出显示 `OrderResult` 字段树

### 场景 3：返回对象继续用于下游

场景：

- 服务节点 A 返回 `CustomerProfile`
- 下游节点 B 需要 `profile.level`

验收：

- A 节点输出字段可见
- B 节点映射时可以直接选到上游字段

## 9. 建议实施顺序

### 迭代 1

- FE-1
- FE-2
- FE-3
- FE-4

目标：

- 服务节点先选服务，再自动回填入参/出参

### 迭代 2

- FE-5
- FE-6
- BE-2

目标：

- 对象字段映射体验稳定

### 迭代 3

- BE-3
- PLG-1
- PLG-2

目标：

- 从“依赖特定注解”正式升级到“规则化暴露服务”

## 10. 最终判断

服务节点支持“选择服务”不仅可行，而且应该成为后续配置体验的主轴。

一句话总结：

- 平台先让用户选服务方法
- 平台自动回填入参/出参/对象字段
- 用户只做映射，不做低层签名维护
