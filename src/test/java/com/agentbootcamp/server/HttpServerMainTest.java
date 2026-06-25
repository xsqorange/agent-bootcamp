package com.agentbootcamp.server;

import com.agentbootcamp.metrics.MetricsCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Day 13: HttpServerMain 单元测试.
 * 4 cases: /health /health/liveness /health/readiness /metrics + /api/run.
 */
class HttpServerMainTest {

    private HttpServerMain server;
    private HttpClient client;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        // 用 0 端口让 OS 分配空闲端口 (避免测试冲突)
        server = new HttpServerMain(0, new MetricsCollector());
        server.start();
        port = server.getPort();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void get_health_returnsUpJson() throws Exception {
        var resp = client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/health")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"status\":\"UP\""), "body 应含 status:UP: " + resp.body());
        assertTrue(resp.body().contains("\"uptimeSec\""), "body 应含 uptimeSec: " + resp.body());
    }

    @Test
    void get_healthLiveness_returnsUp() throws Exception {
        var resp = client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/health/liveness")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertEquals("{\"status\":\"UP\"}", resp.body().trim());
    }

    @Test
    void get_healthReadiness_returns200IfConfigOk() throws Exception {
        var resp = client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/health/readiness")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        // 200 OK (UP/DOWN 都返 200, K8s readiness 不该让 pod 挂)
        assertEquals(200, resp.statusCode(), "readiness 必须 200 (UP/DOWN 任一): " + resp.statusCode());
        // body 应含 status (UP 或 DOWN) + 详细字段 (dependencies 或 reason)
        assertTrue(resp.body().contains("\"status\""), "应含 status: " + resp.body());
        assertTrue(resp.body().contains("dependencies") || resp.body().contains("reason"),
            "应含 dependencies 或 reason: " + resp.body());
    }

    @Test
    void get_metrics_returnsPrometheusText() throws Exception {
        var resp = client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/metrics")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        // 应该是 Prometheus text 格式 (空 registry 也返空字符串或 comment)
        assertNotNull(resp.body());
        assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("text/plain"));
    }

    @Test
    void post_apiRun_returns202WithGoal() throws Exception {
        var resp = client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/run"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"goal\":\"say hi\"}"))
            .build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(202, resp.statusCode());
        assertTrue(resp.body().contains("say hi"), "body 应含 goal: " + resp.body());
    }
}