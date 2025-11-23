# 节点参数校验配置设计

## 边界模型设计

### 1. 设计原则

1. **校验边界**：参数校验作用于节点的 `inputs`，每个 input 可以有独立的校验规则
2. **规则边界**：校验规则是声明式的，不包含执行逻辑
3. **执行边界**：校验执行在运行时框架中，不在前端或后端平台
4. **数据边界**：校验配置存储在节点的 `data` 中，与节点数据一起保存

### 2. 数据结构设计

#### 2.1 节点数据结构

```typescript
interface NodeData {
  // ... 现有字段
  inputs?: InputConfig[]
  validation?: ValidationConfig  // 节点级别的校验配置（可选）
}

interface InputConfig {
  name: string
  valueType: string
  typeName?: string
  script?: string  // Groovy 脚本，用于获取参数值（统一使用 script，不再使用 resolver/converter）
  validators?: InputValidationRule[]  // 该 input 的校验器列表（支持多个）
  objectValidation?: ObjectValidationConfig  // 对象类型字段校验配置（当 valueType 为 OBJECT 时）
}

interface ValidationConfig {
  enabled: boolean  // 是否启用校验
  failPolicy: "throw" | "default" | "skip"  // 校验失败策略
  rules?: ValidationRule[]  // 节点级别的校验规则（跨 input）
}
```

#### 2.2 校验规则类型

```typescript
interface InputValidationRule {
  id: string  // 规则唯一标识
  type: "required" | "notEmpty" | "notBlank" | "type" | "range" | "length" | "regex" | "expression" | "custom"
  enabled: boolean  // 是否启用该规则
  message?: string  // 校验失败时的错误消息
  // 根据 type 的不同，有不同的配置
  config?: ValidationRuleConfig
}

interface ValidationRuleConfig {
  // required 类型：检查参数是否存在且不为 null
  required?: boolean
  
  // notEmpty 类型：检查集合/数组/Map 是否不为空
  notEmpty?: boolean
  
  // notBlank 类型：检查字符串是否不为空白（去除空格后不为空）
  notBlank?: boolean
  
  // type 类型：检查参数类型是否匹配
  expectedType?: string  // 期望的类型（如 "STRING", "NUMBER", "OBJECT"）
  expectedTypeName?: string  // 期望的完整类型名（如 "java.lang.String"）
  
  // range 类型（用于数字）：检查数字是否在指定范围内
  min?: number
  max?: number
  
  // length 类型（用于字符串/数组/集合）：检查长度是否在指定范围内
  minLength?: number
  maxLength?: number
  
  // regex 类型（用于字符串）：检查字符串是否匹配正则表达式
  pattern?: string  // 正则表达式模式
  flags?: string  // 正则标志（如 "i" 忽略大小写）
  preset?: string  // 预设正则表达式名称（如 "email", "phone", "idCard"）
  
  // expression 类型（Groovy 表达式）：使用表达式进行自定义校验
  expression?: string  // 返回 true 表示校验通过，false 或抛出异常表示失败
  // 变量：value（当前参数值）、ctx（流程上下文）、input（当前 input 对象）
  
  // custom 类型（自定义校验器）：使用自定义校验器类进行校验
  validator?: string  // 校验器类型或类名
  validatorConfig?: Record<string, any>  // 校验器配置
}

// 对象类型字段校验配置
interface ObjectFieldValidation {
  fieldPath: string  // 字段路径，如 "user.name"、"items[0].id"
  rules: InputValidationRule[]  // 该字段的校验规则
}

// 对象类型校验配置
interface ObjectValidationConfig {
  enabled: boolean
  fieldValidations?: ObjectFieldValidation[]  // 对象字段的校验规则
}
```

#### 2.3 预设正则表达式

前端提供以下内置正则表达式预设：

```typescript
const REGEX_PRESETS = {
  email: {
    pattern: "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
    flags: "",
    label: "邮箱",
    description: "标准邮箱格式"
  },
  phone: {
    pattern: "^1[3-9]\\d{9}$",
    flags: "",
    label: "手机号",
    description: "中国手机号格式（11位数字，1开头）"
  },
  idCard: {
    pattern: "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$",
    flags: "",
    label: "身份证号",
    description: "中国18位身份证号格式"
  },
  url: {
    pattern: "^https?://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-\\.,@?^=%&:/~\\+#]*[\\w\\-\\@?^=%&/~\\+#])?$",
    flags: "i",
    label: "URL",
    description: "HTTP/HTTPS URL 格式"
  },
  ip: {
    pattern: "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$",
    flags: "",
    label: "IP地址",
    description: "IPv4 地址格式"
  },
  numeric: {
    pattern: "^\\d+$",
    flags: "",
    label: "纯数字",
    description: "只包含数字"
  },
  alphanumeric: {
    pattern: "^[A-Za-z0-9]+$",
    flags: "",
    label: "字母数字",
    description: "只包含字母和数字"
  }
}
```

