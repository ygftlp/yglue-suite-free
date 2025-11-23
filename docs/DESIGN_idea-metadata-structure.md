# IDEA 插件上报数据模型结构

## 概述

IDEA 插件通过 `UploadMetadataAction` 将项目元数据上报到平台后端。上报的数据结构是一个 JSON 对象，存储在 `contentJson` 字段中。

## 边界模型设计

### 设计原则

1. **整体上报原则**：IDEA 插件以 Service 为单位整体上报，包含所有 operations
2. **职责边界清晰**：
   - **Service 层**：负责类级别信息（class, bean, name, version, description）
   - **Operation 层**：负责方法级别信息（method, params, returnType），并携带父级 service 信息
3. **数据结构边界**：
   - **上报阶段**：Service 对象包含完整的 operations 数组
   - **存储阶段**：后端只创建 SERVICE 记录，operations 信息包含在 SERVICE 的 `configJson` 中
4. **关联关系**：通过 `pathKey` 格式和 `configJson` 中的 service 信息字段建立关联

### 边界划分

```
┌─────────────────────────────────────────────────────────┐
│                    IDEA 插件层（采集）                      │
│  - 扫描 @FlowApi 注解的类                                  │
│  - 提取类级别信息（class, bean, name, version）            │
│  - 提取方法级别信息（method, params, returnType）          │
│  - 组装为 Service 对象（包含 operations 数组）             │
└─────────────────────────────────────────────────────────┘
                        ↓ 上报
┌─────────────────────────────────────────────────────────┐
│                   后端 API 层（解析）                       │
│  - 接收 Service 对象（整体）                               │
│  - 解析 Service 信息                                       │
│  - 解析 Operations 信息                                    │
│  - 为 Operation 注入父级 Service 信息                      │
└─────────────────────────────────────────────────────────┘
                        ↓ 存储
┌─────────────────────────────────────────────────────────┐
│                   数据库层（存储）                          │
│  - SERVICE 记录：类级别信息 + operations 数组（存储在 configJson 中）│
└─────────────────────────────────────────────────────────┘
                        ↓ 查询
┌─────────────────────────────────────────────────────────┐
│                   前端展示层（使用）                        │
│  - 从 SERVICE 的 configJson 中解析 operations           │
│  - 通过 configJson 中的 service 信息建立关联               │
│  - 展示两级结构：Service -> Operations                    │
└─────────────────────────────────────────────────────────┘
```

## 数据上报流程

1. **导出阶段** (`ExportMetadataAction.exportProject`)
   - 扫描项目中的所有 Java 文件
   - 提取 FlowApi、FlowModel、FlowResolver、REST 端点等信息
   - 生成 `export.json` 文件

2. **上报阶段** (`UploadMetadataAction.upload`)
   - 读取 `export.json` 文件内容
   - 通过 HTTP POST 请求发送到 `/api/projects/{projectKey}/metadata`
   - 请求体包含 `contentJson` 和 `projectName`

## 数据结构

### 根对象结构

```json
{
  "project": "项目名称",
  "services": [],   // FlowService 数组（原 apis，已重命名）
  "rests": [],      // REST 端点数组
  "models": [],     // FlowModel 数组
  "resolvers": []   // FlowResolver 数组
}
```

### 1. FlowService (services 数组)

从 `@FlowApi` 注解的类中提取。

```json
{
  "class": "com.example.UserService",           // 完整类名
  "name": "用户服务",                            // Service 显示名称（从 @FlowApi name 获取）
  "bean": "userService",                        // Bean 名称（从 Spring 注解或类名生成）
  "description": "用户相关操作",                 // 描述
  "version": "1.0.0",                           // 版本号
  "operations": [                               // 操作列表（@FlowOperation/@DevflowOperation）
    {
      "method": "getUserById",                  // 方法名
      "name": "获取用户",                        // 操作名称（从 @FlowOperation name 获取）
      "description": "根据ID获取用户信息",       // 描述
      "tags": ["用户", "查询"],                  // 标签数组
      "params": [                               // 参数列表
        {
          "name": "id",
          "type": "java.lang.Long"
        }
      ],
      "returnType": "com.example.User"          // 返回类型
    }
  ]
}
```

**Bean 名称提取规则：**
1. 优先从 `@Service` 或 `@Component` 注解的 `value` 属性获取
2. 如果没有指定 `value`，使用类名首字母小写（Spring 默认规则）
3. 如果类没有 Spring 注解，从 `@FlowApi` 的 `value` 或 `name` 获取
4. 最后使用类名首字母小写作为默认值

