# 快速配置优先级与元数据需求（单项目）

## 1. 目标
- 让普通用户在不写脚本的前提下，完成服务节点入参与分支条件配置。
- 复杂场景仍可覆盖，但默认界面保持“快速可用”。
- 以单项目为边界：仅依赖当前项目通过 IDEA 插件同步的服务与模型信息。

## 2. 当前优先级（建议）
1. P0（已落地）  
- 参数构造 V2 增加 `快速/高级` 模式（默认快速）。
- 分支条件 V2 增加 `快速/高级` 模式（默认快速）。
- 快速模式仅暴露高频来源（ctx/const/tempVar/serviceCall），高级模式再开放 HTTP/List 管线与分支线直接服务调用。
- 服务调用编辑器增加“签名失效提示”：若 `serviceBean + methodSignature` 不在最新目录中，提示用户重选方法。
- 发布前增加后端阻断：`publish` 时校验流程内 `serviceRef`，签名失效直接拒绝发布。
- 新增问题扫描接口：`GET /api/projects/{projectKey}/flows/issues/service-signatures`（返回最新版本存在签名问题的流程清单）。
- 前端发布前预检：点击发布先拉取问题清单，命中当前 `flowCode + versionNo` 时先提示并阻断，减少无效发布请求。
- 版本列表增加签名异常标识：对命中签名问题的版本打红标，降低误发布概率。
- 对“签名异常版本”的“保存并发布”增加二次确认弹窗，避免误发布。
- 签名问题面板支持复制示例路径，方便用户回到节点配置快速检索定位。

2. P1（必须）  
- 冻结并落地 IDEA 元数据契约：服务签名、参数列表、返回类型、模型字段树、枚举/约束信息。
- 服务签名变更后，前端可直接标红受影响配置（而不是运行时报错）。

3. P2（增强）  
- 配置向导（Wizard）：按“目标参数 -> 选择来源 -> 类型校验 -> 预览”分步完成。
- 推荐填充（Auto-bind）：同名字段自动映射、类型兼容优先推荐。

## 3. 普通用户快速配置需要的最小信息
1. 服务目录（Service Catalog）
- `serviceBean`
- `methodName`
- `methodSignature`（唯一键，必须）
- `methodSignatureHash`（建议，用于签名变更快速比对）
- `params[]`：每个参数的 `name/type/required`
- `returnType`

2. 模型结构（Model Schema）
- 可递归字段树：`fieldPath`、`javaType`、`isCollection`、`elementType`
- 字段中文描述（可选但强烈建议）
- 枚举候选值（可选）

3. 入口上下文结构（Request/Context Schema）
- `request.body`、`request.query`、`request.headers`、`pathVariable`
- 每个路径的类型提示

4. 变更追踪信息
- 元数据版本号/哈希（用于检测本地代码变更）
- 下发时间与来源（IDEA 插件版本、项目分支）

## 4. IDEA 插件同步契约（建议）
```json
{
  "projectKey": "testMave",
  "schemaVersion": "v2",
  "generatedAt": "2026-03-07T12:00:00Z",
  "metadataVersion": "2026-03-07T12:00:00Z#abc123",
  "services": [
    {
      "serviceBean": "userService",
      "serviceName": "用户服务",
      "methods": [
        {
          "methodName": "queryProfile",
          "methodSignature": "queryProfile(java.lang.String,java.lang.Long,com.example.QueryOption)",
          "methodSignatureHash": "6f7a...",
          "returnType": "com.example.UserProfile",
          "params": [
            { "name": "tenantId", "type": "java.lang.String", "required": true },
            { "name": "userId", "type": "java.lang.Long", "required": true },
            { "name": "option", "type": "com.example.QueryOption", "required": false }
          ]
        }
      ]
    }
  ],
  "models": [
    {
      "typeName": "com.example.QueryOption",
      "fields": [
        { "path": "withScore", "type": "java.lang.Boolean", "required": false },
        { "path": "tags", "type": "java.util.List<java.lang.String>", "required": false }
      ]
    }
  ]
}
```

## 5. 为什么这些信息是“必须”
1. 无 `methodSignature`  
- 无法精确绑定重载方法，配置稳定性差。

2. 无参数名/参数类型  
- 无法自动生成入参槽位，用户只能手填文本，误配率高。

3. 无模型字段树  
- 对象/List 入参无法进行字段级可视化映射。

4. 无版本哈希  
- 服务签名变更后，前端无法精确识别失效配置。

## 6. 覆盖场景与边界
可覆盖：
- 单值入参（String/Number/Boolean）
- 对象入参（字段级映射）
- List 入参（元素映射 + 可选后处理）
- 分支条件（ctx/const/tempVar，必要时服务调用）

仍有边界（需高级模式或运行时扩展）：
- 高动态反射参数构造（编译期无法静态识别）
- 极复杂聚合逻辑（建议沉淀为项目内服务后被编排调用）
- 超大 List 的逐项服务调用（需批处理策略与限流）

## 7. 对用户体验的直接收益
1. 配置速度提升  
- 从“手写方法名/脚本”变为“选择服务 + 自动入参槽位”。

2. 错误前置  
- 配置阶段即发现类型与签名问题，减少发布后故障。

3. 学习成本降低  
- 快速模式只保留高频入口，普通用户不需要理解全部高级概念。
