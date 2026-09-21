package com.tetranyble.ailearn.chat;

import java.util.List;

public interface ChatEventStore {

    ChatStreamEvent append(ChatStreamEvent event);

    List<ChatStreamEvent> replay(String runId, String afterCursor, int limit);
}
