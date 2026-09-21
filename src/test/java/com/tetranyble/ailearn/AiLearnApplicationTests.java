package com.tetranyble.ailearn;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "langchain4j.open-ai.chat-model.api-key=test-key")
@ActiveProfiles("test")
class AiLearnApplicationTests {

    @Test
    void contextLoads() {
    }

}
