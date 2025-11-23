# 校验器包结构设计

## 1. 包结构

```
org.yglue.flow.runtime.core.validator/
├── Validator.java                    # 校验器核心接口
├── ValidatorEngine.java              # 校验引擎（管理所有校验器）
├── ValidatorRegistry.java            # 校验器注册表
├── ValidationContext.java            # 校验上下文
├── ValidationResult.java             # 校验结果
├── ValidationException.java          # 校验异常
├── ValidationRule.java               # 校验规则（数据模型）
├── ValidationRuleConfig.java         # 校验规则配置（数据模型）
└── validators/                       # 具体校验器实现
    ├── RequiredValidator.java        # 必填校验器
    ├── NotEmptyValidator.java        # 非空校验器
    ├── NotBlankValidator.java        # 非空白校验器
    ├── TypeValidator.java            # 类型校验器
    ├── RangeValidator.java           # 范围校验器
    ├── LengthValidator.java          # 长度校验器
    ├── RegexValidator.java           # 正则校验器
    ├── ExpressionValidator.java      # 表达式校验器
    └── CustomValidator.java          # 自定义校验器
```

## 2. 核心接口设计

### 2.1 Validator 接口

```java
package org.yglue.flow.runtime.core.validator;

/**
 * 校验器核心接口
 * <p>
 * 所有校验器实现都应该实现此接口。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public interface Validator {
    
    /**
     * 校验值是否符合规则
     *
     * @param value 待校验的值
     * @param rule 校验规则配置
     * @param context 校验上下文
     * @return 校验结果
     */
    ValidationResult validate(Object value, ValidationRule rule, ValidationContext context);
    
    /**
     * 获取校验器类型
     *
     * @return 校验器类型（如 "required", "notEmpty", "regex" 等）
     */
    String getType();
    
    /**
     * 检查是否支持该类型的值
     *
     * @param valueType 值类型（如 "STRING", "NUMBER", "OBJECT" 等）
     * @return 如果支持则返回 true
     */
    boolean supports(String valueType);
}
```

### 2.2 ValidatorEngine 接口

```java
package org.yglue.flow.runtime.core.validator;

import java.util.List;

/**
 * 校验引擎
 * <p>
 * 负责管理所有校验器，并提供统一的校验入口。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public interface ValidatorEngine {
    
    /**
     * 执行校验
     * <p>
     * 按照规则列表的顺序依次执行校验，如果某个规则失败，根据 failPolicy 决定是否继续。
     * </p>
     *
     * @param value 待校验的值
     * @param rules 校验规则列表
     * @param context 校验上下文
     * @return 校验结果列表
     */
    List<ValidationResult> validate(Object value, List<ValidationRule> rules, ValidationContext context);
    
    /**
     * 注册校验器
     *
     * @param validator 校验器实例
     */
    void register(Validator validator);
    
    /**
     * 获取校验器
     *
     * @param type 校验器类型
     * @return 校验器实例，如果不存在则返回 null
     */
    Validator getValidator(String type);
}
```

### 2.3 ValidationContext

```java
package org.yglue.flow.runtime.core.validator;

import org.yglue.flow.runtime.FlowContext;

/**
 * 校验上下文
 * <p>
 * 提供校验过程中需要的上下文信息，如流程上下文、表达式引擎等。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class ValidationContext {
    
    private final FlowContext flowContext;
    private final org.yglue.flow.runtime.core.expression.ExpressionEngine expressionEngine;
    private final String inputName;
    private final String valueType;
    
    public ValidationContext(FlowContext flowContext,
                             org.yglue.flow.runtime.core.expression.ExpressionEngine expressionEngine,
                             String inputName,
                             String valueType) {
        this.flowContext = flowContext;
        this.expressionEngine = expressionEngine;
        this.inputName = inputName;
        this.valueType = valueType;
    }
    
    public FlowContext getFlowContext() {
        return flowContext;
    }
    
    public org.yglue.flow.runtime.core.expression.ExpressionEngine getExpressionEngine() {
        return expressionEngine;
    }
    
    public String getInputName() {
        return inputName;
    }
    
    public String getValueType() {
        return valueType;
    }
}
```

### 2.4 ValidationResult

