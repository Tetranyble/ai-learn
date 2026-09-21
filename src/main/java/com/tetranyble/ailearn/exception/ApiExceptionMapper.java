package com.tetranyble.ailearn.exception;

import com.tetranyble.ailearn.api.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionMapper.class);

    @Override
    public Response toResponse(ApiException exception) {
        if (exception.getStatus() >= 500) {
            log.error(
                    "request_failed status={} code={}",
                    exception.getStatus(),
                    exception.getCode(),
                    exception
            );
        } else {
            log.warn(
                    "request_rejected status={} code={} reason={}",
                    exception.getStatus(),
                    exception.getCode(),
                    exception.getMessage()
            );
        }

        return Response.status(exception.getStatus())
                .entity(new ApiErrorResponse(
                        exception.getMessage(),
                        Map.of("code", exception.getCode())
                ))
                .build();
    }
}
