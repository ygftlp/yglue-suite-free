# 零入侵编排运行时设计（服务调用 + 参数构造 + List 场景）

## 1. 背景与问题

当前平台目标是单项目低代码编排，核心诉求是：

1. 不改业务代码，不新增业务服务类，尽量零入侵。
2. 在编排中可调用项目内已有 Spring 服务获取分支条件和参数。
3. 参数构造不只是字段映射，必须支持“调用服务补数”。
4. 大量参数为 `List` 类型，需要稳定、可控、高性能的处理能力。
5. 运行时性能不能依赖 Groovy 解释执行路径。

本文给出可落地方案：`结构化执行计划 + 零入侵服务调用 + List 参数构造引擎`。

---

## 2. 设计目标与非目标

### 2.1 目标

1. 保持零入侵：业务项目无需新增服务代码，仅复用已有 Bean/HTTP 接口。
2. 替代脚本主路径：分支与参数构造从 Groovy 转为结构化 DSL。
3. 支持隐式服务调用：服务可不出现在画布节点，但可在条件/参数中调用。
4. 强化 List 支持：过滤、映射、聚合、关联补数、分页切片等。
5. 可治理：白名单、超时、重试、降级、缓存、观测、审计。
6. 高性能：发布时编译，运行时执行计划直通。

### 2.2 非目标

1. 不支持任意脚本直接访问任意 Bean。
2. 不支持跨项目服务目录与跨租户编排能力。
3. 不在一期引入分布式规则中心。

---

## 3. 零入侵原则

1. 不要求业务方新增注解或实现新接口。
2. 仅依赖已有机制：
`IDEA 插件扫描元数据 -> Orchestrator 存储 -> Runtime 按发布产物执行`。
3. 服务调用入口统一由平台内置 `InvocationAdapter` 完成，不要求业务代码适配。
4. 对业务代码的唯一前提：目标方法是 Spring 容器可获取且可访问的公开方法。

---

## 4. 总体架构

```text
IDEA 插件（扫描项目现有类/方法）
  -> Orchestrator（函数白名单配置 + DSL 校验 + 发布编译）
  -> 发布产物（ExecutionPlan）
  -> Runtime（零脚本主路径执行）
      - ConditionEngine
      - ParamBuilderEngine
      - ServiceCallEngine
      - ListPipelineEngine
```

### 4.1 两阶段模型

1. 发布时（Compile Time）
`Flow DSL -> ExecutionPlan`，完成校验、绑定、优化。

2. 运行时（Run Time）
按 ExecutionPlan 直接执行，不解析 Groovy，不做反射扫描。

---

## 5. 数据模型（DSL V2）

## 5.1 值来源 ValueSource

```json
{
  "kind": "const | ctx | serviceCall | listPipeline | expr",
  "typeHint": "java.lang.String | java.util.List<com.xx.Dto> | ...",
  "value": {}
}
```

说明：
1. `ctx`：上下文路径读取。
2. `serviceCall`：调用项目内已有服务。
3. `listPipeline`：对 List 做结构化处理。
4. `expr`：结构化表达式（非 Groovy）。

### 5.2 条件树 ConditionTree

```json
{
  "op": "and",
  "args": [
    { "op": "gt", "left": { "kind": "ctx", "value": "request.body.amount" }, "right": { "kind": "const", "value": 1000 } },
    { "op": "eq", "left": { "kind": "serviceCall", "value": { "fn": "risk.check", "args": [{ "kind": "ctx", "value": "request.body.userId" }] } }, "right": { "kind": "const", "value": true } }
  ]
}
```

支持操作符：
`and/or/not/eq/ne/gt/ge/lt/le/in/notIn/isNull/notNull/contains/matches`。

### 5.3 参数构造 ParamSpec

