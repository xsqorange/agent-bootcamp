package com.agentbootcamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * LLM 配置 — 从环境变量读 / LLM config — read from env vars
 *
 * 支持任意 OpenAI 兼容 API / Supports any OpenAI-compatible API:
 * - OpenAI (默认 / default)
 * - DeepSeek (国内性价比 / good for China)
 * - 通义 Qwen (兼容模式 / compatible mode)
 * - Ollama (本地 / local)
 */
public record LlmConfig(
    String apiKey,
    String baseUrl,
    String model,
    int timeoutSeconds
) {
    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    public static LlmConfig fromEnv() {
        // 优先级:DeepSeek > OpenAI > 通义 > Anthropic / Priority: DeepSeek > OpenAI > Qwen > Anthropic
        String key = firstNonBlank(
            System.getenv("DEEPSEEK_API_KEY"),
            System.getenv("OPENAI_API_KEY"),
            System.getenv("DASHSCOPE_API_KEY"),
            System.getenv("ANTHROPIC_API_KEY")
        );
        if (key == null) {
            throw new IllegalStateException(
                "未设置任何 LLM API key。请设置以下之一: " +
                "DEEPSEEK_API_KEY / OPENAI_API_KEY / DASHSCOPE_API_KEY / ANTHROPIC_API_KEY"
            );
        }

        // 默认 base URL:按 key 类型推断 / infer base URL by key type
        String baseUrl = Optional.ofNullable(System.getenv("LLM_BASE_URL"))
            .orElse(defaultBaseUrl(key));
        String model = Optional.ofNullable(System.getenv("LLM_MODEL"))
            .orElse(defaultModel(key));
        int timeout = Integer.parseInt(
            Optional.ofNullable(System.getenv("LLM_TIMEOUT_SECONDS")).orElse("60")
        );

        LlmConfig cfg = new LlmConfig(key, baseUrl, model, timeout);
        log.info("LLM 配置: base={}, model={}, timeout={}s", baseUrl, model, timeout);
        return cfg;
    }

    private static String defaultBaseUrl(String apiKey) {
        if (apiKey.startsWith("sk-")) return "https://api.openai.com/v1";
        return "https://api.openai.com/v1"; // 兜底 / safe default
    }

    private static String defaultModel(String apiKey) {
        if (apiKey.startsWith("sk-")) return "gpt-4o-mini";
        return "gpt-4o-mini";
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
