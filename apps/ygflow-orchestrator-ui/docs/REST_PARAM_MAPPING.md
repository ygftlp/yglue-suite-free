# REST 请求参数映射说明

## 概述

当 REST 请求执行流程编排时，系统会自动将请求参数提取到流程上下文中。本文档说明如何将这些参数映射到流程节点的输入。

## 参数结构

REST 请求的参数会被提取到 `request` 对象中，包含以下结构：

```javascript
{
  method: "POST",           // HTTP 方法
  uri: "/api/projects/xxx/flows/yyy/publish",  // 请求 URI
  path: {                   // 路径变量（@PathVariable）
    projectKey: "xxx",
    code: "yyy"
  },
  query: {                  // 查询参数（@RequestParam）
    page: "1",
    size: "10"
  },
  headers: {                // 请求头
    "Content-Type": "application/json",
    "Authorization": "Bearer xxx"
  },
  body: {                   // 请求体（@RequestBody）
    versionNo: 1,
    publishedBy: "admin",
    comment: "发布说明"
  }
}
```

## 参数映射规则

### 1. 路径变量（@PathVariable）

路径变量从 REST 接口路径中提取，例如：

```java
@PostMapping("/{projectKey}/flows/{code}/publish")
public FlowVersion publish(
    @PathVariable("projectKey") String projectKey,
    @PathVariable("code") String code,
    @RequestBody FlowPublishRequest req
) {
    // ...
}
```

路径 `/api/projects/{projectKey}/flows/{code}/publish` 中的 `{projectKey}` 和 `{code}` 会被提取到 `request.path` 中。

**映射方式：**
- `request.path.projectKey` - 获取路径变量 `projectKey`
- `request.path.code` - 获取路径变量 `code`

### 2. 查询参数（@RequestParam）

查询参数从 URL 查询字符串中提取，例如：

```
GET /api/users?page=1&size=10
```

**映射方式：**
- `request.query.page` - 获取查询参数 `page`
- `request.query.size` - 获取查询参数 `size`

### 3. 请求体（@RequestBody）

请求体从 HTTP 请求体中提取，通常为 JSON 格式：

```json
{
  "versionNo": 1,
  "publishedBy": "admin",
  "comment": "发布说明"
}
```

**映射方式：**
- `request.body.versionNo` - 获取请求体字段 `versionNo`
- `request.body.publishedBy` - 获取请求体字段 `publishedBy`
- `request.body.comment` - 获取请求体字段 `comment`

对于嵌套对象，使用点号分隔：

```json
{
  "user": {
    "name": "张三",
    "age": 30
  }
}
```

- `request.body.user.name` - 获取嵌套字段
- `request.body.user.age` - 获取嵌套字段

### 4. 请求头（@RequestHeader）

请求头从 HTTP 请求头中提取：

**映射方式：**
- `request.headers.Content-Type` - 获取请求头 `Content-Type`
- `request.headers.Authorization` - 获取请求头 `Authorization`

## 在流程节点中使用

### 方式 1：使用参数解析器（ParamResolver）

在节点配置中，选择"请求参数"类型，然后输入参数路径：

#### 单个字段映射

1. **路径变量：** `request.path.projectKey`
2. **查询参数：** `request.query.page`
3. **请求体字段：** `request.body.versionNo`
4. **请求头：** `request.headers.Authorization`

#### 整个对象映射

如果需要映射整个对象（例如将整个请求体传递给节点参数），可以使用以下路径：

1. **整个请求体：** `request.body` - 返回完整的请求体对象（Map）
2. **所有路径变量：** `request.path` - 返回所有路径变量的 Map
3. **所有查询参数：** `request.query` - 返回所有查询参数的 Map
4. **所有请求头：** `request.headers` - 返回所有请求头的 Map
5. **整个请求对象：** `request` - 返回包含 method、uri、path、query、headers、body 的完整对象

**使用场景：**
- 当节点参数类型为 `OBJECT` 时，可以直接使用 `request.body` 传递整个请求体
- 当需要将整个请求对象传递给下游服务时，可以使用 `request`
- 当需要批量处理路径变量或查询参数时，可以使用 `request.path` 或 `request.query`

### 方式 2：使用表达式（SpEL）

在节点配置中，选择"表达式"类型，使用 SpEL 表达式：

```spel
#{request.path.projectKey}
#{request.body.versionNo}
#{request.query.page}
```

