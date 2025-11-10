package org.yglue.flow.annotations;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface FlowOperation {
    String name();
    String description() default "";
    String[] tags() default {};
}
