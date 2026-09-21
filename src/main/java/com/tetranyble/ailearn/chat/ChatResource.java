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
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.stereotype.Component;

@Component
@Path("/v1/chat")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ChatResource {

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    private final ChatService chatService;
    private final ConversationService conversationService;
    private final ChatRunSseService streamService;

    public ChatResource(
            ChatService chatService,
            ConversationService conversationService,
            ChatRunSseService streamService
    ) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.streamService = streamService;
    }

    @POST
    public Response chat(@Valid ChatRequest request) {
        if (request.replyToId() != null) {
            throw RequestValidationException.forField(
                    "replyToId",
                    "replyToId cannot be used when starting a conversation"
            );
        }

        String message = request.message().trim();
        return Response.accepted(chatService.startConversation(message)).build();
    }

    @POST
    @Path("{conversationId}")
    public Response continueChat(
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
        ChatSubmissionResponse submission = chatService.submit(
                conversationId,
                request.message().trim(),
                idempotencyKey,
                request.replyToId(),
                request.effectiveMode()
        );
        return Response.accepted(submission).build();
    }

    @POST
    @Path("{conversationId}/cancel")
    public Response cancel(
            @PathParam("conversationId")
            @Pattern(regexp = UUID_PATTERN, message = "conversationId must be a UUID")
            String conversationId
    ) {
        return Response.accepted(chatService.cancel(conversationId)).build();
    }

    @POST
    @Path("{conversationId}/runs/{runId}/cancel")
    public Response cancelRun(
            @PathParam("conversationId")
            @Pattern(regexp = UUID_PATTERN, message = "conversationId must be a UUID")
            String conversationId,
            @PathParam("runId")
            @Pattern(regexp = UUID_PATTERN, message = "runId must be a UUID")
            String runId
    ) {
        return Response.accepted(chatService.cancel(conversationId, runId)).build();
    }

    @GET
    @Path("{conversationId}/runs/{runId}/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void streamRun(
            @PathParam("conversationId")
            @Pattern(regexp = UUID_PATTERN, message = "conversationId must be a UUID")
            String conversationId,
            @PathParam("runId")
            @Pattern(regexp = UUID_PATTERN, message = "runId must be a UUID")
            String runId,
            @HeaderParam("Last-Event-ID") String lastEventId,
            @Context SseEventSink sink,
            @Context Sse sse,
            @Context HttpServletResponse response
    ) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        streamService.stream(conversationId, runId, lastEventId, sink, sse);
    }

    void streamRun(
            String conversationId,
            String runId,
            SseEventSink sink,
            Sse sse,
            HttpServletResponse response
    ) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        streamService.stream(conversationId, runId, sink, sse);
    }

    @GET
    @Path("{conversationId}")
    public ChatStateResponse history(
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
        return conversationService.chatState(conversationId, after, limit);
    }
}
