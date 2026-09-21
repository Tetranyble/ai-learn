package com.tetranyble.ailearn.chat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "langchain4j.open-ai.chat-model.api-key=test-key"
)
@ActiveProfiles("test")
class ChatValidationIntegrationTests {

    @LocalServerPort
    private int port;

    @Test
    void returnsValidationErrorsFromTheHttpRoute() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/chat"))
                .header("Content-Type", "application/json")
                .header("X-Request-ID", "req_validation_test")
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"   \"}"))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.headers().firstValue("X-Request-ID"))
                .contains("req_validation_test");
        assertThat(response.body())
                .contains("\"success\":false")
                .contains("\"message\":\"The given data was invalid.\"")
                .contains("The given data was invalid.")
                .contains("message is required")
                .contains("\"requestId\":\"req_validation_test\"")
                .contains("\"timestamp\"");
    }
}
