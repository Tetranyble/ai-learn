package com.tetranyble.ailearn.chat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketIntegrationTests {

    @LocalServerPort
    private int port;

    @Test
    void exposesTheChatWebSocketBesideJersey() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> frame = new AtomicReference<>();
        WebSocket socket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(
                        URI.create("ws://localhost:" + port + "/ws/chat"),
                        new WebSocket.Listener() {
                            @Override
                            public void onOpen(WebSocket webSocket) {
                                webSocket.request(1);
                            }

                            @Override
                            public CompletionStage<?> onText(
                                    WebSocket webSocket,
                                    CharSequence data,
                                    boolean last
                            ) {
                                frame.set(data.toString());
                                received.countDown();
                                webSocket.request(1);
                                return null;
                            }
                        }
                )
                .get(5, TimeUnit.SECONDS);

        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(frame.get()).contains("\"type\":\"ready\"");
        socket.abort();
    }
}
