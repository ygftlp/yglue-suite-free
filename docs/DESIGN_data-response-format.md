# 数据响应格式配置

## 1. 概述

不同系统的 REST 响应结构可能不同，例如：
- **A 系统**：`{errorCode, message, data}`
- **B 系统**：`{ret, msg, result}`
- **C 系统**：`{错误码, 错误信息, 数据}`

为了支持这种差异，系统提供了可配置的数据响应格式功能。该功能不仅用于错误响应，也用于成功响应的格式化。

## 2. 配置方式

### 2.1 预设格式

系统提供了标准格式（standard）：

#### 标准格式（standard）

**成功响应：**
```json
{
  "errorCode": 1,
  "message": "操作成功",
  "data": {
    "id": 1,
    "name": "张三",
    "email": "zhangsan@example.com"
  }
}
```

**错误响应：**
```json
{
  "errorCode": -1,
  "message": "参数 'userId' 校验失败"
}
```

**说明：**
- `errorCode` 字段：错误码（失败时为 -1，成功时为 1）
- `message` 字段：错误消息，已经包含了足够的错误信息
- `data` 字段：**只在成功响应时存在**，包含业务数据；错误响应时**不包含** `data` 字段

**注意：** 系统已移除简化格式（simple）和中文格式（chinese）预设，只保留标准格式。如需自定义格式，请使用自定义配置。

### 2.2 自定义格式

支持完全自定义字段名和自定义字段：

```json
{
  "errorCodeField": "ret",
  "messageField": "msg",
  "customFields": [  // 自定义字段列表
    {
      "fieldName": "timestamp"
    },
    {
      "fieldName": "requestId"
    }
  ]
}
```

**成功响应示例：**
```json
{
  "ret": 1,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "name": "张三",
    "email": "zhangsan@example.com"
  },
  "timestamp": "2024-01-01T00:00:00Z",
  "requestId": "req-123"
}
```

**错误响应示例：**
```json
{
  "ret": -1,
  "msg": "参数 'userId' 校验失败",
  "timestamp": "2024-01-01T00:00:00Z",
  "requestId": "req-123"
}
```

**注意：** 
- `data` 字段：
  - **成功响应时**：包含业务数据（如服务返回的 user 对象）
  - **错误响应时**：**不包含** `data` 字段（而不是空对象）
- `message` 字段：已经包含了足够的错误信息
- `customFields` 中的字段名可以自定义，字段值需要在运行时通过表达式计算

## 3. 配置位置

### 3.1 FlowEntryPoint 配置

在 `FlowEntryPoint` 实体中添加 `dataResponseFormat` 字段：

```java
public class FlowEntryPoint {
    // ... 其他字段
    
    /**
     * 数据响应格式配置
     * 支持预设名称（如 "standard"）或自定义配置
     */
    private String dataResponseFormat;  // JSON 字符串或预设名称
}
```

**注意：** 字段名已从 `errorResponseFormat` 重命名为 `dataResponseFormat`，以反映其不仅用于错误响应，也用于成功响应。

### 3.2 数据库配置

在数据库中存储配置：

**方式1：预设名称**
```sql
UPDATE flow_entry_point 
SET data_response_format = 'standard' 
WHERE id = 1;
```

**方式2：自定义配置（JSON）**
```sql
UPDATE flow_entry_point 
SET data_response_format = '{"errorCodeField":"ret","messageField":"msg","customFields":[{"fieldName":"timestamp"}]}' 
WHERE id = 2;
```

### 3.3 前端配置

在前端流程设置中配置：

```typescript
interface FlowEntrypoint {
  path: string
  method: string
  dataResponseFormat?: string | {
    errorCodeField: string
    messageField: string
    customFields?: Array<{
      fieldName: string
    }>
  }
}
```

## 4. 使用示例

### 4.1 使用预设格式

**配置：**
```json
{
  "dataResponseFormat": "standard"
}
```

