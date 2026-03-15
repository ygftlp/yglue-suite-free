# 运行时重构方案（2026-03-12）

## 1. 背景

当前运行时已经具备基础闭环能力，但从架构审计结果看，复杂流程场景仍存在几个设计级风险：

- branch 分支控制结果和业务返回值共用同一个 `returnValue`
- 动态入参解析结果写回共享的节点配置
- DAG 执行模型只有“按路径递归”，没有真正的 join / merge 语义
- 并发路径共享同一个上下文对象，控制流状态和业务变量没有分层

这些问题并不意味着“branch 不能读取全局变量”。真正需要调整的是：

- 允许 branch 继续读取全局业务变量
- 但必须把“控制流状态”从“业务上下文”里分离出来

## 2. 设计原则

### 2.1 保留的能力

- branch 条件可以读取全局上下文变量
- service / transformer / rest 等节点可以继续读写业务变量
- flow 执行结束后仍然输出最终业务结果

### 2.2 必须调整的边界

- `FlowDefinition` 和 `NodeDefinition` 必须视为只读定义对象
- 请求级执行数据不能写回定义对象
- 分支选路结果不能再借用全局 `returnValue`
- 控制流状态必须是当前路径私有或当前节点私有

## 3. 目标模型

建议把当前执行模型拆成三层：

### 3.1 定义层

只放不可变定义：

- `FlowDefinition`
- `NodeDefinition`
- `RestEntryPoint`

职责：

- 表示规则结构
- 不承载运行时态数据

### 3.2 执行上下文层

新增请求级执行态对象，例如：

- `ExecutionContext`
- `NodeExecutionState`
- `PathExecutionState`

建议职责划分如下：

- `ExecutionContext`
  - 当前请求共享的业务变量
  - ruleId
  - request payload
  - 事件总线句柄
  - 最终结果

- `NodeExecutionState`
  - 当前节点解析后的入参
  - 当前节点执行结果
  - 当前节点控制信号

- `PathExecutionState`
  - 当前执行路径的 visited 集
  - 当前路径局部控制状态

### 3.3 调度层

新增明确的 DAG 调度器，例如：

- `DagScheduler`
- `SequentialScheduler`

职责：

- 只决定“哪个节点什么时候执行”
- 不承载业务计算本身

## 4. 关键重构点

### 4.1 分支控制结果从 `returnValue` 中剥离

当前问题：

- branch 节点把目标节点 id 作为返回值返回
- `GraphExecutor` 再从全局 `returnValue` 读取下一跳

建议改成：

- 节点执行统一返回 `NodeExecutionResult`
- 对 branch 节点：
  - `businessValue` 可为空
  - `controlSignal.nextNodeId` 表示选中的目标节点

示意结构：

```java
public final class NodeExecutionResult {
    private final Object businessValue;
    private final ControlSignal controlSignal;
}

public final class ControlSignal {
    private final String nextNodeId;
    private final boolean stopFlow;
}
```

这样 branch 仍然可以读取全局业务变量，但不会污染全局业务返回值。

### 4.2 动态入参改成请求级存储

当前问题：

- `ParamResolveInterceptor` 把 `_resolvedArgs` 写入 `node.getConfig()`
- `FlowDefinition` 默认缓存后，会形成跨请求共享

建议改成：

- 入参解析结果放到 `NodeExecutionState`
- `ServiceNodeExecutor` 从 `NodeExecutionContext.getResolvedArgs()` 读取
- 禁止任何 interceptor 修改 `NodeDefinition.config`

建议接口：

```java
public final class NodeExecutionContext {
    private final FlowDefinition flow;
    private final NodeDefinition node;
    private final ExecutionContext executionContext;
    private final List<Object> resolvedArgs;
}
```

### 4.3 DAG 调度器补齐 join / merge 语义

当前问题：

- 多前驱节点的后继节点会被重复执行
- 多 entry path 共用 visited 集

建议改成“拓扑调度”：

