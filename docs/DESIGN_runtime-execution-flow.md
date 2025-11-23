# 运行时流程执行过程梳理

## 1. 整体执行流程

```
REST 请求
    ↓
FlowOrchestratedAspect (AOP 拦截)
    ↓
LiteFlowRuleEngine.execute()
    ↓
FlowDefinitionToLiteFlowConverter.convertAndRegister()
    ↓
LiteFlowExecutor.execute2Resp()
    ↓
ServiceNodeComponent.process() (每个节点)
    ↓
NodeExecutor.execute() (具体执行器)
    ↓
结果保存到 FlowContext
    ↓
返回 FlowExecutionResult
```

## 2. 详细执行步骤

### 2.1 流程启动阶段

**入口：`LiteFlowRuleEngine.execute()`**

```java
public FlowExecutionResult execute(String ruleId, Map<String, Object> input) throws IOException {
    // 1. 加载流程定义
    FlowDefinition definition = loadDefinition(ruleId);
    
    // 2. 转换为 LiteFlow 规则并注册
    String chainName = converter.convertAndRegister(definition);
    
    // 3. 使用 LiteFlow 执行
    LiteflowResponse response = liteFlowExecutor.execute2Resp(chainName, input);
    
    // 4. 转换执行结果
    return convertResponse(ruleId, response);
}
```

**关键点：**
- `input` 参数是 REST 请求的原始数据（Map）
- `convertAndRegister()` 会将 `FlowDefinition` 转换为 LiteFlow EL 表达式
- LiteFlow 会根据 EL 表达式确定节点执行顺序

### 2.2 规则转换阶段

**入口：`FlowDefinitionToLiteFlowConverter.convertAndRegister()`**

**转换过程：**
1. **解析节点和边**：从 `FlowDefinition` 中提取 `nodes` 和 `edges`
2. **构建图结构**：构建邻接表和入度表
3. **生成 EL 表达式**：
   - 找到所有起始节点（入度为 0 的节点）
   - 从每个起始节点开始，按照边的方向构建链表达式
   - 例如：`chain("chain1") = THEN(service_node1, service_node2, transformer_node3)`
4. **注册节点组件**：将节点定义注册到 `ServiceNodeComponent.componentRegistry`
5. **注册 LiteFlow 规则**：将 EL 表达式注册到 LiteFlow

**节点 ID 映射规则：**
- `service` 节点：`service_{nodeId}` 或 `{bean}.{method}`
- `transformer` 节点：`script_{nodeId}`
- `branch` 节点：`branch_{nodeId}`
- `transaction` 节点：`txn_{variant}_{nodeId}`

### 2.3 LiteFlow 执行阶段

**入口：`LiteFlowExecutor.execute2Resp()`**

**执行过程：**
1. **解析 EL 表达式**：LiteFlow 解析链表达式，构建执行计划
2. **按顺序执行节点**：
   - 根据 EL 表达式中的 `THEN`、`WHEN`、`IF` 等关键字确定执行顺序
   - 对于 `THEN(a, b, c)`，按顺序执行 a → b → c
   - 对于 `WHEN(a, b, c)`，并行执行 a、b、c
   - 对于 `IF(x, a, b)`，根据 x 的结果选择执行 a 或 b
3. **调用节点组件**：对每个节点，调用对应的 `NodeComponent.process()` 方法

### 2.4 节点执行阶段

**入口：`ServiceNodeComponent.process()`**

**执行流程：**
```java
public void process() throws Exception {
    // 1. 获取组件 ID
    String componentId = this.getTag() != null ? this.getTag() : this.getNodeId();
    
    // 2. 从注册表获取节点定义
    ComponentInfo info = componentRegistry.get(componentId);
    NodeDefinition nodeDefinition = info.nodeDefinition;
    FlowDefinition flowDefinition = info.flowDefinition;
    
    // 3. 获取或创建 FlowContext
    FlowContext flowContext = getFlowContext();
    
    // 4. 获取对应的执行器
    NodeExecutor executor = executorRegistry.get(nodeDefinition.getType());
    
    // 5. 创建执行上下文
    NodeExecutionContext executionContext = new NodeExecutionContext(
        flowDefinition, nodeDefinition, flowContext, childRunner
    );
    
    // 6. 执行节点
    Object result = executor.execute(executionContext);
    
    // 7. 保存结果到 LiteFlow Context
    if (result != null) {
        saveResultToContext(result, componentId);
    }
}
```