**成功响应示例：**
```json
{
  "errorCode": 1,
  "message": "操作成功",
  "data": {
    "id": 1,
    "name": "张三",
    "email": "zhangsan@example.com"
  }
}
```

**错误响应示例：**
```json
{
  "errorCode": -1,
  "message": "参数 'userId' 不能为空"
}
```

### 4.2 使用自定义格式

**配置：**
```json
{
  "dataResponseFormat": {
    "errorCodeField": "ret",
    "messageField": "msg",
    "customFields": [
      {
        "fieldName": "timestamp"
      }
    ]
  }
}
```

**错误响应示例：**
```json
{
  "ret": -1,
  "msg": "参数 'email' 校验失败：不能为空，格式不正确",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

**成功响应示例：**
```json
{
  "ret": 1,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "name": "张三",
    "email": "zhangsan@example.com"
  },
  "timestamp": "2024-01-01T00:00:00Z"
}
```

### 4.3 最小化格式（只包含错误码、消息和数据）

**配置：**
```json
{
  "dataResponseFormat": {
    "errorCodeField": "code",
    "messageField": "message"
  }
}
```

**成功响应示例：**
```json
{
  "code": 1,
  "message": "操作成功",
  "data": {
    "id": 1,
    "name": "张三"
  }
}
```

**错误响应示例：**
```json
{
  "code": -1,
  "message": "参数 'userId' 校验失败"
}
```

**注意：** 
- `data` 字段：
  - **成功响应时**：包含业务数据（如 `{ "id": 1, "name": "张三" }`）
  - **错误响应时**：**不包含** `data` 字段（而不是空对象）

## 5. 实现细节

### 5.1 ErrorResponseFormat 类

```java
public class ErrorResponseFormat {
    // 预设格式（只保留标准格式）
    public static final ErrorResponseFormat STANDARD = ...;
    
    // 从配置创建
    public static ErrorResponseFormat fromConfig(Map<String, Object> config);
    
    // 从预设名称创建
    public static ErrorResponseFormat fromPreset(String presetName);
}
```

**注意：** 已移除 `SIMPLE` 和 `CHINESE` 预设格式，只保留 `STANDARD`。

### 5.2 ValidationException 转换

```java
public class ValidationException extends RuntimeException {
    // 转换为标准格式
    public Map<String, Object> toErrorBody();
    
    // 转换为指定格式
    public Map<String, Object> toErrorBody(ErrorResponseFormat format);
}
```

### 5.3 FlowOrchestratedAspect 处理

```java
@Aspect
public class FlowOrchestratedAspect {
    private void writeValidationError(
        HttpServletResponse response,
        ValidationException validationException,
        RestEntryPoint entryPoint
    ) {
        // 获取数据响应格式
        ErrorResponseFormat format = entryPoint.getDataResponseFormat();
        
        // 转换为指定格式并写入响应
        Map<String, Object> errorBody = validationException.toErrorBody(format);
        objectMapper.writeValue(response.getWriter(), errorBody);
    }
}
```

**注意：** 方法名已从 `getErrorResponseFormat()` 更新为 `getDataResponseFormat()`。

## 6. 配置优先级

1. **FlowEntryPoint.dataResponseFormat**：入口点级别的配置（最高优先级）
2. **默认标准格式**：如果未配置，使用标准格式 `{errorCode, message, data}`

## 7. 注意事项

1. **字段名唯一性**：确保自定义字段名不与业务字段冲突
2. **向后兼容**：未配置时默认使用标准格式，保证向后兼容
3. **类型安全**：配置可以是字符串（预设名称）或对象（自定义配置）

## 8. 扩展性

如果需要支持更多预设格式，可以在 `ErrorResponseFormat` 类中添加：

```java
/** 自定义格式：{status, message, data} */
public static final ErrorResponseFormat STATUS = new ErrorResponseFormat(
    "status", "message", null, "data", 1, -1
);
```

然后在 `fromPreset` 方法中添加对应的 case。

**注意：** 建议优先使用自定义格式配置，而不是添加新的预设格式。


