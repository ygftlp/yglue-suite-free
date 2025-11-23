package org.yglue.flow.runtime.core.validator.validators;

import org.yglue.flow.runtime.core.validator.*;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 正则表达式校验器
 * <p>
 * 检查字符串是否匹配正则表达式。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class RegexValidator implements Validator {
    
    private static final Map<String, RegexPreset> PRESETS = Map.of(
        "email", new RegexPreset("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", ""),
        "phone", new RegexPreset("^1[3-9]\\d{9}$", ""),
        "idCard", new RegexPreset("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$", ""),
        "url", new RegexPreset("^https?://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-\\.,@?^=%&:/~\\+#]*[\\w\\-\\@?^=%&/~\\+#])?$", "i"),
        "ip", new RegexPreset("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$", ""),
        "numeric", new RegexPreset("^\\d+$", ""),
        "alphanumeric", new RegexPreset("^[A-Za-z0-9]+$", "")
    );
    
    @Override
    public ValidationResult validate(Object value, ValidationRule rule, ValidationContext context) {
        if (value == null) {
            return ValidationResult.success(); // null 值由 required 校验器处理
        }
        
        if (!(value instanceof String)) {
            String message = rule.getMessage() != null && !rule.getMessage().isBlank()
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
            String message = rule.getMessage() != null && !rule.getMessage().isBlank()
                ? rule.getMessage()
                : "参数 '" + context.getInputName() + "' 格式不正确";
            return ValidationResult.failure(message, getType());
        }
        
        return ValidationResult.success();
    }
    
    private Pattern getPattern(Map<String, Object> config) {
        // 优先使用预设
        String preset = getString(config, "preset");
        if (preset != null && PRESETS.containsKey(preset)) {
            RegexPreset regexPreset = PRESETS.get(preset);
            return Pattern.compile(regexPreset.pattern(), parseFlags(regexPreset.flags()));
        }
        
        // 使用自定义正则
        String pattern = getString(config, "pattern");
        if (pattern != null && !pattern.isBlank()) {
            String flags = getString(config, "flags");
            return Pattern.compile(pattern, parseFlags(flags != null ? flags : ""));
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
    
    private String getString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value != null ? String.valueOf(value) : null;
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

