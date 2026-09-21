package com.tetranyble.ailearn.chat;

import com.tetranyble.ailearn.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationService {

    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final LearningAssistant assistant;

    public ConversationService(
            ConversationRepository conversations,
            ConversationMessageRepository messages,
            LearningAssistant assistant
    ) {
        this.conversations = conversations;
        this.messages = messages;
        this.assistant = assistant;
    }

    public ConversationResponse create(String title) {
        return ConversationResponse.from(
                conversations.save(new Conversation(title))
        );
    }

    public List<ConversationResponse> recent(int limit) {
        return conversations.findRecent(limit)
                .stream()
                .map(ConversationResponse::from)
                .toList();
    }

    public ConversationResponse find(String id) {
        return ConversationResponse.from(findConversation(id));
    }

    public ConversationResponse update(String id, String title) {
        Conversation conversation = findConversation(id);
        conversation.rename(title);
        return ConversationResponse.from(conversations.save(conversation));
    }

    public MessagePageResponse messages(
            String conversationId,
            long after,
            int limit
    ) {
        findConversation(conversationId);
        List<ConversationMessage> found = messages.findAfter(
                conversationId,
                after,
                limit + 1
        );
        boolean hasMore = found.size() > limit;
        List<MessageResponse> page = found.stream()
                .limit(limit)
                .map(MessageResponse::from)
                .toList();
        Long nextCursor = hasMore && !page.isEmpty()
                ? page.getLast().sequence()
                : null;

        return new MessagePageResponse(page, nextCursor, hasMore);
    }

    public void delete(String id) {
        Conversation conversation = findConversation(id);
        conversations.delete(conversation);
        assistant.evictChatMemory(id);
    }

    private Conversation findConversation(String id) {
        return conversations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", id));
    }
}
