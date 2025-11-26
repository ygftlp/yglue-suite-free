package org.yglue.flow.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一个解析器（ParamResolver）元数据。
 * <p>
 * 该注解主要用于 IDE 插件 & 元数据上报，描述解析器的类型、名称、说明及配置结构等信息。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FlowResolver {

    /**
     * 解析器类型唯一标识（例如 REQUEST / CONTEXT / EXPRESSION）。
     */
    String value();

    /**
     * 展示名称。
     */
    String name() default "";

    /**
     * 解析器描述。
     */
    String description() default "";

    /**
     * 解析器分组/类别，如 "HTTP"、"上下文" 等。
     */
    String category() default "";

    /**
     * 配置 schema，使用 JSON 字符串描述前端所需的配置项（可选）。
     */
    String configSchema() default "";

    /**
     * 是否为平台内置解析器。
     */
    boolean builtin() default false;
}





