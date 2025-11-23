# 校验器数据结构定义

## 1. 核心数据结构

### 1.1 节点数据结构（NodeData）

```typescript
interface NodeData {
  // ... 现有字段
  inputs?: InputConfig[]
  validation?: ValidationConfig  // 节点级别的校验配置（可选）
}
```

### 1.2 输入参数配置（InputConfig）

```typescript
interface InputConfig {
  name: string                    // 参数名称
  valueType: string              // 值类型：STRING, NUMBER, OBJECT, ARRAY 等
  typeName?: string              // 完整类型名（如 "java.lang.String"）
  script?: string                // Groovy 脚本，用于获取参数值（统一使用 script，不再使用 resolver/converter）
  validators?: InputValidationRule[]  // 该 input 的校验器列表（支持多个）
  objectValidation?: ObjectValidationConfig  // 对象类型字段校验配置（当 valueType 为 OBJECT 时）
}
```

### 1.3 校验器规则（InputValidationRule）

```typescript
interface InputValidationRule {
  id: string  // 规则唯一标识（用于前端编辑和删除）
  type: "required" | "notEmpty" | "notBlank" | "type" | "range" | "length" | "regex" | "expression" | "custom"
  enabled: boolean  // 是否启用该规则
  message?: string  // 校验失败时的错误消息
  config?: ValidationRuleConfig  // 根据 type 的不同，有不同的配置
}
```

### 1.4 校验规则配置（ValidationRuleConfig）

```typescript
interface ValidationRuleConfig {
  // ========== required 类型 ==========
  // 检查参数是否存在且不为 null
  required?: boolean
  
  // ========== notEmpty 类型 ==========
  // 检查集合/数组/Map 是否不为空（长度 > 0）
  notEmpty?: boolean
  
  // ========== notBlank 类型 ==========
  // 检查字符串是否不为空白（去除空格后长度 > 0）
  notBlank?: boolean
  
  // ========== type 类型 ==========
  // 检查参数类型是否匹配
  expectedType?: string         // 期望的类型（如 "STRING", "NUMBER", "OBJECT"）
  expectedTypeName?: string      // 期望的完整类型名（如 "java.lang.String"）
  
  // ========== range 类型 ==========
  // 检查数字是否在指定范围内
  min?: number                  // 最小值（包含）
  max?: number                  // 最大值（包含）
  
  // ========== length 类型 ==========
  // 检查字符串/数组/集合的长度是否在指定范围内
  minLength?: number            // 最小长度（包含）
  maxLength?: number            // 最大长度（包含）
  
  // ========== regex 类型 ==========
  // 检查字符串是否匹配正则表达式
  pattern?: string              // 正则表达式模式（自定义）
  flags?: string                // 正则标志（如 "i" 忽略大小写）
  preset?: string               // 预设正则表达式名称（如 "email", "phone", "idCard"）
  // 优先级：preset > pattern
  
  // ========== expression 类型 ==========
  // 使用 Groovy 表达式进行自定义校验
  expression?: string            // Groovy 表达式，返回 true 表示校验通过
  // 可用变量：
  // - value: 当前参数值
  // - ctx: 流程上下文
  // - input: 当前 input 对象
  
  // ========== custom 类型 ==========
  // 使用自定义校验器类进行校验
  validator?: string             // 校验器类型或类名
  validatorConfig?: Record<string, any>  // 校验器配置
}
```

### 1.5 对象类型字段校验配置

```typescript
// 对象字段校验规则
interface ObjectFieldValidation {
  fieldPath: string             // 字段路径，如 "name"、"user.email"、"items[0].id"
  rules: InputValidationRule[]  // 该字段的校验规则
}

// 对象类型校验配置
interface ObjectValidationConfig {
  enabled: boolean               // 是否启用对象字段校验
  fieldValidations?: ObjectFieldValidation[]  // 对象字段的校验规则
}
```

### 1.6 节点级别校验配置（可选）

```typescript
interface ValidationConfig {
  enabled: boolean               // 是否启用节点级别校验
  failPolicy: "throw" | "default" | "skip"  // 校验失败策略
  rules?: ValidationRule[]      // 节点级别的校验规则（跨 input）
}

interface ValidationRule {
  id: string
  type: "expression" | "custom"  // 节点级别只支持表达式和自定义校验器
  enabled: boolean
  message?: string
  config?: {
    expression?: string
    validator?: string
    validatorConfig?: Record<string, any>
  }
}
```

## 2. 预设正则表达式数据结构

