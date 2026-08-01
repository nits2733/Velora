package com.velora.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velora.backend.exception.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Renders security failures raised inside the filter chain - before any controller runs,
 * so {@link com.velora.backend.exception.GlobalExceptionHandler} never sees them - in the
 * same {@link ApiErrorResponse} shape the rest of the API uses. Implements both Spring
 * Security SPIs because the two cases differ only in status and message.
 */
@Component
@RequiredArgsConstructor
public class RestSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** No (or invalid) credentials on a protected endpoint. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED,
                "Authentication is required to access this resource");
    }

    /** Authenticated, but the role is not allowed to perform this action. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                        HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                null
        );

        objectMapper.writeValue(response.getWriter(), body);
    }
}
