# YGFlow Suite

## 项目简介

YGFlow Suite（易构流程套件）是一个面向企业级业务流程的低代码编排平台，覆盖"开发者 IDE → 可视化设计台 → 运行时引擎"的全链路，实现入参治理、数据转换和流程调度的统一管理。产品专注于降低研发联调成本、提升上线效率，并提供可扩展的生态能力。

## 核心特性

- **可视化流程设计**：基于拖拽画布的流程编排，支持多种节点类型和复杂流程控制
- **参数解析与验证**：支持多种参数来源和验证规则，确保数据合法性
- **数据转换引擎**：支持 DSL 驱动的数据转换和 Groovy 脚本执行
- **版本管理**：完整的流程版本控制和发布管理
- **IDE 集成**：IntelliJ IDEA 插件支持，实现元数据自动扫描和规则同步
- **运行时引擎**：基于 LiteFlow 的高性能流程执行引擎

## 项目结构

YGFlow Suite 采用多模块架构，各模块职责清晰：

### 核心模块

- **[yglue-annotations](docs/MODULE_yglue-annotations.md)** - 注解定义模块
  - 提供用于标记和描述业务流程相关组件的 Java 注解
  - 定义元数据标注规范

- **[yglue-runtime](docs/MODULE_yglue-runtime.md)** - 运行时核心引擎
  - 流程执行引擎（基于 LiteFlow）
  - 节点执行器（服务节点、分支节点、转换节点等）
  - 参数解析引擎
  - 数据转换引擎
  - 表达式引擎（SpEL）
  - 验证器引擎

- **[yglue-runtime-spring-boot2](docs/MODULE_yglue-runtime-spring-boot2.md)** - Spring Boot 2.x 集成
  - Spring Boot 2.x 自动配置
  - REST 接口 AOP 拦截
  - 流程规则匹配和执行

- **[yglue-runtime-spring-boot3](docs/MODULE_yglue-runtime-spring-boot3.md)** - Spring Boot 3.x 集成
  - Spring Boot 3.x 自动配置
  - REST 接口 AOP 拦截
  - Jakarta EE 9+ 适配

### 服务模块

- **[yglue-orchestrator](docs/MODULE_yglue-orchestrator.md)** - 编排器服务
  - 流程定义管理
  - 入口点管理
  - 元数据管理
  - 规则同步服务
  - REST API 服务

### 工具模块

- **[yglue-idea-plugin](docs/MODULE_yglue-idea-plugin.md)** - IntelliJ IDEA 插件
  - 元数据扫描和上传
  - 规则文件同步
  - 自动同步机制

- **[ygflow-orchestrator-ui](docs/MODULE_ygflow-orchestrator-ui.md)** - 可视化设计台
  - 流程设计画布
  - 节点配置面板
  - 版本管理界面
  - 规则预览功能

## 快速开始

### 环境要求

- **Java**: 17+
- **Maven**: 3.8+
- **Node.js**: 18+ (仅前端需要)
- **MySQL**: 8.0+ (仅编排器服务需要)
- **IntelliJ IDEA**: 2024.2+ (仅插件开发需要)

### 构建项目

```bash
# 克隆项目
git clone <repository-url>
cd ygflow-suite

# 构建所有模块
mvn clean install

# 构建前端
cd apps/ygflow-orchestrator-ui
npm install
npm run build
```

### 运行编排器服务

```bash
cd yglue-orchestrator
mvn spring-boot:run
```

### 运行前端

```bash
cd apps/ygflow-orchestrator-ui
npm run dev
```

### 安装 IDEA 插件

```bash
cd yglue-idea-plugin
./gradlew buildPlugin
```

然后在 IntelliJ IDEA 中通过 `File → Settings → Plugins → Install Plugin from Disk...` 安装生成的插件包。

## 文档目录

### 模块文档