**注意：**
- `operations` 数组中不再包含 `flowApiBeanName`、`flowApiName`、`flowApiClass` 等冗余字段
- 这些信息在父级 `service` 对象中，后端会在处理时将 `serviceBean`、`serviceName`、`serviceClass` 添加到 operation 的 `configJson` 中，供前端使用

### 2. REST 端点 (rests 数组)

从 Spring `@RestController` 或 `@Controller` + `@ResponseBody` 的类中提取。

```json
{
  "class": "com.example.UserController",        // 完整类名
  "method": "getUser",                          // 方法名
  "path": "/api/users/{id}",                    // 完整路径（类路径 + 方法路径）
  "httpMethod": "GET",                          // HTTP 方法
  "produces": ["application/json"],             // 响应类型
  "consumes": ["application/json"],             // 请求类型
  "name": "获取用户",                            // 名称（从 JavaDoc 第一行提取，或使用方法名）
  "description": "根据ID获取用户信息\n@param id 用户ID\n@return 用户信息", // 完整 JavaDoc 描述
  "requestSchemaJson": "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\",\"x-javaType\":\"java.lang.Long\"}}}",  // 请求参数结构（JSON Schema 字符串，由 SchemaGenerator 生成）
  "responseSchema": {                           // 响应结构
    "type": "com.example.User"
  }
}
```

**名称提取规则：**
- `name`: 优先使用 JavaDoc 的第一行摘要（最多 50 字符），如果没有则使用方法名
- `description`: 使用完整的 JavaDoc 描述（不包括 @param、@return 等标签）

### 3. FlowModel (models 数组)

从 `@FlowModel` 注解的类中提取。

```json
{
  "class": "com.example.User",                 // 完整类名
  "id": "user",                                // 标识符（从 @FlowModel value 获取，或使用 name）
  "name": "用户",                               // 名称（从 @FlowModel name 获取，或使用类名）
  "description": "用户实体",                   // 描述
  "category": "实体",                          // 分类
  "version": "1.0.0",                          // 版本号
  "tags": ["用户", "实体"],                     // 标签数组
  "schema": {                                  // 模型结构（由 SchemaGenerator 生成）
    "type": "object",
    "properties": {
      "id": {
        "type": "integer",
        "x-javaType": "java.lang.Long"
      },
      "name": {
        "type": "string",
        "x-javaType": "java.lang.String"
      }
    }
  }
}
```

### 4. FlowResolver (resolvers 数组)

从 `@FlowResolver` 注解的类中提取，同时包含内置解析器。

```json
{
  "type": "REQUEST",                           // 类型（从 @FlowResolver value 获取，或使用类名）
  "name": "请求参数",                           // 名称
  "description": "从 HTTP 请求中的 path/query/header/body/form 位置提取字段", // 描述
  "category": "HTTP",                          // 分类
  "builtin": true,                             // 是否为内置解析器
  "configSchema": "{}",                        // 配置结构（JSON Schema 字符串）
  "class": "org.yglue.flow.resolvers.RequestResolver" // 完整类名
}
```

**内置解析器：**
1. `REQUEST` - 请求参数
2. `CONTEXT` - 流程上下文
3. `CONSTANT` - 常量
4. `EXPRESSION` - 表达式

## 后端处理

### 接收接口

**路径：** `POST /api/projects/{projectKey}/metadata`

**请求体：**
```json
{
  "contentJson": "{...}",  // 上述数据结构的 JSON 字符串
  "projectName": "项目名称"
}
```

### 处理逻辑 (`MetadataService.parseFlowEndpoints`)

后端**只创建 SERVICE 类型的 EndpointPayload**，operations 信息包含在 SERVICE 的 `configJson` 中：

#### SERVICE 类型的 EndpointPayload（服务本身，包含所有 operations）

```java
EndpointPayload(
    endpointType: "SERVICE",
    componentType: "BUSINESS",
    methodKey: "userService",                    // 从 service.name 或 service.class 提取
    pathKey: "com.example.UserService:1.0.0",   // serviceClass:version 格式
    displayName: "用户服务 v1.0.0",               // serviceName + " v" + version
    description: "用户相关服务",                   // 从 service.description 获取
    configJson: "{完整的 service 对象 JSON 字符串}"  // 包含完整的 service 对象，包括 operations 数组
)
```

