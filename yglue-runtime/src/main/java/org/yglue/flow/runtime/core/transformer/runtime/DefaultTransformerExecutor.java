package org.yglue.flow.runtime.core.transformer.runtime;

import org.yglue.flow.runtime.core.param.ResolveError;
import org.yglue.flow.runtime.core.param.ResolveResult;
import org.yglue.flow.runtime.core.param.ValueType;
import org.yglue.flow.runtime.core.transformer.dsl.TransformerCollectionStep;
import org.yglue.flow.runtime.core.transformer.dsl.TransformerConditionStep;
import org.yglue.flow.runtime.core.transformer.dsl.TransformerFieldStep;
import org.yglue.flow.runtime.core.transformer.dsl.TransformerObjectStep;
import org.yglue.flow.runtime.core.transformer.dsl.TransformerOutputMode;
import org.yglue.flow.runtime.core.transformer.dsl.TransformerScriptStep;
import org.yglue.flow.runtime.core.transformer.dsl.TransformerStep;
import org.yglue.flow.runtime.core.transformer.dsl.TransformerTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 默认的 Transformer DSL 执行器。
 */
public class DefaultTransformerExecutor implements TransformerExecutor {

    @Override
    public TransformerExecutionResult execute(TransformerTemplate template, TransformerExecutionContext context) {
        Objects.requireNonNull(template, "template must not be null");
        Objects.requireNonNull(context, "context must not be null");
        ExecutionState state = new ExecutionState(context, template.outputMode());
        Map<String, Object> root = new LinkedHashMap<>();

        applySteps(template.steps(), root, "", new Scope(), state);

        Object output;
        if (state.outputMode == TransformerOutputMode.SINGLE) {
            if (state.singleValue != null) {
                output = state.singleValue;
            } else if (root.size() == 1) {
                output = root.values().iterator().next();
            } else {
                output = root;
            }
        } else {
            output = root;
        }

        return new TransformerExecutionResult(output, state.errors);
    }

