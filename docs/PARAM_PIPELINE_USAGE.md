# 参数构造管线（V2）使用说明

## 1. 解决的问题
- 用结构化配置替代 Groovy 参数脚本。
- 支持节点内“隐式服务调用”做参数补数。
- 支持 List 参数处理（`filter/map/enrich/groupBy/reduce`）。
- 支持“临时变量 -> 最终入参”两阶段构造，解决参数依赖链问题。

## 2. 配置位置
- 位置：服务节点右侧面板 -> `参数构造管线（V2）`
- 数据落点：节点模型 `data.paramPlansV2`

## 3. 数据模型（V2）
```json
{
  "tempPlans": [],
  "argPlans": []
}
```

`listPipeline` 场景下每条计划额外包含：
- `listInput`：列表输入来源（ctx/const/serviceCall）
- `listCompose`：目标元素字段映射（字段级来源绑定）
- `listSteps`：可选后处理步骤（filter/map/enrich/groupBy/reduce）

- `tempPlans`：节点内临时变量计划（中间结果，不直接作为服务入参）
- `argPlans`：最终服务入参计划（必须映射到真实入参）

### 3.1 兼容性说明（重要）
- 从当前版本开始，`paramPlansV2` 仅接受上述新结构。
- 不再兼容旧结构（例如旧数组模型、`plans` 字段）。
- 若加载到旧结构数据，前端会按新模型默认值处理（`tempPlans=[]`，`argPlans` 至少 1 条默认计划）。

## 4. 两阶段使用流程
1. 先配置 `tempPlans`（可选）。
2. 再配置 `argPlans`（必配，最终给服务方法入参赋值）。

### 4.1 临时变量计划（tempPlans）
每条至少包含：
- `key`：变量名（例如 `userId`）
- `source`：取值来源（`ctx/const/serviceCall/httpCall/listPipeline`）

推荐引用路径约定：
- `ctx.__tmp.<nodeId>.<key>`

说明：
- 一个临时变量可以依赖前一个临时变量（通过 `ctx.__tmp...` 引用）。
- 适合“先调服务A拿值，再调服务B，再组装最终入参”。

### 4.2 入参计划（argPlans）
每条至少包含：
- `target`：目标入参名（例如 `a`）
- `typeHint`：类型提示（例如 `java.lang.String`）
- `source`：值来源（可引用 `tempPlans` 结果）

## 5. serviceCall 字段含义
`source.kind = serviceCall` 时：
- `fn`：函数别名（指向项目内可调用方法）
- `serviceRef`：选中的服务方法元数据（serviceBean/methodSignature 等）
- `argBindings`：参数绑定列表（按方法签名自动生成，数量固定）
- `args`：运行时兼容字段（由 `argBindings` 投影而来）
- `serviceResultPath`：结果提取路径（可选，服务返回对象时可提取某个属性）

注意：`serviceCall` 是“为了构造参数而调用的辅助服务”，不是当前节点主服务本身。

## 6. 当前前端校验能力
### argPlans
- 严格模式下（已识别服务入参）：
  - 入参计划数量 <= 服务入参数量
  - `target` 只能选真实入参
  - `target` 不能重复
  - `typeHint` 与入参类型不匹配会告警
- 若 `source.kind=httpCall`：
  - URL 必填
  - Headers / Query 必须是合法 JSON
  - 非 GET 时 Body 必须是合法 JSON

### tempPlans
- `key` 必填
- `key` 仅支持字母/数字/下划线，且不能数字开头
- `key` 不能重复
- 若 `source.kind=httpCall`：
  - URL 必填
  - Headers / Query 必须是合法 JSON
  - 非 GET 时 Body 必须是合法 JSON

## 7. 单入参示例（a: String）
目标：先调用服务拿用户名，再赋给 `a`

```json
{
  "tempPlans": [
    {
      "key": "userId",
      "source": {
        "kind": "serviceCall",
        "serviceCall": {
          "fn": "token.parseUserId",
          "argsText": "[request.headers.Authorization]"
        }
      }
    },
    {
      "key": "userName",
      "source": {
        "kind": "serviceCall",
        "serviceCall": {
          "fn": "user.queryName",
          "argsText": "[ctx.__tmp.node123.userId]"
        }
      }
    }
  ],
  "argPlans": [
    {
      "target": "a",
      "typeHint": "java.lang.String",
      "source": {
        "kind": "ctx",
        "path": "ctx.__tmp.node123.userName"
      }
    }
  ]
}
```

## 8. 多入参 + 对象参数示例（serviceCall）
场景：辅助服务 `user.queryProfile` 需要 3 个参数：
- `tenantId: String`
- `userId: Long`
- `options: QueryOption`（对象）

对象参数可使用“对象构造器（字段映射）”：

```json
{
  "kind": "serviceCall",
  "serviceCall": {
    "fn": "user.queryProfile",
    "serviceRef": {
      "serviceBean": "userService",
      "methodName": "queryProfile",
      "methodSignature": "queryProfile(java.lang.String,java.lang.Long,com.example.QueryOption)"
    },
    "argBindings": [
      {
        "paramName": "tenantId",
        "paramType": "java.lang.String",
        "source": {
          "mode": "direct",
          "kind": "ctx",
          "path": "request.headers.tenantId"
        }
      },
      {
        "paramName": "userId",
        "paramType": "java.lang.Long",
        "source": {
          "mode": "direct",
          "kind": "tempVar",
          "tempKey": "userId"
        }
      },
      {
        "paramName": "options",
        "paramType": "com.example.QueryOption",
        "source": {
          "mode": "objectBuilder",
          "objectFields": [
            {
              "fieldPath": "includeTags",
              "source": { "kind": "const", "constValue": "true" }
            },
            {
              "fieldPath": "withScore",
              "source": { "kind": "const", "constValue": "false" }
            },
            {
              "fieldPath": "operatorId",
              "source": { "kind": "tempVar", "tempKey": "userId" }
            }
          ]
        }
      }
    ]
  }
}
```

