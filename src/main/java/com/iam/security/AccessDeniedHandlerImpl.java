package com.iam.security;

import com.iam.exception.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Wired into SecurityConfig as the {@code accessDeniedHandler}: produces our standard
 * JSON {@code ErrorResponse} (403) instead of Spring's default error page.
 * <p>
 * In practice this only fires for denials raised by the security <em>filter chain</em>
 * itself (e.g. a future {@code authorizeHttpRequests} URL rule). Method-level
 * {@code @PreAuthorize} denials on controller endpoints - which is how every
 * permission check in this app is actually enforced - throw from inside the
 * DispatcherServlet's handler invocation and are caught by
 * {@code GlobalExceptionHandler#handleAccessDenied} instead, since a
 * {@code @RestControllerAdvice} intercepts them before they would reach this handler.
 * Both produce the same JSON shape, so callers never see the difference - but if
 * you're debugging why a breakpoint here isn't hit for an expected 403, that's why.
 */
@Component
@RequiredArgsConstructor
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        errorResponseWriter.write(response, HttpServletResponse.SC_FORBIDDEN, "You do not have permission to perform this action");
    }
}
