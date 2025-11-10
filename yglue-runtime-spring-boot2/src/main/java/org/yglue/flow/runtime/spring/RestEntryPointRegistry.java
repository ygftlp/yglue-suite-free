package org.yglue.flow.runtime.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.yglue.flow.runtime.core.definition.EntryPointLoader;
import org.yglue.flow.runtime.core.definition.EntryPointsDefinition;
import org.yglue.flow.runtime.core.definition.RestEntryPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class RestEntryPointRegistry {

    private static final Logger log = LoggerFactory.getLogger(RestEntryPointRegistry.class);

    private final List<RestEntryPoint> entryPoints;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RestEntryPointRegistry(List<RestEntryPoint> entryPoints) {
        this.entryPoints = entryPoints == null ? List.of() : new ArrayList<>(entryPoints);
    }

    public Optional<RestEntryPoint> findMatch(String method, String path) {
        String normalizedMethod = normalizeMethod(method);
        for (RestEntryPoint entryPoint : entryPoints) {
            if (entryPoint.getPath() == null || entryPoint.getFlowCode() == null) {
                continue;
            }
            if (entryPoint.getEnabled() != null && !entryPoint.getEnabled()) {
                continue;
            }
            if (!matchesMethod(entryPoint.getMethod(), normalizedMethod)) {
                continue;
            }
            if (matcher.match(entryPoint.getPath(), path)) {
                return Optional.of(entryPoint);
            }
        }
        return Optional.empty();
    }

    private String normalizeMethod(String method) {
        return method == null ? null : method.toUpperCase(Locale.ROOT);
    }

    private boolean matchesMethod(String configured, String actual) {
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return Objects.equals(configureUpper(configured), actual);
    }

    private String configureUpper(String method) {
        return method == null ? null : method.trim().toUpperCase(Locale.ROOT);
    }

    public static RestEntryPointRegistry loadDefault() {
        try {
            EntryPointsDefinition definition = new EntryPointLoader().load();
            return new RestEntryPointRegistry(definition.restsView());
        } catch (IOException ex) {
            log.debug("No entrypoints definition found: {}", ex.getMessage());
            return new RestEntryPointRegistry(List.of());
        }
    }
}
