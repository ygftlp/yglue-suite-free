# YGFlow Suite 项目总览

## 1. 项目定位

YGFlow Suite 面向企业业务流程编排场景，提供从流程设计、规则发布、插件同步到运行时执行的一体化能力。它适合以下几类需求：

- 用统一方式管理 flow 与 REST 入口
- 把流程规则以本地文件形式随业务服务发布
- 尽量少改业务代码，就能把部分入口接入流程引擎
- 为不同业务线提供可插拔的干预能力

## 2. 核心模块

- `yglue-annotations`
  提供注解和元数据定义。
- `yglue-runtime`
  运行时核心，负责流程加载、图执行、节点执行、参数解析和上下文管理。
- `yglue-runtime-spring-boot2`
  Spring Boot 2 适配层。
- `yglue-runtime-spring-boot3`
  Spring Boot 3 适配层。
- `yglue-orchestrator`
  编排器后端，负责项目、流程、版本和 entrypoint 管理。
- `apps/ygflow-orchestrator-ui`
  编排器前端。
- `yglue-idea-plugin`
  IDEA 插件，负责规则与元数据同步。
- `samples/yglue-sample-service`
  示例服务，用于联调与验收。

## 3. 官方链路

当前推荐链路如下：

1. 在编排器中创建并发布 flow。
2. 在编排器中配置并启用 entrypoint。
3. 使用 IDEA 插件把规则同步到业务项目本地 `.ygflow`。
4. 业务服务打包并发布，运行时读取本地规则执行。

链路边界如下：

- `yglue-runtime` 负责执行，不负责远程同步。
- 规则同步责任在编排器与 IDEA 插件。
- 生产运行结果依赖本地 `.ygflow` 与发布内容保持一致。

## 4. 可扩展性建议

对于框架类项目，增加扩展点是合理的，否则后续很容易把业务定制逻辑不断塞回核心 runtime。

当前建议重点保留两类扩展点：

- 运行时节点拦截：通过 `NodeInterceptor` 对节点执行前后做统一治理。
- 入口请求拦截：通过 `InboundRequestInterceptor` 在 flow 执行前做参数补充、租户透传、鉴权增强和场景拦截。

比较适合放到扩展点里的业务能力包括：

- 多租户信息注入
- 自定义身份校验
- 特殊灰度开关
- 审计打点
- 风控预检查
- 上下文补偿或默认值填充

## 5. 构建方式

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

### 编排器

```bash
cd yglue-orchestrator
mvn spring-boot:run
```

### IDEA 插件

```bash
cd yglue-idea-plugin
./gradlew buildPlugin
```

## 6. 发布前检查

建议发布前至少确认以下事项：

1. 目标 flow 版本已发布。
2. 目标 entrypoint 已启用。
3. 本地 `.ygflow` 已完成同步。
4. 后端与前端构建通过。
5. 运行日志、IDE 文件、临时目录和本地依赖未被提交。