```java
package org.yglue.flow.runtime.core.validator;

/**
 * 校验结果
 *
 * @author yglue
 * @since 1.0
 */
public class ValidationResult {
    
    private final boolean passed;
    private final String message;
    private final String validatorType;
    private final Throwable error;
    
    private ValidationResult(boolean passed, String message, String validatorType, Throwable error) {
        this.passed = passed;
        this.message = message;
        this.validatorType = validatorType;
        this.error = error;
    }
    
    public static ValidationResult success() {
        return new ValidationResult(true, null, null, null);
    }
    
    public static ValidationResult failure(String message, String validatorType) {
        return new ValidationResult(false, message, validatorType, null);
    }
    
    public static ValidationResult failure(String message, String validatorType, Throwable error) {
        return new ValidationResult(false, message, validatorType, error);
    }
    
    public boolean isPassed() {
        return passed;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getValidatorType() {
        return validatorType;
    }
    
    public Throwable getError() {
        return error;
    }
}
```

### 2.5 ValidationRule（数据模型）

```java
package org.yglue.flow.runtime.core.validator;

import java.util.Map;

/**
 * 校验规则
 * <p>
 * 从节点配置中解析出的校验规则数据模型。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class ValidationRule {
    
    private String id;
    private String type;
    private boolean enabled;
    private String message;
    private Map<String, Object> config;
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Map<String, Object> getConfig() {
        return config;
    }
    
    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }
}
```

## 3. 校验器实现示例

### 3.1 RequiredValidator

```java
package org.yglue.flow.runtime.core.validator.validators;

import org.yglue.flow.runtime.core.validator.*;

/**
 * 必填校验器
 *
 * @author yglue
 * @since 1.0
 */
public class RequiredValidator implements Validator {
    
    @Override
    public ValidationResult validate(Object value, ValidationRule rule, ValidationContext context) {
        if (value == null) {
            String message = rule.getMessage() != null 
                ? rule.getMessage() 
                : "参数 '" + context.getInputName() + "' 不能为空";
            return ValidationResult.failure(message, getType());
        }
        return ValidationResult.success();
    }
    
    @Override
    public String getType() {
        return "required";
    }
    
    @Override
    public boolean supports(String valueType) {
        return true; // 所有类型都支持必填校验
    }
}
```

### 3.2 RegexValidator

```java
package org.yglue.flow.runtime.core.validator.validators;

import org.yglue.flow.runtime.core.validator.*;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 正则表达式校验器
 *
 * @author yglue
 * @since 1.0
 */
public class RegexValidator implements Validator {
    
    private static final Map<String, RegexPreset> PRESETS = Map.of(
        "email", new RegexPreset("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", ""),
        "phone", new RegexPreset("^1[3-9]\\d{9}$", ""),
        // ... 其他预设
    );
    
    @Override
    public ValidationResult validate(Object value, ValidationRule rule, ValidationContext context) {
        if (value == null) {
            return ValidationResult.success(); // null 值由 required 校验器处理
        }
        
        if (!(value instanceof String)) {
            String message = rule.getMessage() != null 
                ? rule.getMessage() 
                : "参数 '" + context.getInputName() + "' 必须是字符串类型";
            return ValidationResult.failure(message, getType());
        }
        
        String strValue = (String) value;
        Map<String, Object> config = rule.getConfig();
        if (config == null) {
            return ValidationResult.success();
        }
        
        Pattern pattern = getPattern(config);
        if (pattern == null) {
            return ValidationResult.success();
        }
        
        if (!pattern.matcher(strValue).matches()) {
            String message = rule.getMessage() != null 
                ? rule.getMessage() 
                : "参数 '" + context.getInputName() + "' 格式不正确";
            return ValidationResult.failure(message, getType());
        }
        
        return ValidationResult.success();
    }
    
    private Pattern getPattern(Map<String, Object> config) {
        // 优先使用预设
        String preset = (String) config.get("preset");
        if (preset != null && PRESETS.containsKey(preset)) {
            RegexPreset regexPreset = PRESETS.get(preset);
            return Pattern.compile(regexPreset.pattern(), 
                parseFlags(regexPreset.flags()));
        }
        
        // 使用自定义正则
        String pattern = (String) config.get("pattern");
        if (pattern != null) {
            String flags = (String) config.getOrDefault("flags", "");
            return Pattern.compile(pattern, parseFlags(flags));
        }
        
        return null;
    }
    
    private int parseFlags(String flags) {
        int result = 0;
        if (flags != null) {
            if (flags.contains("i")) result |= Pattern.CASE_INSENSITIVE;
            if (flags.contains("m")) result |= Pattern.MULTILINE;
            if (flags.contains("s")) result |= Pattern.DOTALL;
        }
        return result;
    }
    
    @Override
    public String getType() {
        return "regex";
    }
    
    @Override
    public boolean supports(String valueType) {
        return "STRING".equals(valueType);
    }
    
    private record RegexPreset(String pattern, String flags) {}
}
```

