# YGFlow Suite

YGFlow Suite 是一套面向企业业务场景的流程编排方案，覆盖编排器、运行时引擎、Spring Boot 集成层、IDEA 插件和可视化前端。项目的目标是把接口入口治理、流程编排、运行时执行和规则同步串成一条可落地的研发链路。

## 仓库结构

- `yglue-annotations`
  注解与元数据定义。
- `yglue-runtime`
  核心执行引擎，负责加载本地 `.ygflow` 规则、节点执行、参数解析和上下文传递。
- `yglue-runtime-spring-boot2`
  Spring Boot 2 集成层。
- `yglue-runtime-spring-boot3`
  Spring Boot 3 集成层，负责入口拦截、请求组装和 HTTP 接管。
- `yglue-orchestrator`
  编排器后端服务。
- `apps/ygflow-orchestrator-ui`
  编排器前端。
- `yglue-idea-plugin`
  IntelliJ IDEA 插件。
- `samples/yglue-sample-service`
  示例业务服务。

## 运行模式

当前仓库采用“编排器发布 + IDEA 插件同步 + 业务服务本地执行”的模式：

1. 在编排器中设计并发布 flow 与 entrypoint。
2. 通过 IDEA 插件把规则同步到业务项目本地 `.ygflow` 目录。
3. 业务服务通过 `yglue-runtime` 或 `yglue-runtime-spring-boot2/3` 加载并执行本地规则。

这意味着 `yglue-runtime` 只负责执行，不负责远程拉取规则；规则分发与同步属于编排器和插件链路。

## 扩展点

为了让框架用于真实业务时更灵活，当前推荐保留并使用两类扩展点：

- `NodeInterceptor`
  位于 `yglue-runtime`，适合做节点执行前后干预，例如审计、灰度标记、上下文补偿、统一监控、特殊异常转换。
- `InboundRequestInterceptor`
  位于 `yglue-runtime-spring-boot3`，适合在 HTTP 请求进入 flow 之前做参数注入、鉴权补充、租户信息透传、特殊场景熔断或拦截。

这类扩展点是合理的。对框架项目来说，业务差异往往出现在“进入执行前”和“节点执行中”两个阶段，如果完全写死在 runtime 里，后续会越来越难适配特殊场景。

## 快速开始

### 后端

```bash
mvn test
```

### 前端

```bash
cd apps/ygflow-orchestrator-ui
npm install
npm run build
```

### 启动编排器

```bash
cd yglue-orchestrator
mvn spring-boot:run
```

### 构建 IDEA 插件

```bash
cd yglue-idea-plugin
./gradlew buildPlugin
```

## 开源整理说明

为了便于发布到 GitHub，这个仓库应避免提交以下内容：

- IDE 工程文件与本地工作区文件
- 前端 `node_modules`、构建产物和临时调试文件
- 崩溃日志、运行日志、临时片段
- 本地工具生成的辅助目录

仓库根目录已经通过 `.editorconfig` 统一为 UTF-8，无 BOM。
