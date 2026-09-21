package com.tetranyble.ailearn.course;

import com.tetranyble.ailearn.validation.RequestValidationException;
import com.tetranyble.ailearn.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

@Component
@Path("/api/v1/courses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CourseResource {

    private final CourseRepository courses;

    public CourseResource(CourseRepository courses) {
        this.courses = courses;
    }

    @GET
    public List<Course> index() {
        return courses.findAll();
    }

    @GET
    @Path("{id}")
    public Course show(
            @Positive(message = "id must be a positive number")
            @PathParam("id") Long id
    ) {
        return findCourse(id);
    }

    @POST
    public Response store(
            @Valid CourseRequest request,
            @Context UriInfo uriInfo
    ) {
        String slug = request.slug().trim();
        ensureSlugIsAvailable(slug, null);

        Course course = new Course(
                request.title().trim(),
                slug,
                normalizedDescription(request.description())
        );

        Course saved = courses.save(course);

        URI location = uriInfo.getAbsolutePathBuilder()
                .path(saved.getId().toString())
                .build();

        return Response.created(location)
                .entity(saved)
                .build();
    }

    @PUT
    @Path("{id}")
    public Course update(
            @Positive(message = "id must be a positive number")
            @PathParam("id") Long id,
            @Valid CourseRequest request
    ) {
        Course course = findCourse(id);
        String slug = request.slug().trim();
        ensureSlugIsAvailable(slug, id);

        course.update(
                request.title().trim(),
                slug,
                normalizedDescription(request.description())
        );

        return courses.save(course);
    }

    @DELETE
    @Path("{id}")
    public Response destroy(
            @Positive(message = "id must be a positive number")
            @PathParam("id") Long id
    ) {
        Course course = findCourse(id);
        courses.delete(course);

        return Response.ok().build();
    }

    private Course findCourse(Long id) {
        return courses.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Course", id)
                );
    }

    private void ensureSlugIsAvailable(String slug, Long ignoredCourseId) {
        courses.findBySlug(slug)
                .filter(course -> !course.getId().equals(ignoredCourseId))
                .ifPresent(course -> {
                    throw RequestValidationException.forField(
                            "slug",
                            "slug has already been taken"
                    );
                });
    }

    private String normalizedDescription(String description) {
        return description == null ? null : description.trim();
    }
}
