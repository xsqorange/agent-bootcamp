package com.agentbootcamp.server;

import com.agentbootcamp.LlmConfig;
import com.agentbootcamp.metrics.MetricsCollector;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Day 13: 轻量 HTTP server (JDK 内置 com.sun.net.httpserver, 0 新依赖).
 *
 * 中文:把 CLI Agent 升级为 HTTP 服务,让 K8s / docker-compose 能探活 + scrape metrics + 跑 agent.
 *      4 个端点:
 *        GET  /health              → 200 {"status":"UP","uptimeSec":N}  (总健康, 进程在)
 *        GET  /health/liveness     → 200 {"status":"UP"}                (K8s liveness, 不依赖外部)
 *        GET  /health/readiness    → 200 + 检查 LLM config 是否齐        (K8s readiness)
 *        GET  /metrics             → Prometheus text (Day 11 MetricsCollector)
 *        POST /api/run             → {"goal":"..."} → 跑 Agent → {"finalAnswer":"..."}
 *
 * English: Upgrade CLI Agent to HTTP server for K8s/docker-compose healthcheck + metrics scrape + remote agent run.
 *
 * 用法 / Usage:
 *   new HttpServerMain(8080, metricsCollector).start();  // 阻塞
 *   // 或后台: new HttpServerMain(...).startAsync();
 */
public class HttpServerMain {
    private static final Logger log = LoggerFactory.getLogger(HttpServerMain.class);
    private static final long START_TIME_MS = System.currentTimeMillis();

    private final int port;
    private final MetricsCollector metrics;  // Day 11, 可选 null
    private HttpServer server;

    public HttpServerMain(int port) {
        this(port, null);
    }

    public HttpServerMain(int port, MetricsCollector metrics) {
        this.port = port;
        this.metrics = metrics;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/health", new HealthHandler());
        server.createContext("/health/liveness", new LivenessHandler());
        server.createContext("/health/readiness", new ReadinessHandler());
        server.createContext("/metrics", new MetricsHandler());
        server.createContext("/api/run", new ApiRunHandler());
        server.start();
        log.info("[Day13] HTTP server listening on http://0.0.0.0:{}", port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            log.info("[Day13] HTTP server stopped");
        }
    }

    public int getPort() {
        // 返 OS 实际 bind 的端口 (构造时 0 表示让 OS 分配空闲)
        return server != null && server.getAddress() != null ? server.getAddress().getPort() : port;
    }

    // ========== 5 Handlers ==========

    /** GET /health → 200 + JSON 总健康状态 */
    static class HealthHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            long uptimeSec = (System.currentTimeMillis() - START_TIME_MS) / 1000;
            String json = String.format("{\"status\":\"UP\",\"uptimeSec\":%d}", uptimeSec);
            respondJson(ex, 200, json);
        }
    }

    /** GET /health/liveness → 200 + 进程在, 永远是 UP (K8s liveness 用) */
    static class LivenessHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            respondJson(ex, 200, "{\"status\":\"UP\"}");
        }
    }

    /** GET /health/readiness → 200 + 检查 LLM config 是否齐 (K8s readiness 用) */
    static class ReadinessHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            try {
                LlmConfig cfg = LlmConfig.fromEnv();
                boolean ready = cfg.apiKey() != null && !cfg.apiKey().isBlank()
                    && cfg.baseUrl() != null && cfg.model() != null;
                String json = String.format(
                    "{\"status\":\"%s\",\"dependencies\":{\"llm\":\"%s\",\"model\":\"%s\"}}",
                    ready ? "UP" : "DOWN",
                    ready ? "ok" : "missing-config",
                    cfg.model() != null ? cfg.model() : "unknown");
                respondJson(ex, ready ? 200 : 503, json);
            } catch (Exception e) {
                // LlmConfig.fromEnv() 抛异常时返 200 + DOWN (K8s readiness 不该让 pod 挂)
                String json = String.format(
                    "{\"status\":\"DOWN\",\"reason\":\"config-error: %s\"}",
                    e.getMessage() != null ? e.getMessage().replace("\"", "'") : "unknown");
                respondJson(ex, 200, json);
            }
        }
    }

    /** GET /metrics → Prometheus text 格式 (Day 11) */
    class MetricsHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (metrics == null) {
                respondJson(ex, 503, "{\"error\":\"MetricsCollector not configured\"}");
                return;
            }
            String prom = com.agentbootcamp.metrics.MetricsReporter.printPrometheus(metrics);
            byte[] body = prom.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/plain; version=0.0.4");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        }
    }

    /** POST /api/run → JSON body {"goal":"..."} → 跑 Agent.run() 返 JSON {"finalAnswer":"..."} */
    static class ApiRunHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                respondJson(ex, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            // 简化实现: 读 body 拿 goal, 不真跑 (Day 13 重点是 healthcheck + metrics 端点, /api/run 留 Day 14 集成)
            byte[] body = ex.getRequestBody().readAllBytes();
            String bodyStr = new String(body, StandardCharsets.UTF_8);
            String goal = bodyStr.replaceAll(".*\"goal\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            respondJson(ex, 202, String.format(
                "{\"status\":\"ACCEPTED\",\"goal\":\"%s\",\"note\":\"/api/run 跑 Agent 集成留 Day 14\"}",
                goal.replace("\"", "\\\"")));
        }
    }

    static void respondJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }
}