**关键点：**
- `getFlowContext()` 会从 LiteFlow Context 中提取数据，创建或复用 `FlowContext`
- `saveResultToContext()` 会将节点执行结果保存到 LiteFlow Context，使用 `componentId` 作为 key

## 3. 节点执行顺序

### 3.1 顺序执行（THEN）

**EL 表达式：** `THEN(a, b, c)`

**执行顺序：**
```
节点 a → 节点 b → 节点 c
```

**数据流：**
```
节点 a 执行 → 结果保存到 ctx[a] → 
节点 b 执行（可访问 ctx[a]）→ 结果保存到 ctx[b] → 
节点 c 执行（可访问 ctx[a], ctx[b]）→ 结果保存到 ctx[c]
```

### 3.2 并行执行（WHEN）

**EL 表达式：** `WHEN(a, b, c)`

**执行顺序：**
```
节点 a、b、c 并行执行
```

**数据流：**
```
节点 a、b、c 同时执行 → 
各自结果保存到 ctx[a], ctx[b], ctx[c] → 
后续节点可以访问所有并行节点的结果
```

### 3.3 条件执行（IF）

**EL 表达式：** `IF(x, a, b)`

**执行顺序：**
```
节点 x 执行 → 根据结果选择执行 a 或 b
```

**数据流：**
```
节点 x 执行 → 结果判断 → 
如果 true：执行节点 a → 结果保存到 ctx[a]
如果 false：执行节点 b → 结果保存到 ctx[b]
```

### 3.4 复杂流程示例

**EL 表达式：** `THEN(a, WHEN(b, c), IF(d, e, f), g)`

**执行顺序：**
```
1. 节点 a 执行
2. 节点 b、c 并行执行（可访问 ctx[a]）
3. 节点 d 执行（可访问 ctx[a], ctx[b], ctx[c]）
4. 根据 d 的结果，执行 e 或 f
5. 节点 g 执行（可访问所有前面节点的结果）
```

## 4. 数据流

### 4.1 数据存储位置

**LiteFlow Context：**
- LiteFlow 框架提供的上下文对象
- 通过 `ServiceNodeComponent.saveResultToContext()` 保存节点结果
- Key 格式：`componentId`（如 `service_node1`、`script_node2`）

**FlowContext：**
- 运行时框架封装的上下文对象
- 包含 `globals`（全局数据）、`attributes`（属性）、`nodeStack`（节点作用域栈）
- 通过 `FlowContext.set()` 保存数据
- 通过 `FlowContext.get()` 获取数据

### 4.2 数据流转路径

```
REST 请求参数
    ↓
LiteFlow Context (input Map)
    ↓
FlowContext (从 LiteFlow Context 提取)
    ↓
节点执行器（通过 ParamResolver 解析参数）
    ↓
节点执行结果
    ↓
FlowContext.set() / FlowContext.setReturnValue()
    ↓
ServiceNodeComponent.saveResultToContext()
    ↓
LiteFlow Context (保存结果)
    ↓
下一个节点（从 LiteFlow Context 获取）
    ↓
...
    ↓
最终结果（从 LiteFlow Context 提取）
    ↓
FlowExecutionResult
```

### 4.3 节点间数据传递

**方式1：通过 FlowContext（推荐）**

```java
// 节点 A 保存结果
flowContext.set("resultA", resultA);
flowContext.setReturnValue(resultA);

// 节点 B 获取结果
Object resultA = flowContext.get("resultA");
// 或通过 ParamResolver
Map<String, Object> resolverConfig = Map.of(
    "type", "context",
    "path", "resultA"
);
Object value = ParamResolver.resolve(resolverConfig, flowContext);
```

**方式2：通过 LiteFlow Context（自动）**

```java
// ServiceNodeComponent 自动保存
saveResultToContext(result, componentId);
// 结果保存为：ctx[componentId] = result

// 下一个节点可以通过 ParamResolver 访问
Map<String, Object> resolverConfig = Map.of(
    "type", "context",
    "path", "service_node1"  // componentId
);
Object value = ParamResolver.resolve(resolverConfig, flowContext);
```

**方式3：通过 `as` 配置（节点别名）**

```json
{
  "type": "service",
  "id": "node1",
  "config": {
    "as": "userService"  // 结果保存为 ctx["userService"]
  }
}
```

### 4.4 转换器节点的数据流

**TransformerNodeExecutor 的数据获取优先级：**

1. **配置的 inputSource**：如果节点配置了 `inputSource`，优先使用
2. **`_lastNodeResult`**：最后一个节点的输出（由 FlowExecutor 自动保存）
3. **`ret`**：返回值（`FlowContext.setReturnValue()` 设置）
4. **上下文中的其他值**：向后兼容

