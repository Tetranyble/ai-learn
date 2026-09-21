package com.tetranyble.ailearn.course;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class CourseRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Course> findAll() {
        return entityManager.createQuery("""
                select course
                from Course course
                order by course.createdAt desc
                """, Course.class)
                .getResultList();
    }

    public Optional<Course> findById(Long id) {
        return Optional.ofNullable(entityManager.find(Course.class, id));
    }

    public Optional<Course> findBySlug(String slug) {
        return entityManager.createQuery("""
                select course
                from Course course
                where course.slug = :slug
                """, Course.class)
                .setParameter("slug", slug)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    public boolean existsBySlug(String slug) {
        Long matches = entityManager.createQuery("""
                select count(course)
                from Course course
                where course.slug = :slug
                """, Long.class)
                .setParameter("slug", slug)
                .getSingleResult();

        return matches > 0;
    }

    public long count() {
        return entityManager.createQuery(
                        "select count(course) from Course course",
                        Long.class
                )
                .getSingleResult();
    }

    @Transactional
    public Course save(Course course) {
        return saveWithinCurrentTransaction(course);
    }

    @Transactional
    public List<Course> saveAll(Iterable<Course> courses) {
        List<Course> savedCourses = new ArrayList<>();

        for (Course course : courses) {
            savedCourses.add(saveWithinCurrentTransaction(course));
        }

        return savedCourses;
    }

    @Transactional
    public void delete(Course course) {
        Course managedCourse = entityManager.contains(course)
                ? course
                : entityManager.merge(course);

        entityManager.remove(managedCourse);
    }

    private Course saveWithinCurrentTransaction(Course course) {
        if (course.getId() == null) {
            entityManager.persist(course);
            return course;
        }

        return entityManager.merge(course);
    }
}
