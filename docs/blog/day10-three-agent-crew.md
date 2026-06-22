# Day 10 收束博客:3 Agent 团队 / 3-Agent Crew

> **中文**:Researcher (只读) + Critic (零工具纯推理) + Editor (写入),CodeReviewOrchestrator 串 3 步流水线 + `EditFile` 工具。**防御性编程 / Defense in depth**:工具白名单 + 角色分工。
>
> **English**: Day 10 — Researcher (read-only) + Critic (zero-tool pure reasoning) + Editor (write) + EditFile tool. **Defense in depth** via tool whitelist + role separation.

---

## 🎯 背景

Day 8 多 Agent 跑通 (1 主 + 1 worker),但**所有 worker 都能用所有工具** → ResearcherAgent 拿到 `write_file` 可能误改文件,EditorAgent 拿到 `exec("rm -rf")` 可能误删。

Day 10 加 **角色分工 + 工具白名单** = **防御性编程**:

| Agent | 角色 | 工具白名单 |
|---|---|---|
| **ResearcherAgent** | 只读分析 | `read_file` / `grep` / `get_current_time` / `exec("git log")` |
| **CriticAgent** | 纯推理 | `[]` (零工具) |
| **EditorAgent** | 写入 | `read_file` / `write_file` / `edit_file` |

**关键**:**不能**靠 system prompt 限制 ("你不许 write") — LLM 不一定守规矩。**靠工具白名单 RUNTIME 过滤**。

---

## 🏗️ 3 Agent + Orchestrator

### ResearcherAgent (只读)

```java
public class ResearcherAgent extends Agent {
    private static final Set<String> ALLOWED = Set.of("read_file", "grep", "get_current_time");
    public ResearcherAgent(LlmClient llm, ...) {
        super(llm, tools.stream().filter(t -> ALLOWED.contains(t.name())).toList(), ...);
    }
}
```

### CriticAgent (零工具)

```java
public class CriticAgent extends Agent {
    public CriticAgent(LlmClient llm, ...) {
        super(llm, List.of(), trace, maxSteps, maxCostUsd);  // 工具列表 = 空
    }
}
```

### EditorAgent (写入)

```java
public class EditorAgent extends Agent {
    private static final Set<String> ALLOWED = Set.of("read_file", "write_file", "edit_file");
    public EditorAgent(LlmClient llm, ...) {
        super(llm, tools.stream().filter(t -> ALLOWED.contains(t.name())).toList(), ...);
    }
}
```

### CodeReviewOrchestrator (3 步串行)

```java
public RunResult reviewPR(String prDiff) {
    // Step 1: Researcher 分析 diff
    String summary = researcher.run("读 PR diff,生成 summary").finalAnswer();

    // Step 2: Critic 找 bug (纯推理,无工具)
    String bugs = critic.run("基于 summary 找潜在 bug:\n" + summary).finalAnswer();

    // Step 3: Editor 给建议 patch
    String patch = editor.run("基于 bugs 给 patch:\n" + summary + "\n\nBugs:\n" + bugs).finalAnswer();

    return new RunResult(
        "## Summary\n" + summary + "\n\n## Bugs\n" + bugs + "\n\n## Patch\n" + patch,
        FINAL_ANSWER, 3, totalCost
    );
}
```

---

## 💻 EditFile 工具 (新)

```java
public class EditFile implements Tool {
    public String execute(Map<String,Object> args) {
        String path = (String) args.get("path");
        String oldString = (String) args.get("old_string");
        String newString = (String) args.get("new_string");

        Path p = workDir.resolve(path).normalize();
        String content = Files.readString(p);

        if (!content.contains(oldString)) return "[error: old_string not found]";
        String updated = content.replace(oldString, newString);
        Files.writeString(p, updated);
        return "edited " + path + " (" + oldString.length() + " → " + newString.length() + " chars)";
    }
}
```

**关键防护**: `old_string not found` 返回错误字符串,不让 Agent 瞎猜。

---

## 🐛 2 个 Day 10 真坑

1. **Worker shared state 串消息** — Day 8 修过,Day 10 复现 → 每个 Worker 独立 MemoryManager
2. **E2E timeout 60s 偏紧** — 3 Agent 串行跑超时,**修法**: Day 11 提到 120s

---

## 📊 验收数据

| 指标 | 数字 |
|---|---|
| 新增文件 | 4 (ResearcherAgent + CriticAgent + EditorAgent + EditFile + EditFileTest 6 单元) |
| 新增工具 | 1 (`edit_file`, 第 7 个) |
| 总测试 | **62** (Java 单元) + 3 E2E (3 skipped 等 API key) |
| Commit | 5 (feat × 3 + test + docs) |
| 烧钱 | ~$0.012 |
| mvn test | ~3 min |

---

## 🚀 Day 11 预告

**可观测性 + 成本 / Observability + Cost** — Micrometer 1.12.5 + MetricsCollector (6 核心 metric) + CostCalculator (7 厂商定价) + LlmClient/Agent 累加 metrics (向后兼容) + MetricsReporter + Main `-m/--metrics` flag。**从此 LLM 调用有数据**: token / cost / duration / tool_calls / agent_steps 全可见。