- [yglue-annotations 模块文档](docs/MODULE_yglue-annotations.md)
- [yglue-runtime 模块文档](docs/MODULE_yglue-runtime.md)
- [yglue-runtime-spring-boot2 模块文档](docs/MODULE_yglue-runtime-spring-boot2.md)
- [yglue-runtime-spring-boot3 模块文档](docs/MODULE_yglue-runtime-spring-boot3.md)
- [yglue-orchestrator 模块文档](docs/MODULE_yglue-orchestrator.md)
- [yglue-idea-plugin 模块文档](docs/MODULE_yglue-idea-plugin.md)
- [ygflow-orchestrator-ui 模块文档](docs/MODULE_ygflow-orchestrator-ui.md)

### 设计文档

- [产品介绍](docs/DESIGN_product-introduction.md)
- [数据流设计](docs/DESIGN_data-flow.md)
- [运行时执行流程](docs/DESIGN_runtime-execution-flow.md)
- [IDEA 元数据结构](docs/DESIGN_idea-metadata-structure.md)
- [节点验证设计](docs/DESIGN_node-validation.md)
- [验证数据结构](docs/DESIGN_validation-data-structure.md)
- [验证器包设计](docs/DESIGN_validator-package.md)
- [错误响应格式](docs/DESIGN_error-response-format.md)
- [Transformer DSL](docs/DESIGN_transformer-dsl.md)

### 其他文档

- [代码规范](docs/CODE_STYLE.md)
- [用户手册](docs/USER_MANUAL.md)

## 技术栈

### 后端技术

- **Spring Boot**: 3.3.4
- **MyBatis**: 3.0.3
- **LiteFlow**: 2.12.1
- **Groovy**: 3.0.20
- **MySQL**: 8.0+

### 前端技术

- **Vue**: 3.5.x
- **TypeScript**: 5.6.x
- **Vue Flow**: 1.47.0
- **Monaco Editor**: 0.50.0
- **Vite**: 5.4.x

### 开发工具

- **IntelliJ Platform SDK**: 2024.2+
- **Gradle**: 8.x
- **Maven**: 3.8+

## 架构设计

YGFlow Suite 采用分层架构设计：

```
┌─────────────────────────────────────────────────────────┐
│              可视化设计台 (ygflow-orchestrator-ui)        │
│              Vue 3 + TypeScript + Vue Flow              │
└─────────────────────────────────────────────────────────┘
                          ↕ HTTP REST API
┌─────────────────────────────────────────────────────────┐
│              编排器服务 (yglue-orchestrator)             │
│              Spring Boot + MyBatis + MySQL             │
└─────────────────────────────────────────────────────────┘
                          ↕ 规则同步
┌─────────────────────────────────────────────────────────┐
│              IDEA 插件 (yglue-idea-plugin)              │
│              IntelliJ Platform SDK                     │
└─────────────────────────────────────────────────────────┘
                          ↕ 流程执行
┌─────────────────────────────────────────────────────────┐
│              运行时引擎 (yglue-runtime)                  │
│              LiteFlow + Spring + Groovy                │
└─────────────────────────────────────────────────────────┘
```

## 核心概念

### 流程（Flow）

流程是由多个节点组成的业务编排逻辑，支持顺序执行、条件分支、并行执行等复杂场景。

### 节点（Node）

流程中的基本执行单元，包括：
- **入口节点**：流程的起始点
- **服务节点**：调用业务服务方法
- **转换节点**：执行数据转换
- **分支节点**：根据条件选择执行路径
- **事务节点**：事务控制
- **出口节点**：流程的结束点

### 参数解析器（Param Resolver）

用于从不同来源解析参数值，支持：
- HTTP 请求参数（路径变量、查询参数、请求体）
- 流程上下文变量
- SpEL 表达式计算结果
- 常量值

### 数据转换器（Transformer）

用于执行数据转换，支持：
- 字段映射
- 集合处理
- 条件转换
- Groovy 脚本执行

### 验证器（Validator）

用于验证参数合法性，支持：
- 必填验证
- 类型验证
- 长度验证
- 范围验证
- 正则表达式验证
- 表达式验证

## 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

本项目采用内部许可证，仅供公司内部使用。

## 联系方式

如有问题或建议，请联系项目维护团队。

---

**注意**：本文档会持续更新，请关注最新版本。
