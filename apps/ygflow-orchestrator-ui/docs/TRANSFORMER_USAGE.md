# 转换器节点使用指南

## 概述

转换器节点用于将流程输出转换为目标接口的请求/响应结构。特别适用于以下场景：

1. **类型转换**：将业务组件返回的简单类型（如 `String`）转换为复杂类型（如 `Map`、`List`、自定义对象等）
2. **数据格式转换**：将流程上下文的数据转换为 REST 接口需要的格式
3. **响应构造**：构造符合 REST 接口返回类型的响应结构（支持各种返回类型，如 `String`、`Map`、`List`、`ResponseEntity` 等）

## 转换器的输入来源

转换器可以访问以下数据源：

### 1. 前一个业务组件的输出（推荐）

**自动获取**：转换器会自动获取最后一个节点的输出，保存在 `input` 变量中。

**示例场景**：
- 业务组件返回：`String result = "success"`
- 转换器可以通过 `input` 访问：`input` = `"success"`

### 2. 流程上下文（ctx）

**访问方式**：在 Groovy 脚本中使用 `ctx` 变量访问流程上下文。

**可用数据**：
- `ctx['_lastNodeResult']` - 最后一个节点的输出（自动保存）
- `ctx['_node_xxx']` - 指定节点ID的输出（xxx 是节点ID）
- `ctx['request.path.projectKey']` - 请求路径变量
- `ctx['request.query.page']` - 请求查询参数
- `ctx['request.body.xxx']` - 请求体字段
- `ctx['xxx']` - 其他上下文变量（由节点通过 `as` 配置保存）

### 3. 指定输入源路径（高级用法）

在转换器节点配置中，可以设置 `inputSource` 字段，指定从上下文的哪个路径获取输入：

```json
{
  "type": "transformer",
  "config": {
    "inputSource": "ctx._node_task_1",  // 从指定节点的输出获取
    "mappingConfig": {...},
    "script": "..."
  }
}
```

## 使用场景示例

### 场景1：将 String 转换为 Map（简单响应）

**REST 接口**：
```java
@PostMapping("/{projectKey}")
public Map<String, Object> create(
    @PathVariable("projectKey") String projectKey,
    @RequestBody @Valid ProjectEndpointCreateRequest request
) {
    // 流程编排
}
```

**流程设计**：
1. 业务组件节点（task）：返回 `String result = "success"`
2. 转换器节点（transformer）：将 `String` 转换为 `Map<String, Object>`

**转换器配置**：

#### 方式1：使用字段映射

**字段映射配置**：
- 源字段：`input`（前一个节点的输出）
- 目标字段：`message`
- 转换类型：直接映射

**结果**：转换器返回 `{"message": "success"}`

#### 方式2：使用 Groovy 脚本

```groovy
// input 是前一个业务组件的输出（String）
[
    success: true,
    message: input,
    projectKey: ctx['request.path.projectKey'],
    timestamp: System.currentTimeMillis()
]
```

### 场景2：将 String 转换为 ResponseEntity<Map<String, Object>>

**REST 接口**：
```java
@PostMapping("/{projectKey}")
public ResponseEntity<Map<String, Object>> create(
    @PathVariable("projectKey") String projectKey,
    @RequestBody @Valid ProjectEndpointCreateRequest request
) {
    // 流程编排
}
```

**流程设计**：
1. 业务组件节点（task）：返回 `String result = "success"`
2. 转换器节点（transformer）：将 `String` 转换为 `ResponseEntity<Map<String, Object>>`

**转换器配置**：

#### 方式1：使用 Groovy 脚本（推荐）

在转换器的 Groovy 脚本中：

```groovy
// input 是前一个业务组件的输出（String）
// ctx 是流程上下文，可以访问请求参数

// 构造响应 Map
def responseBody = [
    success: true,
    message: input,  // 业务组件返回的 String
    projectKey: ctx['request.path.projectKey'],  // 从请求路径获取
    timestamp: System.currentTimeMillis()
]

// 返回 ResponseEntity 结构（Map 格式）
// 后端会自动处理为 ResponseEntity
[
    statusCode: 200,
    headers: [
        'Content-Type': 'application/json'
    ],
    body: responseBody
]
```

#### 方式2：使用字段映射 + Groovy 脚本

**字段映射配置**：
- 源字段：`input`（前一个节点的输出）
- 目标字段：`body.message`
- 转换类型：直接映射

**Groovy 脚本**：
```groovy
// output 是字段映射的结果
def responseBody = output
responseBody.projectKey = ctx['request.path.projectKey']
responseBody.timestamp = System.currentTimeMillis()

// 返回 ResponseEntity 结构
[
    statusCode: 200,
    headers: ['Content-Type': 'application/json'],
    body: responseBody
]
```

### 场景3：返回简单类型（String、Number 等）

**REST 接口**：
```java
@GetMapping("/{id}")
public String getStatus(@PathVariable("id") String id) {
    // 流程编排
}
```

**转换器配置**：

