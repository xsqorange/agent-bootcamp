# Day 11 收束博客:可观测性 + 成本 / Observability + Cost

> **中文**:Micrometer 1.12.5 + MetricsCollector (6 核心 metric) + CostCalculator (7 厂商定价) + LlmClient/Agent 累加 (向后兼容) + MetricsReporter + Main `-m/--metrics` flag。**从此 LLM 调用有数据**。
>
> **English**: Day 11 — Micrometer 1.12.5 + 6 core metrics + 7-vendor CostCalculator + LlmClient/Agent accumulate (backward-compat) + MetricsReporter + `-m/--metrics` flag. LLM observability done.

---

## 🎯 背景

Day 1-10 跑通,但**没有数据**:
- 跑了多少 token?
- 烧了多少钱?
- 哪个 tool 调用最多?
- 哪个 model P95 最慢?
- Agent 平均几步完事?

Day 11 装 **Micrometer** (Java Prometheus 通用门面) + **CostCalculator** (按 model 算 USD)。**1.12.5 而非 1.13.0** (1.13.0 改包名到 `io.micrometer.prometheusmetrics`,避坑)。

---

## 🏗️ 6 核心 metric

| Metric | 类型 | Tags | 用途 |
|---|---|---|---|
| `llm_tokens_in_total` | Counter | `model` | 输入 token 总数 |
| `llm_tokens_out_total` | Counter | `model` | 输出 token 总数 |
| `llm_cost_usd_total` | Counter | `model` | 成本 (USD) |
| `llm_duration_seconds` | Timer | `model` | 调用耗时 (P50/P95/P99) |
| `tool_calls_total` | Counter | `tool`, `status` | 工具调用次数 |
| `agent_steps_total` | Counter | `stop_reason` | Agent 步数 + 停止原因 |

---

## 💻 MetricsCollector (~150 行)

```java
public class MetricsCollector {
    private final MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    public void recordLlmCall(String model, int tokensIn, int tokensOut, double costUsd, long durationMs) {
        registry.counter("llm_tokens_in_total", "model", model).increment(tokensIn);
        registry.counter("llm_tokens_out_total", "model", model).increment(tokensOut);
        registry.counter("llm_cost_usd_total", "model", model).increment(costUsd);
        registry.timer("llm_duration_seconds", "model", model).record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordToolCall(String tool, String status) {
        registry.counter("tool_calls_total", "tool", tool, "status", status).increment();
    }

    public void recordAgentStep(String stopReason) {
        registry.counter("agent_steps_total", "stop_reason", stopReason).increment();
    }
}
```

## CostCalculator (7 厂商定价)

```java
public class CostCalculator {
    private static final Map<String, double[]> PRICES = Map.of(
        "minimax-m3",         new double[]{0.10, 0.40},   // input $/MTok, output $/MTok
        "claude-sonnet-4-5",  new double[]{3.00, 15.00},
        "gpt-4o-mini",        new double[]{0.15, 0.60},
        "gpt-4o",             new double[]{2.50, 10.00},
        "deepseek-chat",      new double[]{0.14, 0.28},
        "qwen-plus",          new double[]{0.80, 2.00},
        "ollama-llama3",      new double[]{0.0, 0.0}
    );

    public static double estimate(String model, int tokensIn, int tokensOut) {
        double[] price = PRICES.getOrDefault(model, new double[]{0, 0});
        return (tokensIn / 1_000_000.0) * price[0] + (tokensOut / 1_000_000.0) * price[1];
    }
}
```

## 向后兼容 (跟 Day 4 MemoryManager 同款)

```java
// LlmClient
public LlmClient(LlmConfig config) { this(config, null); }     // 1 参 delegate
public LlmClient(LlmConfig config, MetricsCollector metrics) { /* 存字段 */ }

// Agent
public Agent(... 5 params)               { this(... 5 params, null); }       // 5 参 delegate
public Agent(... 5 params, MemoryManager) { this(... 5 params, null, null); }  // 6 参 delegate
public Agent(... 5 params, MemoryManager, MetricsCollector) { /* 7 参全量 */ }
```

## Main `-m/--metrics` flag

```bash
./mvnw exec:java -Dexec.args="--goal '...' --metrics target/metrics.txt"
# 跑完打印 summary + 写 Prometheus text 到文件
# Grafana/Prometheus 直接 scrape
```

---

## 🐛 4 个 Day 11 真坑

1. **Micrometer 1.13.0 改包名** — `io.micrometer.prometheus` → `io.micrometer.prometheusmetrics`,锁版本到 1.12.5
2. **patch tool 加构造器静默重复 `this.X = X`** — 加 MetricsCollector 时 7 参构造字段赋值重复,patch 后立即 `mvn compile` 验
3. **Prometheus 1.12.5 scrape dedup hide tagged** — 同名 metric 有/无 tag dedup,只累加 tagged
4. **`baseUnit("USD")` 改名** — counter name 加 `_USD_total` 后缀,去掉用普通 name

---

## 📊 验收数据

| 指标 | 数字 |
|---|---|
| 新增文件 | 3 (MetricsCollector + CostCalculator + MetricsReporter + 6 测试) |
| 总测试 | **68** (9 Day 11 + 59 现有无回归) ✅ 全过 |
| 6 metric | tokens_in/out, cost, duration, tool_calls, agent_steps |
| 7 厂商 | minimax-m3 / claude-sonnet-4-5 / gpt-4o-mini / gpt-4o / deepseek / qwen / ollama |
| Commit | 4 (feat + refactor + feat + docs) |
| 烧钱 | ~$0.005 |
| mvn test | ~3s |

---

## 🚀 Day 12 预告

**安全 + 可靠性 / Safety + Reliability** — Prompt injection 防护 (5 attack pattern) + LLM 不可靠 (Resilience4j 装饰模式: CircuitBreaker + Retry + TimeLimiter) + ResilientLlmClient extends LlmClient,**Main 零改向上转型**。**生产 0 容忍** = "代码挂之前 5xx/超时/prompt 注入都已经被拦截 + 监控看到 + 自动恢复"。