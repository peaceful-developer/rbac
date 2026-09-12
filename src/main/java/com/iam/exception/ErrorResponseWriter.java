package com.iam.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iam.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes a JSON {@link ErrorResponse} directly to the response for failures that happen
 * outside the DispatcherServlet's exception-handling flow (filters, entry points).
 */
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        ErrorResponse body = ErrorResponse.of(status, org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase(),
                message, null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