```json
{
  "target": "req.items",
  "source": {
    "kind": "listPipeline",
    "value": {
      "input": { "kind": "serviceCall", "value": { "fn": "order.queryItems", "args": [{ "kind": "ctx", "value": "request.body.orderId" }] } },
      "steps": [
        { "op": "filter", "predicate": { "op": "gt", "left": { "kind": "ctx", "value": "$.qty" }, "right": { "kind": "const", "value": 0 } } },
        { "op": "map", "mapping": {
          "skuId": { "kind": "ctx", "value": "$.skuId" },
          "price": { "kind": "serviceCall", "value": { "fn": "price.query", "args": [{ "kind": "ctx", "value": "$.skuId" }] } }
        }}
      ]
    }
  },
  "onError": "fail | default | skip",
  "defaultValue": []
}
```

### 5.4 服务调用 ServiceCallSpec

```json
{
  "fn": "price.query",
  "args": [{ "kind": "ctx", "value": "$.skuId" }],
  "timeoutMs": 50,
  "retry": { "maxAttempts": 1, "backoffMs": 0 },
  "cache": { "scope": "request", "ttlMs": 0 },
  "fallback": { "kind": "const", "value": null }
}
```

---

## 6. 零入侵服务调用设计

## 6.1 函数目录（Function Catalog）

来源：
1. IDEA 插件上报项目类/方法元数据。
2. 编排台选择方法并配置别名 `fn`，如 `price.query`。
3. 发布时固化为 `FunctionBinding`：
`fn -> beanName + methodSignature + argMeta + returnType`。

关键点：
1. 只使用已有服务，不新增业务类。
2. 只允许发布时白名单绑定的方法，运行时不可任意调用。

### 6.2 运行时绑定

1. 启动时把 `FunctionBinding` 编译为 `MethodHandle` 并缓存。
2. 调用时按绑定直接执行，不做反射扫描。
3. 参数转换使用预编译 `ArgumentPlan`，减少运行时判断。

### 6.3 调用适配器

1. `SpringBeanInvocationAdapter`：调用项目内 Bean 方法。
2. `HttpInvocationAdapter`：调用项目内/外 HTTP 接口（可选）。
3. 后续可扩展 `RPCInvocationAdapter`，不影响 DSL。

---

## 7. List 参数处理（重点）

## 7.1 ListPipeline 操作集

基础操作：
`filter/map/flatMap/distinct/sort/limit/offset/groupBy/reduce/join/enrich`。

典型场景：
1. 从上下文拿 List 并筛选。
2. 先调用服务拿 List，再映射为目标 DTO List。
3. 对 List 每项调用服务补数（enrich）。
4. 多 List 关联（join）后组装参数。
5. 取子集用于下游分页参数。

### 7.2 Enrich 与 N+1 控制

问题：
List 每项服务调用可能造成 N+1。

策略：
1. 优先支持批量函数：
`price.batchQuery(List<skuId>)`。
2. 若仅单条函数可用：
请求级缓存 + 并发上限 + 去重调用。
3. 引入 `enrich.batchKey` 让执行器优先做批处理。

### 7.3 类型系统

1. 列表元素类型 `elementType` 在发布时推断并固化。
2. 编译器校验 `map` 目标字段类型兼容性。
3. 不兼容时发布失败，避免运行时类型异常。

---

## 8. 执行引擎设计

## 8.1 编译阶段（Orchestrator）

步骤：
1. DSL 语法校验。
2. 函数白名单校验（`fn` 必须已绑定）。
3. 条件树类型校验。
4. 参数计划类型校验（含 List 元素类型）。
5. 生成 `ExecutionPlan`。
6. 对 `ExecutionPlan` 生成 checksum，供 Runtime 缓存。

产物建议：
`flowCode.plan.json`（可选二进制优化版）。

### 8.2 运行阶段（Runtime）

顺序：
1. 加载 `ExecutionPlan`。
2. 执行分支条件树。
3. 执行参数构造计划（含 ListPipeline）。
4. 调用目标服务节点。
5. 写回上下文。

执行保障：
1. 每次服务调用应用 timeout/retry/fallback。
2. 调用结果按策略缓存（request scope）。
3. 严格区分失败策略：
`fail-fast / fallback-default / skip-item`。

---

## 9. 性能策略