**数据转换流程：**

```
上游节点结果
    ↓
getUpstreamOutput() (获取输入)
    ↓
applyFieldMappings() (字段映射)
    ↓
executeGroovyScript() (Groovy 脚本执行，可选)
    ↓
结果保存到 FlowContext
    ↓
返回结果
```

## 5. 当前存在的问题和优化建议

### 5.1 数据流问题

**问题1：数据存储位置不统一**
- **现状**：数据既存储在 LiteFlow Context，又存储在 FlowContext
- **影响**：可能导致数据不一致，增加调试难度
- **建议**：
  - 统一使用 FlowContext 作为数据存储
  - LiteFlow Context 仅作为 FlowContext 的载体
  - 在 `ServiceNodeComponent` 中确保数据同步

**问题2：节点结果保存方式不明确**
- **现状**：节点结果通过 `saveResultToContext()` 保存到 LiteFlow Context，使用 `componentId` 作为 key
- **影响**：下一个节点需要通过 `componentId` 访问，不够直观
- **建议**：
  - 优先使用节点的 `as` 配置作为 key
  - 如果没有 `as` 配置，使用 `componentId`
  - 同时保存到 FlowContext，确保数据一致性

**问题3：转换器节点数据获取逻辑复杂**
- **现状**：`getUpstreamOutput()` 有多个优先级，逻辑复杂
- **影响**：可能导致数据获取不准确
- **建议**：
  - 简化优先级逻辑
  - 明确文档说明数据获取规则
  - 增加日志输出，便于调试

### 5.2 执行顺序问题

**问题1：节点执行顺序依赖 EL 表达式生成**
- **现状**：执行顺序完全由 `generateLiteFlowEL()` 生成的 EL 表达式决定
- **影响**：如果 EL 表达式生成错误，执行顺序会错误
- **建议**：
  - 增加 EL 表达式验证
  - 增加执行顺序的可视化展示
  - 增加单元测试覆盖各种流程场景

**问题2：并行节点结果合并逻辑不明确**
- **现状**：`WHEN(a, b, c)` 并行执行后，结果如何合并不明确
- **影响**：后续节点可能无法正确访问并行节点的结果
- **建议**：
  - 明确并行节点结果的合并规则
  - 增加文档说明
  - 增加示例代码

### 5.3 性能优化建议

**建议1：减少数据复制**
- **现状**：数据在 LiteFlow Context 和 FlowContext 之间多次复制
- **建议**：使用引用而不是复制，减少内存开销

**建议2：优化节点注册表查找**
- **现状**：每次节点执行都需要从 `componentRegistry` 查找
- **建议**：考虑使用更高效的数据结构（如 `ConcurrentHashMap` 已经足够）

**建议3：增加执行结果缓存**
- **现状**：每次执行都重新计算
- **建议**：对于相同输入的节点，可以考虑缓存结果（需要谨慎处理副作用）

### 5.4 可观测性优化

**建议1：增加执行日志**
- **现状**：日志不够详细
- **建议**：
  - 记录每个节点的执行开始和结束时间
  - 记录数据流转过程
  - 记录节点执行结果

**建议2：增加执行追踪**
- **现状**：无法追踪整个流程的执行路径
- **建议**：
  - 增加 `executionTrace` 记录执行路径
  - 记录每个节点的输入和输出
  - 提供执行报告接口

**建议3：增加错误处理**
- **现状**：错误处理不够完善
- **建议**：
  - 增加节点执行失败的回滚机制
  - 增加错误重试机制
  - 增加错误报告和通知

## 6. 优化实施计划

### Phase 1: 数据流统一（高优先级）

1. **统一数据存储**
   - 修改 `ServiceNodeComponent`，确保数据同时保存到 FlowContext 和 LiteFlow Context
   - 修改 `getFlowContext()`，确保从 LiteFlow Context 正确提取数据

2. **优化节点结果保存**
   - 优先使用节点的 `as` 配置作为 key
   - 如果没有 `as` 配置，使用 `componentId`
   - 同时保存到 FlowContext

3. **简化转换器节点数据获取**
   - 简化 `getUpstreamOutput()` 逻辑
   - 明确优先级规则
   - 增加日志输出

### Phase 2: 执行顺序优化（中优先级）

1. **增加 EL 表达式验证**
   - 验证 EL 表达式的正确性
   - 检查节点依赖关系

