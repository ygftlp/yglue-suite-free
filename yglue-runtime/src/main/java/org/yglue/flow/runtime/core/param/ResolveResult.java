package org.yglue.flow.runtime.core.param;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 参数解析结果。
 *
 * @param <T> 目标类型
 */
public final class ResolveResult<T> {

    private final T value;
    private final List<ResolveError> errors;
    private final boolean appliedDefault;

    private ResolveResult(Builder<T> builder) {
        this.value = builder.value;
        this.errors = Collections.unmodifiableList(new ArrayList<>(builder.errors));
        this.appliedDefault = builder.appliedDefault;
    }

    public T value() {
        return value;
    }

    public List<ResolveError> errors() {
        return errors;
    }

    public boolean appliedDefault() {
        return appliedDefault;
    }

    public boolean hasError() {
        return !errors.isEmpty();
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private T value;
        private final List<ResolveError> errors = new ArrayList<>();
        private boolean appliedDefault;

        private Builder() {
        }

        public Builder<T> value(T value) {
            this.value = value;
            return this;
        }

        public Builder<T> addError(ResolveError error) {
            errors.add(Objects.requireNonNull(error, "error must not be null"));
            return this;
        }

        public Builder<T> addErrors(List<ResolveError> errors) {
            if (errors != null) {
                errors.forEach(this::addError);
            }
            return this;
        }

        public Builder<T> appliedDefault(boolean appliedDefault) {
            this.appliedDefault = appliedDefault;
            return this;
        }

        public ResolveResult<T> build() {
            return new ResolveResult<>(this);
        }
    }
}




