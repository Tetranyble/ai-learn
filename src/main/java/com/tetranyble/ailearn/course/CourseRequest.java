package com.tetranyble.ailearn.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CourseRequest(
        @NotBlank(message = "title is required")
        @Size(max = 160, message = "title must not exceed 160 characters")
        String title,

        @NotBlank(message = "slug is required")
        @Size(max = 180, message = "slug must not exceed 180 characters")
        @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "slug must contain lowercase letters, numbers, and hyphens"
        )
        String slug,

        @Size(
                max = 10_000,
                message = "description must not exceed 10000 characters"
        )
        String description
) {
}
