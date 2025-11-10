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
```java
// 从 IDEA 导出的 JSON 中解析
String opMethod = textOrDefault(opNode, "method", "");  // 实际方法名
String opName = textOrDefault(opNode, "name", "");      // 显示名称

// methodKey 应该使用实际方法名，而不是显示名称
String opMethodKey = !opMethod.isBlank() ? opMethod : opName;

// 保存到数据库的 configJson 中确保包含 flowApiBeanName
opConfig.put("flowApiBeanName", flowApiBeanName);
opConfig.put("flowApiName", flowApiName);
opConfig.put("flowApiClass", flowApiClassValue);
```

### 2.2 后端返回给前端的数据结构
```json
{
  "id": 1,
  "name": "测试方法",              // 显示名称（用于前端显示）
  "method": "测试",                // 实际方法名（用于运行时）
  "path": "org.example.TestService#测试",
  "configJson": "{                 // 完整的 FlowOperation 元数据
    \"method\": \"测试\",          // 实际方法名
    \"name\": \"测试方法\",        // 显示名称
    \"flowApiBeanName\": \"testService\",
    \"flowApiName\": \"测试服务\",
    \"flowApiClass\": \"org.example.TestService\",
    \"params\": [...],
    \"returnType\": \"String\"
  }",
  "endpointType": "FLOW_OPERATION"
}
```

## 3. 前端保存到规则文件

### 3.1 拖拽组件时保存的数据结构
```javascript
{
  type: "task",
  data: {
    comp: {
      bean: item.bean,              // 可能为空，优先从 configJson 获取
      method: (() => {               // 优先从 configJson.method 获取实际方法名
        const config = JSON.parse(item.configJson);
        return config.method || item.method;
      })(),
      configJson: item.configJson,   // 完整的元数据 JSON 字符串
      endpointType: item.endpointType
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
      "type": "task",
      "data": {
        "comp": {
          "bean": null,              // 可能为空，运行时从 configJson 获取
          "method": "测试",          // 实际方法名（从 configJson.method 提取）
          "configJson": "{           // 完整的元数据
            \"method\": \"测试\",
            \"name\": \"测试方法\",
            \"flowApiBeanName\": \"testService\",
            \"flowApiName\": \"测试服务\",
            \"flowApiClass\": \"org.example.TestService\",
            \"params\": [...],
            \"returnType\": \"String\"
          }",
          "endpointType": "FLOW_OPERATION"
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

### 4.2 TaskNodeExecutor 执行节点
```java
// 从 config 中获取 comp
Map<String, Object> comp = (Map<String, Object>) config.get("comp");

// 获取 bean 名称（优先级）
String beanName = getBeanName(comp);
// 1. comp.bean（直接指定）
// 2. comp.flowApiBeanName（如果规则文件中直接保存了该字段）
// 3. comp.configJson.flowApiBeanName（从 configJson 中解析）
// 4. comp.configJson.flowApiName（回退）

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
| **实际方法名** | `method` | `configJson.method` | `comp.method` (从 `configJson.method` 提取) | `comp.configJson.method` (优先) |
| **显示名称** | `name` | `name` (数据库字段) | - | - |
| **Bean 名称** | `flowApiBeanName` | `configJson.flowApiBeanName` | `comp.configJson` (包含) | `comp.configJson.flowApiBeanName` (优先) |

### 5.2 最佳实践
1. **实际方法名**：始终从 `configJson.method` 获取，确保是真实的 Java 方法名
2. **Bean 名称**：优先从 `configJson.flowApiBeanName` 获取，确保是 Spring bean 名称
3. **显示名称**：仅用于前端显示，不参与运行时逻辑
4. **configJson**：保存完整的元数据，确保运行时能获取所有必要信息

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
  "type": "task",
  "data": {
    "comp": {
      "bean": "可选，运行时优先从 configJson 获取",
      "method": "实际方法名（从 configJson.method 提取）",
      "configJson": "完整的 FlowOperation 元数据 JSON 字符串",
      "endpointType": "FLOW_OPERATION"
    },
    "inputs": [...],
    "output": {...}
  }
}
```

### 7.2 configJson 规范
```json
{
  "method": "实际 Java 方法名（必需）",
  "name": "显示名称（可选）",
  "flowApiBeanName": "Spring bean 名称（必需）",
  "flowApiName": "FlowApi 显示名称（可选）",
  "flowApiClass": "FlowApi 类名（可选）",
  "params": [...],
  "returnType": "..."
}
```

