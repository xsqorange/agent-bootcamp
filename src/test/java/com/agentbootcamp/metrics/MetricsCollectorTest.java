package com.agentbootcamp.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Day 11: MetricsCollector 单元测试.
 * 5 cases: counter / timer / tag 区分 / reset / Prometheus scrape.
 *
 * 注意:recordLlmCall/recordToolCall/recordAgentStep 都只累加 tagged counter
 * (Prometheus 1.12.5 scrape 对同名 metric (有/无 tag) 会 dedup hide tagged,
 * 所以索性都按 tag 走)。
 */
class MetricsCollectorTest {

    @Test
    void recordLlmCall_incrementsTokensAndCost() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        MetricsCollector mc = new MetricsCollector(reg);

        mc.recordLlmCall("minimax-m3", 1000, 500, 0.0002, 1500);

        // tagged counter 累加
        assertEquals(1000.0, mc.getRegistry().counter("llm_tokens_in_total", "model", "minimax-m3").count());
        assertEquals(500.0, mc.getRegistry().counter("llm_tokens_out_total", "model", "minimax-m3").count());
        assertEquals(0.0002, mc.getRegistry().counter("llm_cost_usd", "model", "minimax-m3").count(), 1e-9);
        // timer 记录 1 次 (按 model tag 区分)
        var timer = mc.getRegistry().timer("llm_call_duration_seconds", "model", "minimax-m3");
        assertEquals(1, timer.count());
        assertEquals(1.5, timer.totalTime(java.util.concurrent.TimeUnit.SECONDS), 0.01);
    }

    @Test
    void recordLlmCall_multipleTimes_accumulates() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        MetricsCollector mc = new MetricsCollector(reg);

        mc.recordLlmCall("gpt-4o-mini", 100, 50, 0.0001, 1000);
        mc.recordLlmCall("gpt-4o-mini", 200, 100, 0.0002, 2000);

        // 同 model 累加到同一 tagged counter
        assertEquals(300.0, mc.getRegistry().counter("llm_tokens_in_total", "model", "gpt-4o-mini").count());
        assertEquals(150.0, mc.getRegistry().counter("llm_tokens_out_total", "model", "gpt-4o-mini").count());
        assertEquals(0.0003, mc.getRegistry().counter("llm_cost_usd", "model", "gpt-4o-mini").count(), 1e-9);
        assertEquals(2, mc.getRegistry().timer("llm_call_duration_seconds", "model", "gpt-4o-mini").count());
    }

    @Test
    void recordLlmCall_differentModels_separateCounters() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        MetricsCollector mc = new MetricsCollector(reg);

        mc.recordLlmCall("gpt-4o-mini", 100, 50, 0.0001, 1000);
        mc.recordLlmCall("minimax-m3", 200, 100, 0.0002, 2000);

        // 不同 model 各自独立累加
        assertEquals(100.0, mc.getRegistry().counter("llm_tokens_in_total", "model", "gpt-4o-mini").count());
        assertEquals(200.0, mc.getRegistry().counter("llm_tokens_in_total", "model", "minimax-m3").count());
    }

    @Test
    void recordToolCall_taggedByToolName() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        MetricsCollector mc = new MetricsCollector(reg);

        mc.recordToolCall("read_file");
        mc.recordToolCall("read_file");
        mc.recordToolCall("write_file");

        assertEquals(2.0, mc.getRegistry().counter("tool_calls_total", "tool", "read_file").count());
        assertEquals(1.0, mc.getRegistry().counter("tool_calls_total", "tool", "write_file").count());
    }

    @Test
    void recordAgentStep_taggedByStopReason() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        MetricsCollector mc = new MetricsCollector(reg);

        mc.recordAgentStep("FINAL_ANSWER");
        mc.recordAgentStep("MAX_STEPS");
        mc.recordAgentStep("MAX_STEPS");

        assertEquals(1.0, mc.getRegistry().counter("agent_steps_total", "stop_reason", "FINAL_ANSWER").count());
        assertEquals(2.0, mc.getRegistry().counter("agent_steps_total", "stop_reason", "MAX_STEPS").count());
    }

    @Test
    void scrape_prometheusFormat_containsAllMetrics() {
        MetricsCollector mc = new MetricsCollector();  // 默认 Prometheus registry

        mc.recordLlmCall("minimax-m3", 100, 50, 0.0001, 1000);
        mc.recordToolCall("read_file");
        mc.recordAgentStep("FINAL_ANSWER");

        String scrape = mc.scrape();
        // Prometheus text format: 包含 # HELP / # TYPE / metric_name
        assertTrue(scrape.contains("llm_tokens_in_total"), "scrape 应含 llm_tokens_in_total");
        assertTrue(scrape.contains("llm_tokens_out_total"));
        assertTrue(scrape.contains("llm_cost_usd"), "scrape 应含 llm_cost_usd (counter name, 不带 _total 避免 baseUnit 后缀)");
        assertTrue(scrape.contains("llm_call_duration_seconds"));
        assertTrue(scrape.contains("tool_calls_total"));
        assertTrue(scrape.contains("agent_steps_total"));
        assertTrue(scrape.contains("# HELP"), "Prometheus 格式应含 # HELP 行");
        assertTrue(scrape.contains("# TYPE"), "Prometheus 格式应含 # TYPE 行");
        // model tag 应出现在 metric 行
        assertTrue(scrape.contains("model=\"minimax-m3\""),
            "scrape 应含 model=\"minimax-m3\" tag");
    }
}
