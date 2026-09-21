package com.tetranyble.ailearn.exception;

import com.tetranyble.ailearn.api.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;

@Provider
public class DataIntegrityViolationExceptionMapper
        implements ExceptionMapper<DataIntegrityViolationException> {

    @Override
    public Response toResponse(DataIntegrityViolationException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ApiErrorResponse(
                        "The request conflicts with the current resource state.",
                        Map.of("code", "resource_conflict")
                ))
                .build();
    }
}
