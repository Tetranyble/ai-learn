package com.tetranyble.ailearn.chat;

public interface ChatSignalBus {

    void publishQueued(String conversationId);

    void publishCancellation(String runId, ChatCancelReason reason);

    void publishStreamEvent(ChatStreamEvent event);
}