#### 2.4 校验规则示例

```json
{
  "inputs": [
    {
      "name": "userId",
      "valueType": "STRING",
      "validators": [
        {
          "id": "validator-1",
          "type": "required",
          "enabled": true,
          "message": "用户ID不能为空"
        },
        {
          "id": "validator-2",
          "type": "notBlank",
          "enabled": true,
          "message": "用户ID不能为空白"
        },
        {
          "id": "validator-3",
          "type": "length",
          "enabled": true,
          "message": "用户ID长度必须在 1-50 之间",
          "config": {
            "minLength": 1,
            "maxLength": 50
          }
        },
        {
          "id": "validator-4",
          "type": "regex",
          "enabled": true,
          "message": "用户ID格式不正确",
          "config": {
            "preset": "numeric"
          }
        }
      ]
    },
    {
      "name": "email",
      "valueType": "STRING",
      "validators": [
        {
          "id": "validator-5",
          "type": "required",
          "enabled": true,
          "message": "邮箱不能为空"
        },
        {
          "id": "validator-6",
          "type": "regex",
          "enabled": true,
          "message": "邮箱格式不正确",
          "config": {
            "preset": "email"
          }
        }
      ]
    },
    {
      "name": "age",
      "valueType": "NUMBER",
      "validators": [
        {
          "id": "validator-7",
          "type": "range",
          "enabled": true,
          "message": "年龄必须在 0-150 之间",
          "config": {
            "min": 0,
            "max": 150
          }
        }
      ]
    },
    {
      "name": "user",
      "valueType": "OBJECT",
      "typeName": "com.example.UserDto",
      "objectValidation": {
        "enabled": true,
        "fieldValidations": [
          {
            "fieldPath": "name",
            "rules": [
              {
                "id": "field-validator-1",
                "type": "notBlank",
                "enabled": true,
                "message": "用户名不能为空"
              },
              {
                "id": "field-validator-2",
                "type": "length",
                "enabled": true,
                "message": "用户名长度必须在 2-20 之间",
                "config": {
                  "minLength": 2,
                  "maxLength": 20
                }
              }
            ]
          },
          {
            "fieldPath": "email",
            "rules": [
              {
                "id": "field-validator-3",
                "type": "regex",
                "enabled": true,
                "message": "邮箱格式不正确",
                "config": {
                  "preset": "email"
                }
              }
            ]
          }
        ]
      }
    }
  ],
  "validation": {
    "enabled": true,
    "failPolicy": "throw",
    "rules": [
      {
        "type": "expression",
        "enabled": true,
        "message": "用户ID和邮箱不能同时为空",
        "config": {
          "expression": "ctx['userId'] != null || ctx['email'] != null"
        }
      }
    ]
  }
}
```

### 3. 边界划分

```
┌─────────────────────────────────────────────────────────┐
│                   前端设计器层（配置）                      │
│  - 提供校验规则配置 UI                                    │
│  - 校验规则编辑器（表达式编辑器、正则编辑器等）            │
│  - 校验规则预览和测试                                     │
│  输出：节点 data.validation 配置                          │
└─────────────────────────────────────────────────────────┘
                        ↓ 保存
┌─────────────────────────────────────────────────────────┐
│                   后端平台层（存储）                        │
│  - 接收并存储节点配置（包含 validation）                  │
│  - 不执行校验逻辑                                         │
│  存储：contentJson 中包含 validation 配置                 │
└─────────────────────────────────────────────────────────┘
                        ↓ 加载
┌─────────────────────────────────────────────────────────┐
│                   运行框架层（执行）                        │
│  - 解析节点配置中的 validation                            │
│  - 在执行节点前执行参数校验                               │
│  - 根据 failPolicy 处理校验失败                          │
│  执行：使用 Groovy 或 Java 执行校验规则                    │
└─────────────────────────────────────────────────────────┘
```

### 4. 校验规则类型详细说明

#### 4.1 required（必填校验）
- **作用**：检查参数是否存在且不为 null
- **配置**：`{ required: true }`
- **适用类型**：所有类型

#### 4.2 notEmpty（非空校验）
- **作用**：检查集合/数组/Map 是否不为空（长度 > 0）
- **配置**：`{ notEmpty: true }`
- **适用类型**：ARRAY、OBJECT（Map/List 类型）

#### 4.3 notBlank（非空白校验）
- **作用**：检查字符串是否不为空白（去除空格后长度 > 0）
- **配置**：`{ notBlank: true }`
- **适用类型**：STRING

