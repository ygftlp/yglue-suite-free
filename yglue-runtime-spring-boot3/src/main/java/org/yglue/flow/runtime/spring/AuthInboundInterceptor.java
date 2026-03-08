package org.yglue.flow.runtime.spring;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

class AuthInboundInterceptor implements InboundRequestInterceptor {

    @Override
    public String code() {
        return "auth";
    }

    @Override
    public void apply(InboundRequestContext context, Map<String, Object> config) {
        boolean required = readBoolean(config, "required", true);
        String principalPath = readString(config, "principalPath", "request.auth.user");
        String userIdPath = readString(config, "userIdPath", "request.auth.userId");

        Principal principal = context.request().getUserPrincipal();
        if (principal == null) {
            if (required) {
                throw InboundInterceptorException.unauthorized("Authentication required");
            }
            return;
        }

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("name", principal.getName());
        user.put("authType", context.request().getAuthType());

        context.put(principalPath, user);
        context.put(userIdPath, principal.getName());
    }

    private boolean readBoolean(Map<String, Object> config, String key, boolean fallback) {
        if (config == null) return fallback;
        Object value = config.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String readString(Map<String, Object> config, String key, String fallback) {
        if (config == null) return fallback;
        Object value = config.get(key);
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }
}

