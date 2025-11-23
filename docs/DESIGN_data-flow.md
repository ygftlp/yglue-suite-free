# 数据流梳理文档

## 概述
本文档梳理从 IDEA 插件上报数据到规则文件保存，再到运行时执行的完整数据流。

## 1. IDEA 插件导出数据结构

### 1.1 FlowApi 类导出结构
```json
{
  "class": "org.example.TestService",
  "name": "测试服务",           // @FlowApi 的 name，用于显示
  "beanName": "testService",    // Spring bean 名称（从 @Service/@Component 获取）
  "description": "测试服务描述",
  "version": "1.0.0",
  "operations": [
    {
      "method": "测试",          // 实际 Java 方法名（m.getName()）
      "name": "测试方法",        // @FlowOperation 的 name，用于显示
      "description": "方法描述",
      "flowApiBeanName": "testService",  // 所属 FlowApi 的 bean 名称
      "flowApiName": "测试服务",         // 所属 FlowApi 的显示名称
      "flowApiClass": "org.example.TestService",  // 所属 FlowApi 的类名
      "params": [...],
      "returnType": "String"
    }
  ]
}
```

### 1.2 关键字段说明
- **`method`**: 实际 Java 方法名（`m.getName()`），用于运行时查找方法
- **`name`**: 显示名称（来自 `@FlowOperation(name = "...")`），仅用于前端显示
- **`beanName`**: Spring bean 名称（从 `@Service/@Component` 获取），用于运行时获取 bean
- **`flowApiBeanName`**: 所属 FlowApi 的 bean 名称，用于运行时获取 bean

## 2. 后端处理流程

### 2.1 MetadataService 处理

后端**只创建 SERVICE 类型的端点**，operations 信息包含在 SERVICE 的 `configJson` 中：

```java
// 从 IDEA 导出的 JSON 中解析 Service 对象
JsonNode serviceNode = ...;  // 包含完整的 service 对象，包括 operations 数组

// 只创建 SERVICE 记录，operations 信息包含在 configJson 中
endpoints.add(new EndpointPayload(
    "SERVICE",
    "BUSINESS",
    serviceMethodKey,        // 从 service.name 或 service.class 提取
    servicePathKey,          // serviceClass:version 格式
    serviceDisplay,          // serviceName + " v" + version
    serviceDescription,
    serviceNode.toString()   // 完整的 service 对象 JSON，包含所有 operations
));
```

### 2.2 后端返回给前端的数据结构

**SERVICE 类型的端点：**
```json
{
  "id": 1,
  "name": "测试服务 v1.0.0",        // Service 显示名称
  "method": "testService",          // Service methodKey
  "path": "org.example.TestService:1.0.0",
  "configJson": "{                 // 完整的 Service 对象，包含 operations 数组
    \"class\": \"org.example.TestService\",
    \"name\": \"测试服务\",
    \"bean\": \"testService\",
    \"version\": \"1.0.0\",
    \"description\": \"测试服务描述\",
    \"operations\": [
      {
        \"method\": \"测试\",          // 实际方法名
        \"name\": \"测试方法\",        // 显示名称
        \"description\": \"方法描述\",
        \"params\": [...],
        \"returnType\": \"String\"
      }
    ]
  }",
  "endpointType": "SERVICE"
}
```

**前端从 SERVICE 的 configJson 中解析 operations：**
```javascript
// 前端解析 operations
const serviceConfig = JSON.parse(serviceItem.configJson);
const operations = serviceConfig.operations.map(op => ({
  method: op.method,              // 实际方法名
  name: op.name,                  // 显示名称
  configJson: JSON.stringify({
    ...op,
    serviceBean: serviceConfig.bean,      // 添加 service 信息
    serviceName: serviceConfig.name,
    serviceClass: serviceConfig.class
  })
  // 注意：不再使用 endpointType: "FLOW_OPERATION"，前端通过其他方式标识可拖拽的操作方法
}));
```

## 3. 前端保存到规则文件

### 3.1 拖拽组件时保存的数据结构

**从 SERVICE 的 operations 中拖拽：**
```javascript
{
  type: "service",  // 节点类型为 service（不再是 task）
  data: {
    comp: {
      bean: operationConfig.serviceBean,  // 从解析后的 operation configJson 获取
      method: operationConfig.method,     // 实际方法名（从 operation.method 获取）
      configJson: JSON.stringify({        // 完整的 operation 元数据 + service 信息
        ...operationConfig,
        serviceBean: serviceConfig.bean,
        serviceName: serviceConfig.name,
        serviceClass: serviceConfig.class
      })
      // 注意：不再使用 endpointType 字段
    },
    inputs: [...],
    output: {...},
    label: "..."
  }
}
```

