# Day 2 收束博客:从零写 ReAct 循环

> **中文**:用 200 行 Java 写一个 ReAct 循环 — 不引 LangChain / Spring AI Agent,**真懂每行**。
>
> **English**: Day 2 — write a 200-line ReAct loop from scratch, no framework. The core day of the 2-week sprint.

---

## 🎯 背景

ReAct = Reasoning + Acting。LLM **思考** (调用哪些工具) → **执行** (工具跑) → **观察** (工具结果) → **再思考** → … → **最终答案**。

Day 1 已经能调 LLM + 跑工具,Day 2 把这两件事**循环起来**。

**为什么不用框架**:
- LangChain / LangGraph / Spring AI Agent 都是大黑盒,**出 bug 不会调**
- 200 行手写循环 = **每行都能解释**,production 出问题秒定位
- 14 天速成后,真正上 LangGraph 才是"换骨架,不动器官"

---

## 🏗️ ReAct 循环

```java
for (int step = 0; step < maxSteps; step++) {
    var resp = llm.chat(messages, toolSchemas);

    // 1. LLM 说 done → 返回最终答案
    if (!resp.hasToolCall()) {
        trace.writeStep(new AgentStep(step, FINAL_ANSWER, resp.content()));
        return new RunResult(resp.content(), FINAL_ANSWER, step+1, totalCostUsd);
    }

    // 2. LLM 说调 N 个工具 → 并行执行 (一个响应可能调多个工具)
    var executions = new ArrayList<ToolExecutionRecord>();
    for (var tc : resp.toolCalls()) {  // 注意是 for-each 不是 get(0)
        try {
            var obs = tools.get(tc.name()).execute(parseArgs(tc.arguments()));
            executions.add(new ToolExecutionRecord(... ok=true ...));
        } catch (Exception e) {
            // 异常隔离:工具失败不让循环挂
            executions.add(new ToolExecutionRecord(... ok=false, error=e.getMessage() ...));
        }
    }

    // 3. 把结果塞 messages 给 LLM
    messages.add(assistant(resp.content(), resp.toolCalls()));
    for (var exec : executions) {
        messages.add(toolMessage(exec.toolCallId(), exec.result()));
    }
    trace.writeStep(new AgentStep(step, null, null));

    // 4. 成本护栏
    if (totalCostUsd > maxCostUsd) {
        return new RunResult("...", COST_LIMIT, step+1, totalCostUsd);
    }
}
return new RunResult("...", MAX_STEPS, maxSteps, totalCostUsd);
```

**4 个新类型** (Day 2 必须):
- `StopReason` enum:`FINAL_ANSWER` / `MAX_STEPS` / `COST_LIMIT` / `ERROR`
- `AgentStep` record: 单步 trace 快照 (含 token + cost)
- `RunResult` record: `run()` 返回值
- `TraceWriter` class: JSONL 追加写入器,**每步刷盘**

---

## 💻 关键设计

### 并行工具 (不是 get(0))

**错**:`var obs = tools.get(resp.toolCall().name()).execute(...);`
**对**:`for (var tc : resp.toolCalls()) { ... }`

一个 LLM 响应里可能调 N 个工具,`get(0)` 只取第一个,丢了其他。

### 异常隔离

工具抛异常不让循环挂 — **错误字符串塞 messages**,LLM 下次会修正:

```java
} catch (Exception e) {
    executions.add(new ToolExecutionRecord(... ok=false, error=e.getMessage() ...));
    // 不 throw, 不 break, 继续下一轮
}
```

### JSONL Trace

每步写一行到 `target/trace.jsonl`,崩了 trace 还在:

```json
{"step":0,"stopReason":null,"llmContent":"I'll call get_current_time","toolCalls":[{"id":"call_1","name":"get_current_time","args":{}}],"toolResults":[],"tokensIn":120,"tokensOut":35,"costUsd":0.000035}
```

---

## 🐛 5 个 Day 2 真坑

1. **嵌套 record 跨包访问**: `LlmClient.LlmResponse` 必须显式 import (Java 17 record 嵌套特性)
2. **trace 缺最后一笔 stopReason**: `MAX_STEPS` / `COST_LIMIT` break 时不写 trace,最后一行 stopReason=null — **修法**: 循环外 `writeSummaryTrace()` 写一行"印章"
3. **static 字段 package-private**: `static final ObjectMapper JSON` 同包共享,**不要 public**
4. **record 不能被继承**: 想扩展 record 行为 → composition (包一个普通 class) 或 sealed interface
5. **真 LLM 端到端测试依赖网络**: Day 2 没 API key 跑挂,Day 11 才有 ScriptedLlmClient 重放

---

## 📊 验收数据

| 指标 | 数字 |
|---|---|
| 新增类型 | 4 (`StopReason` enum + `AgentStep` record + `RunResult` record + `TraceWriter` class) |
| Agent.java 行数 | ~200 (含 while 循环 + 并行工具 + 异常隔离 + 成本护栏) |
| 新增 flag | 3 (`--max-steps` / `--trace` / `--max-cost`) |
| 总测试 | **6** (3 Day 1 + 3 Day 2 E2E: TC-1/2/3 真 LLM) ✅ 全过 |
| Commit | 2 (`feat(day2): ReAct loop + 4 新类型` + `docs(day2): 架构图 + 验收清单`) |
| 烧钱 | ~$0.003 |

---

## 🚀 Day 3 预告

**+ 2 工具 + 评测脚手架** — 加 `write_file` + `grep` 工具,跑 10 个黄金用例 JUnit harness,把"agent 能跑"升级到"agent 能改 + 能测"。