package com.tetranyble.ailearn.chat;

import com.tetranyble.ailearn.exception.ChatProviderException;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final LearningAssistant assistant;
    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final JpaChatMemoryStore memoryStore;

    public ChatService(
            LearningAssistant assistant,
            ConversationRepository conversations,
            ConversationMessageRepository messages,
            JpaChatMemoryStore memoryStore
    ) {
        this.assistant = assistant;
        this.conversations = conversations;
        this.messages = messages;
        this.memoryStore = memoryStore;
    }

    public ChatResponse startConversation(String message) {
        Conversation conversation = conversations.save(new Conversation(null));
        return this.chat(conversation.getId(), message, null, null);
    }

    public ChatResponse chat(String conversationId, String message) {
        return this.chat(conversationId, message, null, null);
    }

    public ChatResponse chat(String conversationId, String message, String idempotencyKey, String replyToId) {
        String leaseToken = this.conversations.acquireProcessingLease(conversationId);

        try {
            var existing = this.messages.findTurnByIdempotencyKey(
                    conversationId,
                    idempotencyKey
            );

            if (existing.isPresent()) {
                return new ChatResponse(
                        conversationId,
                        MessageResponse.from(existing.get().userMessage()),
                        MessageResponse.from(existing.get().assistantMessage())
                );
            }

            this.messages.validateReplyTarget(conversationId, replyToId);

            String reply = this.requestAssistant(conversationId, message);
            ConversationTurn turn;

            try {
                turn = messages.appendTurn(
                        conversationId,
                        message,
                        reply,
                        idempotencyKey,
                        replyToId
                );
            } catch (RuntimeException persistenceFailure) {
                memoryStore.deleteMessages(conversationId);
                throw persistenceFailure;
            }

            return new ChatResponse(
                    conversationId,
                    MessageResponse.from(turn.userMessage()),
                    MessageResponse.from(turn.assistantMessage())
            );
        } finally {
            evictInProcessMemory(conversationId);
            conversations.releaseProcessingLease(conversationId, leaseToken);
        }
    }

    private String requestAssistant(String conversationId, String message) {
        try {
            return this.assistant.chat(conversationId, message);
        } catch (RuntimeException exception) {
            this.memoryStore.deleteMessages(conversationId);
            throw new ChatProviderException(exception);
        }
    }

    private void evictInProcessMemory(String conversationId) {
        assistant.evictChatMemory(conversationId);
    }
}
