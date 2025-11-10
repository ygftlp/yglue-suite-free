package org.yglue.flow.annotations;

import java.lang.annotation.*;

/**
 * FlowApi 注解
 * 用于标记 Flow API 类
 * 
 * @param value bean 名称（Service name），如果未指定则使用 name 字段
 * @param name API 名称（显示名称），如果未指定则使用类名
 * @param description API 描述
 * @param version API 版本
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface FlowApi {
    /**
     * bean 名称（Service name）
     * 如果未指定，则使用 name 字段作为 bean 名称
     */
    String value() default "";
    
    /**
     * API 名称（显示名称）
     * 如果未指定，则使用类名
     */
    String name() default "";
    
    String description() default "";
    String version() default "1.0.0";
}
