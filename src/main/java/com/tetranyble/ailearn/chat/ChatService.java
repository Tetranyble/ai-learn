package com.tetranyble.ailearn.chat;

import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ConversationRepository conversations;
    private final ChatRunRepository runs;
    private final ChatRunCoordinator coordinator;

    public ChatService(
            ConversationRepository conversations,
            ChatRunRepository runs,
            ChatRunCoordinator coordinator
    ) {
        this.conversations = conversations;
        this.runs = runs;
        this.coordinator = coordinator;
    }

    public ChatSubmissionResponse startConversation(String message) {
        Conversation conversation = conversations.save(new Conversation(null));
        return submit(
                conversation.getId(),
                message,
                null,
                null,
                ChatMode.QUEUE
        );
    }

    public ChatSubmissionResponse submit(
            String conversationId,
            String message,
            String idempotencyKey,
            String replyToId,
            ChatMode mode
    ) {
        EnqueuedChatRun enqueued = runs.enqueue(
                conversationId,
                message,
                idempotencyKey,
                replyToId,
                mode
        );
        coordinator.accepted(enqueued);
        return enqueued.submission();
    }

    public ChatCancellationResponse cancel(String conversationId) {
        return coordinator.cancelActive(conversationId)
                .map(run -> new ChatCancellationResponse(
                        conversationId,
                        true,
                        run
                ))
                .orElseGet(() -> new ChatCancellationResponse(
                        conversationId,
                        false,
                        null
                ));
    }

    public ChatCancellationResponse cancel(
            String conversationId,
            String runId
    ) {
        return coordinator.cancelRun(conversationId, runId)
                .map(run -> new ChatCancellationResponse(
                        conversationId,
                        true,
                        run
                ))
                .orElseGet(() -> new ChatCancellationResponse(
                        conversationId,
                        false,
                        null
                ));
    }
}
