# yglue-runtime-spring-boot3 模块文档

## 模块概述

`yglue-runtime-spring-boot3` 是 YGFlow Suite 的 Spring Boot 3.x 集成模块，提供了将运行时引擎集成到 Spring Boot 3.x 应用中的自动配置和 AOP 拦截能力。与 Spring Boot 2 版本功能相同，但针对 Spring Boot 3 进行了适配。

## 模块职责

- **Spring Boot 3 自动配置**：提供 Spring Boot 3.x 的自动配置类
- **REST 拦截**：通过 AOP 拦截 REST 接口，自动执行流程编排
- **依赖注入**：将运行时引擎组件注册为 Spring Bean
- **配置管理**：提供 Spring Boot 配置属性支持

## 核心能力

### 1. 自动配置

**核心类：**
- `FlowRuntimeAutoConfiguration`: Spring Boot 自动配置类

**主要功能：**
- 自动注册 `RuleEngine` Bean
- 自动注册 `NodeExecutorRegistry` Bean
- 自动注册各种节点执行器
- 自动配置 LiteFlow 规则引擎

### 2. REST 接口拦截

**核心类：**
- `FlowOrchestratedAspect`: AOP 切面，拦截标记了 `@FlowOrchestrated` 的方法

**主要功能：**
- 拦截 REST 接口请求
- 根据路径和方法匹配流程规则
- 执行流程编排
- 将流程结果写入 HTTP 响应
- 处理异常并返回统一错误格式

### 3. 流程规则匹配

**匹配规则：**
- 根据 HTTP 路径和方法匹配 `RestEntryPoint`
- 支持路径变量匹配
- 支持方法匹配（GET, POST, PUT, DELETE 等）

### 4. 响应处理

**支持的响应格式：**
- 标准响应格式：`{errorCode, message, data}`
- 自定义响应格式：支持自定义字段和结构
- 单值响应：直接返回单个值
- 多值响应：返回数组或对象

### 5. 错误处理

**错误处理机制：**
- 统一捕获异常
- 根据配置的响应格式返回错误信息
- 支持验证异常的特殊处理
- 防止重复提交响应

## 技术特性

### 1. AspectJ AOP
- 使用 AspectJ 实现方法拦截
- 支持编译时和运行时织入
- 高性能的 AOP 实现

### 2. Spring Boot Starter
- 遵循 Spring Boot Starter 规范
- 自动配置，开箱即用
- 支持配置属性自定义

### 3. HTTP 响应处理
- 支持直接写入 `HttpServletResponse`
- 支持 `ResponseEntity` 结构
- 支持自定义响应格式

### 4. Spring Boot 3 适配
- 使用 Spring Boot 3.x 的 API
- 兼容 Jakarta EE 9+ 命名空间
- 支持 Spring Framework 6.x

## 依赖关系

### 核心依赖
- `yglue-runtime`: 运行时核心引擎
- `spring-boot-starter-web`: Spring Boot Web 支持
- `spring-boot-starter-aop`: Spring Boot AOP 支持
- `aspectjrt`: AspectJ 运行时支持

### 被依赖场景
- Spring Boot 3.x 业务应用集成运行时引擎

## 使用场景

1. **业务服务集成**：在 Spring Boot 3.x 业务服务中集成流程编排能力
2. **REST 接口增强**：为现有 REST 接口添加流程编排功能
3. **统一网关集成**：在 API 网关中集成流程编排

## 配置示例

### Maven 依赖
```xml
<dependency>
    <groupId>org.yg</groupId>
    <artifactId>yglue-runtime-spring-boot3</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 使用注解
```java
@RestController
public class MyController {
    
    @FlowOrchestrated
    @PostMapping("/api/users")
    public ResponseEntity<User> createUser(@RequestBody UserRequest request) {
        // 方法体可能不会被执行，流程结果会直接返回
        return ResponseEntity.ok(new User());
    }
}
```

## 构建与打包

- **打包方式**：JAR
- **Maven 坐标**：`org.yg:yglue-runtime-spring-boot3:0.1.0-SNAPSHOT`
- **源码打包**：自动生成 `-sources.jar` 文件

## 版本兼容性

- **Java 版本**：Java 17+
- **Spring Boot 版本**：3.0.x - 3.3.x
- **Spring 版本**：6.0.x - 6.1.x

## 与 Spring Boot 2 版本的差异

1. **命名空间**：使用 Jakarta EE 9+ 命名空间（javax.* → jakarta.*）
2. **Spring Framework**：使用 Spring Framework 6.x
3. **Java 版本**：最低要求 Java 17
4. **API 变更**：部分 Spring Boot API 有变化

## 注意事项

1. 需要确保 AspectJ 依赖正确配置
2. 如果使用编译时织入，需要配置 AspectJ Maven 插件
3. 响应格式配置需要在 `RestEntryPoint` 中设置
4. 流程规则需要在应用启动前加载完成
5. Spring Boot 3 使用 Jakarta EE，注意包名变化

