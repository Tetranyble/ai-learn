package com.tetranyble.ailearn.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;

import java.time.Instant;

@Provider
public class ApiResponseFilter implements ContainerResponseFilter {

    @Context
    private HttpServletRequest servletRequest;

    @Override
    public void filter(
            ContainerRequestContext requestContext,
            ContainerResponseContext responseContext
    ) {
        MediaType responseType = responseContext.getMediaType();
        if (responseType != null
                && responseType.isCompatible(MediaType.SERVER_SENT_EVENTS_TYPE)) {
            return;
        }

        Object entity = responseContext.getEntity();

        if (entity instanceof ApiResponse<?>) {
            return;
        }

        int status = responseContext.getStatus();
        boolean success = status >= 200 && status < 400;
        String message;
        Object data = null;
        Object errors = null;

        if (success) {
            message = successMessage(status);
            data = entity;
        } else if (entity instanceof ApiErrorResponse error) {
            message = error.message();
            errors = error.errors();
        } else {
            message = errorMessage(status);
        }

        String requestId = (String) servletRequest.getAttribute(RequestContext.REQUEST_ID);

        responseContext.setEntity(new ApiResponse<>(
                success,
                message,
                data,
                errors,
                new ApiResponseMeta(requestId),
                Instant.now()
        ));
        responseContext.getHeaders().putSingle(
                "Content-Type",
                MediaType.APPLICATION_JSON
        );
    }

    private String successMessage(int status) {
        if (status == Response.Status.CREATED.getStatusCode()) {
            return "Resource created successfully.";
        }

        return "Request successful.";
    }

    private String errorMessage(int status) {
        return switch (status) {
            case 400 -> "Bad request.";
            case 401 -> "Unauthenticated.";
            case 403 -> "Forbidden.";
            case 404 -> "Resource not found.";
            case 405 -> "Method not allowed.";
            case 409 -> "The request conflicts with the current resource state.";
            case 410 -> "Resource is no longer available.";
            case 422 -> "The given data was invalid.";
            case 502 -> "An upstream service is temporarily unavailable.";
            case 503 -> "Service temporarily unavailable.";
            default -> "Internal server error.";
        };
    }
}