```groovy
// 直接返回 String
input.toString()
```

或者使用字段映射，将 `input` 映射到目标字段。

### 场景4：返回 List 类型

**REST 接口**：
```java
@GetMapping
public List<Map<String, Object>> list() {
    // 流程编排
}
```

**转换器配置**：

```groovy
// input 是业务组件返回的数据
// 构造 List
[
    [id: 1, name: 'Item 1'],
    [id: 2, name: 'Item 2']
]
```

### 场景5：解析 JSON 字符串并构造响应

如果业务组件返回的是 JSON 字符串：

```groovy
import groovy.json.JsonSlurper

// input 是 JSON 字符串
def jsonSlurper = new JsonSlurper()
def data = jsonSlurper.parseText(input)

// 构造响应（可以是 Map、List 或简单类型）
[
    success: true,
    data: data,
    projectKey: ctx['request.path.projectKey']
]
```

如果需要返回 ResponseEntity：

```groovy
import groovy.json.JsonSlurper

def jsonSlurper = new JsonSlurper()
def data = jsonSlurper.parseText(input)

// 返回 ResponseEntity 结构
[
    statusCode: 200,
    headers: ['Content-Type': 'application/json'],
    body: [
        success: true,
        data: data,
        projectKey: ctx['request.path.projectKey']
    ]
]
```

### 场景6：访问多个节点的输出

如果流程中有多个业务组件，可以通过节点ID访问：

```groovy
// 访问指定节点的输出
def firstResult = ctx['_node_task_1']  // task_1 节点的输出
def secondResult = ctx['_node_task_2']  // task_2 节点的输出

// 构造响应
[
    statusCode: 200,
    body: [
        first: firstResult,
        second: secondResult,
        combined: firstResult + secondResult
    ]
]
```

## 响应结构说明

转换器可以返回各种类型，后端会根据返回类型和 REST 接口的返回类型进行匹配：

### 1. 简单类型（String、Number、Boolean）

```groovy
// 返回 String
"success"

// 返回 Number
200

// 返回 Boolean
true
```

### 2. Map 类型（普通响应）

```groovy
// 返回 Map，会被直接序列化为 JSON
[
    success: true,
    message: input,
    data: [...]
]
```

### 3. List 类型

```groovy
// 返回 List
[
    [id: 1, name: 'Item 1'],
    [id: 2, name: 'Item 2']
]
```

### 4. ResponseEntity 结构（需要控制状态码和响应头）

如果 REST 接口返回类型是 `ResponseEntity<T>`，转换器可以返回 ResponseEntity 结构：

```groovy
[
    statusCode: 200,           // HTTP 状态码（可选，默认 200）
    headers: [                 // 响应头（可选）
        'Content-Type': 'application/json',
        'X-Custom-Header': 'value'
    ],
    body: {...}               // 响应体（必需）
]
```

**注意**：只有当 REST 接口的返回类型是 `ResponseEntity` 时，才需要返回这种结构。如果接口返回类型是 `Map`、`List` 或简单类型，直接返回对应的值即可。

### 简化格式

如果只返回一个 Map（不包含 `statusCode`、`headers`、`body` 字段），会被直接作为响应体：

```groovy
// 返回普通 Map
[
    success: true,
    message: input
]

// 如果 REST 接口返回类型是 Map，直接使用
// 如果 REST 接口返回类型是 ResponseEntity<Map>，需要包装：
[
    statusCode: 200,
    body: [
        success: true,
        message: input
    ]
]
```

## 最佳实践

1. **优先使用 `input` 变量**：转换器会自动获取前一个节点的输出
2. **使用 `ctx` 访问请求参数**：如 `ctx['request.path.projectKey']`
3. **在 Groovy 脚本中处理复杂逻辑**：字段映射适合简单转换，复杂逻辑用脚本
4. **根据 REST 接口返回类型选择响应格式**：
   - 如果接口返回 `String`、`Map`、`List` 等简单类型，直接返回对应值
   - 如果接口返回 `ResponseEntity<T>`，需要返回包含 `statusCode`、`headers`、`body` 的结构
5. **保持响应类型一致性**：确保转换器返回的类型与 REST 接口声明的返回类型匹配

## 注意事项

1. **输入来源优先级**：
   - 如果配置了 `inputSource`，优先使用指定的路径
   - 否则使用 `_lastNodeResult`（最后一个节点的输出）
   - 最后回退到上下文中的其他值

2. **节点输出保存**：
   - 每个节点的返回值会自动保存到 `_lastNodeResult`
   - 同时保存到 `_node_{nodeId}`，可以通过节点ID访问

3. **返回类型匹配**：
   - 转换器返回的类型应该与 REST 接口声明的返回类型匹配
   - 如果接口返回 `ResponseEntity<T>`，转换器需要返回包含 `statusCode`、`headers`、`body` 的结构
   - 如果接口返回 `Map`、`List` 或简单类型，直接返回对应值即可
   - 后端会自动将转换器的返回值序列化为 JSON 响应

