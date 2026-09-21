package com.tetranyble.ailearn.database.factory;

import com.tetranyble.ailearn.course.Course;

import java.util.UUID;

public final class CourseFactory {

    private CourseFactory() {
    }

    public static Course make(int sequence) {
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        return new Course(
                "AI Course " + sequence,
                "ai-course-" + sequence + "-" + suffix,
                "A generated course for learning AI concepts."
        );
    }
}
