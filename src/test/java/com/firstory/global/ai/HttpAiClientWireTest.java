package com.firstory.global.ai;

import com.firstory.domain.safety.entity.SafetyLevel;
import com.firstory.domain.safety.entity.SafetySource;
import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.config.FirstoryProperties.Ai.AiMode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HttpAiClient.create()가 만든 실제 HTTP 클라이언트가 보내는 요청을 본다 (MockRestServiceServer로는 안 보이는 부분).
 * JDK HttpClient는 기본값(HTTP/2)이면 평문 HTTP에 Upgrade: h2c를 붙이는데, uvicorn 기본 파서는 이때 본문을 버린다.
 */
class HttpAiClientWireTest {

    HttpServer server;
    final AtomicReference<Headers> headers = new AtomicReference<>();
    final AtomicReference<String> body = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/safety/check", exchange -> {
            headers.set(exchange.getRequestHeaders());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"safety": {"level": "OK", "category": null, "reason": null}}""".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void HTTP_1_1로_보내고_Upgrade_헤더를_붙이지_않는다() {
        HttpAiClient client = HttpAiClient.create(new FirstoryProperties.Ai(AiMode.HTTP,
                "http://127.0.0.1:" + server.getAddress().getPort(), "internal-secret",
                Duration.ofSeconds(3), Duration.ofSeconds(5), null));

        SafetyResult result = client.checkSafety(SafetySource.FORM, List.of("어린이집에서 친구가 밀쳐서 넘어졌어요."), null);

        assertThat(result.level()).isEqualTo(SafetyLevel.OK);
        assertThat(headers.get().containsKey("Upgrade")).as("Upgrade: h2c가 붙으면 AI 서버가 본문을 버린다").isFalse();
        assertThat(headers.get().getFirst("X-Internal-Token")).isEqualTo("internal-secret");
        assertThat(body.get()).contains("\"source\":\"form\"", "어린이집에서 친구가 밀쳐서 넘어졌어요.");
    }
}
