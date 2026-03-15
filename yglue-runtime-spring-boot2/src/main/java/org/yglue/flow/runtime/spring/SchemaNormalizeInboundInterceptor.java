package org.yglue.flow.runtime.spring;

import java.util.Map;

class SchemaNormalizeInboundInterceptor implements InboundRequestInterceptor {

    private final RequestSchemaValidator requestSchemaValidator;

    SchemaNormalizeInboundInterceptor(RequestSchemaValidator requestSchemaValidator) {
        this.requestSchemaValidator = requestSchemaValidator;
    }

    @Override
    public String code() {
        return "schemaNormalize";
    }

    @Override
    public void apply(InboundRequestContext context, Map<String, Object> config) {
        if (requestSchemaValidator == null || context.entryPoint() == null) {
            return;
        }
        RequestSchemaValidator.ValidationResult result =
                requestSchemaValidator.validate(context.entryPoint(), context.request(), context.payload());
        if (!result.isValid()) {
            throw new InboundInterceptorException(400, result.toErrorBody());
        }
        if (result.getNormalized() == null) {
            return;
        }
        String outputPath = readString(config, "outputPath", "request.params");
        context.put(outputPath, result.getNormalized());
    }

    private String readString(Map<String, Object> config, String key, String fallback) {
        if (config == null) return fallback;
        Object value = config.get(key);
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }
}
