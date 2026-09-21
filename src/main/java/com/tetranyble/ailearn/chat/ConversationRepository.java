package com.tetranyble.ailearn.chat;

import com.tetranyble.ailearn.exception.ConversationBusyException;
import com.tetranyble.ailearn.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class ConversationRepository {

    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(2);

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<Conversation> findById(String id) {
        return Optional.ofNullable(entityManager.find(Conversation.class, id));
    }

    public List<Conversation> findRecent(int limit) {
        return entityManager.createQuery("""
                        select conversation
                        from Conversation conversation
                        order by coalesce(
                            conversation.lastMessageAt,
                            conversation.createdAt
                        ) desc
                        """, Conversation.class)
                .setMaxResults(limit)
                .getResultList();
    }

    @Transactional
    public Conversation save(Conversation conversation) {
        if (conversation.getId() == null
                || entityManager.find(Conversation.class, conversation.getId()) == null) {
            entityManager.persist(conversation);
            return conversation;
        }

        return entityManager.merge(conversation);
    }

    @Transactional
    public void delete(Conversation conversation) {
        Conversation managed = entityManager.contains(conversation)
                ? conversation
                : entityManager.merge(conversation);
        entityManager.remove(managed);
    }

    @Transactional
    public String acquireProcessingLease(String conversationId) {
        Conversation conversation = findForUpdate(conversationId);
        Instant now = Instant.now();

        if (conversation.isProcessingAt(now)) {
            throw new ConversationBusyException();
        }

        String token = UUID.randomUUID().toString();
        conversation.acquire(token, now.plus(PROCESSING_LEASE));
        return token;
    }

    @Transactional
    public void releaseProcessingLease(String conversationId, String token) {
        Conversation conversation = entityManager.find(
                Conversation.class,
                conversationId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (conversation != null) {
            conversation.release(token);
        }
    }

    private Conversation findForUpdate(String id) {
        Conversation conversation = entityManager.find(
                Conversation.class,
                id,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (conversation == null) {
            throw new ResourceNotFoundException("Conversation", id);
        }

        return conversation;
    }
}
