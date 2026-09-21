package com.tetranyble.ailearn.database;

import com.tetranyble.ailearn.course.CourseRepository;
import com.tetranyble.ailearn.database.factory.CourseFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.stream.IntStream;

@Component
@Profile("dev")
public class DatabaseSeeder implements ApplicationRunner {

    private final CourseRepository courses;

    public DatabaseSeeder(CourseRepository courses) {
        this.courses = courses;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (courses.count() > 0) {
            return;
        }

        courses.saveAll(
                IntStream.rangeClosed(1, 20)
                        .mapToObj(CourseFactory::make)
                        .toList()
        );
    }
}
