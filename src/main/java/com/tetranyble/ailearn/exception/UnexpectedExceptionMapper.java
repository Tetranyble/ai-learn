package com.tetranyble.ailearn.exception;

import com.tetranyble.ailearn.api.ApiErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Provider
public class UnexpectedExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger log = LoggerFactory.getLogger(UnexpectedExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        log.error("unhandled_request_failure", exception);

        return Response.serverError()
                .entity(new ApiErrorResponse(
                        "Internal server error.",
                        Map.of("code", "internal_server_error")
                ))
                .build();
    }
}
