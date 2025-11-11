package org.yglue.flow.runtime.core.param;

import java.util.Objects;

/**
 * 解析过程中的错误信息。
 */
public final class ResolveError {
    private final String code;
    private final String message;
    private final String sourceField;
    private final Object rawValue;

    private ResolveError(Builder builder) {
        this.code = builder.code;
        this.message = builder.message;
        this.sourceField = builder.sourceField;
        this.rawValue = builder.rawValue;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public String sourceField() {
        return sourceField;
    }

    public Object rawValue() {
        return rawValue;
    }

    public static Builder builder(String code, String message) {
        return new Builder(code, message);
    }

    public static final class Builder {
        private final String code;
        private final String message;
        private String sourceField;
        private Object rawValue;

        private Builder(String code, String message) {
            this.code = Objects.requireNonNull(code, "code must not be null");
            this.message = Objects.requireNonNull(message, "message must not be null");
        }

        public Builder sourceField(String sourceField) {
            this.sourceField = sourceField;
            return this;
        }

        public Builder rawValue(Object rawValue) {
            this.rawValue = rawValue;
            return this;
        }

        public ResolveError build() {
            return new ResolveError(this);
        }
    }
}


