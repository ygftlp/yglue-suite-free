package org.yglue.flow.annotations;

import java.lang.annotation.*;

/**
 * FlowModel 注解
 * 用于标记在流程编排中可复用的领域模型
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface FlowModel {

    /**
     * 模型标识，默认使用类名
     */
    String value() default "";

    /**
     * 展示名称
     */
    String name() default "";

    /**
     * 模型描述
     */
    String description() default "";

    /**
     * 模型分类，可用于前端分组展示
     */
    String category() default "";

    /**
     * 模型版本
     */
    String version() default "1.0.0";

    /**
     * 额外标签
     */
    String[] tags() default {};
}





