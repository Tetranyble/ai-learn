package com.tetranyble.ailearn.validation;

import com.tetranyble.ailearn.api.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class RequestValidationExceptionMapper
        implements ExceptionMapper<RequestValidationException> {

    @Override
    public Response toResponse(RequestValidationException exception) {
        return Response.status(422)
                .entity(new ApiErrorResponse(
                        exception.getMessage(),
                        exception.getErrors()
                ))
                .build();
    }
}
