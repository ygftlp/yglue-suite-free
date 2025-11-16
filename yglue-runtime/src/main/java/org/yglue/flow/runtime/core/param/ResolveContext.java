package org.yglue.flow.runtime.core.param;

import org.yglue.flow.runtime.FlowContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 参数解析运行时上下文。
 */
public final class ResolveContext {

    private final FlowContext flowContext;
    private final Map<String, Object> pathParams;
    private final Map<String, Object> queryParams;
    private final Map<String, Object> headers;
    private final Map<String, Object> formData;
    private final Object body;
    private final Map<String, Object> environment;
    private final Map<String, Object> variables;

    private ResolveContext(Builder builder) {
        this.flowContext = builder.flowContext;
        this.pathParams = unmodifiable(builder.pathParams);
        this.queryParams = unmodifiable(builder.queryParams);
        this.headers = unmodifiable(builder.headers);
        this.formData = unmodifiable(builder.formData);
        this.body = builder.body;
        this.environment = unmodifiable(builder.environment);
        this.variables = unmodifiable(builder.variables);
    }

    public FlowContext flowContext() {
        return flowContext;
    }

    public Map<String, Object> pathParams() {
        return pathParams;
    }

    public Map<String, Object> queryParams() {
        return queryParams;
    }

    public Map<String, Object> headers() {
        return headers;
    }

    public Map<String, Object> formData() {
        return formData;
    }

    public Object body() {
        return body;
    }

    public Map<String, Object> environment() {
        return environment;
    }

    public Map<String, Object> variables() {
        return variables;
    }

    private Map<String, Object> unmodifiable(Map<String, Object> source) {
        return Collections.unmodifiableMap(new HashMap<>(source));
    }

    public static Builder builder(FlowContext flowContext) {
        return new Builder(flowContext);
    }

    public static final class Builder {
        private final FlowContext flowContext;
        private final Map<String, Object> pathParams = new HashMap<>();
        private final Map<String, Object> queryParams = new HashMap<>();
        private final Map<String, Object> headers = new HashMap<>();
        private final Map<String, Object> formData = new HashMap<>();
        private Object body;
        private final Map<String, Object> environment = new HashMap<>();
        private final Map<String, Object> variables = new HashMap<>();

        private Builder(FlowContext flowContext) {
            this.flowContext = Objects.requireNonNull(flowContext, "flowContext must not be null");
        }

        public Builder pathParam(String key, Object value) {
            pathParams.put(key, value);
            return this;
        }

        public Builder pathParams(Map<String, ?> params) {
            if (params != null) {
                params.forEach(this::pathParam);
            }
            return this;
        }

        public Builder queryParam(String key, Object value) {
            queryParams.put(key, value);
            return this;
        }

        public Builder queryParams(Map<String, ?> params) {
            if (params != null) {
                params.forEach(this::queryParam);
            }
            return this;
        }

        public Builder header(String key, Object value) {
            headers.put(key, value);
            return this;
        }

        public Builder headers(Map<String, ?> headers) {
            if (headers != null) {
                headers.forEach(this::header);
            }
            return this;
        }

        public Builder formField(String key, Object value) {
            formData.put(key, value);
            return this;
        }

        public Builder formData(Map<String, ?> form) {
            if (form != null) {
                form.forEach(this::formField);
            }
            return this;
        }

        public Builder body(Object body) {
            this.body = body;
            return this;
        }

        public Builder environment(String key, Object value) {
            environment.put(key, value);
            return this;
        }

        public Builder environment(Map<String, ?> env) {
            if (env != null) {
                env.forEach(this::environment);
            }
            return this;
        }

        public Builder variable(String key, Object value) {
            variables.put(key, value);
            return this;
        }

        public Builder variables(Map<String, ?> vars) {
            if (vars != null) {
                vars.forEach(this::variable);
            }
            return this;
        }

        public ResolveContext build() {
            return new ResolveContext(this);
        }
    }
}




