# Day 8 收束博客:多 Agent 入门 / Multi-Agent Intro

> **中文**:`Orchestrator` + `WorkerAgent` + sealed `Message` interface,1 主 Agent 拆任务派给 worker,BlockingQueue 消息队列。**Week 2 开始的标志**:从单 Agent 升级到多 Agent 协作。
>
> **English**: Day 8 — Orchestrator + WorkerAgent + sealed Message + BlockingQueue. From single-agent to multi-agent collaboration.

---

## 🎯 背景

Day 1-7 单 Agent 在 10 步内能干完简单任务,但**复杂任务**(比如"review PR #42")单 Agent 一次跑不完 — LLM context 爆 + 工具调用链太长 + 状态丢失。

Day 8 装 **Orchestrator-Workers 模式**: 1 个主 Agent (Orchestrator) 拆任务派给 N 个 Worker,Worker 独立跑子任务返结果,Orchestrator 汇总。

**判别标准 / Rule of thumb**: **1 个 agent 在 10 步内能干完的,就不要拆**。过早拆 = 过度工程。

---

## 🏗️ 3 大组件

### Orchestrator (主)

```java
public class Orchestrator {
    private final List<WorkerAgent> workers;
    private final BlockingQueue<Message> incoming;
    private final BlockingQueue<Message> outgoing;

    public RunResult orchestrate(String userGoal) {
        List<Task> tasks = decompose(userGoal);  // 1. 拆任务
        List<Future<Result>> futures = tasks.stream()
            .map(t -> workers.get(t.workerIdx()).submitAsync(t))  // 2. 派任务
            .toList();
        return new RunResult(
            tasks.stream()
                .map(t -> futures.get(t.idx()).join().answer())
                .reduce(this::merge)  // 3. 汇总
                .orElse(""),
            StopReason.FINAL_ANSWER, futures.size(), 0.0
        );
    }
}
```

### WorkerAgent (子, extends Agent)

```java
public class WorkerAgent extends Agent {
    private final BlockingQueue<Message> inbox;
    private final BlockingQueue<Message> outbox;
    private final Set<String> allowedTools;  // 工具白名单 (Day 10 重点)

    @Override
    public LlmResponse chat(List<Message> messages, List<Map<String,Object>> tools) {
        // 过滤掉白名单外的工具 (防御性编程)
        var filtered = tools.stream()
            .filter(t -> allowedTools.contains((String) t.get("name")))
            .toList();
        return super.chat(messages, filtered);
    }
}
```

### sealed Message interface

```java
public sealed interface Message permits TaskMessage, ResultMessage, CancelMessage {
    String id();
}
public record TaskMessage(String id, String task, List<String> tools) implements Message {}
public record ResultMessage(String id, String result, boolean ok) implements Message {}
public record CancelMessage(String id, String reason) implements Message {}
```

---

## 💻 关键设计

| 决策 | 选项 | 理由 |
|---|---|---|
| **Worker 数量** | 1 worker (Day 8) → 3 (Day 10) | Day 8 验证模式,Day 10 加角色 |
| **消息队列** | `LinkedBlockingQueue` | 标准 Java,Backpressure 友好 |
| **工具白名单** | `Set<String>` per Worker | **防御性编程**,Worker 看不到白名单外工具 |
| **任务拆解** | LLM 1 次调用 | Day 10 升级为多步拆解 |

---

## 🐛 4 个 Day 8 真坑

1. **LlmClient 429 retry** — Day 8 多 Agent 跑 23 个 E2E 撞 7 次 429,加 5s/15s retry 救回 7/8
2. **Worker shared state** — Worker 用同一 MemoryManager 互相串消息,**修法**: 每个 Worker 独立 MemoryManager
3. **BlockingQueue unbounded** — Orchestrator 派任务快,Worker 处理慢,堆 1000+ 消息 OOM,**修法**: `LinkedBlockingQueue(100)` 上限
4. **orchestrator 主线程不退出** — `outbox.take()` 阻塞,Worker 完了主线程不死,**修法**: `outbox.poll(30, SECONDS)` 超时

---

## 📊 验收数据

| 指标 | 数字 |
|---|---|
| 新增文件 | 5 (Orchestrator + WorkerAgent + Message + AgentTest 多 agent 用例 + 文档) |
| 新增 CLI flag | 3 (`--multi-agent` / `--workers N` / `--task-timeout 30s`) |
| 总测试 | **57** (34 单元 + 23 E2E) ✅ 全过, 0 回归 |
| Commit | 5 (feat × 3 + test + docs) |
| 烧钱 | ~$0.012 (retry 救回 7/8 AgentTest 撞 429) |
| mvn test | ~2 min |

---

## 🚀 Day 9 预告

**MCP 服务器 / MCP Server** — 把 Day 1-8 的 6 工具暴露成 MCP server,然后用 Python 客户端调用 — **证明跨语言互通**。**关键发现**: MCP 官方只发 Kotlin SDK,Java 没有官方 SDK,**自实现 JSON-RPC 2.0 子集** 是唯一稳路径 (~200 行, 0 依赖)。