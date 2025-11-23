package org.yglue.flow.runtime.core.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bean Validation 注解提取器
 * <p>
 * 从 Java Bean 的注解中提取校验规则，支持 JSR-303/JSR-380 Bean Validation 注解。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class BeanValidationExtractor {
    
    private static final Logger log = LoggerFactory.getLogger(BeanValidationExtractor.class);
    
    /**
     * 从对象类型中提取字段校验规则
     * <p>
     * 如果对象类型有 typeName，尝试加载类并提取注解。
     * </p>
     *
     * @param typeName 完整类型名（如 "com.example.UserDto"）
     * @return 字段校验规则列表
     */
    public static List<ObjectFieldValidation> extractFieldValidations(String typeName) {
        List<ObjectFieldValidation> fieldValidations = new ArrayList<>();
        
        if (typeName == null || typeName.isBlank()) {
            return fieldValidations;
        }
        
        try {
            Class<?> clazz = Class.forName(typeName);
            Field[] fields = clazz.getDeclaredFields();
            
            for (Field field : fields) {
                List<ValidationRule> rules = extractValidationRules(field);
                if (!rules.isEmpty()) {
                    fieldValidations.add(new ObjectFieldValidation(field.getName(), rules));
                }
            }
        } catch (ClassNotFoundException e) {
            log.debug("Class not found for typeName={}, skipping bean validation extraction", typeName);
        } catch (Exception e) {
            log.warn("Failed to extract bean validation from typeName={}: {}", typeName, e.getMessage());
        }
        
        return fieldValidations;
    }
    
    /**
     * 从字段中提取校验规则
     *
     * @param field 字段对象
     * @return 校验规则列表
     */
    private static List<ValidationRule> extractValidationRules(Field field) {
        List<ValidationRule> rules = new ArrayList<>();
        
        Annotation[] annotations = field.getAnnotations();
        for (Annotation annotation : annotations) {
            ValidationRule rule = convertAnnotationToRule(annotation, field.getName());
            if (rule != null) {
                rules.add(rule);
            }
        }
        
        return rules;
    }
    
    /**
     * 将注解转换为校验规则
     *
     * @param annotation 注解对象
     * @param fieldName 字段名称
     * @return 校验规则，如果无法转换则返回 null
     */
    private static ValidationRule convertAnnotationToRule(Annotation annotation, String fieldName) {
        String annotationName = annotation.annotationType().getName();
        
        try {
            // javax.validation / jakarta.validation 注解
            if (annotationName.equals("javax.validation.constraints.NotNull") ||
                annotationName.equals("jakarta.validation.constraints.NotNull")) {
                return createRequiredRule(fieldName, annotation);
            }
            
            if (annotationName.equals("javax.validation.constraints.NotEmpty") ||
                annotationName.equals("jakarta.validation.constraints.NotEmpty")) {
                return createNotEmptyRule(fieldName, annotation);
            }
            
            if (annotationName.equals("javax.validation.constraints.NotBlank") ||
                annotationName.equals("jakarta.validation.constraints.NotBlank")) {
                return createNotBlankRule(fieldName, annotation);
            }
            
            if (annotationName.equals("javax.validation.constraints.Size") ||
                annotationName.equals("jakarta.validation.constraints.Size")) {
                return createSizeRule(fieldName, annotation);
            }
            
            if (annotationName.equals("javax.validation.constraints.Min") ||
                annotationName.equals("jakarta.validation.constraints.Min")) {
                return createMinRule(fieldName, annotation);
            }
            
            if (annotationName.equals("javax.validation.constraints.Max") ||
                annotationName.equals("jakarta.validation.constraints.Max")) {
                return createMaxRule(fieldName, annotation);
            }
            
            if (annotationName.equals("javax.validation.constraints.DecimalMin") ||
                annotationName.equals("jakarta.validation.constraints.DecimalMin")) {
                return createDecimalMinRule(fieldName, annotation);
            }
            
            if (annotationName.equals("javax.validation.constraints.DecimalMax") ||
                annotationName.equals("jakarta.validation.constraints.DecimalMax")) {
                return createDecimalMaxRule(fieldName, annotation);
            }
            
            if (annotationName.equals("javax.validation.constraints.Email") ||
                annotationName.equals("jakarta.validation.constraints.Email")) {
                return createEmailRule(fieldName, annotation);
            }
            
            if (annotationName.equals("javax.validation.constraints.Pattern") ||
                annotationName.equals("jakarta.validation.constraints.Pattern")) {
                return createPatternRule(fieldName, annotation);
            }
            
            if (annotationName.equals("javax.validation.constraints.Positive") ||
                annotationName.equals("jakarta.validation.constraints.Positive")) {
                return createPositiveRule(fieldName, annotation);
            }
            
            if (annotationName.equals("javax.validation.constraints.PositiveOrZero") ||
                annotationName.equals("jakarta.validation.constraints.PositiveOrZero")) {
                return createPositiveOrZeroRule(fieldName, annotation);
            }
            
            if (annotationName.equals("javax.validation.constraints.Negative") ||
                annotationName.equals("jakarta.validation.constraints.Negative")) {
                return createNegativeRule(fieldName, annotation);
            }
            
            if (annotationName.equals("javax.validation.constraints.NegativeOrZero") ||
                annotationName.equals("jakarta.validation.constraints.NegativeOrZero")) {
                return createNegativeOrZeroRule(fieldName, annotation);
            }
            
        } catch (Exception e) {
            log.debug("Failed to convert annotation {} to validation rule: {}", annotationName, e.getMessage());
        }
        
        return null;
    }
    
    private static ValidationRule createRequiredRule(String fieldName, Annotation annotation) {
        ValidationRule rule = new ValidationRule();
        rule.setId("bean-validation-" + fieldName + "-required");
        rule.setType("required");
        rule.setEnabled(true);
        rule.setMessage(getMessage(annotation, "不能为空"));
        return rule;
    }
    
    private static ValidationRule createNotEmptyRule(String fieldName, Annotation annotation) {
        ValidationRule rule = new ValidationRule();
        rule.setId("bean-validation-" + fieldName + "-notEmpty");
        rule.setType("notEmpty");
        rule.setEnabled(true);
        rule.setMessage(getMessage(annotation, "不能为空"));
        return rule;
    }
    
    private static ValidationRule createNotBlankRule(String fieldName, Annotation annotation) {
        ValidationRule rule = new ValidationRule();
        rule.setId("bean-validation-" + fieldName + "-notBlank");
        rule.setType("notBlank");
        rule.setEnabled(true);
        rule.setMessage(getMessage(annotation, "不能为空白"));
        return rule;
    }
    
    private static ValidationRule createSizeRule(String fieldName, Annotation annotation) {
        try {
            Method minMethod = annotation.annotationType().getMethod("min");
            Method maxMethod = annotation.annotationType().getMethod("max");
            
            int min = (Integer) minMethod.invoke(annotation);
            int max = (Integer) maxMethod.invoke(annotation);
            
            ValidationRule rule = new ValidationRule();
            rule.setId("bean-validation-" + fieldName + "-size");
            rule.setType("length");
            rule.setEnabled(true);
            rule.setMessage(getMessage(annotation, "长度不符合要求"));
            
            Map<String, Object> config = new java.util.HashMap<>();
            if (min > 0) {
                config.put("minLength", min);
            }
            if (max < Integer.MAX_VALUE) {
                config.put("maxLength", max);
            }
            rule.setConfig(config);
            
            return rule;
        } catch (Exception e) {
            log.debug("Failed to extract Size annotation values: {}", e.getMessage());
            return null;
        }
    }
    
    private static ValidationRule createMinRule(String fieldName, Annotation annotation) {
        try {
            Method valueMethod = annotation.annotationType().getMethod("value");
            long min = (Long) valueMethod.invoke(annotation);
            
            ValidationRule rule = new ValidationRule();
            rule.setId("bean-validation-" + fieldName + "-min");
            rule.setType("range");
            rule.setEnabled(true);
            rule.setMessage(getMessage(annotation, "值不能小于 " + min));
            
            Map<String, Object> config = new java.util.HashMap<>();
            config.put("min", min);
            rule.setConfig(config);
            
            return rule;
        } catch (Exception e) {
            log.debug("Failed to extract Min annotation value: {}", e.getMessage());
            return null;
        }
    }
    
    private static ValidationRule createMaxRule(String fieldName, Annotation annotation) {
        try {
            Method valueMethod = annotation.annotationType().getMethod("value");
            long max = (Long) valueMethod.invoke(annotation);
            
            ValidationRule rule = new ValidationRule();
            rule.setId("bean-validation-" + fieldName + "-max");
            rule.setType("range");
            rule.setEnabled(true);
            rule.setMessage(getMessage(annotation, "值不能大于 " + max));
            
            Map<String, Object> config = new java.util.HashMap<>();
            config.put("max", max);
            rule.setConfig(config);
            
            return rule;
        } catch (Exception e) {
            log.debug("Failed to extract Max annotation value: {}", e.getMessage());
            return null;
        }
    }
    
    private static ValidationRule createDecimalMinRule(String fieldName, Annotation annotation) {
        try {
            Method valueMethod = annotation.annotationType().getMethod("value");
            String minStr = (String) valueMethod.invoke(annotation);
            double min = Double.parseDouble(minStr);
            
            ValidationRule rule = new ValidationRule();
            rule.setId("bean-validation-" + fieldName + "-decimalMin");
            rule.setType("range");
            rule.setEnabled(true);
            rule.setMessage(getMessage(annotation, "值不能小于 " + min));
            
            Map<String, Object> config = new java.util.HashMap<>();
            config.put("min", min);
            rule.setConfig(config);
            
            return rule;
        } catch (Exception e) {
            log.debug("Failed to extract DecimalMin annotation value: {}", e.getMessage());
            return null;
        }
    }
    
    private static ValidationRule createDecimalMaxRule(String fieldName, Annotation annotation) {
        try {
            Method valueMethod = annotation.annotationType().getMethod("value");
            String maxStr = (String) valueMethod.invoke(annotation);
            double max = Double.parseDouble(maxStr);
            
            ValidationRule rule = new ValidationRule();
            rule.setId("bean-validation-" + fieldName + "-decimalMax");
            rule.setType("range");
            rule.setEnabled(true);
            rule.setMessage(getMessage(annotation, "值不能大于 " + max));
            
            Map<String, Object> config = new java.util.HashMap<>();
            config.put("max", max);
            rule.setConfig(config);
            
            return rule;
        } catch (Exception e) {
            log.debug("Failed to extract DecimalMax annotation value: {}", e.getMessage());
            return null;
        }
    }
    
    private static ValidationRule createEmailRule(String fieldName, Annotation annotation) {
        ValidationRule rule = new ValidationRule();
        rule.setId("bean-validation-" + fieldName + "-email");
        rule.setType("regex");
        rule.setEnabled(true);
        rule.setMessage(getMessage(annotation, "邮箱格式不正确"));
        
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("preset", "email");
        rule.setConfig(config);
        
        return rule;
    }
    
    private static ValidationRule createPatternRule(String fieldName, Annotation annotation) {
        try {
            Method regexpMethod = annotation.annotationType().getMethod("regexp");
            Method flagsMethod = annotation.annotationType().getMethod("flags");
            
            String pattern = (String) regexpMethod.invoke(annotation);
            int flags = (Integer) flagsMethod.invoke(annotation);
            
            ValidationRule rule = new ValidationRule();
            rule.setId("bean-validation-" + fieldName + "-pattern");
            rule.setType("regex");
            rule.setEnabled(true);
            rule.setMessage(getMessage(annotation, "格式不正确"));
            
            Map<String, Object> config = new java.util.HashMap<>();
            config.put("pattern", pattern);
            // 转换 flags（简化处理，只处理常用的）
            String flagsStr = "";
            if ((flags & 0x02) != 0) { // CASE_INSENSITIVE
                flagsStr += "i";
            }
            if (!flagsStr.isEmpty()) {
                config.put("flags", flagsStr);
            }
            rule.setConfig(config);
            
            return rule;
        } catch (Exception e) {
            log.debug("Failed to extract Pattern annotation values: {}", e.getMessage());
            return null;
        }
    }
    
    private static ValidationRule createPositiveRule(String fieldName, Annotation annotation) {
        ValidationRule rule = new ValidationRule();
        rule.setId("bean-validation-" + fieldName + "-positive");
        rule.setType("range");
        rule.setEnabled(true);
        rule.setMessage(getMessage(annotation, "必须大于 0"));
        
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("min", 0.000001); // 大于 0
        rule.setConfig(config);
        
        return rule;
    }
    
    private static ValidationRule createPositiveOrZeroRule(String fieldName, Annotation annotation) {
        ValidationRule rule = new ValidationRule();
        rule.setId("bean-validation-" + fieldName + "-positiveOrZero");
        rule.setType("range");
        rule.setEnabled(true);
        rule.setMessage(getMessage(annotation, "必须大于等于 0"));
        
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("min", 0);
        rule.setConfig(config);
        
        return rule;
    }
    
    private static ValidationRule createNegativeRule(String fieldName, Annotation annotation) {
        ValidationRule rule = new ValidationRule();
        rule.setId("bean-validation-" + fieldName + "-negative");
        rule.setType("range");
        rule.setEnabled(true);
        rule.setMessage(getMessage(annotation, "必须小于 0"));
        
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("max", -0.000001); // 小于 0
        rule.setConfig(config);
        
        return rule;
    }
    
    private static ValidationRule createNegativeOrZeroRule(String fieldName, Annotation annotation) {
        ValidationRule rule = new ValidationRule();
        rule.setId("bean-validation-" + fieldName + "-negativeOrZero");
        rule.setType("range");
        rule.setEnabled(true);
        rule.setMessage(getMessage(annotation, "必须小于等于 0"));
        
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("max", 0);
        rule.setConfig(config);
        
        return rule;
    }
    
    /**
     * 获取注解中的 message 属性值
     *
     * @param annotation 注解对象
     * @param defaultMessage 默认消息
     * @return 消息字符串
     */
    private static String getMessage(Annotation annotation, String defaultMessage) {
        try {
            Method messageMethod = annotation.annotationType().getMethod("message");
            String message = (String) messageMethod.invoke(annotation);
            // 如果 message 是默认值（通常是 {}），使用默认消息
            if (message == null || message.isEmpty() || message.equals("{}")) {
                return defaultMessage;
            }
            return message;
        } catch (Exception e) {
            return defaultMessage;
        }
    }
    
    /**
     * 对象字段校验配置
     */
    public static class ObjectFieldValidation {
        private final String fieldPath;
        private final List<ValidationRule> rules;
        
        public ObjectFieldValidation(String fieldPath, List<ValidationRule> rules) {
            this.fieldPath = fieldPath;
            this.rules = rules;
        }
        
        public String getFieldPath() {
            return fieldPath;
        }
        
        public List<ValidationRule> getRules() {
            return rules;
        }
    }
}