说明：
- `argBindings` 按方法签名顺序一一对应，不可随意增删。
- 对象参数支持两种方式：
  - `mode=direct`：整体赋值（ctx/常量/tempVar）
  - `mode=objectBuilder`：字段级映射（推荐）
- List 参数若走 `const`，常量值必须是 JSON 数组；对象参数若走 `const`，常量值必须是 JSON 对象。
- 若参数来自前置步骤，优先用 `tempVar` 引用 `ctx.__tmp.<nodeId>.<key>` 对应变量。

## 9. 服务返回对象时提取属性
场景：目标入参是 `String`，但服务返回 `UserDto`。

可在 `serviceCall` 来源上配置：
```json
{
  "kind": "serviceCall",
  "serviceResultPath": "data.userName",
  "serviceCall": {
    "fn": "user.queryById",
    "serviceRef": {
      "serviceBean": "userService",
      "methodName": "queryById",
      "methodSignature": "queryById(java.lang.Long)"
    },
    "argBindings": [
      {
        "paramName": "userId",
        "paramType": "java.lang.Long",
        "source": { "mode": "direct", "kind": "ctx", "path": "request.body.userId" }
      }
    ]
  }
}
```

说明：
- `serviceResultPath` 为空：使用整个返回值。
- `serviceResultPath` 不为空：从返回对象中提取指定属性再写入目标入参。
- List 管线输入同样支持 `serviceResultPath`，用于从包装对象里提取列表字段（例如 `data.items`）。

## 10. 当前实现状态（截至 2026-03-06）
已完成：
- 前端可视化配置（含 `tempPlans + argPlans`）
- 节点数据联动保存到 `data.paramPlansV2`
- `serviceCall` 已支持“选择服务方法 -> 自动带出入参槽位 -> 逐参数绑定来源”
- `httpCall` 已支持用于临时变量/入参来源（method/url/headers/query/body/resultPath）
- 对象类型入参已支持“对象构造器（字段级映射）”与基础校验
- 分支条件与参数构造共用同一套 `serviceCall` 选择器交互（避免一处下拉一处文本）
- 临时变量与入参均改为“列表摘要 + 点击弹框编辑”交互，侧栏仅保留摘要信息

待接入：
- 发布编译阶段将 `paramPlansV2` 编译为运行时执行计划
- Runtime 按 V2 计划执行参数构造（当前主路径仍以旧 `inputs.script` 为主）

## 11. List 入参（分散来源）推荐方案
当目标入参是 `List<T>` 且字段来自多个来源时，推荐使用“字段映射优先”的方式：
- 先定义目标元素类型：`listCompose.itemTypeHint`
- 再为每个目标字段配置来源：`ctx/const/tempVar/serviceCall`
- 最后可选追加后处理步骤：`filter/map/enrich/groupBy/reduce`

示例结构：
```json
{
  "source": { "kind": "listPipeline" },
  "listInput": {
    "kind": "serviceCall",
    "serviceResultPath": "data.items"
  },
  "listCompose": {
    "itemTypeHint": "com.example.OrderItemDto",
    "fields": [
      {
        "targetField": "skuId",
        "source": { "kind": "ctx", "path": "$.skuId" }
      },
      {
        "targetField": "price",
        "source": {
          "kind": "serviceCall",
          "serviceResultPath": "data.price"
        }
      },
      {
        "targetField": "operatorId",
        "source": { "kind": "tempVar", "tempKey": "userId" }
      }
    ]
  },
  "listSteps": [
    { "op": "filter", "exprText": "$.qty > 0" }
  ]
}
```

## 12. 参数配置交互规范（单值 vs 对象）
### 12.1 签名驱动原则
- 方法入参由服务方法签名自动生成，参数数量固定，不允许手工新增或删除。
- 用户仅能配置“每个参数的取值方式”，不能修改参数名与顺序。

### 12.2 单值参数（String/Long/Boolean/Number）
- 默认模式：`direct`。
- 支持来源：`ctx`、`const`、`tempVar`。
- `const` 走基础类型校验（数字、布尔、字符串格式），错误时标记为“待修正”。

### 12.3 对象参数（DTO/Map）
- 默认模式：`objectBuilder`（推荐）。
- 支持两种方式：
  - `objectBuilder`：字段级映射（`fieldPath -> source`）
  - `direct`：整体对象赋值（高级用法）
- 对象构造辅助：
  - 输入样例 JSON 自动生成字段映射
  - 按 `ctx` 基路径批量填充字段路径

### 12.4 强校验规则
- `objectBuilder` 至少 1 个字段映射。
- 字段路径不可重复，不可为空。
- 字段来源为 `ctx/tempVar/const` 时，必要值必须填写。
- 对象参数若使用 `const`，值必须是 JSON 对象；List 参数若使用 `const`，值必须是 JSON 数组。
