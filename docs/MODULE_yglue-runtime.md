# yglue-runtime 模块文档

## 模块概述

`yglue-runtime` 是 YGFlow Suite 的运行时核心引擎模块，负责流程定义解析、节点执行、数据转换、参数解析等核心运行时能力。该模块是流程编排的执行基础，提供了完整的流程执行框架。

## 模块职责

- **流程执行引擎**：基于 LiteFlow 实现流程编排和执行
- **节点执行器**：提供各种类型节点的执行器实现（服务节点、分支节点、转换节点等）
- **参数解析引擎**：支持多种参数来源和解析策略
- **数据转换引擎**：支持 DSL 驱动的数据转换和 Groovy 脚本执行
- **表达式引擎**：支持 SpEL 表达式计算
- **验证器引擎**：支持参数验证和对象字段验证

## 核心能力

### 1. 流程执行引擎

**核心类：**
- `RuleEngine`: 规则引擎接口，负责流程规则的加载和执行
- `LiteFlowRuleEngine`: 基于 LiteFlow 的规则引擎实现
- `FlowExecutor`: 流程执行器，负责节点调度和上下文管理

**主要功能：**
- 流程定义解析和注册
- 节点执行顺序控制
- 执行上下文管理
- 异常处理和错误传播

### 2. 节点执行器

**支持的节点类型：**
- `service`: 服务节点，调用业务服务方法
- `branch`: 分支节点，根据条件选择执行路径
- `transformer`: 转换节点，执行数据转换
- `if`: 条件节点，支持条件判断
- `set`: 设置节点，设置上下文变量
- `log`: 日志节点，记录执行日志
- `delay`: 延迟节点，执行延迟等待
- `call`: 调用节点，调用其他流程

**核心接口：**
- `NodeExecutor`: 节点执行器接口
- `NodeExecutionContext`: 节点执行上下文
- `NodeExecutorRegistry`: 节点执行器注册表

### 3. 参数解析引擎

**支持的参数来源：**
- `request`: HTTP 请求参数（路径变量、查询参数、请求体）
- `context`: 流程上下文变量
- `expression`: SpEL 表达式计算结果
- `constant`: 常量值

**核心类：**
- `ParamResolver`: 参数解析器接口
- `ResolveContext`: 解析上下文
- `ResolveResult`: 解析结果

### 4. 数据转换引擎

**支持的转换方式：**
- **字段映射**：直接映射字段
- **集合处理**：支持数组和集合的遍历转换
- **条件转换**：根据条件选择不同的转换逻辑
- **脚本转换**：Groovy 脚本执行

**核心类：**
- `TransformerExecutor`: 转换器执行器接口
- `DefaultTransformerExecutor`: 默认转换器实现
- `TransformerTemplate`: 转换模板定义
- `TransformerExecutionContext`: 转换执行上下文

### 5. 表达式引擎

**支持的表达式：**
- SpEL (Spring Expression Language)
- 支持上下文变量访问
- 支持 Map 键值访问（点号语法）

**核心类：**
- `ExpressionEngine`: 表达式引擎接口
- `SpelExpressionEngine`: SpEL 表达式引擎实现
- `ExpressionEvaluationContext`: 表达式求值上下文

### 6. 验证器引擎

**支持的验证类型：**
- `required`: 必填验证
- `notEmpty`: 非空验证
- `notBlank`: 非空白验证
- `type`: 类型验证
- `length`: 长度验证
- `range`: 范围验证
- `regex`: 正则表达式验证
- `expression`: 表达式验证
- `custom`: 自定义验证

**核心类：**
- `ValidatorEngine`: 验证器引擎接口
- `DefaultValidatorEngine`: 默认验证器引擎实现
- `Validator`: 验证器接口
- `ValidationException`: 验证异常

### 7. Bean Validation 集成

**功能：**
- 自动提取 Java Bean Validation 注解（@NotNull, @Size, @Pattern 等）
- 支持对象字段的自动验证
- 与自定义验证器无缝集成

**核心类：**
- `BeanValidationExtractor`: Bean Validation 注解提取器

## 技术特性

### 1. 基于 LiteFlow
- 使用 LiteFlow 作为流程编排底层框架
- 支持复杂的流程控制逻辑
- 高性能的流程执行

### 2. 插件化架构
- 节点执行器可扩展
- 参数解析器可扩展
- 表达式引擎可扩展

### 3. 上下文管理
- `FlowContext`: 流程执行上下文
- 支持节点间数据传递
- 支持变量作用域管理

### 4. 错误处理
- 统一的异常处理机制
- 支持错误传播和恢复
- 详细的错误信息记录

## 依赖关系

### 核心依赖
- `yglue-annotations`: 注解定义
- `spring-context`: Spring 上下文支持
- `spring-expression`: SpEL 表达式支持
- `jackson-databind`: JSON 处理
- `groovy`: Groovy 脚本执行
- `liteflow-core`: LiteFlow 核心框架
- `liteflow-spring`: LiteFlow Spring 集成

### 被依赖模块
- `yglue-runtime-spring-boot2`: Spring Boot 2 集成模块
- `yglue-runtime-spring-boot3`: Spring Boot 3 集成模块

## 使用场景

1. **流程执行**：在业务服务中集成运行时引擎，执行流程编排
2. **参数解析**：解析 HTTP 请求参数，转换为流程需要的格式
3. **数据转换**：执行复杂的数据转换逻辑
4. **参数验证**：验证输入参数的合法性

## 构建与打包

- **打包方式**：JAR
- **Maven 坐标**：`org.yg:yglue-runtime:0.1.0-SNAPSHOT`
- **源码打包**：自动生成 `-sources.jar` 文件

## 版本兼容性

- **Java 版本**：Java 17+
- **Spring 版本**：Spring 5.x / 6.x
- **LiteFlow 版本**：2.12.1

## 扩展性

该模块提供了丰富的扩展点：
- 自定义节点执行器
- 自定义参数解析器
- 自定义表达式引擎
- 自定义验证器
- 自定义事件监听器

## 注意事项

1. 流程执行是线程安全的，但需要注意上下文隔离
2. 节点执行器应该避免阻塞操作，建议使用异步执行
3. 表达式执行需要注意性能，避免复杂计算
4. 验证器应该快速失败，避免不必要的计算

