package org.yglue.flow.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 流程执行上下文
 * 用于在流程执行过程中存储和传递数据
 * 
 * <p>当前上下文语义：
 * <ul>
 *   <li>globals: 全局数据存储，供流程节点共享</li>
 *   <li>attributes: 内部属性存储，不进入流程数据快照</li>
 *   <li>returnValue: 流程执行结果，通过 "ret" key 存储</li>
 * </ul>
 */
public class FlowContext {
    private final Map<String, Object> globals = new HashMap<>();
    private final Map<String, Object> attributes = new HashMap<>();
    public static final String RETURN_KEY = "ret";

    /**
     * 获取全局数据存储
     * @return 全局数据 Map
     */
    public Map<String, Object> data() {
        return globals;
    }

    /**
     * 获取指定 key 的值
     * @param key 数据 key
     * @return 对应的值，不存在则返回 null
     */
    public Object get(String key) {
        return globals.get(key);
    }

    /**
     * 设置指定 key 的值
     * @param key 数据 key
     * @param value 要存储的值
     */
    public void set(String key, Object value) {
        globals.put(key, value);
    }

    /**
     * 获取指定 key 的值并转换为目标类型
     * @param key 数据 key
     * @param type 目标类型
     * @param <T> 类型参数
     * @return 转换后的值，不存在或无法转换则返回 null
     * @throws IllegalArgumentException 如果无法转换为目标类型
     */
    public <T> T getAs(String key, Class<T> type) {
        Object v = globals.get(key);
        if (v == null) return null;
        if (type.isInstance(v)) return type.cast(v);
        if (v instanceof Number n) {
            if (type == Integer.class || type == int.class) return type.cast(n.intValue());
            if (type == Long.class || type == long.class) return type.cast(n.longValue());
            if (type == Double.class || type == double.class) return type.cast(n.doubleValue());
        }
        if (type == String.class) return type.cast(String.valueOf(v));
        throw new IllegalArgumentException("Cannot cast " + v + " to " + type);
    }

    /**
     * 设置内部属性（不会进入流程数据快照）
     * @param key 属性 key
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 获取内部属性
     * @param key 属性 key
     * @return 属性值，不存在则返回 null
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * 创建上下文快照
     * @return 全局数据的副本
     */
    public Map<String, Object> snapshot() {
        return new HashMap<>(globals);
    }

    /**
     * 从快照恢复上下文
     * @param snapshot 快照数据
     */
    public void restore(Map<String, Object> snapshot) {
        globals.clear();
        globals.putAll(Optional.ofNullable(snapshot).orElse(Map.of()));
    }

    /**
     * 设置流程执行结果
     * @param value 返回值
     */
    public void setReturnValue(Object value) {
        set(RETURN_KEY, value);
    }

    /**
     * 获取流程执行结果
     * @return 返回值
     */
    public Object getReturnValue() {
        return get(RETURN_KEY);
    }
}