#### 4.4 type（类型校验）
- **作用**：检查参数类型是否匹配
- **配置**：`{ expectedType: "STRING", expectedTypeName: "java.lang.String" }`
- **适用类型**：所有类型

#### 4.5 range（范围校验）
- **作用**：检查数字是否在指定范围内
- **配置**：`{ min: 0, max: 100 }`
- **适用类型**：NUMBER

#### 4.6 length（长度校验）
- **作用**：检查字符串/数组/集合的长度是否在指定范围内
- **配置**：`{ minLength: 1, maxLength: 100 }`
- **适用类型**：STRING、ARRAY

#### 4.7 regex（正则校验）
- **作用**：检查字符串是否匹配正则表达式
- **配置**：
  - 使用预设：`{ preset: "email" }` 或 `{ preset: "phone" }`
  - 自定义正则：`{ pattern: "^[0-9]+$", flags: "i" }`
- **预设正则表达式**：
  - `email`：邮箱格式
  - `phone`：手机号格式（中国）
  - `idCard`：身份证号格式（中国）
  - `url`：URL 格式
  - `ip`：IP 地址格式
  - `numeric`：纯数字
  - `alphanumeric`：字母数字
- **适用类型**：STRING

#### 4.8 expression（表达式校验）
- **作用**：使用 Groovy 表达式进行自定义校验
- **配置**：`{ expression: "value != null && value.length() > 0" }`
- **变量**：
  - `value`：当前参数值
  - `ctx`：流程上下文
  - `input`：当前 input 对象
- **适用类型**：所有类型

#### 4.9 custom（自定义校验器）
- **作用**：使用自定义校验器类进行校验
- **配置**：`{ validator: "EmailValidator", validatorConfig: {...} }`
- **适用类型**：所有类型

### 4.10 对象类型字段校验

当 input 的 `valueType` 为 `OBJECT` 时，支持对对象内部字段进行校验：

```json
{
  "name": "user",
  "valueType": "OBJECT",
  "typeName": "com.example.UserDto",
  "objectValidation": {
    "enabled": true,
    "fieldValidations": [
      {
        "fieldPath": "name",
        "rules": [
          {
            "type": "notBlank",
            "enabled": true,
            "message": "用户名不能为空"
          },
          {
            "type": "length",
            "enabled": true,
            "message": "用户名长度必须在 2-20 之间",
            "config": {
              "minLength": 2,
              "maxLength": 20
            }
          }
        ]
      },
      {
        "fieldPath": "email",
        "rules": [
          {
            "type": "regex",
            "enabled": true,
            "message": "邮箱格式不正确",
            "config": {
              "preset": "email"
            }
          }
        ]
      },
      {
        "fieldPath": "age",
        "rules": [
          {
            "type": "range",
            "enabled": true,
            "message": "年龄必须在 0-150 之间",
            "config": {
              "min": 0,
              "max": 150
            }
          }
        ]
      }
    ]
  }
}
```

### 5. 校验失败策略

#### 5.1 throw（抛出异常）
- **行为**：校验失败时抛出异常，中断流程执行
- **适用场景**：关键参数校验失败必须中断流程

#### 5.2 default（使用默认值）
- **行为**：校验失败时使用默认值继续执行
- **配置**：需要在 input 的 script 中配置默认值，或在校验规则配置中指定默认值
- **适用场景**：可选参数校验失败时使用默认值

#### 5.3 skip（跳过节点）
- **行为**：校验失败时跳过当前节点，继续执行下一个节点
- **适用场景**：非关键节点，校验失败时可以跳过

### 6. 前端 UI 设计

#### 6.1 校验器配置位置
- **位置**：在 `Inspector.vue` 中，每个 input 的"值"配置下方添加"校验器"配置区域
- **展示方式**：显示校验器列表，每个校验器可以展开/收起配置

#### 6.2 校验器列表 UI
- **校验器列表**：显示当前 input 的所有校验器
- **添加校验器**：点击"添加校验器"按钮，打开校验器配置弹框
- **校验器卡片**：每个校验器显示为一个卡片，包含：
  - 校验器类型图标和名称
  - 启用/禁用开关
  - 错误消息预览
  - 编辑按钮（打开配置弹框）
  - 删除按钮
- **校验器排序**：可以拖拽调整校验器执行顺序

#### 6.3 校验器配置弹框（ValidationEditor）

