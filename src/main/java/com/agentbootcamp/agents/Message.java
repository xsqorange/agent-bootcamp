package com.agentbootcamp.agents;

import java.util.Map;
import java.util.UUID;

/**
 * Day 8: 多 Agent 消息协议 / Multi-agent message protocol
 *
 * 中文:Orchestrator 派 Task → Worker 收到 → Worker 跑 Agent → 返回 Result。
 *      Cancel 暂不实现(Day 8 简化版)。
 * English: Orchestrator dispatches Task → Worker receives → Worker runs Agent → returns Result.
 *          Cancel not implemented in Day 8.
 *
 * 关键设计 / Key design:
 *   - sealed interface + record: 编译期穷尽 (switch expression 强制覆盖所有 case)
 *   - 携带 correlationId: Task → Result 配对 (1 个 orch 派 N 个 task,worker 回 N 个 result,要能配对)
 *   - JSON 序列化友好: record 字段名直接是 JSON key,Day 9+ MCP 互通复用
 *
 * Java 锚定 / Java anchor:
 *   - sealed (JEP 409, Java 17 GA) + record (JEP 395)
 *   - 子类必须在同 module / 同 package (这里都用 nested record)
 */
public sealed interface Message permits Message.Task, Message.Result, Message.Cancel {

    /** 配对 ID: Task.correlationId() ↔ Result.correlationId() */
    String correlationId();

    /**
     * Task: 派给 worker 的子任务 / Sub-task dispatched to worker.
     * @param goal 用户给 Agent 的目标 (跟 Agent.run(goal) 一致)
     * @param args 附加参数 (Day 8 暂不用,留给 Day 9+ 多 worker 协同)
     */
    record Task(String correlationId, String goal, Map<String, Object> args) implements Message {
        /** 便捷构造器: 自动生成 correlationId / Convenience ctor: auto-generate correlationId. */
        public Task(String goal, Map<String, Object> args) {
            this(UUID.randomUUID().toString(), goal, args);
        }
    }

    /**
     * Result: worker 跑完 Agent.run() 返回的结果 / Worker finished Agent.run() result.
     * @param finalAnswer Agent 最终回答 (从 RunResult.finalAnswer() 来)
     * @param totalSteps Agent 跑的步数 (成本/调试用)
     * @param totalCostUsd Agent 烧的钱 (USD,Day 11+ 可观测性会用到)
     */
    record Result(String correlationId, String finalAnswer, int totalSteps, double totalCostUsd)
        implements Message {}

    /**
     * Cancel: orchestrator 取消 worker (Day 8 暂不实现,留接口给 Day 9+).
     * @param reason 取消原因 (如 "timeout", "user request", "downstream failed")
     */
    record Cancel(String correlationId, String reason) implements Message {}
}
