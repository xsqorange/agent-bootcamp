package com.agentbootcamp.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.util.concurrent.TimeUnit;

/**
 * Day 11: 集中式 metrics 收集器 (Micrometer 包装).
 *
 * 中文:6 个核心 metric — token_in / token_out / cost / llm_call_duration / tool_calls / agent_steps。
 *      Counter 累加, Timer 测延迟 (P95), tags 区分 model / tool / stop_reason。
 * English: Centralized metrics collector. 6 core metrics covering LLM cost, latency, tool usage.
 *
 * 用法 / Usage:
 *   MetricsCollector mc = new MetricsCollector();
 *   mc.recordLlmCall("minimax-m3", 1234, 567, 0.0003, 1500);
 *   mc.recordToolCall("read_file");
 *   mc.recordAgentStep("FINAL_ANSWER");
 *   System.out.println(mc.scrape());  // Prometheus text format
 */
public class MetricsCollector {
    private final MeterRegistry registry;
    private final Counter tokensIn;
    private final Counter tokensOut;
    private final Counter costUsd;
    private final Timer llmCallDuration;
    private final Counter agentSteps;

    /**
     * 默认构造:PrometheusMeterRegistry (输出 Prometheus text format)
     */
    public MetricsCollector() {
        this(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
    }

    /**
     * 注入自定义 registry (测试用,可换 SimpleMeterRegistry)
     */
    public MetricsCollector(MeterRegistry registry) {
        this.registry = registry;
        this.tokensIn = Counter.builder("llm_tokens_in_total")
            .description("Total LLM input tokens (prompt)")
            .register(registry);
        this.tokensOut = Counter.builder("llm_tokens_out_total")
            .description("Total LLM output tokens (completion)")
            .register(registry);
        this.costUsd = Counter.builder("llm_cost_usd_total")
            .description("Total LLM cost in USD")
            .baseUnit("USD")
            .register(registry);
        this.llmCallDuration = Timer.builder("llm_call_duration_seconds")
            .description("LLM call latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
        this.agentSteps = Counter.builder("agent_steps_total")
            .description("Total agent steps (each step = 1 LLM call)")
            .register(registry);
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    /**
     * 记录 1 次 LLM 调用:token_in/out + cost + duration
     * @param model LLM 模型名 (tag)
     * @param in 输入 token 数
     * @param out 输出 token 数
     * @param costUSD 花费 USD
     * @param durationMs 耗时毫秒
     *
     * 注意:Prometheus 1.12.5 scrape 对同名 metric (有/无 tag) 会 dedup hide tagged,
     * 所以本方法只累加 tagged counter。untagged total 通过 scrape 端 SUM 聚合得到。
     */
    public void recordLlmCall(String model, long in, long out, double costUSD, long durationMs) {
        Counter.builder("llm_tokens_in_total")
            .description("Total LLM input tokens (prompt)")
            .tag("model", model)
            .register(registry)
            .increment(in);
        Counter.builder("llm_tokens_out_total")
            .description("Total LLM output tokens (completion)")
            .tag("model", model)
            .register(registry)
            .increment(out);
        Counter.builder("llm_cost_usd")
            .description("Total LLM cost in USD (counter name 不带 _total,避免 baseUnit 后缀混淆)")
            .tag("model", model)
            .register(registry)
            .increment(costUSD);
        Timer.builder("llm_call_duration_seconds")
            .description("LLM call latency")
            .tag("model", model)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录 1 次工具调用
     * @param toolName 工具名 (tag)
     */
    public void recordToolCall(String toolName) {
        Counter.builder("tool_calls_total")
            .description("Total tool calls by tool name")
            .tag("tool", toolName)
            .register(registry)
            .increment();
    }

    /**
     * 记录 1 步 agent 执行
     * @param stopReason FINAL_ANSWER / MAX_STEPS / COST_LIMIT / ERROR
     */
    public void recordAgentStep(String stopReason) {
        Counter.builder("agent_steps_total")
            .description("Total agent steps by stop reason")
            .tag("stop_reason", stopReason)
            .register(registry)
            .increment();
        agentSteps.increment();
    }

    /**
     * 输出 Prometheus text 格式 (供 --metrics flag 写到 stdout / file)
     */
    public String scrape() {
        if (registry instanceof PrometheusMeterRegistry pmr) {
            return pmr.scrape();
        }
        return "# registry is " + registry.getClass().getSimpleName() + " (no Prometheus scrape)\n";
    }
}