1. 去脚本主路径：条件与参数构造默认不走 Groovy。
2. MethodHandle 缓存：函数绑定预热。
3. 请求级缓存：同参数调用去重。
4. List 批处理优先：避免 N+1。
5. 执行计划 checksum：未变更不重编译。
6. 并发控制：enrich 并发池限流。

建议指标：
1. `condition_eval_p95_ms`
2. `param_build_p95_ms`
3. `service_call_p95_ms`
4. `list_enrich_batch_ratio`
5. `request_cache_hit_ratio`
6. `groovy_slow_lane_ratio`

---

## 10. 安全与治理

1. 函数白名单：仅允许发布绑定函数。
2. 函数分级：
`READ_ONLY`（可用于分支）与 `MUTATING`（仅服务节点）。
3. 超时上限：
平台配置全局上限，单调用不能突破。
4. 熔断与降级：
按 `fn` 维度统计失败率，触发降级策略。
5. 审计：
记录 `flowCode + nodeId + fn + latency + resultCode`。

---

## 11. 与现有系统兼容

## 11.1 兼容策略

1. 保留 Groovy 旧能力，标记为 `legacy-slow-lane`。
2. 新建流程默认使用 DSL V2。
3. 旧流程可按节点逐步迁移。

### 11.2 回滚策略

1. 运行时支持按 flowVersion 回滚至旧版本。
2. 若 V2 执行异常，可切回 V1（Groovy）版本。

---

## 12. 分阶段落地计划

## 阶段 1（2 周）

1. 定义 DSL V2（ConditionTree + ParamSpec + ServiceCallSpec + ListPipeline）。
2. Runtime 增加 `FunctionCatalog` 与 `MethodHandle` 调用器。
3. 新增请求级缓存与调用超时控制。

## 阶段 2（2~4 周）

1. 实现 ListPipeline（filter/map/enrich/join/groupBy/reduce）。
2. 编译器类型校验与发布拦截。
3. 指标、日志、审计接入。

## 阶段 3（2 周）

1. 迁移高频流程至 V2。
2. 对比基线性能并收敛慢路径占比。
3. 输出迁移规范与最佳实践。

---

## 13. 验收标准

1. 不新增业务服务代码即可完成分支与参数补数编排。
2. 条件和参数构造不依赖 Groovy 也可覆盖 80%+ 场景。
3. List 参数场景支持率达到目标项目主要用例。
4. 编排框架额外开销（不含外部 I/O）p95 小于 3ms。
5. Groovy 慢路径占比低于 10%，后续压降至 5%。

---

## 14. 示例：零入侵 + List 参数补数

```json
{
  "condition": {
    "op": "and",
    "args": [
      { "op": "eq", "left": { "kind": "ctx", "value": "request.body.channel" }, "right": { "kind": "const", "value": "APP" } },
      { "op": "eq", "left": { "kind": "serviceCall", "value": { "fn": "risk.check", "args": [{ "kind": "ctx", "value": "request.body.userId" }], "timeoutMs": 30 } }, "right": { "kind": "const", "value": true } }
    ]
  },
  "params": [
    {
      "target": "req.items",
      "source": {
        "kind": "listPipeline",
        "value": {
          "input": { "kind": "serviceCall", "value": { "fn": "cart.queryItems", "args": [{ "kind": "ctx", "value": "request.body.cartId" }] } },
          "steps": [
            { "op": "filter", "predicate": { "op": "gt", "left": { "kind": "ctx", "value": "$.qty" }, "right": { "kind": "const", "value": 0 } } },
            { "op": "enrich", "fields": { "price": { "kind": "serviceCall", "value": { "fn": "price.query", "args": [{ "kind": "ctx", "value": "$.skuId" }], "cache": { "scope": "request" } } } } },
            { "op": "map", "mapping": {
              "skuId": { "kind": "ctx", "value": "$.skuId" },
              "qty": { "kind": "ctx", "value": "$.qty" },
              "price": { "kind": "ctx", "value": "$.price" }
            }}
          ]
        }
      }
    }
  ]
}
```

这个示例满足：
1. 零入侵调用已有服务。
2. 分支条件依赖内部服务结果。
3. List 参数通过结构化流水线构造，不依赖 Groovy。

