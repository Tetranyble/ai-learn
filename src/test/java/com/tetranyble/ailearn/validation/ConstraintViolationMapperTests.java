package com.tetranyble.ailearn.validation;

import com.tetranyble.ailearn.api.ApiErrorResponse;
import com.tetranyble.ailearn.course.CourseRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConstraintViolationMapperTests {

    @Test
    void returnsLaravelStyleValidationErrors() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var violations = validatorFactory.getValidator().validate(
                    new CourseRequest("", "Invalid Slug", null)
            );

            var exception = new ConstraintViolationException(violations);

            try (var response = new ConstraintViolationMapper().toResponse(exception)) {
                assertThat(response.getStatus()).isEqualTo(422);
                assertThat(response.getEntity())
                        .isEqualTo(new ApiErrorResponse(
                                "The given data was invalid.",
                                java.util.Map.of(
                                        "title", List.of("title is required"),
                                        "slug", List.of(
                                                "slug must contain lowercase letters, numbers, and hyphens"
                                        )
                                )
                        ));
            }
        }
    }
}
