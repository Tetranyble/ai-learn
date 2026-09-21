package com.tetranyble.ailearn.course;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "langchain4j.open-ai.streaming-chat-model.api-key=test-key")
@ActiveProfiles("test")
@Transactional
class CourseRepositoryTests {

    @Autowired
    private CourseRepository courses;

    @Test
    void performsCrudWithEntityManager() {
        Course course = courses.save(new Course(
                "Practical AI",
                "practical-ai",
                "Build useful AI applications."
        ));

        assertThat(course.getId()).isNotNull();
        assertThat(courses.count()).isOne();
        assertThat(courses.findById(course.getId())).contains(course);
        assertThat(courses.findBySlug("practical-ai")).contains(course);
        assertThat(courses.existsBySlug("practical-ai")).isTrue();

        course.update(
                "Practical AI Engineering",
                "practical-ai-engineering",
                "Build production AI applications."
        );

        Course updated = courses.save(course);

        assertThat(updated.getTitle()).isEqualTo("Practical AI Engineering");
        assertThat(courses.findAll()).containsExactly(updated);

        courses.delete(updated);

        assertThat(courses.count()).isZero();
    }
}
