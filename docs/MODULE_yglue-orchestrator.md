# yglue-orchestrator 模块文档

## 模块概述

`yglue-orchestrator` 是 YGFlow Suite 的编排器服务模块，提供流程定义管理、元数据管理、版本控制、规则同步等核心后端服务能力。该模块是可视化设计台和 IDE 插件的后端支撑，负责流程资产的统一管理和分发。

## 模块职责

- **流程管理**：流程定义的 CRUD 操作、版本管理、发布管理
- **入口点管理**：REST 入口点配置、路径匹配规则管理
- **元数据管理**：组件元数据（Flow Model、Flow Resolver）的存储和查询
- **规则同步**：与 IDE 插件之间的规则文件同步机制
- **项目管理**：项目维度的资源管理和权限控制
- **统计服务**：流程执行统计和事件记录

## 核心能力

### 1. 流程管理服务

**核心类：**
- `FlowService`: 流程管理服务
- `FlowController`: 流程 REST 控制器

**主要功能：**
- 流程定义的创建、更新、删除、查询
- 流程版本管理（保存草稿、发布版本）
- 流程内容序列化和反序列化
- UI 字段清理（移除 position 等前端专用字段）

### 2. 入口点管理服务

**核心类：**
- `FlowEntryPointService`: 入口点管理服务
- `FlowEntryPointController`: 入口点 REST 控制器

**主要功能：**
- REST 入口点的创建和更新
- 路径和方法规范化（统一大小写、去除首尾斜杠）
- 入口点与流程的关联管理
- 数据响应格式配置管理

### 3. 项目管理服务

**核心类：**
- `ProjectService`: 项目管理服务
- `ProjectController`: 项目 REST 控制器

**主要功能：**
- 项目的创建、查询、更新
- 项目下端点组件的查询和管理
- 项目元数据管理

### 4. 端点组件服务

**核心类：**
- `ProjectEndpointService`: 端点组件服务
- `ProjectEndpointController`: 端点组件 REST 控制器

**主要功能：**
- REST 端点的 CRUD 操作
- 端点组件的类型识别（SERVICE）
- 请求和响应 Schema 管理
- 端点与流程的关联

### 5. 元数据管理服务

**核心类：**
- `MetadataService`: 元数据管理服务
- `FlowModelService`: Flow Model 管理服务
- `FlowResolverService`: Flow Resolver 管理服务

**主要功能：**
- Flow Model 的存储和查询
- Flow Resolver 的存储和查询
- 元数据的版本管理

### 6. 规则同步服务

**核心类：**
- `PluginSyncService`: 插件同步服务
- `RuleSyncService`: 规则同步服务（IDE 插件侧）

**主要功能：**
- 插件心跳检测
- 待同步流程列表查询
- 规则文件下载和确认
- 本地文件存在性检查

### 7. 统计服务

**核心类：**
- `StatService`: 统计服务
- `StatController`: 统计 REST 控制器

**主要功能：**
- 流程执行事件记录
- 执行统计查询

## 技术特性

### 1. Spring Boot Web
- 基于 Spring Boot 3.x 构建
- RESTful API 设计
- 统一的异常处理和响应格式

### 2. MyBatis 数据持久化
- 使用 MyBatis 进行数据库操作
- 支持 JSON 字段存储（流程内容、Schema 等）
- Flyway 数据库迁移管理

### 3. 数据库设计
- 项目表（yglue_project）
- 流程表（yglue_flow）
- 流程版本表（yglue_flow_version）
- 流程入口点表（yglue_flow_entrypoint）
- 端点组件表（yglue_project_endpoint）
- Flow Model 表（yglue_flow_model）
- Flow Resolver 表（yglue_flow_resolver）
- 元数据表（yglue_project_metadata）
- 统计事件表（yglue_stat_event）

### 4. 版本管理
- 流程版本号自增管理
- 已发布版本标记
- 版本内容快照存储

### 5. 路径规范化
- HTTP 路径统一处理（去除首尾斜杠、统一大小写）
- HTTP 方法统一处理（统一大写）
- 路径变量支持

## 依赖关系

### 核心依赖
- `spring-boot-starter-web`: Spring Boot Web 支持
- `spring-boot-starter-jdbc`: JDBC 支持
- `spring-boot-starter-validation`: 参数验证支持
- `mybatis-spring-boot-starter`: MyBatis 集成
- `mysql-connector-j`: MySQL 数据库驱动
- `flyway-core`: 数据库迁移工具

### 被依赖场景
- `ygflow-orchestrator-ui`: 前端 UI 调用后端 API
- `yglue-idea-plugin`: IDE 插件同步规则和元数据

## API 接口

### 流程管理 API
- `GET /api/projects/{projectKey}/flows`: 查询流程列表
- `POST /api/projects/{projectKey}/flows`: 创建流程
- `GET /api/projects/{projectKey}/flows/{flowCode}/versions`: 查询流程版本列表
- `GET /api/projects/{projectKey}/flows/{flowCode}/versions/{versionNo}`: 查询指定版本
- `POST /api/projects/{projectKey}/flows/{flowCode}/publish`: 发布流程

### 入口点管理 API
- `GET /api/projects/{projectKey}/entrypoints/flows/{flowCode}`: 查询入口点
- `POST /api/projects/{projectKey}/entrypoints/flows/{flowCode}`: 创建或更新入口点

### 项目管理 API
- `GET /api/projects`: 查询项目列表
- `GET /api/projects/{projectKey}`: 查询项目详情
- `GET /api/projects/{projectKey}/endpoints`: 查询端点列表
- `GET /api/projects/{projectKey}/endpoints/rests`: 查询 REST 端点列表

### 元数据管理 API
- `GET /api/projects/{projectKey}/models`: 查询 Flow Model 列表
- `GET /api/projects/{projectKey}/resolvers`: 查询 Flow Resolver 列表
- `POST /api/projects/{projectKey}/metadata`: 上传元数据

### 插件同步 API
- `POST /api/projects/{projectKey}/plugins/heartbeat`: 插件心跳
- `GET /api/projects/{projectKey}/plugins`: 查询插件实例列表
- `POST /api/projects/{projectKey}/plugins/{instanceKey}/sync/ack`: 同步确认

## 使用场景

1. **流程设计台后端**：为可视化设计台提供流程管理能力
2. **IDE 插件后端**：为 IDE 插件提供规则同步和元数据查询能力
3. **流程发布中心**：统一管理流程的发布和版本控制
4. **元数据仓库**：存储和管理组件元数据

## 构建与打包

- **打包方式**：JAR（可执行 Spring Boot 应用）
- **Maven 坐标**：`org.yg:yglue-orchestrator:0.1.0-SNAPSHOT`
- **启动类**：`org.yglue.flow.orch.YgflowOrchestratorApplication`

## 版本兼容性

- **Java 版本**：Java 17+
- **Spring Boot 版本**：3.3.4
- **MySQL 版本**：8.0+

## 配置说明

### 数据库配置
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ygflow
    username: root
    password: password
```

### Flyway 配置
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
```

## 注意事项

1. 流程内容以 JSON 格式存储，需要注意 JSON 大小限制
2. 路径规范化逻辑需要与运行时引擎保持一致
3. 版本号管理需要保证原子性，避免并发问题
4. 规则同步需要考虑网络异常和重试机制
