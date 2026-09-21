package com.tetranyble.ailearn.config;

import com.tetranyble.ailearn.chat.ChatResource;
import com.tetranyble.ailearn.chat.ConversationResource;
import com.tetranyble.ailearn.api.ApiResponseFilter;
import com.tetranyble.ailearn.course.CourseResource;
import com.tetranyble.ailearn.exception.ApiExceptionMapper;
import com.tetranyble.ailearn.exception.DataIntegrityViolationExceptionMapper;
import com.tetranyble.ailearn.exception.UnexpectedExceptionMapper;
import com.tetranyble.ailearn.exception.WebApplicationExceptionMapper;
import com.tetranyble.ailearn.validation.ConstraintViolationMapper;
import com.tetranyble.ailearn.validation.RequestValidationExceptionMapper;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.validation.ValidationFeature;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        register(ValidationFeature.class);
        register(ConstraintViolationMapper.class);
        register(RequestValidationExceptionMapper.class);
        register(ApiExceptionMapper.class);
        register(DataIntegrityViolationExceptionMapper.class);
        register(WebApplicationExceptionMapper.class);
        register(UnexpectedExceptionMapper.class);
        register(ApiResponseFilter.class);
        register(ChatResource.class);
        register(ConversationResource.class);
        register(CourseResource.class);
    }
}