1. 预计算每个节点的入度和前驱集合。
2. 节点执行完成后，标记当前节点完成。
3. 只有当目标节点所有必需前驱都完成后，目标节点才进入 ready 队列。
4. 每个节点默认只执行一次。
5. branch 只激活被选中的后继边，未激活边不计入 ready 判定。

这才是真正可上线的 DAG 语义。

### 4.4 业务变量与控制变量分层

建议：

- `ExecutionContext.variables`
  - 业务全局变量
  - branch 可读
  - service / transformer 可读写

- `PathExecutionState`
  - visited
  - 当前路径激活边
  - 局部调度状态

- `NodeExecutionState`
  - 当前节点解析参数
  - 当前节点执行结果
  - 当前节点控制信号

结论就是：

- 业务变量允许共享
- 控制变量必须隔离

## 5. branch 场景的推荐语义

### 5.1 允许读取的内容

branch 应允许读取：

- `request.path`
- `request.query`
- `request.body`
- 先前节点写入的全局变量
- tempVars

### 5.2 不建议再复用的内容

branch 不应再依赖：

- 全局 `returnValue` 推导下一跳
- 共享配置上的临时字段

### 5.3 建议输出

branch 节点的标准输出应分成两部分：

- `selectedTargetNodeId`
- 可选的 branch 业务结果

如果没有业务结果，可以只返回控制信号。

## 6. 动态入参的推荐语义

建议把动态入参解析拆成两个阶段：

### 6.1 解析阶段

由 `ParamResolveInterceptor` 或新的 `ArgumentPlanningInterceptor` 完成：

- 根据 `paramPlans` 解析 tempPlans
- 根据 `argPlans` 生成 `resolvedArgs`
- 结果只写入当前 `NodeExecutionState`

### 6.2 调用阶段

由 `ServiceNodeExecutor` 完成：

- 读取 `resolvedArgs`
- 做类型转换
- 做校验
- 调用目标 bean method

这样职责更清楚，也不会再污染定义对象。

## 7. 执行框架的推荐流程

建议把运行时执行统一成下面这条链：

1. `FlowDispatchInterceptor` / `FlowOrchestratedAspect` 构建请求输入
2. `RuleEngine` 加载只读 `FlowDefinition`
3. `ExecutionContextFactory` 创建请求级执行上下文
4. 调度器根据 flow 结构启动 ready 节点
5. 节点执行前构建 `NodeExecutionContext`
6. interceptor 只写请求级状态，不改定义对象
7. 节点执行返回 `NodeExecutionResult`
8. 调度器依据 `controlSignal` 决定下一批 ready 节点
9. flow 结束后汇总最终业务结果

## 8. 分阶段落地建议

### Phase 1

低风险收口：

- 禁止 `_resolvedArgs` 写回 `NodeDefinition.config`
- 为节点执行引入请求级 `resolvedArgs`
- branch 控制结果从 `returnValue` 中剥离

### Phase 2

核心框架改造：

- 新增 `NodeExecutionResult`
- 新增 `ExecutionContext`
- 新增 `PathExecutionState`
- GraphExecutor 拆成真正的调度器

### Phase 3

复杂图能力：

- join / merge 语义
- 多分支并发一致性
- 节点级事件与 trace
- 路径级超时与取消

## 9. 测试补强建议

上线前至少补这些测试：

1. 并发两个请求打同一条缓存 flow，动态入参不能串值。
2. 菱形 DAG 合流节点只能执行一次。
3. branch 读取全局变量选路正确。
4. branch 的 tempVars expression 能被正确求值。
5. 多 entry path 并发执行时，控制流状态互不污染。
6. JSON array body 在 REST 接管链路里能被正确传入 flow。

## 10. 最终判断

针对“分支可能使用全局变量”这个前提，上述方案是成立的，而且比现在更清晰：

- 保留全局业务变量给 branch 使用
- 隔离分支控制信号
- 隔离请求级动态入参
- 重建真正的 DAG 调度语义

这条路线不会削弱 branch 的表达能力，反而会让运行时在复杂流程和并发场景下更可靠。
