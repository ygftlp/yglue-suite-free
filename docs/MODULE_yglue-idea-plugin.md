# yglue-idea-plugin 模块文档

## 模块概述

`yglue-idea-plugin` 是 YGFlow Suite 的 IntelliJ IDEA 插件模块，为开发人员提供 IDE 集成能力，包括元数据扫描、规则文件同步、自动上传等功能。该插件是开发阶段的重要工具，实现了 IDE 与编排平台的无缝集成。

## 模块职责

- **元数据扫描**：扫描项目中的注解，生成 Flow API、Flow Model、Flow Resolver 等元数据
- **规则文件同步**：从编排平台下载流程规则文件到本地
- **元数据上传**：将扫描到的元数据上传到编排平台
- **自动同步**：定时检查规则更新并自动同步
- **配置管理**：管理插件配置（服务器地址、项目标识等）

## 核心能力

### 1. 元数据扫描

**核心类：**
- `SchemaGenerator`: JSON Schema 生成器
- `YgflowConfigurationResolver`: 配置解析器

**主要功能：**
- 扫描 `@FlowApi` 注解，生成服务元数据
- 扫描 `@FlowModel` 注解，生成模型元数据
- 扫描 `@FlowResolver` 注解，生成解析器元数据
- 解析 Controller 方法，生成 REST 端点 Schema
- 提取 Bean Validation 注解，生成验证规则

### 2. 元数据上传

**核心类：**
- `UploadMetadataAction`: 上传元数据 Action
- `MetadataAutoUploadScheduler`: 自动上传调度器
- `MetadataChangeListener`: 元数据变更监听器

**主要功能：**
- 手动触发元数据上传
- 文件变更时自动上传
- 批量上传项目元数据
- 上传进度提示

### 3. 规则文件同步

**核心类：**
- `SyncRulesAction`: 同步规则 Action
- `RuleSyncService`: 规则同步服务
- `RuleAutoSyncScheduler`: 自动同步调度器

**主要功能：**
- 手动触发规则同步
- 定时检查规则更新
- 下载规则文件到本地
- 本地文件存在性检查
- 同步确认机制

### 4. 插件心跳

**核心类：**
- `PluginHeartbeatScheduler`: 心跳调度器

**主要功能：**
- 定时向服务器发送心跳
- 报告插件实例信息（IDE 类型、版本、主机 IP 等）
- 接收服务器同步指令

### 5. 配置管理

**核心类：**
- `YgflowSettingsState`: 配置状态管理
- `YgflowSettingsConfigurable`: 配置界面

**主要功能：**
- 服务器地址配置
- 项目标识配置
- 自动上传开关配置
- 自动同步开关配置
- 配置持久化存储

### 6. 启动活动

**核心类：**
- `MetadataAutoUploadStartupActivity`: 启动时自动上传活动

**主要功能：**
- IDE 启动时自动上传元数据
- 确保元数据及时同步

## 技术特性

### 1. IntelliJ Platform SDK
- 基于 IntelliJ Platform SDK 开发
- 支持 IntelliJ IDEA 2024.2+
- 使用 Gradle 构建

### 2. PSI (Program Structure Interface)
- 使用 PSI API 解析 Java 代码
- 支持注解扫描和类型解析
- 支持方法签名解析

### 3. 后台任务
- 使用 `BackgroundTask` 执行耗时操作
- 避免阻塞 UI 线程
- 提供进度提示

### 4. 文件监听
- 使用 `BulkFileListener` 监听文件变更
- 自动触发元数据上传
- 支持批量文件处理

### 5. 持久化存储
- 使用 `PersistentStateComponent` 存储配置
- 配置自动保存和恢复

## 依赖关系

### 核心依赖
- `org.jetbrains.intellij`: IntelliJ Platform SDK
- `org.json:json`: JSON 处理

### 被依赖场景
- IntelliJ IDEA 插件市场
- 开发人员本地 IDE 环境

## 使用场景

1. **开发阶段**：扫描项目代码，生成元数据并上传到平台
2. **规则同步**：从平台下载流程规则文件，在本地查看和编辑
3. **自动化工作流**：配置自动上传和同步，减少手动操作
4. **团队协作**：确保团队成员使用最新的规则和元数据

## 安装与配置

### 安装方式
1. **本地构建安装**：
   ```bash
   cd yglue-idea-plugin
   ./gradlew buildPlugin
   ```
   然后在 IDEA 中通过 `File → Settings → Plugins → Install Plugin from Disk...` 安装

2. **插件市场安装**：从内部插件市场安装（如果已发布）

### 配置步骤
1. 打开 `File → Settings → Tools → YGFlow Settings`
2. 配置服务器地址（如：`http://localhost:8080`）
3. 配置项目标识（如：`myProject`）
4. 启用自动上传和自动同步（可选）

## 功能使用

### 1. 上传元数据
- **手动上传**：右键项目 → `YGFlow → Upload Metadata`
- **自动上传**：文件保存时自动上传（需启用自动上传）

### 2. 同步规则
- **手动同步**：右键项目 → `YGFlow → Sync Rules`
- **自动同步**：定时检查并同步（需启用自动同步）

### 3. 导出元数据
- 右键项目 → `YGFlow → Export Metadata`
- 导出为 JSON 文件，可用于备份或迁移

## 构建与打包

- **构建工具**：Gradle
- **插件版本**：兼容 IntelliJ IDEA 2024.2+
- **Java 版本**：Java 17+

### 构建命令
```bash
# 构建插件
./gradlew buildPlugin

# 运行测试
./gradlew runIde

# 打包发布
./gradlew publishPlugin
```

## 版本兼容性

- **IntelliJ IDEA 版本**：2024.2+
- **Java 版本**：Java 17+
- **Gradle 版本**：8.x

## 注意事项

1. 插件需要网络访问编排平台服务器
2. 元数据扫描可能耗时，建议在后台执行
3. 规则文件同步需要考虑文件冲突问题
4. 配置变更后需要重启 IDE 才能生效（部分配置）
5. 自动上传和同步功能可能增加服务器负载，需谨慎使用