## 4. ValidatorEngine 实现

```java
package org.yglue.flow.runtime.core.validator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认校验引擎实现
 *
 * @author yglue
 * @since 1.0
 */
public class DefaultValidatorEngine implements ValidatorEngine {
    
    private final Map<String, Validator> validators = new HashMap<>();
    
    public DefaultValidatorEngine() {
        // 注册内置校验器
        register(new RequiredValidator());
        register(new NotEmptyValidator());
        register(new NotBlankValidator());
        register(new TypeValidator());
        register(new RangeValidator());
        register(new LengthValidator());
        register(new RegexValidator());
        register(new ExpressionValidator());
        register(new CustomValidator());
    }
    
    @Override
    public List<ValidationResult> validate(Object value, List<ValidationRule> rules, ValidationContext context) {
        List<ValidationResult> results = new ArrayList<>();
        
        for (ValidationRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }
            
            Validator validator = getValidator(rule.getType());
            if (validator == null) {
                results.add(ValidationResult.failure(
                    "未知的校验器类型: " + rule.getType(), 
                    rule.getType()));
                continue;
            }
            
            ValidationResult result = validator.validate(value, rule, context);
            results.add(result);
            
            // 如果校验失败，根据 failPolicy 决定是否继续
            // 这里可以根据需要实现 failPolicy 逻辑
        }
        
        return results;
    }
    
    @Override
    public void register(Validator validator) {
        validators.put(validator.getType(), validator);
    }
    
    @Override
    public Validator getValidator(String type) {
        return validators.get(type);
    }
}
```

## 5. 在节点执行器中使用

```java
// 在 ServiceNodeExecutor 中使用
public class ServiceNodeExecutor implements NodeExecutor {
    
    private final ValidatorEngine validatorEngine;
    
    public ServiceNodeExecutor(ValidatorEngine validatorEngine) {
        this.validatorEngine = validatorEngine;
    }
    
    @Override
    public void execute(NodeExecutionContext context) {
        // 1. 解析参数
        Map<String, Object> inputs = resolveInputs(context);
        
        // 2. 执行校验
        NodeDefinition node = context.getNodeDefinition();
        List<InputConfig> inputConfigs = node.getInputs();
        
        for (InputConfig inputConfig : inputConfigs) {
            Object value = inputs.get(inputConfig.getName());
            List<ValidationRule> rules = inputConfig.getValidators();
            
            if (rules != null && !rules.isEmpty()) {
                ValidationContext validationContext = new ValidationContext(
                    context.getFlowContext(),
                    context.getExpressionEngine(),
                    inputConfig.getName(),
                    inputConfig.getValueType()
                );
                
                List<ValidationResult> results = validatorEngine.validate(value, rules, validationContext);
                
                // 处理校验结果
                for (ValidationResult result : results) {
                    if (!result.isPassed()) {
                        // 根据 failPolicy 处理失败情况
                        handleValidationFailure(result, inputConfig.getFailPolicy());
                    }
                }
            }
        }
        
        // 3. 执行服务调用
        // ...
    }
}
```

## 6. 与现有模块的集成

校验器包应该：
1. **独立包结构**：`org.yglue.flow.runtime.core.validator`
2. **依赖关系**：
   - 依赖 `expression` 包（用于表达式校验）
   - 被 `executors` 包使用（节点执行器调用校验）
3. **注册机制**：类似 `NodeExecutorRegistry`，提供 `ValidatorRegistry`
4. **Spring 集成**：在 Spring Boot 3 模块中提供自动配置

## 7. 实现优先级

### Phase 1: 核心框架
1. Validator 接口
2. ValidatorEngine 接口和实现
3. ValidationContext、ValidationResult
4. ValidationRule 数据模型

### Phase 2: 基础校验器
5. RequiredValidator
6. NotEmptyValidator
7. NotBlankValidator
8. LengthValidator
9. RegexValidator（包含预设正则）

### Phase 3: 高级校验器
10. TypeValidator
11. RangeValidator
12. ExpressionValidator

### Phase 4: 扩展功能
13. CustomValidator
14. 对象字段校验支持
15. 校验失败策略处理