2. **明确并行节点结果合并规则**
   - 定义并行节点结果的合并规则
   - 更新文档和示例

### Phase 3: 性能优化（低优先级）

1. **减少数据复制**
   - 使用引用而不是复制
   - 优化数据传递路径

2. **增加执行结果缓存**
   - 实现缓存机制
   - 处理副作用问题

### Phase 4: 可观测性增强（中优先级）

1. **增加执行日志**
   - 记录节点执行时间
   - 记录数据流转过程

2. **增加执行追踪**
   - 实现执行路径追踪
   - 提供执行报告接口

3. **增强错误处理**
   - 实现回滚机制
   - 实现重试机制

## 7. 数据流示例

### 示例1：简单顺序执行

**流程：** 节点 A → 节点 B → 节点 C

**数据流：**
```
1. REST 请求参数 → LiteFlow Context (input)
2. 节点 A 执行：
   - 输入：从 input 解析参数
   - 执行：调用服务方法
   - 输出：resultA
   - 保存：ctx["service_nodeA"] = resultA, flowContext.set("resultA", resultA)
3. 节点 B 执行：
   - 输入：从 ctx["service_nodeA"] 或 flowContext.get("resultA") 获取
   - 执行：调用服务方法
   - 输出：resultB
   - 保存：ctx["service_nodeB"] = resultB, flowContext.set("resultB", resultB)
4. 节点 C 执行：
   - 输入：从 ctx["service_nodeB"] 或 flowContext.get("resultB") 获取
   - 执行：调用服务方法
   - 输出：resultC
   - 保存：ctx["service_nodeC"] = resultC, flowContext.set("resultC", resultC)
5. 最终结果：从 ctx["service_nodeC"] 或 flowContext.get("resultC") 获取
```

### 示例2：包含转换器的流程

**流程：** 节点 A → 转换器 B → 节点 C

**数据流：**
```
1. REST 请求参数 → LiteFlow Context (input)
2. 节点 A 执行：
   - 输出：resultA
   - 保存：ctx["service_nodeA"] = resultA, flowContext.set("_lastNodeResult", resultA)
3. 转换器 B 执行：
   - 输入：从 flowContext.get("_lastNodeResult") 获取 resultA
   - 转换：应用字段映射和 Groovy 脚本
   - 输出：transformedResult
   - 保存：ctx["script_nodeB"] = transformedResult, flowContext.set("_lastNodeResult", transformedResult)
4. 节点 C 执行：
   - 输入：从 flowContext.get("_lastNodeResult") 获取 transformedResult
   - 输出：resultC
   - 保存：ctx["service_nodeC"] = resultC
5. 最终结果：resultC
```

### 示例3：并行执行流程

**流程：** 节点 A → WHEN(节点 B, 节点 C) → 节点 D

**数据流：**
```
1. 节点 A 执行：
   - 输出：resultA
   - 保存：ctx["service_nodeA"] = resultA
2. 节点 B、C 并行执行：
   - 节点 B：输入 resultA，输出 resultB，保存 ctx["service_nodeB"] = resultB
   - 节点 C：输入 resultA，输出 resultC，保存 ctx["service_nodeC"] = resultC
3. 节点 D 执行：
   - 输入：可以访问 ctx["service_nodeA"]、ctx["service_nodeB"]、ctx["service_nodeC"]
   - 输出：resultD
   - 保存：ctx["service_nodeD"] = resultD
4. 最终结果：resultD
```

## 8. 关键代码位置

### 8.1 流程执行入口
- `LiteFlowRuleEngine.execute()` - 流程执行入口
- `FlowDefinitionToLiteFlowConverter.convertAndRegister()` - 规则转换

### 8.2 节点执行
- `ServiceNodeComponent.process()` - 节点组件执行入口
- `NodeExecutor.execute()` - 节点执行器接口
- `ServiceNodeExecutor.execute()` - 服务节点执行器
- `TransformerNodeExecutor.execute()` - 转换器节点执行器

### 8.3 数据流
- `FlowContext` - 流程上下文
- `ServiceNodeComponent.getFlowContext()` - 获取 FlowContext
- `ServiceNodeComponent.saveResultToContext()` - 保存结果到 LiteFlow Context
- `ParamResolver.resolve()` - 参数解析器

### 8.4 EL 表达式生成
- `FlowDefinitionToLiteFlowConverter.generateLiteFlowEL()` - 生成 EL 表达式
- `FlowDefinitionToLiteFlowConverter.buildChainExpression()` - 构建链表达式


