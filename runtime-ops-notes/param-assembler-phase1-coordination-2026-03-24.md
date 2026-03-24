# 参数装配器一期协调方案

## 目标

一期目标是把复杂参数配置从“技术上可实现”提升到“用户可用”，但不做完整重构。

一期统一由架构方案协调，前端、后端、元数据三条线并行推进，统一遵循 `param-ast/v1` 最小合同。

## 一期范围

### 一期要做

- 定义唯一编辑合同 `param-ast/v1`
- 支持标量参数、对象参数、嵌套对象、单层列表参数
- 支持来源类型：`request`、`context`、`temp`、`nodeOutput`、`const`、`serviceCall`、`httpCall`
- 支持列表操作：`filter`、`map`、`compose`
- 支持对象字段草稿自动生成
- 支持字段级校验和结构预览
- 支持发布前参数校验
- 保留现有高级编辑入口，避免旧流程中断
- 一期继续复用现有 `paramPlans`、`serviceCall`、`listPipeline` 执行链

### 一期不做

- 不做 `groupBy`
- 不做 `reduce`
- 不做 `flatten`
- 不做批量 `enrich` 执行语义
- 不做嵌套数组的完整可视化装配
- 不做动态 `Map key` 装配
- 不做运行时全面 AST 执行器替换
- 不做拖拽式映射器
- 不做双向 AST JSON 编辑器

## 统一合同

### param-ast/v1

```json
{
  "version": "param-ast/v1",
  "args": [
    {
      "name": "customer",
      "javaType": "com.demo.CustomerDTO",
      "required": true,
      "value": {
        "kind": "object",
        "fields": [
          {
            "path": "id",
            "value": {
              "kind": "source",
              "sourceType": "request",
              "path": "request.customerId"
            }
          },
          {
            "path": "profile.name",
            "value": {
              "kind": "source",
              "sourceType": "temp",
              "path": "profile.name"
            }
          }
        ]
      }
    }
  ],
  "temps": [
    {
      "key": "profile",
      "value": {
        "kind": "call",
        "callType": "service",
        "ref": {
          "serviceBean": "customerProfileService",
          "methodName": "queryProfile",
          "methodSignature": "queryProfile(java.lang.String)"
        },
        "args": [
          {
            "name": "customerId",
            "value": {
              "kind": "source",
              "sourceType": "request",
              "path": "request.customerId"
            }
          }
        ],
        "resultPath": "data"
      }
    }
  ]
}
```

### 一期允许的节点

- `source`
- `call`
- `object`
- `list`
- `expr`
- `coalesce`

### 一期 sourceType

- `request`
- `context`
- `temp`
- `nodeOutput`
- `const`
- `item`

### 一期 list.ops

- `filter`
- `map`
- `compose`

## 兼容策略

- 前端统一编辑 `param-ast/v1`
- 后端负责将 AST 编译为现有 `paramPlans`
- 运行时一期继续执行现有结构
- 老配置继续可打开、可保存、可发布

## 三条实施泳道

### 后端泳道

- 定义 `param-ast/v1` DTO、错误码、校验器
- 新增 `GET /api/projects/{projectKey}/param-assembler/context?endpointId=...`
- 新增 `POST /api/projects/{projectKey}/param-assembler/draft`
- 新增 `POST /api/projects/{projectKey}/param-assembler/validate`
- 实现 `param-ast/v1 -> paramPlans` 编译器
- 将参数校验接入发布流程
- 统一别名：`expr/expression`、`resultPath/serviceResultPath`
- 补齐聚合接口缺口

### 前端泳道

- 在服务节点配置中接入新的参数装配入口
- 将 `ParamPlanBuilder.vue` 收敛为高级兼容模式
- 复用 `ServiceCallEditor.vue` 作为方法选择器和 schema 读取器
- 增加一期任务型装配面板
- 支持对象参数一键生成映射草稿
- 支持单层列表参数基础模式
- 补充 `class-aggregate` client 接口
- 保留“基础装配 / 高级模式”双入口

### 元数据泳道

- 持续保证参数 schema、返回 schema、模型 schema 上报完整
- 补齐类树、字段树、方法签名聚合信息
- 为 `class-aggregate` 增加 jar 回退能力
- 聚合 `code-snapshots` 与 `metadata` 两条链路输出
- 触达文件时同步修复历史乱码，统一 UTF-8 无 BOM

## 评审门禁

### 架构门禁

- 前端只允许编辑 `param-ast/v1`
- 一期禁止引入无定义语义的 `groupBy/reduce/enrich`
- 禁止依赖运行时内部临时字段漂移

### 后端门禁

- 发布前必须校验必填未映射、来源缺失、路径非法、调用配置缺失、列表结构非法
- 旧流程不能回归
- 不允许前端主流程依赖多条碎片接口拼装

### 前端门禁

- 主流程必须是“选方法 -> 生成结构 -> 选择来源 -> 预览/校验”
- 必填字段和未完成字段必须直接可见
- 列表模式必须独立建模

### 编码与质量门禁

- 所有新增或修改文件必须 UTF-8 无 BOM
- 触达文件存在乱码时必须同改修复
- 受影响模块必须完成构建和测试
- 变更完成后必须检查无 mojibake

## 验收标准

- 能配置含多个对象参数的方法
- 能配置单层列表参数，来源可为请求或 `serviceCall`
- 对象参数支持自动生成字段映射草稿
- 发布前能拦截必填字段未覆盖和明显类型不兼容
- 前端提供结构预览
- 旧 `paramPlans` 仍可打开和保存

## 开发顺序

1. 冻结 `param-ast/v1` 最小合同和错误码
2. 后端先完成 `context`、`draft`、`validate` 合同层
3. 元数据线同步补齐聚合查询缺口
4. 前端并行开发装配器骨架
5. 后端完成 `AST -> paramPlans` 编译器
6. 前后端联调对象参数与单层列表参数
7. 接入发布门禁、兼容测试、构建测试

## 统一协调结论

一期必须克制，先把合同、聚合接口、基础装配体验和发布校验做稳。

统一原则如下：

- 前端统一只编辑 `param-ast/v1`
- 后端统一负责校验和编译
- runtime 一期尽量复用
- 数组和集合只做到单层基础能力
