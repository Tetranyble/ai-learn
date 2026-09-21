package com.tetranyble.ailearn.validation;

import com.tetranyble.ailearn.api.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Provider
public class ConstraintViolationMapper
        implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        Map<String, List<String>> errors = new LinkedHashMap<>();

        exception.getConstraintViolations().forEach(violation -> {
            String field = lastNode(violation.getPropertyPath());
            errors.computeIfAbsent(field, ignored -> new ArrayList<>())
                    .add(violation.getMessage());
        });

        return Response.status(422)
                .entity(new ApiErrorResponse(
                        "The given data was invalid.",
                        errors
                ))
                .build();
    }

    private String lastNode(Path path) {
        String field = "request";

        for (Path.Node node : path) {
            if (node.getName() != null) {
                field = node.getName();
            }
        }

        return field;
    }
}
