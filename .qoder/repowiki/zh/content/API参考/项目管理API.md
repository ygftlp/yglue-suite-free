# 项目管理API

<cite>
**本文档引用文件**  
- [ProjectController.java](file://yglue-orchestrator/src/main/java/org/yglue/flow/orch/web/controller/ProjectController.java)
- [ProjectCreateRequest.java](file://yglue-orchestrator/src/main/java/org/yglue/flow/orch/web/dto/project/request/ProjectCreateRequest.java)
- [ProjectEnsureRequest.java](file://yglue-orchestrator/src/main/java/org/yglue/flow/orch/web/dto/project/request/ProjectEnsureRequest.java)
- [ProjectEnsureResponse.java](file://yglue-orchestrator/src/main/java/org/yglue/flow/orch/web/dto/project/response/ProjectEnsureResponse.java)
- [Project.java](file://yglue-orchestrator/src/main/java/org/yglue/flow/orch/domain/Project.java)
- [ProjectService.java](file://yglue-orchestrator/src/main/java/org/yglue/flow/orch/service/ProjectService.java)
- [ProjectMapper.xml](file://yglue-orchestrator/src/main/resources/mapper/ProjectMapper.xml)
- [V1__init.sql](file://yglue-orchestrator/src/main/resources/db/migration/V1__init.sql)
- [ProjectsPage.vue](file://apps/ygflow-orchestrator-ui/src/pages/ProjectsPage.vue)
- [client.ts](file://apps/ygflow-orchestrator-ui/src/api/client.ts)
</cite>

## 目录
1. [创建项目API](#创建项目api)
2. [确保项目存在API](#确保项目存在api)
3. [项目对象结构](#项目对象结构)
4. [项目标识约束与命名规范](#项目标识约束与命名规范)
5. [前端使用场景](#前端使用场景)
6. [错误处理](#错误处理)

## 创建项目API

`POST /api/projects` 端点用于创建新项目。该API是系统资源组织的基础，所有其他功能均依赖于项目上下文。

### 请求方法
- **HTTP方法**: POST
- **端点**: `/api/projects`

### 请求体
请求体必须是 `ProjectCreateRequest` 对象的JSON表示，包含以下字段：

| 字段 | 类型 | 必需 | 描述 |
|------|------|------|------|
| key | 字符串 | 是 | 项目的唯一标识符 |
| name | 字符串 | 是 | 项目的显示名称 |

**字段约束**:
- `key` 和 `name` 字段均被 `@NotBlank` 注解标记，不能为空或仅包含空白字符
- `key` 字段具有唯一性约束，不能与现有项目重复

### 请求示例
```json
{
  "key": "my-project",
  "name": "我的项目"
}
```

### 响应
成功创建项目后，返回 `Project` 对象。

### 响应示例
```json
{
  "id": 1,
  "key": "my-project",
  "name": "我的项目",
  "createTime": "2025-01-01T10:00:00",
  "updateTime": "2025-01-01T10:00:00",
  "createBy": null,
  "updateBy": null,
  "delFlag": 0
}
```

**接口实现**:
```java
@PostMapping
public Project create(@RequestBody @Valid ProjectCreateRequest req) {
    return projectService.create(req.key, req.name);
}
```

**接口实现逻辑**:
1. 接收 `ProjectCreateRequest` 请求体
2. 通过 `@Valid` 注解进行请求验证
3. 调用 `ProjectService.create()` 方法创建项目
4. 返回创建的 `Project` 对象

**接口实现源码**:
- [ProjectController.java](file://yglue-orchestrator/src/main/java/org/yglue/flow/orch/web/controller/ProjectController.java#L28-L31)
- [ProjectCreateRequest.java](file://yglue-orchestrator/src/main/java/org/yglue/flow/orch/web/dto/project/request/ProjectCreateRequest.java#L4-L10)

## 确保项目存在API

`POST /api/projects/{projectKey}/ensure` 端点用于确保项目存在。如果项目不存在则创建，如果已存在则返回现有项目。

### 请求方法
- **HTTP方法**: POST
- **端点**: `/api/projects/{projectKey}/ensure`

### 路径参数
| 参数 | 类型 | 必需 | 描述 |
|------|------|------|------|
| projectKey | 字符串 | 是 | 项目的唯一标识符 |

### 请求体
请求体为可选的 `ProjectEnsureRequest` 对象，包含以下字段：

| 字段 | 类型 | 必需 | 描述 |
|------|------|------|------|
| name | 字符串 | 否 | 项目的显示名称 |

**行为逻辑**:
- 如果请求体中提供了 `name` 且不为空，则使用该名称
- 如果未提供 `name` 或为空，则使用 `projectKey` 作为项目名称

### 请求示例
```json
{
  "name": "我的项目"
}
```

### 响应
返回 `ProjectEnsureResponse` 对象，包含项目信息和是否新创建的标志。

### 响应示例
```json
{
  "created": true,
  "projectId": 1,
  "projectKey": "my-project",
  "projectName": "我的项目"
}
```

**接口实现**:
```java
@PostMapping("/{projectKey}/ensure")
public ProjectEnsureResponse ensure(@PathVariable("projectKey") String projectKey,
                                    @RequestBody(required = false) ProjectEnsureRequest req) {
    String name = req != null && req.getName() != null && !req.getName().isBlank()
            ? req.getName()
            : projectKey;
    return ProjectEnsureResponse.from(projectService.ensureProjectWithFlag(projectKey, name));
}
```

**接口实现逻辑**:
1. 从路径参数获取 `projectKey`
2. 从请求体获取 `name`（可选）
3. 确定项目名称：如果提供了有效的 `name` 则使用，否则使用 `projectKey`
4. 调用 `ProjectService.ensureProjectWithFlag()` 方法
5. 将结果转换为 `ProjectEnsureResponse` 对象返回

**接口实现源码**:
- [ProjectController.java](file://yglue-orchestrator/src/main/java/org/yglue/flow/orch/web/controller/ProjectController.java#L33-L40)
- [ProjectEnsureRequest.java](file://yglue-orchestrator/src/main/java/org/yglue/flow/orch/web/dto/project/request/ProjectEnsureRequest.java#L2-L13)
- [ProjectEnsureResponse.java](file://yglue-orchestrator/src/main/java/org/yglue/flow/orch/web/dto/project/response/ProjectEnsureResponse.java#L5-L53)

## 项目对象结构

`Project` 对象是系统中的核心实体，用于组织和管理所有相关资源。

### 属性定义
| 属性 | 类型 | 描述 |
|------|------|------|
| id | 长整型 | 项目的唯一数据库ID |
| key | 字符串 | 项目的唯一标识符 |
| name | 字符串 | 项目的显示名称 |
| createTime | 日期 | 项目创建时间 |
| updateTime | 日期 | 项目最后更新时间 |
| createBy | 字符串 | 创建者 |
| updateBy | 字符串 | 最后更新者 |
| delFlag | 整型 | 删除标志（0表示未删除） |

### Java实体类
```java
@Data
public class Project {
    private Long id;
    private String key;
    private String name;
    private Date createTime;
    private Date updateTime;
    private String createBy;
    private String updateBy;
    private Integer delFlag;
}
```

**实体类源码**:
- [Project.java](file://yglue-orchestrator/src/main/java/org/yglue/flow/orch/domain/Project.java#L8-L18)

## 项目标识约束与命名规范

### 唯一性约束
项目标识（`projectKey`）在系统中必须是唯一的，这一约束通过以下方式实现：

1. **数据库层面**:
   - `yglue_project` 表的 `project_key` 列具有唯一性约束
   - 在 `V1__init.sql` 文件中定义了表结构

2. **服务层面**:
   - `ProjectService.create()` 方法在创建前检查项目是否已存在
   - 如果项目已存在，抛出 `ResponseStatusException` 异常

3. **控制器层面**:
   - `ProjectController.create()` 方法调用服务层的创建逻辑
   - 自动处理验证和异常

### 数据库表结构
```sql
CREATE TABLE yglue_project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_key VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_by VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  del_flag TINYINT NOT NULL DEFAULT 0
);
```

### 命名规范
- **项目标识 (key)**:
  - 必须是唯一的
  - 不能为空或仅包含空白字符
  - 建议使用小写字母、数字和连字符的组合
  - 例如：`my-project`, `api-gateway`, `data-processing`

- **项目名称 (name)**:
  - 必须是唯一的
  - 不能为空或仅包含空白字符
  - 可以包含空格和特殊字符，用于显示目的
  - 例如："我的项目", "API网关", "数据处理系统"

**约束实现源码**:
- [V1__init.sql](file://yglue-orchestrator/src/main/resources/db/migration/V1__init.sql#L1-L22)
- [ProjectMapper.xml](file://yglue-orchestrator/src/main/resources/mapper/ProjectMapper.xml#L22-L25)
- [ProjectService.java](file://yglue-orchestrator/src/main/java/org/yglue/flow/orch/service/ProjectService.java#L47-L51)

## 前端使用场景

`ProjectsPage.vue` 是前端项目列表页面，展示了API在用户界面中的实际使用场景。

### 页面功能
- 显示所有项目的列表
- 允许用户选择并进入特定项目
- 提供项目创建和管理的入口

### API调用逻辑
1. **加载项目列表**:
   - 在页面加载时调用 `api.listProjects()` 方法
   - 获取所有项目并显示在界面上

2. **打开项目**:
   - 用户点击项目卡片时调用 `router.push()`
   - 导航到特定项目的REST接口管理页面

### 前端代码示例
```typescript
async function loadProjects() {
  loading.value = true
  errorMessage.value = null
  try {
    projects.value = await api.listProjects()
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : "加载项目列表失败"
    projects.value = []
  } finally {
    loading.value = false
  }
}

function openProject(project: Project & { projectKey?: string }) {
  const key = resolveProjectKey(project)
  if (!key) {
    window.alert("当前项目缺少标识，无法打开，请先在后端补齐。")
    return
  }
  router.push(`/projects/${encodeURIComponent(key)}/rests`)
}
```

### API客户端定义
`client.ts` 文件定义了前端API客户端，包括项目相关的所有方法：

```typescript
export const api = {
  listProjects(): Promise<Project[]> {
    return request<Project[]>("/projects")
  },

  getProject(projectKey: string): Promise<Project> {
    return request<Project>(`/projects/${encodeURIComponent(projectKey)}`)
  },

  // 其他项目相关方法...
}
```

**前端实现源码**:
- [ProjectsPage.vue](file://apps/ygflow-orchestrator-ui/src/pages/ProjectsPage.vue#L21-L31)
- [client.ts](file://apps/ygflow-orchestrator-ui/src/api/client.ts#L340-L343)

## 错误处理

### 冲突错误（409 Conflict）
当尝试创建已存在的项目时，系统会返回冲突错误。

**错误场景**:
- 创建项目时，`projectKey` 已被使用
- 试图创建具有相同标识的项目

**错误响应**:
```http
HTTP/1.1 409 Conflict
Content-Type: application/json

{
  "timestamp": "2025-01-01T10:00:00",
  "status": 409,
  "error": "Conflict",
  "message": "Project already exists: my-project",
  "path": "/api/projects"
}
```

**错误处理逻辑**:
1. `ProjectService.create()` 方法检查项目是否存在
2. 如果存在，抛出 `ResponseStatusException` 异常
3. Spring框架自动将异常转换为409状态码响应

```java
@Transactional
public Project create(String key, String name) {
    if (projectMapper.selectByKey(key) != null) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Project already exists: " + key);
    }
    return doCreate(key, name);
}
```

### 其他可能的错误
- **400 Bad Request**: 请求体验证失败（如 `key` 或 `name` 为空）
- **404 Not Found**: 查询不存在的项目
- **500 Internal Server Error**: 服务器内部错误

**错误处理源码**:
- [ProjectService.java](file://yglue-orchestrator/src/main/java/org/yglue/flow/orch/service/ProjectService.java#L46-L50)