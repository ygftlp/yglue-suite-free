# 代码规范

## 注释规范

### 基本原则

**所有新增和修改的代码必须添加注释**，以提高代码可读性和可维护性。

### 注释要求

#### 1. Java 代码注释

##### 类注释
每个类都应该有类级别的 JavaDoc 注释，说明类的用途、主要功能和使用场景。

```java
/**
 * REST 端点控制器
 * 提供 REST 接口的 CRUD 操作和查询功能
 * 
 * @author 开发者名称
 * @since 版本号
 */
@RestController
@RequestMapping("/api/projects/{projectKey}/endpoints")
public class ProjectEndpointController {
    // ...
}
```

##### 方法注释
所有 public 和 protected 方法必须添加 JavaDoc 注释，包括：
- 方法功能描述
- 参数说明（@param）
- 返回值说明（@return）
- 异常说明（@throws）

```java
/**
 * 获取指定项目的 REST 端点列表
 * 
 * @param projectKey 项目标识
 * @return REST 端点列表
 * @throws ResponseStatusException 当项目不存在时抛出 404 错误
 */
@GetMapping("/rests")
public List<ProjectEndpointResponse> listEndpointRests(@PathVariable("projectKey") String projectKey) {
    // ...
}
```

##### 复杂逻辑注释
对于复杂的业务逻辑、算法或特殊处理，必须添加行内注释说明。

```java
// 将请求参数包装在 "request" 键下，以便通过 request.path.xxx, request.body.xxx 等方式访问
Map<String, Object> flowInput = new LinkedHashMap<>();
flowInput.put("request", payload);
```

##### 字段注释
重要的类字段应该添加注释说明其用途。

```java
/**
 * 流程执行器注册表
 * 用于管理和查找不同类型的节点执行器
 */
private final NodeExecutorRegistry registry;
```

#### 2. TypeScript/JavaScript 代码注释

##### 函数注释
使用 JSDoc 格式为函数添加注释。

```typescript
/**
 * 加载 REST 端点列表
 * 从后端获取指定项目的所有 REST 接口
 * 
 * @param projectKey - 项目标识
 * @returns Promise<ProjectEndpoint[]> REST 端点列表
 */
async function loadRestEndpoints(projectKey: string): Promise<ProjectEndpoint[]> {
    // ...
}
```

##### 接口/类型注释
为接口和类型定义添加注释说明。

```typescript
/**
 * REST 端点配置
 * 包含路径、方法、启用状态等信息
 */
export interface ProjectEndpoint {
    /** 端点 ID */
    id: number
    /** 项目 ID */
    projectId: number
    /** 端点类型（REST/FLOW_API） */
    endpointType: string
    // ...
}
```

##### 复杂逻辑注释
对于复杂的业务逻辑、算法或特殊处理，必须添加注释。

```typescript
// 从入口点路径中提取路径变量（如 /api/projects/{projectKey}/flows/{code} -> [projectKey, code]）
const pathVars = extractPathVariables(props.entrypointPath)
```

##### 组件注释
Vue 组件应该添加注释说明组件的用途和主要功能。

```vue
<!--
  REST 列表页面组件
  功能：
  - 显示项目的所有 REST 接口
  - 支持创建、编辑、删除 REST 接口
  - 支持切换接口的托管状态
-->
<script setup lang="ts">
// ...
</script>
```

#### 3. Vue 组件注释

##### 组件级注释
在 `<script setup>` 或 `<template>` 顶部添加组件说明。

```vue
<!--
  参数解析器编辑器组件
  用于配置节点输入参数的来源（请求参数、上下文、常量、表达式）
-->
<script setup lang="ts">
// ...
</script>
```

##### 计算属性注释
复杂的计算属性应该添加注释说明其逻辑。

```vue
<script setup lang="ts">
/**
 * 生成参数路径提示列表
 * 根据入口点路径和请求参数结构自动生成可用的参数路径
 */
const pathHints = computed(() => {
    // ...
})
</script>
```

##### 方法注释
重要的方法应该添加注释。

```vue
<script setup lang="ts">
/**
 * 从路径中提取路径变量
 * 例如：/api/projects/{projectKey}/flows/{code} -> [projectKey, code]
 * 
 * @param path - REST 接口路径
 * @returns 路径变量名称数组
 */
function extractPathVariables(path: string | null | undefined): string[] {
    // ...
}
</script>
```

#### 4. SQL 注释

数据库迁移脚本和复杂 SQL 应该添加注释。

```sql
-- 创建流程入口点表
-- 用于存储流程与 REST 接口的关联关系
CREATE TABLE flow_entrypoint (
    -- 主键 ID
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    -- 流程代码
    flow_code VARCHAR(255) NOT NULL,
    -- ...
);
```

### 注释最佳实践

1. **及时更新注释**：代码修改时，必须同步更新相关注释
2. **注释要准确**：注释应该准确描述代码的功能，避免误导
3. **避免冗余注释**：不要为显而易见的代码添加无意义的注释
4. **使用中文注释**：项目统一使用中文注释，便于团队协作
5. **注释格式统一**：遵循各语言的注释规范（JavaDoc、JSDoc 等）

### 注释检查

在代码审查（Code Review）时，必须检查：
- ✅ 新增的类、方法是否有注释
- ✅ 修改的复杂逻辑是否有注释说明
- ✅ 注释是否准确描述了代码功能
- ✅ 注释是否与代码保持同步

### 示例

#### ✅ 好的注释示例

```java
/**
 * 解析参数值
 * 支持从流程上下文或请求参数中获取值
 * 
 * @param resolverConfig 解析器配置，格式：
 *   {
 *     "type": "request" | "context" | "constant" | "expression",
 *     "path": "request.path.projectKey" (当 type 为 request 或 context 时),
 *     "constant": "常量值" (当 type 为 constant 时),
 *     "expression": "#{ctx.price * 0.9}" (当 type 为 expression 时),
 *     "default": "默认值"
 *   }
 * @param context 流程上下文
 * @return 解析后的参数值
 */
public static Object resolve(Object resolverConfig, FlowContext context) {
    // ...
}
```

```typescript
/**
 * 生成参数路径提示
 * 根据入口点路径和请求参数结构自动生成可用的参数路径
 * 
 * @returns 参数路径提示数组，如 ["request.path.projectKey", "request.body.versionNo"]
 */
const pathHints = computed(() => {
    // ...
})
```

#### ❌ 不好的注释示例

```java
// 解析参数
public static Object resolve(Object resolverConfig, FlowContext context) {
    // ...
}
```

```typescript
// 生成提示
const pathHints = computed(() => {
    // ...
})
```

### 特殊情况

1. **自解释的代码**：如果代码本身已经非常清晰（如简单的 getter/setter），可以省略注释
2. **工具生成的代码**：自动生成的代码可以不添加注释
3. **临时代码**：临时代码应该添加 `// TODO` 或 `// FIXME` 注释，并说明原因和计划

---

**注意**：本规范自发布之日起生效，所有新增和修改的代码都必须遵循此规范。

