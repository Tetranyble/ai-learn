package com.tetranyble.ailearn.api;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Object errors,
        ApiResponseMeta meta,
        Instant timestamp
) {
}
