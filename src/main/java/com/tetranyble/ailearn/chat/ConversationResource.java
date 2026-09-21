package com.tetranyble.ailearn.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

@Component
@Path("/v1/conversations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ConversationResource {

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    private final ConversationService conversationService;

    public ConversationResource(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GET
    public List<ConversationResponse> index(
            @DefaultValue("50")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 100, message = "limit must not exceed 100")
            @QueryParam("limit") int limit
    ) {
        return conversationService.recent(limit);
    }

    @POST
    public Response store(
            @Valid CreateConversationRequest request,
            @Context UriInfo uriInfo
    ) {
        ConversationResponse conversation = conversationService.create(
                request == null ? null : request.title()
        );
        URI location = uriInfo.getAbsolutePathBuilder()
                .path(conversation.id())
                .build();

        return Response.created(location).entity(conversation).build();
    }

    @GET
    @Path("{conversationId}")
    public ConversationResponse show(
            @PathParam("conversationId")
            @Pattern(regexp = UUID_PATTERN, message = "conversationId must be a UUID")
            String conversationId
    ) {
        return conversationService.find(conversationId);
    }

    @PUT
    @Path("{conversationId}")
    public ConversationResponse update(
            @PathParam("conversationId")
            @Pattern(regexp = UUID_PATTERN, message = "conversationId must be a UUID")
            String conversationId,
            @Valid UpdateConversationRequest request
    ) {
        return conversationService.update(
                conversationId,
                request.title().trim()
        );
    }

    @DELETE
    @Path("{conversationId}")
    public Response destroy(
            @PathParam("conversationId")
            @Pattern(regexp = UUID_PATTERN, message = "conversationId must be a UUID")
            String conversationId
    ) {
        conversationService.delete(conversationId);
        return Response.ok().build();
    }
}