**configJson 内容示例：**
```json
{
  "class": "com.example.UserService",
  "name": "用户服务",
  "bean": "userService",
  "version": "1.0.0",
  "description": "用户相关服务",
  "operations": [
    {
      "method": "getUserById",
      "name": "根据ID获取用户",
      "description": "通过用户ID查询用户信息",
      "params": [...],
      "returnType": "com.example.UserDto"
    }
  ]
}
```

### 存储到数据库 (`ProjectEndpointService.syncEndpoints`)

**只存储 SERVICE 记录：**

```sql
project_endpoint 表：
- endpoint_type: "SERVICE"
- component_type: "BUSINESS"
- method: "userService"              -- methodKey
- path: "com.example.UserService:1.0.0"  -- pathKey
- name: "用户服务 v1.0.0"             -- displayName
- description: "用户相关服务"
- config_json: "{完整的 service 对象 JSON，包含所有 operations}"
```

### 前端处理

前端从 SERVICE 的 `configJson` 中解析 operations：

1. **获取 SERVICE 记录**：通过 `/api/projects/{projectKey}/endpoints/components` 获取所有 SERVICE 类型的端点
2. **解析 operations**：从每个 SERVICE 的 `configJson` 中解析 `operations` 数组
3. **转换为可拖拽组件**：将每个 operation 转换为 `EndpointComponent`，包含：
   - `method`: operation.method
   - `name`: operation.name
   - `description`: operation.description
   - `configJson`: operation 对象 + service 信息（serviceBean, serviceName, serviceClass）
   - `endpointType`: "FLOW_OPERATION"（用于标识这是操作方法，可拖拽）
4. **展示两级结构**：Service（类别）-> Operations（可拖拽的操作方法）

### 关键设计点

1. **整体上报**：IDEA 插件以 Service 为单位整体上报，包含所有 operations
2. **统一存储**：后端只存储 SERVICE 记录，operations 信息包含在 `configJson` 中
3. **前端解析**：前端从 SERVICE 的 `configJson` 中解析 operations，避免数据冗余
4. **边界清晰**：
   - **Service 层**：类级别信息（class, bean, name, version, description）+ operations 数组
   - **Operation 层**：方法级别信息（method, params, returnType），由前端从 configJson 中解析
5. **优势**：
   - 数据不冗余：operations 信息只存储一次
   - 结构简单：只需要维护 SERVICE 记录
   - 易于扩展：新增 operation 只需更新 SERVICE 的 configJson

## 数据流转

### 完整数据流转链路

```
┌─────────────────────────────────────────────────────────────┐
│ 1. IDEA 插件采集阶段                                         │
│    - 扫描 @FlowApi 注解的类                                  │
│    - 提取类级别信息（class, bean, name, version）            │
│    - 提取方法级别信息（method, params, returnType）          │
│    - 组装为 Service 对象（包含 operations 数组）             │
│    输出：export.json（包含完整的 Service 对象）               │
└─────────────────────────────────────────────────────────────┘
                        ↓ HTTP POST
┌─────────────────────────────────────────────────────────────┐
│ 2. 后端接收阶段 (MetadataService.save)                      │
│    - 接收 contentJson（包含 services 数组）                 │
│    - 解析 JSON 对象                                          │
│    - 调用 parseFlowEndpoints 处理 services                  │
└─────────────────────────────────────────────────────────────┘
                        ↓ 解析
┌─────────────────────────────────────────────────────────────┐
│ 3. 后端解析阶段 (MetadataService.parseFlowEndpoints)        │
│    对于每个 Service 对象：                                    │
│    a) 创建 SERVICE 类型的 EndpointPayload                   │
│       - endpointType: "SERVICE"                             │
│       - configJson: 完整的 service 对象 JSON（包含 operations 数组）│
│    b) 只创建 SERVICE 记录，不创建 FLOW_OPERATION 记录        │
└─────────────────────────────────────────────────────────────┘
                        ↓ 存储
┌─────────────────────────────────────────────────────────────┐
│ 4. 数据库存储阶段 (ProjectEndpointService.syncEndpoints)    │
│    - 将 EndpointPayload 转换为 ProjectEndpoint 实体         │
│    - 存储到 project_endpoint 表                             │
│    - 1 个 Service → 1 个 SERVICE 记录（operations 包含在 configJson 中）│
└─────────────────────────────────────────────────────────────┘
                        ↓ 查询
┌─────────────────────────────────────────────────────────────┐
│ 5. 前端展示阶段                                              │
│    - 通过 /api/projects/{projectKey}/endpoints/components   │
│      获取端点列表                                            │
│    - 从 SERVICE 的 configJson 中解析 operations             │
│    - 通过 configJson 中的 service 信息建立关联               │
│    - 展示两级结构：Service -> Operations                    │
└─────────────────────────────────────────────────────────────┘
```

