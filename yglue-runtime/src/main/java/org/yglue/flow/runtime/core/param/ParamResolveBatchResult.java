package org.yglue.flow.runtime.core.param;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 批量解析结果集合。
 */
public final class ParamResolveBatchResult {

    private final Map<String, ResolveResult<?>> results;

    private ParamResolveBatchResult(Map<String, ResolveResult<?>> results) {
        this.results = results;
    }

    public Map<String, ResolveResult<?>> results() {
        return results;
    }

    public ResolveResult<?> get(String name) {
        return results.get(name);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, ResolveResult<?>> results = new LinkedHashMap<>();

        public Builder put(String name, ResolveResult<?> result) {
            if (name != null && result != null) {
                results.put(name, result);
            }
            return this;
        }

        public Builder putAll(Map<String, ResolveResult<?>> resultMap) {
            if (resultMap != null) {
                resultMap.forEach(this::put);
            }
            return this;
        }

        public ParamResolveBatchResult build() {
            return new ParamResolveBatchResult(Collections.unmodifiableMap(new LinkedHashMap<>(results)));
        }
    }
}


