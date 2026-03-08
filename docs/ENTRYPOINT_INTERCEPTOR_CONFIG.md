# 入口拦截器配置说明（REST）

## 1. 配置位置

在流程 `entrypoint` 下新增字段：

```json
{
  "path": "/api/user/query",
  "method": "POST",
  "enabled": true,
  "inboundInterceptors": []
}
```

`inboundInterceptors` 为数组，按 `order` 升序执行。

## 2. 通用结构

```json
[
  {
    "code": "schemaNormalize",
    "enabled": true,
    "order": 400,
    "config": {}
  }
]
```

- `code`: 拦截器编码
- `enabled`: 是否启用
- `order`: 执行顺序，数字越小越先执行
- `config`: 拦截器配置对象

## 3. 内置拦截器

### 3.1 `trace`

注入链路追踪 ID 到请求上下文。

```json
{
  "code": "trace",
  "enabled": true,
  "order": 100,
  "config": {
    "traceHeader": "X-Trace-Id",
    "outputPath": "request.traceId"
  }
}
```

### 3.2 `auth`

注入登录用户信息到请求上下文。

```json
{
  "code": "auth",
  "enabled": true,
  "order": 200,
  "config": {
    "required": true,
    "principalPath": "request.auth.user",
    "userIdPath": "request.auth.userId"
  }
}
```

### 3.3 `multipart`

解析 `multipart/*` 请求，将文本字段和文件字段写入上下文。

```json
{
  "code": "multipart",
  "enabled": true,
  "order": 300,
  "config": {
    "maxFileSizeMb": 20,
    "maxFileCount": 10,
    "allowedContentTypes": ["image/png", "image/jpeg"],
    "formPath": "request.form",
    "filesPath": "request.files"
  }
}
```

### 3.4 `schemaNormalize`

按入口 `requestSchema` 做校验+归一化，输出到目标路径。

```json
{
  "code": "schemaNormalize",
  "enabled": true,
  "order": 400,
  "config": {
    "outputPath": "request.params"
  }
}
```

如果未配置任何拦截器，运行时默认执行 `schemaNormalize`。

## 4. 推荐顺序

建议顺序：

1. `trace`（100）
2. `auth`（200）
3. `multipart`（300）
4. `schemaNormalize`（400）

## 5. 典型完整示例

```json
{
  "path": "/api/user/upload",
  "method": "POST",
  "enabled": true,
  "inboundInterceptors": [
    {
      "code": "trace",
      "enabled": true,
      "order": 100,
      "config": {
        "traceHeader": "X-Trace-Id",
        "outputPath": "request.traceId"
      }
    },
    {
      "code": "auth",
      "enabled": true,
      "order": 200,
      "config": {
        "required": true,
        "principalPath": "request.auth.user",
        "userIdPath": "request.auth.userId"
      }
    },
    {
      "code": "multipart",
      "enabled": true,
      "order": 300,
      "config": {
        "maxFileSizeMb": 20,
        "maxFileCount": 5,
        "allowedContentTypes": ["image/png", "image/jpeg"],
        "formPath": "request.form",
        "filesPath": "request.files"
      }
    },
    {
      "code": "schemaNormalize",
      "enabled": true,
      "order": 400,
      "config": {
        "outputPath": "request.params"
      }
    }
  ]
}
```
