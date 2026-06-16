package com.agentbootcamp.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Day 11: Metrics summary reporter (human-readable, 比 Prometheus text format 友好).
 *
 * 中文:从 MetricsCollector 提取 6 类指标,生成一句话一行的人类可读报告。
 *      用于 Main 跑完 agent 后打印到 stdout。
 * English: Extract 6 metric categories from a MetricsCollector, render as a one-per-line
 *      human-readable report.
 *
 * 用法 / Usage:
 *   MetricsCollector mc = new MetricsCollector();
 *   // ... 跑 agent ...
 *   System.out.println(MetricsReporter.printSummary(mc));
 */
public class MetricsReporter {

    /**
     * 生成 summary 报告 (stdout 友好格式).
     * @param mc 跑了若干次 LLM call + tool call + agent step 的 MetricsCollector
     * @return 人类可读 summary
     */
    public static String printSummary(MetricsCollector mc) {
        MeterRegistry reg = mc.getRegistry();
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Day 11 Metrics Report ===\n");

        // 1. LLM call 总数 + 累计 token + 累计 cost
        long llmCalls = reg.find("llm_call_duration_seconds").timers().stream()
            .mapToLong(t -> t.count()).sum();
        double totalTokensIn = sumCounterByName(reg, "llm_tokens_in_total");
        double totalTokensOut = sumCounterByName(reg, "llm_tokens_out_total");
        double totalCostUsd = sumCounterByName(reg, "llm_cost_usd");
        sb.append(String.format("LLM calls: %d (in=%.0f tokens, out=%.0f tokens, cost=$%.6f)\n",
            llmCalls, totalTokensIn, totalTokensOut, totalCostUsd));

        // 2. P50 / P95 / P99 latency
        Timer anyTimer = reg.find("llm_call_duration_seconds").timers().stream()
            .findFirst().orElse(null);
        if (anyTimer != null && anyTimer.count() > 0) {
            sb.append(String.format("LLM latency: P50=%.0fms, P95=%.0fms, P99=%.0fms, max=%.0fms\n",
                anyTimer.takeSnapshot().percentileValues().length > 0
                    ? anyTimer.takeSnapshot().percentileValues()[0].value(TimeUnit.MILLISECONDS) : 0,
                anyTimer.takeSnapshot().percentileValues().length > 1
                    ? anyTimer.takeSnapshot().percentileValues()[1].value(TimeUnit.MILLISECONDS) : 0,
                anyTimer.takeSnapshot().percentileValues().length > 2
                    ? anyTimer.takeSnapshot().percentileValues()[2].value(TimeUnit.MILLISECONDS) : 0,
                anyTimer.max(TimeUnit.MILLISECONDS)));
        }

        // 3. Tool calls (按 tool 分项)
        Map<String, Double> toolCalls = sumCounterByTagValue(reg, "tool_calls_total", "tool");
        if (!toolCalls.isEmpty()) {
            String toolSummary = toolCalls.entrySet().stream()
                .map(e -> String.format("%s=%.0f", e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));
            sb.append(String.format("Tool calls: %s\n", toolSummary));
        }

        // 4. Agent steps (按 stop reason 分项)
        Map<String, Double> agentSteps = sumCounterByTagValue(reg, "agent_steps_total", "stop_reason");
        if (!agentSteps.isEmpty()) {
            String stepSummary = agentSteps.entrySet().stream()
                .map(e -> String.format("%s=%.0f", e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));
            sb.append(String.format("Agent steps: %s\n", stepSummary));
        }

        // 5. 警报提示
        if (totalCostUsd > 0.10) {
            sb.append(String.format("⚠️  WARNING: cost $%.4f > $0.10 硬上限\n", totalCostUsd));
        }
        if (anyTimer != null && anyTimer.max(TimeUnit.SECONDS) > 5.0) {
            sb.append(String.format("⚠️  WARNING: max latency %.1fs > 5s P95 软上限\n",
                anyTimer.max(TimeUnit.SECONDS)));
        }

        return sb.toString();
    }

    private static double sumCounterByName(MeterRegistry reg, String name) {
        return reg.find(name).counters().stream()
            .mapToDouble(c -> c.count())
            .sum();
    }

    /**
     * 按某个 tag 的值分组,sum 每个分组的 count.
     * 例:tool_calls_total 多个 counter (按 tool tag 分),返 Map<toolName, count>
     */
    private static Map<String, Double> sumCounterByTagValue(MeterRegistry reg, String name, String tagKey) {
        Map<String, Double> result = new LinkedHashMap<>();
        reg.find(name).counters().forEach(c -> {
            String tagVal = c.getId().getTag(tagKey);
            if (tagVal != null) {
                result.merge(tagVal, c.count(), Double::sum);
            }
        });
        return result;
    }
}
