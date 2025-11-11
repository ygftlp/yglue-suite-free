## 产品简介

易构流程套件（YGFlow Suite）是面向企业级业务流程的低代码编排平台，覆盖“开发者 IDE → 可视化设计台 → 运行时引擎”的全链路，实现入参治理、数据转换和流程调度的统一管理。产品专注于降低研发联调成本、提升上线效率，并提供可扩展的生态能力。

## 产品架构

- **IDE 侧（开发者入口）**  
  - IntelliJ IDEA 插件扫描业务项目，解析 Controller、DTO 与校验注解，自动生成 REST Endpoint 元数据与请求 JSON Schema，并支持一键上报。  
  - 内置 ParamResolver 模板与 Transformer DSL 预览，帮助研发在编码阶段定义输入来源与数据变换逻辑。

- **Orchestrator Studio（低代码设计台）**  
  - 基于 Vue 3 + TypeScript + Monaco Editor，提供流程编排画布、节点配置、Schema/Resolver 选择器、Transformer 可视化编辑器。  
  - 支持流程版本管理、发布、模拟执行，实时校验 Transformer DSL，并内嵌帮助文档降低学习门槛。  
  - 通过统一 REST API 与后端交互，存储流程定义、节点配置、入参 Schema、Transformer DSL 等关键数据。

- **运行时平台（Flow Runtime）**  
  - Spring Boot 2/3 双版本拦截器，通过 FlowDispatchInterceptor 接入业务服务。  
  - ParamResolver 引擎按 Schema 抽取请求参数、应用默认值、统一返回 `FLOW_BAD_REQUEST` 错误。  
  - Transformer 执行器解析 DSL，完成字段映射、集合处理、条件逻辑和表达式计算，输出结构化结果。  
  - Flow Engine 负责节点调度、上下文管理、表达式引擎（SpEL，可扩展 MVEL）及插件化组件扩展。

- **统一管理中心（Metadata Service）**  
  - Orchestrator 后端负责流程、入口、节点、Transformer 等模型的持久化与版本比对。  
  - 提供 REST API 给 Studio、IDEA 插件与自动化发布系统使用，未来可扩展权限、审计与发布流水线集成。

## 系统架构

- **前端层**：`ygflow-orchestrator-ui`（Vue 3 + Vite）  
  - 核心模块：Studio 画布、FlowSettings 面板、TransformerEditor、Schema 预览。  
  - 用统一 API 客户端访问 `yglue-orchestrator`，结合 Pinia/VueUse 管理状态。

- **服务层**：`yglue-orchestrator`（Spring Boot + MyBatis）  
  - 模块：流程管理、入口管理、Transformer 管理、发布管理。  
  - 实体：FlowDefinition、FlowNodeConfig、FlowEntryPoint、TransformerTemplate。  
  - 数据库：支持 JSON Schema/DSL 存储，迁移脚本维护于 `db/migration`。  
  - REST 接口供 Studio、IDEA 插件、流水线调用。

- **运行时层**：`yglue-runtime` + `yglue-runtime-spring-boot2/3`  
  - 执行链：请求 → RequestSchemaValidator → ParamResolver → Flow Engine → TransformerExecutor → 结果。  
  - 扩展点：ExpressionEngine、自定义 Resolver、脚本步骤、事件回调。  
  - 单元测试覆盖表达式、Transformer DSL、参数解析等核心能力。

- **开发工具链**  
  - IDEA 插件（`yglue-idea-plugin`）作为元数据采集入口，Gradle/Maven 双构建支持。  
  - 文档中心（如 `docs/transformer-dsl.md`）提供操作指引与示例。  
  - 可对接企业 CI/CD，执行单测与自动化发布。

## 核心价值

- **加速上线**：IDE 自动 Schema 生成 + 低代码编排，显著缩短从需求到上线的周期。  
- **降低风险**：Schema 驱动的入参校验、默认值与统一错误治理，将运行时问题前置到设计阶段。  
- **跨团队协作**：业务、后端、测试在 Studio 中共享可视化流程，内嵌示例与帮助提升认知一致性。  
- **可观测与治理**：全链路元数据可被管控平台采集，实现变更审计、质量追踪与流程复用。

## 未来愿景

- **生态扩展**：开放 ParamResolver、Transformer 步骤插件市场，沉淀行业模板形成共建生态。  
- **智能化**：引入 AIGC 自动生成 Schema/Transformer 初稿，提供智能推荐与语义校验。  
- **跨云互通**：适配 Serverless/Kubernetes 场景，支持多云部署与弹性伸缩。  
- **数据闭环**：接入运营后台，构建流程执行可视化看板与数据洞察，驱动持续优化。  
- **治理与安全**：构建权限体系、版本策略、审批流程，融入企业 DevSecOps 体系。