**弹框结构：**
```
┌─────────────────────────────────────────┐
│  校验器配置                                    │
├─────────────────────────────────────────┤
│  类型: [下拉选择] required/notEmpty/...  │
│  启用: [开关]                              │
│  错误消息: [输入框]                         │
│                                            │
│  配置区域（根据类型动态显示）:                │
│  - required: 无需额外配置                   │
│  - notEmpty: 无需额外配置                   │
│  - notBlank: 无需额外配置                   │
│  - type: 期望类型选择器                     │
│  - range: min/max 输入框                   │
│  - length: minLength/maxLength 输入框      │
│  - regex: 预设选择器 + 自定义正则输入框      │
│  - expression: 表达式编辑器（ScriptEditor）│
│  - custom: 校验器类选择器 + 配置表单        │
│                                            │
│  [取消] [确定]                              │
└─────────────────────────────────────────┘
```

**预设正则表达式选择器：**
- 提供常用正则预设：邮箱、手机号、身份证、URL、IP 等
- 选择预设后自动填充 `pattern` 和 `flags`
- 支持自定义正则表达式输入

#### 6.4 对象类型字段校验 UI

当 input 的 `valueType` 为 `OBJECT` 时：
- 显示"对象字段校验"配置区域
- 可以添加字段路径（如 `name`、`user.email`、`items[0].id`）
- 为每个字段配置独立的校验规则
- 字段路径支持嵌套对象和数组索引

#### 6.5 表达式编辑器
- **集成 ScriptEditor**：复用现有的脚本编辑器组件
- **变量提示**：提供 `value`、`ctx`、`input` 等变量的自动补全
- **语法高亮**：Groovy 语法高亮
- **示例代码**：提供常用校验表达式的示例

### 7. 运行时执行设计

#### 7.1 校验执行时机
- **执行点**：在节点执行前，参数解析完成后
- **执行顺序**：
  1. 节点级别的校验规则（`validation.rules`）
  2. Input 级别的校验规则（`inputs[].validation`），按配置顺序执行

#### 7.2 校验执行逻辑
```java
// 伪代码
// 1. 执行节点级别的校验规则
for (ValidationRule rule : nodeValidationRules) {
    if (!rule.enabled) continue;
    ValidationResult result = executeRule(rule, inputValue, context);
    if (!result.passed) {
        handleValidationFailure(result, failPolicy);
    }
}

// 2. 执行 input 级别的校验器（按配置顺序）
for (InputValidationRule validator : inputValidators) {
    if (!validator.enabled) continue;
    ValidationResult result = executeValidator(validator, inputValue, context);
    if (!result.passed) {
        handleValidationFailure(result, failPolicy);
    }
}

// 3. 如果是对象类型，执行字段级别的校验
if (inputValueType == OBJECT && objectValidation.enabled) {
    for (ObjectFieldValidation fieldValidation : objectValidation.fieldValidations) {
        Object fieldValue = extractFieldValue(inputValue, fieldValidation.fieldPath);
        for (InputValidationRule rule : fieldValidation.rules) {
            if (!rule.enabled) continue;
            ValidationResult result = executeValidator(rule, fieldValue, context);
            if (!result.passed) {
                handleValidationFailure(result, failPolicy);
            }
        }
    }
}
```

#### 7.3 预设正则表达式执行
运行时框架需要支持预设正则表达式的解析：
- 如果 `config.preset` 存在，使用预设的正则表达式
- 如果 `config.pattern` 存在，使用自定义正则表达式
- 优先级：`preset` > `pattern`

### 8. 数据流转

```
前端设计器
  ↓ 配置校验规则
节点 data.validation
  ↓ 保存
后端 contentJson
  ↓ 加载
运行框架
  ↓ 解析并执行
校验结果
  ↓ 根据 failPolicy 处理
继续执行或中断
```

## 实现优先级

### Phase 1: 基础校验规则和 UI
1. required（必填）
2. notEmpty（非空）
3. notBlank（非空白）
4. length（长度）
5. regex（正则，包含预设正则）
6. 校验器配置弹框 UI

### Phase 2: 高级校验规则
7. type（类型）
8. range（范围）
9. expression（表达式）

### Phase 3: 扩展功能
10. custom（自定义校验器）
11. 对象类型字段校验
12. 节点级别校验规则

## 注意事项

1. **性能考虑**：校验规则应该轻量级，避免复杂的表达式影响性能
2. **错误消息**：提供清晰的错误消息，便于调试和排查问题
3. **向后兼容**：现有节点没有 validation 配置时，应该正常工作
4. **校验顺序**：明确校验规则的执行顺序，避免相互影响
5. **对象字段路径**：支持嵌套对象（如 `user.profile.name`）和数组索引（如 `items[0].id`）
6. **预设正则表达式**：前端和后端需要保持一致的正则表达式定义
7. **校验器弹框**：每个校验器配置都应该在独立的弹框中完成，提供清晰的配置界面
8. **多个校验器**：支持为同一个 input 配置多个校验器，按顺序执行