### 方式 3：在 Groovy 脚本中使用

在转换器节点的 Groovy 脚本中，可以通过 `ctx` 访问：

```groovy
def projectKey = ctx['request.path.projectKey']
def versionNo = ctx['request.body.versionNo']
```

## 示例

### 示例 1：发布流程接口

**REST 接口：**
```java
@PostMapping("/{projectKey}/flows/{code}/publish")
public FlowVersion publish(
    @PathVariable("projectKey") String projectKey,
    @PathVariable("code") String code,
    @RequestBody FlowPublishRequest req
) {
    // ...
}
```

**流程节点参数映射：**
- `projectKey` → `request.path.projectKey`
- `code` → `request.path.code`
- `versionNo` → `request.body.versionNo`
- `publishedBy` → `request.body.publishedBy`
- `comment` → `request.body.comment`

### 示例 2：查询用户列表接口

**REST 接口：**
```java
@GetMapping("/users")
public List<User> listUsers(
    @RequestParam("page") int page,
    @RequestParam("size") int size,
    @RequestHeader("Authorization") String token
) {
    // ...
}
```

**流程节点参数映射（单个字段）：**
- `page` → `request.query.page`
- `size` → `request.query.size`
- `token` → `request.headers.Authorization`

**流程节点参数映射（整个对象）：**
- `queryParams` (类型: OBJECT) → `request.query` - 获取所有查询参数
- `allHeaders` (类型: OBJECT) → `request.headers` - 获取所有请求头

### 示例 3：传递整个请求体对象

**REST 接口：**
```java
@PostMapping("/users")
public User createUser(@RequestBody UserCreateRequest req) {
    // ...
}
```

**流程节点参数映射：**
- **方式 1（单个字段）：**
  - `name` → `request.body.name`
  - `email` → `request.body.email`
  - `age` → `request.body.age`

- **方式 2（整个对象，推荐）：**
  - `userRequest` (类型: OBJECT) → `request.body` - 直接传递整个请求体对象

### 示例 4：保存流程接口（路径变量 + 请求体对象）

**REST 接口：**
```java
@PostMapping("/{projectKey}/flows")
public FlowVersion save(
    @PathVariable("projectKey") String projectKey, 
    @RequestBody @Valid FlowSaveRequest req
) {
    return flowService.saveFlow(projectKey, req);
}
```

**流程节点参数映射：**

- **方式 1（单个字段映射）：**
  - `projectKey` (类型: STRING) → `request.path.projectKey` - 路径变量
  - `flowCode` (类型: STRING) → `request.body.flowCode` - 请求体中的字段
  - `flowName` (类型: STRING) → `request.body.flowName` - 请求体中的字段
  - `description` (类型: STRING) → `request.body.description` - 请求体中的字段

- **方式 2（整个对象映射，推荐）：**
  - `projectKey` (类型: STRING) → `request.path.projectKey` - 路径变量（单个字段）
  - `flowRequest` (类型: OBJECT) → `request.body` - **整个请求体对象**（包含 flowCode、flowName、description 等所有字段）

**推荐使用方式 2 的原因：**
- 当请求体包含多个字段时，使用整个对象映射更简洁
- 避免为每个字段单独配置映射
- 如果请求体结构发生变化，只需修改节点参数类型，不需要修改每个字段的映射
- 在节点内部可以通过 `flowRequest.flowCode`、`flowRequest.flowName` 等方式访问具体字段

## 注意事项

1. **参数路径区分大小写**：`request.path.projectKey` 和 `request.path.ProjectKey` 是不同的
2. **嵌套对象使用点号分隔**：`request.body.user.name`
3. **数组/列表访问**：如果参数是数组，可以通过索引访问，如 `request.query.ids[0]`
4. **默认值**：如果参数不存在，可以在参数解析器中设置默认值
5. **类型转换**：系统会自动进行类型转换，也可以在参数解析器中指定类型（STRING、NUMBER、BOOLEAN、OBJECT、ARRAY）

## 参数提示功能

在参数解析器输入框中，系统会自动显示可用的参数路径提示：

- 路径变量：从 REST 接口路径中自动提取
- 请求体字段：从请求参数结构（requestSchema）中自动提取
- 通用提示：`request.query.*` 和 `request.headers.*`

点击提示项可以快速填充参数路径。

