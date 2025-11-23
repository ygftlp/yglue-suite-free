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
    
    public ValidationRule() {
    }
    
    public ValidationRule(String id, String type, boolean enabled, String message, Map<String, Object> config) {
        this.id = id;
        this.type = type;
        this.enabled = enabled;
        this.message = message;
        this.config = config;
    }
    
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

