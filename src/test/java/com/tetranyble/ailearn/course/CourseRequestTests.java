package com.tetranyble.ailearn.course;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourseRequestTests {

    @Test
    void acceptsAValidRequest() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var violations = validatorFactory.getValidator().validate(
                    new CourseRequest(
                            "Practical AI",
                            "practical-ai",
                            "Build useful AI applications."
                    )
            );

            assertThat(violations).isEmpty();
        }
    }

    @Test
    void rejectsInvalidFields() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var violations = validatorFactory.getValidator().validate(
                    new CourseRequest(" ", "Not A Slug", null)
            );

            assertThat(violations)
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("title", "slug");
        }
    }
}