    private void applySteps(List<TransformerStep> steps, Map<String, Object> targetRoot, String basePath, Scope scope, ExecutionState state) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        for (TransformerStep step : steps) {
            applyStep(step, targetRoot, basePath, scope, state);
        }
    }

    private void applyStep(TransformerStep step, Map<String, Object> targetRoot, String basePath, Scope scope, ExecutionState state) {
        switch (step.type()) {
            case FIELD -> applyFieldStep((TransformerFieldStep) step, targetRoot, basePath, scope, state);
            case OBJECT -> applyObjectStep((TransformerObjectStep) step, targetRoot, basePath, scope, state);
            case COLLECTION -> applyCollectionStep((TransformerCollectionStep) step, targetRoot, basePath, scope, state);
            case CONDITIONAL -> applyConditionStep((TransformerConditionStep) step, targetRoot, basePath, scope, state);
            case SCRIPT -> state.addError(ResolveError.builder("TRANSFORMER_SCRIPT_UNSUPPORTED", "Transformer script step is not supported yet")
                    .sourceField(combinePath(basePath, ((TransformerScriptStep) step).language()))
                    .build());
            default -> throw new IllegalArgumentException("Unsupported transformer step: " + step.type());
        }
    }

    private void applyFieldStep(TransformerFieldStep step, Map<String, Object> targetRoot, String basePath, Scope scope, ExecutionState state) {
        String targetPath = combinePath(basePath, step.target());
        Object value = null;
        boolean fromResolver = false;

        if (step.resolverRef() != null && !step.resolverRef().isBlank()) {
            ResolveResult<?> resolved = state.context.resolved(step.resolverRef());
            if (resolved != null) {
                value = resolved.value();
                state.addErrors(resolved.errors());
                fromResolver = true;
            }
        }

        if (value == null && step.source() != null && !step.source().isBlank()) {
            value = state.context.evaluateExpression(step.source(), scope.variables);
        }

        if (step.expression() != null && !step.expression().isBlank()) {
            Map<String, Object> extra = new HashMap<>(scope.variables);
            extra.put("value", value);
            value = state.context.evaluateExpression(step.expression(), extra);
        }

        if (value == null && step.defaultValue() != null) {
            value = step.defaultValue();
            state.setAppliedDefault();
        }

        value = castValue(value, step.cast());

        if (step.required() && isEmpty(value)) {
            state.addError(ResolveError.builder("TRANSFORMER_REQUIRED", "Required field is missing")
                    .sourceField(targetPath)
                    .rawValue(value)
                    .build());
            return;
        }

        if (state.outputMode == TransformerOutputMode.SINGLE && (targetPath == null || targetPath.isBlank())) {
            state.singleValue = value;
        } else {
            writeValue(targetRoot, targetPath, value);
        }

        if (fromResolver) {
            // mark value as resolved for expression usage
            scope.variables.put(step.target(), value);
        }
    }

    private void applyObjectStep(TransformerObjectStep step, Map<String, Object> targetRoot, String basePath, Scope scope, ExecutionState state) {
        String targetPath = combinePath(basePath, step.target());
        Map<String, Object> nested = ensureMap(targetRoot, targetPath);
        applySteps(step.properties(), nested, "", scope.copy(), state);
    }

    private void applyCollectionStep(TransformerCollectionStep step, Map<String, Object> targetRoot, String basePath, Scope scope, ExecutionState state) {
        String targetPath = combinePath(basePath, step.target());
        Object sourceValue = null;

        if (step.resolverRef() != null && !step.resolverRef().isBlank()) {
            ResolveResult<?> resolved = state.context.resolved(step.resolverRef());
            if (resolved != null) {
                sourceValue = resolved.value();
                state.addErrors(resolved.errors());
            }
        }
        if (sourceValue == null && step.source() != null && !step.source().isBlank()) {
            sourceValue = state.context.evaluateExpression(step.source(), scope.variables);
        }

        if (sourceValue == null) {
            writeValue(targetRoot, targetPath, List.of());
            return;
        }

        Collection<?> collection;
        if (sourceValue instanceof Collection<?> col) {
            collection = col;
        } else if (sourceValue instanceof Object[] array) {
            collection = Arrays.asList(array);
        } else if (sourceValue instanceof Iterable<?> iterable) {
            List<Object> tmp = new ArrayList<>();
            iterable.forEach(tmp::add);
            collection = tmp;
        } else {
            collection = List.of(sourceValue);
        }

        List<Object> results = new ArrayList<>();
        for (Object item : collection) {
            Scope itemScope = scope.copy();
            itemScope.variables.put(step.itemAlias(), item);

            if (step.filter() != null && !step.filter().isBlank()) {
                if (!state.context.evaluateCondition(step.filter(), itemScope.variables)) {
                    continue;
                }
            }

            Map<String, Object> elementTarget = new LinkedHashMap<>();
            applySteps(step.itemSteps(), elementTarget, "", itemScope, state);
            results.add(elementTarget);
        }

        writeValue(targetRoot, targetPath, results);
    }

    private void applyConditionStep(TransformerConditionStep step, Map<String, Object> targetRoot, String basePath, Scope scope, ExecutionState state) {
        boolean match = state.context.evaluateCondition(step.when(), scope.variables);
        List<TransformerStep> branches = match ? step.thenSteps() : step.elseSteps();
        applySteps(branches, targetRoot, basePath, scope.copy(), state);
    }

    private String combinePath(String basePath, String target) {
        if (target == null || target.isBlank()) {
            return basePath;
        }
        if (basePath == null || basePath.isBlank()) {
            return target;
        }
        return basePath + "." + target;
    }

    private void writeValue(Map<String, Object> root, String path, Object value) {
        if (path == null || path.isBlank()) {
            if (value instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    root.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object existing = current.get(part);
            if (existing instanceof Map<?, ?> map) {
                current = (Map<String, Object>) map;
            } else if (existing == null) {
                Map<String, Object> next = new LinkedHashMap<>();
                current.put(part, next);
                current = next;
            } else {
                Map<String, Object> next = new LinkedHashMap<>();
                current.put(part, next);
                current = next;
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureMap(Map<String, Object> root, String path) {
        if (path == null || path.isBlank()) {
            return root;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (String part : parts) {
            Object existing = current.get(part);
            if (existing instanceof Map<?, ?> map) {
                current = (Map<String, Object>) map;
            } else {
                Map<String, Object> next = new LinkedHashMap<>();
                current.put(part, next);
                current = next;
            }
        }
        return current;
    }

    private Object castValue(Object value, ValueType targetType) {
        if (targetType == null || targetType == ValueType.AUTO || value == null) {
            return value;
        }
        try {
            return switch (targetType) {
                case STRING -> String.valueOf(value);
                case BOOLEAN -> castBoolean(value);
                case INTEGER -> value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
                case LONG -> value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
                case DOUBLE, FLOAT -> value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
                case DECIMAL -> value instanceof BigDecimal bd ? bd : new BigDecimal(String.valueOf(value));
                case DATETIME -> value instanceof OffsetDateTime odt ? odt : OffsetDateTime.parse(String.valueOf(value));
                default -> value;
            };
        } catch (NumberFormatException | DateTimeParseException ex) {
            return value;
        }
    }

    private Boolean castBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String str) {
            return str.isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        return false;
    }

    private static final class ExecutionState {
        private final TransformerExecutionContext context;
        private final TransformerOutputMode outputMode;
        private final List<ResolveError> errors = new ArrayList<>();
        private boolean appliedDefault;
        private Object singleValue;

        private ExecutionState(TransformerExecutionContext context, TransformerOutputMode outputMode) {
            this.context = context;
            this.outputMode = outputMode;
        }

        private void addError(ResolveError error) {
            errors.add(error);
        }

        private void addErrors(List<ResolveError> list) {
            if (list != null && !list.isEmpty()) {
                errors.addAll(list);
            }
        }

        private void setAppliedDefault() {
            this.appliedDefault = true;
        }
    }

    private static final class Scope {
        private final Map<String, Object> variables = new HashMap<>();

        private Scope copy() {
            Scope scope = new Scope();
            scope.variables.putAll(this.variables);
            return scope;
        }
    }
}

