package org.yglue.flow.runtime.core.param;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 参数解析指令定义。
 */
public final class ResolveInstruction {

    private final String name;
    private final SourceType source;
    private final String key;
    private final String expression;
    private final Object constantValue;
    private final Object defaultValue;
    private final ValueType targetType;
    private final boolean required;
    private final List<String> validators;
    private final String description;

    private ResolveInstruction(Builder builder) {
        this.name = builder.name;
        this.source = builder.source;
        this.key = builder.key;
        this.expression = builder.expression;
        this.constantValue = builder.constantValue;
        this.defaultValue = builder.defaultValue;
        this.targetType = builder.targetType;
        this.required = builder.required;
        this.validators = Collections.unmodifiableList(new ArrayList<>(builder.validators));
        this.description = builder.description;
    }

    public String name() {
        return name;
    }

    public SourceType source() {
        return source;
    }

    public String key() {
        return key;
    }

    public String expression() {
        return expression;
    }

    public Object constantValue() {
        return constantValue;
    }

    public Object defaultValue() {
        return defaultValue;
    }

    public ValueType targetType() {
        return targetType;
    }

    public boolean required() {
        return required;
    }

    public List<String> validators() {
        return validators;
    }

    public String description() {
        return description;
    }

    public static Builder builder(SourceType source) {
        return new Builder(source);
    }

    public static final class Builder {
        private final SourceType source;
        private String name;
        private String key;
        private String expression;
        private Object constantValue;
        private Object defaultValue;
        private ValueType targetType = ValueType.AUTO;
        private boolean required;
        private final List<String> validators = new ArrayList<>();
        private String description;

        private Builder(SourceType source) {
            this.source = Objects.requireNonNull(source, "source must not be null");
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder expression(String expression) {
            this.expression = expression;
            return this;
        }

        public Builder constantValue(Object constantValue) {
            this.constantValue = constantValue;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder targetType(ValueType targetType) {
            if (targetType != null) {
                this.targetType = targetType;
            }
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder addValidator(String validator) {
            if (validator != null && !validator.isBlank()) {
                this.validators.add(validator);
            }
            return this;
        }

        public Builder validators(List<String> validators) {
            if (validators != null) {
                validators.forEach(this::addValidator);
            }
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public ResolveInstruction build() {
            return new ResolveInstruction(this);
        }
    }
}




