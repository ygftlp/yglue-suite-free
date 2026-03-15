# YGFlow Suite

YGFlow Suite（易构流程套件）是一套面向企业应用的流程编排方案，覆盖以下链路：

- 编排器 `yglue-orchestrator`
- 可视化设计台 `ygflow-orchestrator-ui`
- IDEA 插件 `yglue-idea-plugin`
- 运行时引擎 `yglue-runtime`
- Spring Boot 集成层 `yglue-runtime-spring-boot2` / `yglue-runtime-spring-boot3`

它的目标是把接口入参治理、流程编排、数据转换和运行时接管串成一条完整链路。

## 官方发布链路

当前项目的正式链路是：

1. 在编排器中设计并发布 flow、entrypoint。
2. 通过 IDEA 插件把规则和入口点同步到本地项目的 `.ygflow` 目录。
3. 业务服务通过 `yglue-runtime` / `yglue-runtime-spring-boot3` 读取本地 `.ygflow` 并执行。

这里有一个明确边界：

- `yglue-runtime` 只负责加载本地规则并执行，不承担远程同步职责。
- 同步职责属于 IDEA 插件；插件同步是正式发布门禁的一部分。

## 项目文档

- [项目总览](docs/PROJECT_OVERVIEW.md)

## 快速开始

### 环境要求

- Java 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+
- IntelliJ IDEA 2024.2+

### 构建后端

```bash
mvn test
```

### 构建前端

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

然后在 IntelliJ IDEA 中通过 `File -> Settings -> Plugins -> Install Plugin from Disk...` 安装插件包。

## 发布与验收

如果采用项目当前的正式模式上线，建议把下面两个文档作为发布门禁：

- [插件同步发布检查单](runtime-ops-notes/plugin-sync-release-checklist.md)
- [上线准备度评估](runtime-ops-notes/release-readiness-2026-03-12.md)

可选的辅助脚本：

- `verify-yglue-sync.ps1`：校验本地 `.ygflow` 与编排器已发布内容是否一致。

## 当前验证结论

截至 2026-03-12，当前仓库已完成的关键验证包括：

- 根项目 `mvn test` 通过
- 前端 `npm run build` 通过
- 分支执行与入参解析专项测试通过
- Spring Boot 真实 REST 接管测试通过

在“编排器发布 + IDEA 插件同步 + 本地 `.ygflow` 执行”这一既定模式下，项目已经具备上线基础；但仍建议先走灰度或预发演练。

## 文档入口

- [项目总览](docs/PROJECT_OVERVIEW.md)
- [插件同步发布检查单](runtime-ops-notes/plugin-sync-release-checklist.md)
- [上线准备度评估](runtime-ops-notes/release-readiness-2026-03-12.md)
