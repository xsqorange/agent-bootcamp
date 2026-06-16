package com.agentbootcamp.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Day 11: MetricsReporter 单元测试.
 * 3 cases: 空 / 1 step / 多 step P95.
 */
class MetricsReporterTest {

    @Test
    void printSummary_emptyMetrics_printsZero() {
        MetricsCollector mc = new MetricsCollector(new SimpleMeterRegistry());
        String summary = MetricsReporter.printSummary(mc);
        // 至少含 header + 0 LLM calls (没 timer)
        assertTrue(summary.contains("Day 11 Metrics Report"), "应含 header");
        assertTrue(summary.contains("LLM calls: 0"), "空 metrics 应显 0 LLM calls");
    }

    @Test
    void printSummary_singleLlmCall_printsTokensAndCost() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        MetricsCollector mc = new MetricsCollector(reg);
        mc.recordLlmCall("minimax-m3", 1000, 500, 0.0002, 1500);
        mc.recordToolCall("read_file");

        String summary = MetricsReporter.printSummary(mc);
        assertTrue(summary.contains("LLM calls: 1"), "应显 1 LLM call");
        assertTrue(summary.contains("in=1000"), "应显 in=1000 tokens");
        assertTrue(summary.contains("out=500"), "应显 out=500 tokens");
        assertTrue(summary.contains("cost=$0.000200"), "应显 cost=$0.0002");
        assertTrue(summary.contains("Tool calls: read_file=1"), "应显 tool calls 分项");
    }

    @Test
    void printSummary_multipleCalls_aggregatesCorrectly() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        MetricsCollector mc = new MetricsCollector(reg);

        // 3 次 LLM calls
        mc.recordLlmCall("gpt-4o-mini", 100, 50, 0.0001, 1000);
        mc.recordLlmCall("gpt-4o-mini", 200, 100, 0.0002, 2000);
        mc.recordLlmCall("minimax-m3", 300, 150, 0.0003, 3000);

        // 4 个 tool calls (3 read + 1 write)
        mc.recordToolCall("read_file");
        mc.recordToolCall("read_file");
        mc.recordToolCall("read_file");
        mc.recordToolCall("write_file");

        // 2 agent steps (1 FINAL_ANSWER + 1 MAX_STEPS)
        mc.recordAgentStep("FINAL_ANSWER");
        mc.recordAgentStep("MAX_STEPS");

        String summary = MetricsReporter.printSummary(mc);
        // 3 LLM calls 累加
        assertTrue(summary.contains("LLM calls: 3"));
        // 100+200+300 = 600 in tokens
        assertTrue(summary.contains("in=600"));
        // 50+100+150 = 300 out tokens
        assertTrue(summary.contains("out=300"));
        // 0.0001+0.0002+0.0003 = 0.0006 cost
        assertTrue(summary.contains("cost=$0.000600"));
        // tool calls 分项
        assertTrue(summary.contains("read_file=3"));
        assertTrue(summary.contains("write_file=1"));
        // agent steps 分项
        assertTrue(summary.contains("FINAL_ANSWER=1"));
        assertTrue(summary.contains("MAX_STEPS=1"));
    }
}
