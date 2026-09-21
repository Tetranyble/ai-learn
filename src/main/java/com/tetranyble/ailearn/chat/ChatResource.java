package com.tetranyble.ailearn.chat;

import com.tetranyble.ailearn.validation.RequestValidationException;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.stereotype.Component;

@Component
@Path("/api/v1/chat")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ChatResource {

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    private final ChatService chatService;
    private final ConversationService conversationService;

    public ChatResource(
            ChatService chatService,
            ConversationService conversationService
    ) {
        this.chatService = chatService;
        this.conversationService = conversationService;
    }

    @POST
    public ChatResponse chat(@Valid ChatRequest request) {
        if (request.replyToId() != null) {
            throw RequestValidationException.forField(
                    "replyToId",
                    "replyToId cannot be used when starting a conversation"
            );
        }

        String message = request.message().trim();
        return chatService.startConversation(message);
    }

    @POST
    @Path("{conversationId}")
    public ChatResponse continueChat(
            @PathParam("conversationId")
            @Pattern(regexp = UUID_PATTERN, message = "conversationId must be a UUID")
            String conversationId,
            @HeaderParam("Idempotency-Key")
            @Pattern(
                    regexp = "[A-Za-z0-9._:-]{1,100}",
                    message = "Idempotency-Key format is invalid"
            )
            String idempotencyKey,
            @Valid ChatRequest request
    ) {
        return chatService.chat(
                conversationId,
                request.message().trim(),
                idempotencyKey,
                request.replyToId()
        );
    }

    @GET
    @Path("{conversationId}")
    public MessagePageResponse history(
            @PathParam("conversationId")
            @Pattern(regexp = UUID_PATTERN, message = "conversationId must be a UUID")
            String conversationId,
            @DefaultValue("0")
            @Min(value = 0, message = "after must be zero or greater")
            @QueryParam("after") long after,
            @DefaultValue("50")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 100, message = "limit must not exceed 100")
            @QueryParam("limit") int limit
    ) {
        return conversationService.messages(conversationId, after, limit);
    }
}
