package com.tetranyble.ailearn.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class JpaChatMemoryStore implements ChatMemoryStore {

    private static final int REBUILD_MESSAGE_LIMIT = 100;

    @PersistenceContext
    private EntityManager entityManager;

    private final ConversationMessageRepository messages;

    public JpaChatMemoryStore(ConversationMessageRepository messages) {
        this.messages = messages;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String conversationId = memoryId.toString();
        ChatMemorySnapshot snapshot = entityManager.find(
                ChatMemorySnapshot.class,
                conversationId
        );

        if (snapshot != null) {
            return ChatMessageDeserializer.messagesFromJson(snapshot.getMessagesJson());
        }

        return messages.findMemoryHistory(conversationId, REBUILD_MESSAGE_LIMIT)
                .stream()
                .map(this::toLangChainMessage)
                .toList();
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String conversationId = memoryId.toString();
        String json = ChatMessageSerializer.messagesToJson(messages);
        ChatMemorySnapshot snapshot = entityManager.find(
                ChatMemorySnapshot.class,
                conversationId
        );

        if (snapshot == null) {
            entityManager.persist(new ChatMemorySnapshot(conversationId, json));
        } else {
            snapshot.replace(json);
        }
    }

    @Override
    @Transactional
    public void deleteMessages(Object memoryId) {
        ChatMemorySnapshot snapshot = entityManager.find(
                ChatMemorySnapshot.class,
                memoryId.toString()
        );

        if (snapshot != null) {
            entityManager.remove(snapshot);
        }
    }

    private ChatMessage toLangChainMessage(ConversationMessage message) {
        if (message.getRole() == MessageRole.USER) {
            return UserMessage.from(message.getContent());
        }

        return AiMessage.from(message.getContent());
    }
}
