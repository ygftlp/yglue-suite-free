package org.yglue.flow.runtime.core.validator;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 校验规则解析器
 * <p>
 * 从节点配置中解析校验规则。
 * </p>
 *
 * @author yglue
 * @since 1.0
 */
public class ValidationRuleParser {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    /**
     * 从输入参数配置中解析校验规则列表
     *
     * @param input 输入参数配置 Map
     * @return 校验规则列表
     */
    @SuppressWarnings("unchecked")
    public static List<ValidationRule> parseFromInput(Map<String, Object> input) {
        List<ValidationRule> rules = new ArrayList<>();
        
        if (input == null) {
            return rules;
        }
        
        Object validatorsObj = input.get("validators");
        if (validatorsObj == null) {
            return rules;
        }
        
        List<?> validatorsList;
        if (validatorsObj instanceof List<?>) {
            validatorsList = (List<?>) validatorsObj;
        } else {
            try {
                validatorsList = OBJECT_MAPPER.convertValue(validatorsObj, List.class);
            } catch (Exception e) {
                return rules;
            }
        }
        
        for (Object validatorObj : validatorsList) {
            ValidationRule rule = parseRule(validatorObj);
            if (rule != null) {
                rules.add(rule);
            }
        }
        
        return rules;
    }
    
    @SuppressWarnings("unchecked")
    private static ValidationRule parseRule(Object validatorObj) {
        if (validatorObj == null) {
            return null;
        }
        
        Map<String, Object> validatorMap;
        if (validatorObj instanceof Map) {
            validatorMap = (Map<String, Object>) validatorObj;
        } else {
            try {
                validatorMap = OBJECT_MAPPER.convertValue(validatorObj, Map.class);
            } catch (Exception e) {
                return null;
            }
        }
        
        ValidationRule rule = new ValidationRule();
        
        // 解析基本字段
        rule.setId(getString(validatorMap, "id"));
        rule.setType(getString(validatorMap, "type"));
        rule.setEnabled(getBoolean(validatorMap, "enabled", true));
        rule.setMessage(getString(validatorMap, "message"));
        
        // 解析配置
        Object configObj = validatorMap.get("config");
        if (configObj != null) {
            if (configObj instanceof Map) {
                rule.setConfig((Map<String, Object>) configObj);
            } else {
                try {
                    rule.setConfig(OBJECT_MAPPER.convertValue(configObj, Map.class));
                } catch (Exception e) {
                    // 忽略配置解析错误
                }
            }
        }
        
        return rule;
    }
    
    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }
    
    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}

