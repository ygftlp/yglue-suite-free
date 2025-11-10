package org.yglue.flow.orch.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.time.Instant;

@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);
    private static final String ATTR_START = RequestLoggingInterceptor.class.getName() + ".start";

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request,
                             @NotNull HttpServletResponse response,
                             @NotNull Object handler) {
        request.setAttribute(ATTR_START, Instant.now());
        return true;
    }

    @Override
    public void afterCompletion(@NotNull HttpServletRequest request,
                                @NotNull HttpServletResponse response,
                                @NotNull Object handler,
                                @Nullable Exception ex) {
        Instant started = (Instant) request.getAttribute(ATTR_START);
        long elapsedMs = started != null ? Duration.between(started, Instant.now()).toMillis() : -1;
        int status = response.getStatus();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String remote = request.getRemoteAddr();
        String target = query == null ? uri : uri + "?" + query;

        if (ex != null) {
            log.error("HTTP {} {} -> {} ({} ms) from {}", method, target, status, elapsedMs, remote, ex);
            return;
        }

        if (status >= 500) {
            log.error("HTTP {} {} -> {} ({} ms) from {}", method, target, status, elapsedMs, remote);
        } else if (status >= 400) {
            log.warn("HTTP {} {} -> {} ({} ms) from {}", method, target, status, elapsedMs, remote);
        } else {
            log.info("HTTP {} {} -> {} ({} ms) from {}", method, target, status, elapsedMs, remote);
        }
    }
}