```typescript
interface RegexPreset {
  pattern: string    // 正则表达式模式
  flags: string      // 正则标志
  label: string      // 显示名称
  description: string // 描述
}

const REGEX_PRESETS: Record<string, RegexPreset> = {
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

## 3. 完整 JSON 示例

### 3.1 字符串类型参数校验

```json
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
}
```

### 3.2 数字类型参数校验

```json
{
  "name": "age",
  "valueType": "NUMBER",
  "validators": [
    {
      "id": "validator-5",
      "type": "required",
      "enabled": true,
      "message": "年龄不能为空"
    },
    {
      "id": "validator-6",
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
```

### 3.3 邮箱参数校验（使用预设正则）

```json
{
  "name": "email",
  "valueType": "STRING",
  "validators": [
    {
      "id": "validator-7",
      "type": "required",
      "enabled": true,
      "message": "邮箱不能为空"
    },
    {
      "id": "validator-8",
      "type": "regex",
      "enabled": true,
      "message": "邮箱格式不正确",
      "config": {
        "preset": "email"
      }
    }
  ]
}
```

### 3.4 自定义正则表达式校验

```json
{
  "name": "customCode",
  "valueType": "STRING",
  "validators": [
    {
      "id": "validator-9",
      "type": "regex",
      "enabled": true,
      "message": "自定义编码格式不正确",
      "config": {
        "pattern": "^[A-Z]{2}-\\d{4}$",
        "flags": ""
      }
    }
  ]
}
```

### 3.5 表达式校验

```json
{
  "name": "password",
  "valueType": "STRING",
  "validators": [
    {
      "id": "validator-10",
      "type": "expression",
      "enabled": true,
      "message": "密码必须包含字母和数字，长度至少8位",
      "config": {
        "expression": "value != null && value.length() >= 8 && value.matches('.*[A-Za-z].*') && value.matches('.*\\d.*')"
      }
    }
  ]
}
```

### 3.6 数组类型参数校验

```json
{
  "name": "items",
  "valueType": "ARRAY",
  "validators": [
    {
      "id": "validator-11",
      "type": "notEmpty",
      "enabled": true,
      "message": "项目列表不能为空"
    },
    {
      "id": "validator-12",
      "type": "length",
      "enabled": true,
      "message": "项目数量必须在 1-100 之间",
      "config": {
        "minLength": 1,
        "maxLength": 100
      }
    }
  ]
}
```

### 3.7 对象类型参数校验（包含字段校验）

```json
{
  "name": "user",
  "valueType": "OBJECT",
  "typeName": "com.example.UserDto",
  "validators": [
    {
      "id": "validator-13",
      "type": "required",
      "enabled": true,
      "message": "用户对象不能为空"
    },
    {
      "id": "validator-14",
      "type": "type",
      "enabled": true,
      "message": "用户对象类型不正确",
      "config": {
        "expectedType": "OBJECT",
        "expectedTypeName": "com.example.UserDto"
      }
    }
  ],
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
      },
      {
        "fieldPath": "age",
        "rules": [
          {
            "id": "field-validator-4",
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
        "fieldPath": "profile.phone",
        "rules": [
          {
            "id": "field-validator-5",
            "type": "regex",
            "enabled": true,
            "message": "手机号格式不正确",
            "config": {
              "preset": "phone"
            }
          }
        ]
      },
      {
        "fieldPath": "items[0].id",
        "rules": [
          {
            "id": "field-validator-6",
            "type": "required",
            "enabled": true,
            "message": "第一个项目的ID不能为空"
          }
        ]
      }
    ]
  }
}
```

### 3.8 节点级别校验配置

```json
{
  "validation": {
    "enabled": true,
    "failPolicy": "throw",
    "rules": [
      {
        "id": "node-validator-1",
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

## 4. 校验器类型说明

| 类型 | 说明 | 适用类型 | 配置字段 |
|------|------|----------|----------|
| `required` | 必填校验 | 所有类型 | `required: boolean` |
| `notEmpty` | 非空校验（集合/数组长度 > 0） | ARRAY, OBJECT(Map/List) | `notEmpty: boolean` |
| `notBlank` | 非空白校验（字符串去除空格后长度 > 0） | STRING | `notBlank: boolean` |
| `type` | 类型校验 | 所有类型 | `expectedType`, `expectedTypeName` |
| `range` | 范围校验（数字） | NUMBER | `min`, `max` |
| `length` | 长度校验 | STRING, ARRAY | `minLength`, `maxLength` |
| `regex` | 正则校验 | STRING | `preset` 或 `pattern` + `flags` |
| `expression` | 表达式校验（Groovy） | 所有类型 | `expression` |
| `custom` | 自定义校验器 | 所有类型 | `validator`, `validatorConfig` |

## 5. 字段路径格式说明

对象类型字段校验支持以下路径格式：

- **简单字段**：`name`、`email`
- **嵌套字段**：`user.name`、`profile.phone`
- **数组索引**：`items[0]`、`items[0].id`
- **深层嵌套**：`user.profile.contact.phone`

## 6. 校验失败策略

| 策略 | 说明 | 行为 |
|------|------|------|
| `throw` | 抛出异常 | 校验失败时抛出异常，中断流程执行 |
| `default` | 使用默认值 | 校验失败时使用默认值继续执行（需要在 script 中配置默认值） |
| `skip` | 跳过节点 | 校验失败时跳过当前节点，继续执行下一个节点 |


