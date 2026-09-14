package com.iam.security;

import com.iam.exception.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Wired into SecurityConfig as the {@code authenticationEntryPoint}: Spring Security
 * invokes this whenever an unauthenticated request hits an endpoint that requires
 * authentication (i.e. no valid bearer token was ever presented). Its only job is to
 * turn that into our standard JSON {@code ErrorResponse} shape (401) instead of
 * Spring's default HTML/plain-text error page.
 */
@Component
@RequiredArgsConstructor
public class AuthEntryPointJwt implements AuthenticationEntryPoint {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication is required to access this resource");
    }
}
