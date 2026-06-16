package com.agentbootcamp;

import com.agentbootcamp.metrics.CostCalculator;
import com.agentbootcamp.metrics.MetricsCollector;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 客户端 — OpenAI 兼容协议的 HTTP 封装 / LLM client — HTTP wrapper for OpenAI-compatible APIs
 *
 * 一次调用:把 messages + tools 序列化成 JSON,POST 到 /chat/completions,返回模型响应。
 * One call: serialize messages + tools to JSON, POST to /chat/completions, return the response.
 *
 * Day 1:实现基础的 tool calling 协议 / implements basic tool calling protocol.
 * Day 11:加 OpenTelemetry 追踪 / add OTel tracing.
 */
public class LlmClient {
    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    static final ObjectMapper JSON = new ObjectMapper();  // package-private, Agent 也要用

    private final LlmConfig config;
    private final HttpClient http;
    /** Day 11: 可选 metrics 收集器 (null = 不收集,跟 Day 4 MemoryManager 同款兼容模式) */
    private final MetricsCollector metrics;

    public LlmClient(LlmConfig config) {
        this(config, null);
    }

    /**
     * Day 11: 注入 MetricsCollector (OpenTelemetry/Micrometer 风格).
     * 传 null 等价于单参构造,向后兼容 Day 1-10 现有测试/调用方.
     */
    public LlmClient(LlmConfig config, MetricsCollector metrics) {
        this.config = config;
        this.metrics = metrics;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * 调用 LLM,带可选的工具定义 / Call the LLM with optional tool definitions.
     *
     * @param messages  对话历史 / conversation history
     * @param tools     工具定义(OpenAI tools 格式)/ tool definitions in OpenAI tools[] format
     * @return          模型响应(可能含 tool_calls) / model response (may include tool_calls)
     */
    public LlmResponse chat(List<Message> messages, List<Map<String, Object>> tools) throws Exception {
        long startMs = System.currentTimeMillis();
        Map<String, Object> body = new HashMap<>();
        body.put("model", config.model());
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        String payload = JSON.writeValueAsString(body);
        log.debug("→ POST {}: {}", config.baseUrl() + "/chat/completions", payload);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(config.baseUrl() + "/chat/completions"))
            .timeout(Duration.ofSeconds(config.timeoutSeconds()))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + config.apiKey())
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        // Day 8: retry-with-backoff (Day 12 Resilience4j 之前临时方案)
        // - 429 rate limit: 等 5s/15s/45s 后重试
        // - IOException (含 connect timeout): 等同样间隔后重试
        // AgentTest 没 retry 时碰到限流/网络抖全会挂;EvalHarness 也有,但 LlmClient 通用更稳
        HttpResponse<String> resp = null;
        RuntimeException lastError = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (java.io.IOException ioe) {
                lastError = new RuntimeException("LLM HTTP send failed (attempt " + (attempt + 1) + "/3): " + ioe.getMessage(), ioe);
                long waitSec = (long) Math.pow(3, attempt) * 5;  // 5s, 15s, 45s
                log.warn("LLM {} (attempt {}/3), 等 {}s 后重试...", ioe.getClass().getSimpleName(), attempt + 1, waitSec);
                try {
                    Thread.sleep(waitSec * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
                continue;
            }
            if (resp.statusCode() != 429) break;  // 非 429 直接 break,后面正常校验
            lastError = new RuntimeException("LLM call failed: 429 (attempt " + (attempt + 1) + "/3)");
            long waitSec = (long) Math.pow(3, attempt) * 5;  // 5s, 15s, 45s
            log.warn("LLM 429 rate limit, 等 {}s 后重试 ({}/3)...", waitSec, attempt + 1);
            try {
                Thread.sleep(waitSec * 1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Retry interrupted", ie);
            }
        }
        if (resp == null) throw lastError != null ? lastError : new RuntimeException("LLM call failed: no response");

        log.debug("← Status {}: {}", resp.statusCode(),
            resp.body().length() > 500 ? resp.body().substring(0, 500) + "..." : resp.body());

        if (resp.statusCode() != 200) {
            throw new RuntimeException("LLM call failed: " + resp.statusCode() + " " + resp.body());
        }

        // Day 11: 累加 metrics (token + cost + duration)
        long durationMs = System.currentTimeMillis() - startMs;
        LlmResponse response = parseResponse(resp.body());
        if (metrics != null && response.tokensIn() != null) {
            long in = response.tokensIn();
            long out = response.tokensOut() != null ? response.tokensOut() : 0;
            double cost = CostCalculator.compute(config.model(), in, out);
            metrics.recordLlmCall(config.model(), in, out, cost, durationMs);
        }
        return response;
    }

    /** 解析 OpenAI 响应 / parse OpenAI-format response */
    @SuppressWarnings("unchecked")
    private LlmResponse parseResponse(String body) throws Exception {
        Map<String, Object> root = JSON.readValue(body, new TypeReference<>() {});
        List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("Empty LLM response: " + body);
        }
        Map<String, Object> choice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");

        String content = (String) message.getOrDefault("content", "");
        List<Message.ToolCall> toolCalls = null;
        Object rawToolCalls = message.get("tool_calls");
        if (rawToolCalls instanceof List<?> list && !list.isEmpty()) {
            toolCalls = new ArrayList<>();
            for (Object o : list) {
                Map<String, Object> tc = (Map<String, Object>) o;
                String id = (String) tc.get("id");
                String type = (String) tc.get("type");
                Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                String name = (String) fn.get("name");
                String args = (String) fn.get("arguments");
                toolCalls.add(new Message.ToolCall(id, type,
                    new Message.FunctionCall(name, args)));
            }
        }

        // 用量 / usage (可选 / optional)
        Integer tokensIn = null, tokensOut = null;
        Object usageObj = root.get("usage");
        if (usageObj instanceof Map<?, ?> u) {
            Object pin = u.get("prompt_tokens");
            Object pout = u.get("completion_tokens");
            if (pin instanceof Number n) tokensIn = n.intValue();
            if (pout instanceof Number n) tokensOut = n.intValue();
        }

        return new LlmResponse(content, toolCalls, tokensIn, tokensOut);
    }

    /**
     * LLM 响应 / LLM response
     *
     * @param content    文本回复(可能为空,如果只有 tool_calls) / text content (may be empty if only tool_calls)
     * @param toolCalls  模型想调的工具 / tool calls the model wants to make
     * @param tokensIn   prompt tokens(可选,看 API 是否返回)/ prompt tokens (optional)
     * @param tokensOut  completion tokens / completion tokens
     */
    public record LlmResponse(
        String content,
        @JsonProperty("tool_calls") List<Message.ToolCall> toolCalls,
        @JsonProperty("tokens_in") Integer tokensIn,
        @JsonProperty("tokens_out") Integer tokensOut
    ) {
        public boolean hasToolCall() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
