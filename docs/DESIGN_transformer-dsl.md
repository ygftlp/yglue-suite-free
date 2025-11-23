## Transformer DSL 雏形

用于声明式描述节点的低代码转换。配置以 JSON/YAML 形式保存，核心字段如下：

- `name`：模板名称。
- `outputMode`：`OBJECT`（默认）或 `SINGLE`。
- `steps`：有序步骤列表。
  - `type`：`field` / `object` / `collection` / `condition` / `script`。
  - 其余字段依据类型定义：

### `field`
- `target`（必填）：目标字段路径，支持点号。
- `source`：输入路径（如 `$input.order.id`）。
- `resolverRef`：引用 ParamResolver 结果。
- `expression`：表达式，可访问 `value` / `ctx` / 循环变量。
- `cast`：目标类型，采用 `ValueType` 枚举。
- `defaultValue`：默认值。
- `required`：是否必填。

### `object`
- `target`：目标字段路径。
- `properties`：内部步骤列表。

### `collection`
- `target`：目标集合路径。
- `source` / `resolverRef`：集合数据来源（二选一）。
- `itemAlias`：循环变量名，默认 `item`。
- `filter`：过滤表达式。
- `itemSteps`：对每个元素应用的步骤列表。

### `condition`
- `when`：条件表达式。
- `thenSteps` / `elseSteps`：命中分支的步骤。

### `script`
- `language`：脚本语言，默认 `groovy`。
- `inlineScript` / `scriptRef`：内联脚本或脚本引用（二选一）。

### 示例
```json
{
  "name": "BuildOrderPayload",
  "outputMode": "OBJECT",
  "steps": [
    {
      "type": "object",
      "target": "order",
      "properties": [
        {
          "type": "field",
          "target": "id",
          "resolverRef": "orderId",
          "required": true
        },
        {
          "type": "collection",
          "target": "items",
          "source": "#{ctx.cart.items}",
          "itemAlias": "item",
          "itemSteps": [
            {
              "type": "field",
              "target": "sku",
              "expression": "#{item.sku}"
            },
            {
              "type": "field",
              "target": "quantity",
              "expression": "#{item.qty}",
              "cast": "INTEGER",
              "defaultValue": 1
            }
          ]
        }
      ]
    }
  ]
}
```

## 快速上手

1. **选择输出字段**  
   - 在“字段映射”分区直接新增字段，或通过“从请求 Schema 快速添加字段”按钮批量引入。  
   - 目标字段使用点号标识，例如 `order.id`、`result.totalAmount`。

2. **绑定数据来源**  
   - `请求 Schema`：适合直接从 REST 入参读取，会自动生成如 `request.body.xxx` 的路径。  
   - `ParamResolver`：选择已在参数解析页面配置好的 Resolver，引用其结果。  
   - `表达式`：使用表达式引用 `ctx`（流程上下文）、`input`（上游输出）、`resolved`（Resolver 结果）等变量，例如 `#{resolved.orderId ?? 'UNKNOWN'}`。

3. **集合处理**  
   - “集合处理”分区可设置列表来源（Schema、Resolver 或表达式）。  
   - `itemAlias` 指循环变量名称，默认 `item`；可在子字段表达式中使用，如 `#{item.sku}`。  
   - `filter` 支持使用表达式过滤无效数据：`#{item.qty > 0}`。

4. **条件判断**  
   - “条件判断”分区配置 `when` 表达式，支持访问 `resolved`、`ctx`、`input`。  
   - `THEN / ELSE` 子字段与普通字段一致，可分别设置不同来源。

## 表达式参考

| 场景         | 示例表达式                           | 说明                                     |
|--------------|--------------------------------------|------------------------------------------|
| 访问请求体   | `#{request.body.userId}`             | 读取 body 中的 `userId`                  |
| 访问路径参数 | `#{request.path.projectKey}`         | 读取路径变量                             |
| 访问 Resolver| `#{resolved.orderAmount}`            | 使用 ParamResolver 的计算结果           |
| 条件判断     | `#{resolved.vipFlag == true}`        | 返回布尔值                               |
| 数值计算     | `#{(resolved.price ?: 0) * 1.13}`    | 默认值 + 运算                            |
| 字符串处理   | `#{str.upper(resolved.userName)}`\*  | 自定义函数（示例，需在函数库中实现）    |

\* 若使用自定义函数，需要在运行时注册对应函数；未来计划在 DSL 中提供常用函数库。

## 变量说明

- `ctx`：流程上下文 Map，可访问任意节点输出，如 `ctx.lastResult`、`ctx['request.query.limit']`。  
- `input`：转换器的输入，等同于上游节点的返回值。  
- `resolved`：批量解析的 ParamResolver 结果，例如 `resolved.orderId`。  
- 自定义别名：集合步骤里的 `itemAlias`，条件中的临时变量等。

## 内嵌帮助建议

- 在前端界面中提供悬浮提示或帮助按钮，链接到本文件的对应章节。  
- 常见表达式模板（复制按钮）让使用者无需记忆语法。  
- 校验提示（如“字段缺少来源”）已经集成，可结合本指南快速定位问题。

