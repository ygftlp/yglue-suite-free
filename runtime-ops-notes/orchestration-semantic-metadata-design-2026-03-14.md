# 编排语义元数据设计

日期：2026-03-14

## 1. 目标

解决三个问题：

1. 平台不再强依赖特定注解才能发现可编排服务
2. 服务节点配置时，选择服务后自动获得入参、出参、对象字段信息
3. 分支和动态入参补齐不依赖上传全部源码正文，但能够拿到足够完整的类型信息

## 2. 核心结论

建议采用以下正式方案：

- 插件上传“结构化代码元数据”，不是源码正文
- 平台通过“暴露规则”决定哪些类/方法可作为服务节点
- 服务节点作为主要服务编排入口
- 用户在服务节点中先“选择服务方法”，再自动获得入参/出参/对象属性结构
- 分支、动态入参、脚本补齐共享同一套“编排语义元数据”

## 3. 对当前建议的判断

### 建议 1：上传所有代码元数据

方向是对的，但需要收敛成：

- 上传完整的语义元数据
- 不上传源码正文
- 平台不直接暴露全部服务

也就是说：

- 采集范围可以大
- 暴露范围必须可控

### 建议 2：直接在服务节点上支持“选择服务”

这个建议我认同，而且应作为主方案。

原因：

- 服务节点本来就是最自然的业务编排入口
- 用户选择服务后，平台自动回填参数签名和返回类型，心智负担最小
- 不需要额外引入另一套“服务发现配置器”
- 可以最大化复用当前 UI 基础

## 4. 当前项目中的现有基础

项目里已经有这条方案的雏形：

- [ServiceCallEditor.vue](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/ServiceCallEditor.vue)
  - 已支持加载服务目录
  - 已维护 `serviceRef`
  - 已根据所选方法回填 `argBindings`
  - 已能展示对象参数字段结构
- [ServiceNodeConfig.vue](/d:/JavaWorkspace/ygflow-suite/apps/ygflow-orchestrator-ui/src/components/ServiceNodeConfig.vue)
  - 已能展示服务签名摘要和输出信息
- [ExportMetadataAction.java](/d:/JavaWorkspace/ygflow-suite/yglue-idea-plugin/src/main/java/org/yglue/flow/idea/ExportMetadataAction.java)
  - 已具备服务、REST、模型、解析器元数据导出能力
- [CodeSnapshotExporter.java](/d:/JavaWorkspace/ygflow-suite/yglue-idea-plugin/src/main/java/org/yglue/flow/idea/snapshot/CodeSnapshotExporter.java)
  - 已能导出更完整的 public 类型语义结构

所以这不是推翻重来，而是把现有两条线整合成正式架构。

## 5. 推荐架构

### 5.1 结构化代码元数据

插件上传的不是源码，而是语义结构：

- 类型
  - qualifiedName
  - packageName
  - kind
  - JavaDoc 摘要
- 字段
  - name
  - type
  - required
  - description
- 方法
  - name
  - 参数
  - 返回类型
  - JavaDoc 摘要
- Spring Bean 信息
  - beanName
  - stereotype
  - package

### 5.2 服务暴露规则

平台不应把所有扫描到的类/方法都暴露成服务节点。

建议暴露规则支持：

- 按注解
- 按包路径
- 按 Spring stereotype
- 按 Bean 名
- 排除规则

例如：

- 包含：`com.xxx.app.service.**`
- 包含注解：`@Service`
- 排除：`*.Repository`、`*.Mapper`、`*.Config`

这样就不再强依赖 `@FlowApi/@FlowOperation`，但仍然有治理边界。

### 5.3 可达类型图

服务节点选中某个方法后，平台应递归展开：

- 参数类型
- 返回类型
- 参数/返回值内部引用到的 DTO/enum/value object

这样平台就能知道：

- 入参对象有哪些字段
- 返回对象有哪些字段
- 字段类型、说明、是否必填

这就是“对象属性描述怎么知晓”的根本答案。

## 6. 服务节点主方案

### 6.1 用户交互

服务节点的主交互应调整为：

1. 选择服务
2. 选择方法
3. 自动展示入参列表
4. 自动展示返回类型和字段结构
5. 用户为每个入参配置来源

不建议让用户先写：

- `bean.method`
- `fn`
- 原始签名字串

这些应该都由平台内部维护。

### 6.2 选择服务后的自动行为

当用户选中服务方法后，平台自动完成：

- 写入 `serviceRef`
- 生成 `argBindings`
- 回填参数名
- 回填参数类型
- 回填参数 schema
- 回填返回类型
- 回填返回对象字段

### 6.3 动态参数对象场景

例如：

```text
temp.customerProfile = 调用内部 Spring 服务得到 CustomerProfile
下游服务入参需要 CreateOrderCommand.profile.level
```

服务节点在拿到 `CustomerProfile` 类型元数据后，应支持：

- 展示 `CustomerProfile` 的字段树
- 把该字段树作为对象构造器的数据来源候选
- 用户通过点选完成对象字段绑定

这不要求上传所有源码正文，但要求平台拥有 `CustomerProfile` 的结构化类型元数据。

## 7. 分支与入参的统一数据源

建议平台统一维护一份“当前 flow 可见数据面板”，供所有配置器复用。

包括：

- `request.path`
- `request.query`
- `request.headers`
- `request.body`
- `temp`
- `upstream outputs`
- `ctx`
- `service return types`

这样：

- 分支配置器可以从这里选左值/右值
- 动态入参配置器可以从这里选来源路径
- 对象构造器可以从这里选对象字段来源

## 8. 为什么这比只靠注解更合理

只靠注解的问题：

- 接入成本高
- 漏标容易导致平台信息不完整
- 对对象类型补齐支撑不足

而“完整语义元数据 + 暴露规则 + 服务节点选择”能同时满足：

- 不强依赖业务侵入注解
- 平台信息完整
- 用户选择服务后即可开始配置
- 对象字段描述可见

## 9. 推荐实施顺序

### 第一步

把服务节点正式收敛成“选择服务方法”的主入口。

改造重点：

- 服务节点 UI
- 服务目录加载
- 选中方法后的签名/返回值回填

### 第二步

整合 metadata 与 code snapshot 的数据模型。

目标：

- 平台统一只认“编排语义元数据”
- 不再让前端显式区分这是 metadata 还是 snapshot

### 第三步

补可达类型图和对象字段递归展开。

目标：

- 服务返回对象
- DTO 嵌套对象
- `List<T>`
- 枚举和值对象

都能在 UI 中直接补齐。

## 10. 最终判断

你的建议方向是正确的，而且比“继续围绕特定注解打补丁”更合理。

我建议最终落地为：

- 上传完整结构化代码元数据
- 用规则控制可暴露服务
- 服务节点支持先选服务/方法
- 选择后自动获取入参、出参、对象字段信息

一句话总结：

- 平台应依赖“完整的编排语义元数据”
- 服务节点应成为这套信息的第一入口
