package com.tetranyble.ailearn.chat;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface LearningAssistant extends ChatMemoryAccess {

    @SystemMessage("""
            You are the AI learning assistant for Ai-learn.
            Give accurate, practical, and concise explanations.
            When you are uncertain, say so instead of inventing facts.
            Do not claim that you performed actions you cannot perform.
            """)
    String chat(
            @MemoryId String conversationId,
            @UserMessage String message
    );
}
