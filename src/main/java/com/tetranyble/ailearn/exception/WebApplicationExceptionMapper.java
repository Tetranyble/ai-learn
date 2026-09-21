package com.tetranyble.ailearn.exception;

import com.tetranyble.ailearn.api.ApiErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class WebApplicationExceptionMapper
        implements ExceptionMapper<WebApplicationException> {

    @Override
    public Response toResponse(WebApplicationException exception) {
        int status = exception.getResponse().getStatus();

        return Response.status(status)
                .entity(new ApiErrorResponse(
                        messageFor(status),
                        Map.of("code", "http_error")
                ))
                .build();
    }

    private String messageFor(int status) {
        return switch (status) {
            case 400 -> "Bad request.";
            case 401 -> "Unauthenticated.";
            case 403 -> "Forbidden.";
            case 404 -> "Resource not found.";
            case 405 -> "Method not allowed.";
            case 409 -> "Conflict.";
            case 410 -> "Resource is no longer available.";
            case 415 -> "Unsupported media type.";
            default -> "HTTP request failed.";
        };
    }
}