### 3.2 规则文件中的节点结构
```json
{
  "nodes": [
    {
      "id": "node_1",
      "type": "service",  // 节点类型为 service（不再是 task）
      "data": {
        "comp": {
          "bean": "testService",     // Service bean 名称（从 configJson.serviceBean 获取）
          "method": "测试",          // 实际方法名（从 configJson.method 获取）
          "configJson": "{           // 完整的 operation 元数据 + service 信息
            \"method\": \"测试\",
            \"name\": \"测试方法\",
            \"description\": \"方法描述\",
            \"serviceBean\": \"testService\",
            \"serviceName\": \"测试服务\",
            \"serviceClass\": \"org.example.TestService\",
            \"params\": [...],
            \"returnType\": \"String\"
          }"
          // 注意：不再使用 endpointType 字段
        },
        "inputs": [...],
        "output": {...},
        "label": "..."
      }
    }
  ],
  "edges": [...]
}
```

## 4. 运行时执行流程

### 4.1 FlowLoader 解析规则文件
```java
// 从规则文件中解析节点
NodeDefinition node = parseNode(ruleId, index, nodeJson);

// 提取 data 字段中的内容到 config
Map<String, Object> config = JsonUtils.toMap(node.get("data"));
// config 包含: comp, inputs, output, label 等
```

### 4.2 ServiceNodeExecutor 执行节点
```java
// 从 config 中获取 comp
Map<String, Object> comp = (Map<String, Object>) config.get("comp");

// 获取 bean 名称（优先级）
String beanName = getBeanName(comp);
// 1. comp.bean（直接指定）
// 2. comp.configJson.serviceBean（从 configJson 中解析，优先）
// 3. comp.configJson.serviceName（回退）

// 获取方法名（优先级）
String methodName = getMethodName(comp);
// 1. comp.configJson.method（优先，实际方法名）
// 2. comp.method（回退，可能是显示名称）

// 从 Spring 容器获取 bean
Object bean = applicationContext.getBean(beanName);

// 查找方法（使用实际类型，处理代理类）
Method method = resolveMethod(targetClass, methodName, arguments.size());

// 调用方法
Object result = method.invoke(bean, convertedArgs);
```

## 5. 数据流总结

### 5.1 关键字段的流转
| 字段 | IDEA 导出 | 后端保存 | 前端保存 | 运行时读取 |
|------|----------|---------|---------|-----------|
| **实际方法名** | `operations[].method` | `SERVICE.configJson.operations[].method` | `comp.method` (从 operation 提取) | `comp.configJson.method` (优先) |
| **显示名称** | `operations[].name` | `SERVICE.configJson.operations[].name` | - | - |
| **Service Bean** | `service.bean` | `SERVICE.configJson.bean` | `comp.configJson.serviceBean` (前端解析时添加) | `comp.configJson.serviceBean` (优先) |

### 5.2 最佳实践
1. **实际方法名**：始终从 `configJson.method` 获取，确保是真实的 Java 方法名
2. **Service Bean 名称**：优先从 `configJson.serviceBean` 获取，确保是 Spring bean 名称
3. **显示名称**：仅用于前端显示，不参与运行时逻辑
4. **configJson**：保存完整的 operation 元数据 + service 信息，确保运行时能获取所有必要信息
5. **SERVICE 存储**：后端只存储 SERVICE 记录，operations 包含在 `configJson` 中，避免数据冗余

## 6. 问题修复记录

### 6.1 方法名获取优化
- **问题**：`comp.method` 可能是显示名称，而不是实际方法名
- **修复**：运行时优先从 `configJson.method` 获取实际方法名

### 6.2 Bean 名称获取优化
- **问题**：`comp.bean` 可能为空或不准确
- **修复**：运行时优先从 `configJson.flowApiBeanName` 获取 bean 名称

### 6.3 后端 methodKey 优化
- **问题**：`methodKey` 优先使用显示名称，导致运行时找不到方法
- **修复**：`methodKey` 优先使用实际方法名（`opMethod`）

## 7. 数据结构规范

### 7.1 规则文件节点规范
```json
{
  "type": "service",  // 节点类型为 service（不再是 task）
  "data": {
    "comp": {
      "bean": "可选，运行时优先从 configJson 获取",
      "method": "实际方法名（从 configJson.method 提取）",
      "configJson": "完整的 FlowOperation 元数据 JSON 字符串（包含 service 信息）"
      // 注意：不再使用 endpointType 字段
    },
    "inputs": [...],
    "output": {...}
  }
}
```

### 7.2 configJson 规范

**节点中的 configJson（operation 元数据 + service 信息）：**
```json
{
  "method": "实际 Java 方法名（必需）",
  "name": "显示名称（可选）",
  "description": "方法描述（可选）",
  "serviceBean": "Spring bean 名称（必需）",
  "serviceName": "Service 显示名称（可选）",
  "serviceClass": "Service 类名（可选）",
  "params": [...],
  "returnType": "..."
}
```

**SERVICE 端点中的 configJson（完整的 service 对象）：**
```json
{
  "class": "完整类名",
  "name": "Service 显示名称",
  "bean": "Spring bean 名称",
  "version": "版本号",
  "description": "描述",
  "operations": [
    {
      "method": "方法名",
      "name": "显示名称",
      "params": [...],
      "returnType": "..."
    }
  ]
}
```