### 数据结构转换示例

**IDEA 插件上报（整体）：**
```json
{
  "services": [
    {
      "class": "com.example.UserService",
      "name": "用户服务",
      "bean": "userService",
      "version": "1.0.0",
      "operations": [
        { "method": "getUserById", "name": "获取用户", ... },
        { "method": "createUser", "name": "创建用户", ... }
      ]
    }
  ]
}
```

**后端解析后（只创建 SERVICE）：**
```java
// 只创建 SERVICE 记录，operations 包含在 configJson 中
EndpointPayload("SERVICE", "BUSINESS", "userService", 
                "com.example.UserService:1.0.0", 
                "用户服务 v1.0.0", "...", 
                "{完整 service JSON，包含 operations 数组}")
```

**数据库存储：**
```sql
-- 只存储 SERVICE 记录，operations 包含在 config_json 中
INSERT INTO project_endpoint (endpoint_type, method, path, name, config_json)
VALUES ('SERVICE', 'userService', 'com.example.UserService:1.0.0', '用户服务 v1.0.0', 
        '{"class":"com.example.UserService","name":"用户服务","bean":"userService","operations":[...]}');
```

**前端解析：**
```javascript
// 前端从 SERVICE 的 configJson 中解析 operations
const serviceConfig = JSON.parse(serviceItem.configJson)
const operations = serviceConfig.operations.map(op => ({
  method: op.method,
  name: op.name,
  configJson: JSON.stringify({
    ...op,
    serviceBean: serviceConfig.bean,
    serviceName: serviceConfig.name,
    serviceClass: serviceConfig.class
  })
  // 注意：不再使用 endpointType 字段
}))
```

## 边界模型设计总结

### 核心设计原则

1. **整体上报，统一存储**
   - IDEA 插件以 Service 为单位整体上报，保持数据的完整性和一致性
   - 后端只存储 SERVICE 记录，operations 信息包含在 `configJson` 中，避免数据冗余

2. **职责边界清晰**
   - **Service 层**：负责类级别信息（class, bean, name, version, description）+ operations 数组
   - **Operation 层**：负责方法级别信息（method, params, returnType），由前端从 configJson 中解析

3. **数据结构边界**
   - **上报数据结构**：Service 对象包含完整的 operations 数组
   - **存储数据结构**：只存储 SERVICE 记录，operations 包含在 `configJson` 中
   - **前端数据结构**：前端从 SERVICE 的 `configJson` 中解析 operations，转换为可拖拽的组件

4. **关联关系设计**
   - **configJson 字段**：SERVICE 的 `configJson` 包含完整的 service 对象，包括所有 operations
   - **前端解析**：前端解析 operations 时，为每个 operation 添加 `serviceBean`、`serviceName`、`serviceClass` 信息

### 边界模型优势

1. **数据不冗余**：operations 信息只存储一次，避免数据重复
2. **结构简单**：只需要维护 SERVICE 记录，不需要创建额外的 FLOW_OPERATION 记录
3. **扩展性**：新增采集方式时，只需在采集阶段识别新注解，存储阶段统一使用 SERVICE 类型
4. **一致性**：所有服务统一为 SERVICE 类型，前端和后端处理逻辑统一
5. **清晰性**：`endpointType` 反映业务含义（SERVICE），而非技术实现细节（@FlowApi）
6. **可维护性**：边界清晰，职责明确，便于后续维护和扩展

## 注意事项

1. **Bean 名称一致性**：Service 的 `bean` 必须与实际 Spring Bean 名称一致，否则运行时无法找到对应的服务
2. **路径规范化**：REST 端点的路径会自动规范化（去除重复斜杠、确保以 `/` 开头）
3. **JavaDoc 提取**：REST 端点的名称和描述从 JavaDoc 中提取，建议为方法添加完整的 JavaDoc 注释
4. **Schema 生成**：`requestSchemaJson` 和 `schema` 由 `SchemaGenerator` 自动生成，包含类型信息和 `x-javaType` 扩展字段
5. **Service 整体性**：IDEA 插件上报时，Service 必须包含至少一个 operation，否则不会被上报

