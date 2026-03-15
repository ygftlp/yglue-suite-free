# YGFlow Suite 项目总览

## 1. 项目定位

YGFlow Suite 是一套面向企业服务场景的低代码流程编排方案，用来把接口元数据管理、可视化流程设计、流程发布、规则同步和运行时接管串成一条完整链路。

它适合以下场景：

- 统一管理 REST 入口点和流程规则
- 通过 IDEA 插件把已发布规则同步到本地服务项目
- 让业务服务在运行时执行本地 `.ygflow` 规则
- 在不大改业务代码的前提下，用 flow 接管指定 REST 接口

## 2. 官方闭环模型

当前项目的正式闭环是：

1. 在编排器中设计并发布 flow 版本。
2. 在编排器中配置并启用 entrypoint。
3. 通过 IDEA 插件把规则同步到目标本地项目。
4. 构建并发布带有 `.ygflow` 文件的业务服务。
5. 由 `yglue-runtime` 和 `yglue-runtime-spring-boot2/3` 在运行时读取本地规则并执行。

这里有一条明确边界：

- `yglue-runtime` 只负责执行。
- `yglue-runtime` 不负责远程同步。
- 官方同步职责属于 IDEA 插件。

## 3. 仓库结构

### 3.1 后端模块

- `yglue-annotations`
  提供注解定义和元数据标记能力，供插件扫描和运行时接入使用。

- `yglue-runtime`
  核心执行引擎，负责加载本地 `.ygflow`、解析入参、执行节点和返回结果。

- `yglue-runtime-spring-boot2`
  Spring Boot 2 集成层，负责运行时装配和 REST 接管。

- `yglue-runtime-spring-boot3`
  Spring Boot 3 集成层，负责运行时装配、请求提取和 REST 接管。

- `yglue-orchestrator`
  编排器后端，负责项目、流程、版本、入口点、元数据和插件同步接口。

- `samples/yglue-sample-service`
  样例服务，用于验证 flow 执行和真实 REST 接管链路。

### 3.2 前端与工具模块

- `apps/ygflow-orchestrator-ui`
  基于 Vue 3 的可视化编排前端。

- `yglue-idea-plugin`
  IntelliJ IDEA 插件，负责元数据上传和规则同步。

- `runtime-ops-notes`
  上线检查单、运行说明和当前上线准备度评估。

## 4. 核心功能

### 4.1 编排器

- 项目管理
- flow 定义管理
- flow 版本管理与发布
- REST entrypoint 管理
- endpoint 元数据管理
- 插件同步接口

### 4.2 IDEA 插件

- 本地代码元数据扫描
- 元数据上传到编排器
- 从编排器同步规则到本地 `.ygflow`
- 插件心跳与同步确认

### 4.3 运行时引擎

- 加载本地规则文件
- 执行 flow 图
- 从请求、上下文、表达式、常量中解析入参
- 支持 branch 分支选择
- 支持 service、transformer、set、log、call 等节点类型
- 返回 flow 执行结果给业务服务

### 4.4 Spring Boot 集成层

- 拦截匹配到的 REST 请求
- 匹配本地 entrypoint
- 提取请求参数和请求体
- 调用 runtime 执行 flow
- 把 flow 结果回写到 HTTP 响应

### 4.5 可视化前端

- flow 图编辑
- 节点配置
- endpoint 和 entrypoint 管理
- 版本发布操作
- 编排资产可视化管理

## 5. 技术栈

### 5.1 后端

- Java 17
- Spring Boot 3.3.4
- MyBatis Spring Boot Starter 3.0.3
- MySQL 8
- Flyway
- Lombok
- Jackson
- Groovy
- LiteFlow
- AspectJ

### 5.2 前端

- Vue 3
- TypeScript 5
- Vite 5
- Vue Router 4
- Vue Flow
- Monaco Editor
- CodeMirror
- Lucide Vue

### 5.3 构建与工具

- Maven
- Gradle
- IntelliJ Platform SDK
- npm

## 6. 运行时数据模型

运行时不是直接远程拉取规则，而是读取本地文件：

- `.ygflow/rules/<flowCode>.json`
- `.ygflow/entrypoints.json`

这意味着生产行为取决于本地同步结果是否正确，以及这些文件是否已经跟随服务一起发布。

## 7. 当前闭环验证状态

截至 2026-03-12，仓库内已经验证通过的关键链路包括：

- 根项目 Maven 测试通过
- 前端生产构建通过
- branch 分支只沿命中边执行
- 入参解析链路端到端通过
- 样例 flow 执行通过
- Spring Boot 3 真实 REST 接管通过
- 前端 entrypoint 开关已与后端接口对齐

因此，当前仓库已经具备以下核心闭环：

`编排器发布 -> IDEA 插件同步 -> 本地 .ygflow 执行`

## 8. 当前上线判断

如果你们接受当前官方模式：

- 编排器是控制面
- IDEA 插件同步是强制发布门禁
- runtime 只执行本地规则

那么当前项目已经具备上线基础。

如果你们要求“平台一发布，服务端自动拉取并立即热更新”，当前仓库并没有实现这一模式。

## 9. 推荐发布门禁

每次发布前至少做以下动作：

1. 确认目标 flow 版本已发布。
2. 确认目标 entrypoint 已启用。
3. 对目标项目执行 IDEA 插件同步。
4. 确认本地 `.ygflow` 文件已更新。
5. 运行 `verify-yglue-sync.ps1` 做一致性校验。
6. 执行业务服务构建和测试。
7. 对接管接口和非接管接口做 smoke test。

详细流程见 [plugin-sync-release-checklist.md](/d:/JavaWorkspace/ygflow-suite/runtime-ops-notes/plugin-sync-release-checklist.md) 和 [release-readiness-2026-03-12.md](/d:/JavaWorkspace/ygflow-suite/runtime-ops-notes/release-readiness-2026-03-12.md)。

## 10. 快速开始

### 10.1 后端

```bash
mvn test
```

### 10.2 前端

```bash
cd apps/ygflow-orchestrator-ui
npm install
npm run build
```

### 10.3 启动编排器

```bash
cd yglue-orchestrator
mvn spring-boot:run
```

### 10.4 构建 IDEA 插件

```bash
cd yglue-idea-plugin
./gradlew buildPlugin
```

在 IntelliJ IDEA 中安装插件包后，配置编排器地址和项目标识，再执行插件同步动作即可刷新本地 `.ygflow`。
