package com.agentbootcamp.agents;

import com.agentbootcamp.Agent;
import com.agentbootcamp.MemoryManager;
import com.agentbootcamp.RunResult;
import com.agentbootcamp.LlmClient;
import com.agentbootcamp.Tool;
import com.agentbootcamp.TraceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Day 8: Worker Agent — 跑任务的 worker / Worker that runs Agent tasks
 *
 * 中文:Worker 启动一个线程,死循环从 queue poll Task 出来,调 Agent.run(goal)
 *      跑完把 Result put 回 queue,Orchestrator 在另一边等。
 * English: Worker runs a thread, polls Task from queue in a loop, calls Agent.run(goal),
 *          puts Result back to queue, Orchestrator waits on the other side.
 *
 * 设计要点 / Design points:
 *   - 继承 Agent (复用 ReAct 循环 / 工具 / 成本估算)
 *   - runLoop 是阻塞的,只能 1 个线程跑 1 个 worker (Day 8 简化)
 *   - 用 volatile boolean running 控制优雅停止 (Day 8 不实现 Cancel)
 *   - runLoop 内的 Exception 不会挂线程,只 log (Day 12+ 会加 Resilience4j)
 *
 * Java 锚定 / Java anchor:
 *   - extends 复用 (composition over inheritance 的反例,但这里合适)
 *   - BlockingQueue.poll(timeout) — 不阻塞永久,允许线程周期性 check running
 */
public class WorkerAgent extends Agent {
    private static final Logger log = LoggerFactory.getLogger(WorkerAgent.class);

    private final String name;
    private volatile boolean running = true;

    /** 完整构造器(7 参)— extends Agent 6 参 + name。 */
    public WorkerAgent(String name, LlmClient llm, List<Tool> tools, TraceWriter trace,
                       int maxSteps, double maxCostUsd, MemoryManager memory) {
        super(llm, tools, trace, maxSteps, maxCostUsd, memory);
        this.name = name;
    }

    /** 便捷构造器(6 参)— 不带 memory。 */
    public WorkerAgent(String name, LlmClient llm, List<Tool> tools, TraceWriter trace,
                       int maxSteps, double maxCostUsd) {
        this(name, llm, tools, trace, maxSteps, maxCostUsd, null);
    }

    /**
     * Worker 主循环 / Worker main loop.
     * 从 queue poll Task,跑 Agent.run(goal),put Result 回 queue。
     * @param queue 共享 BlockingQueue (Orchestrator 和 worker 共用)
     */
    public void runLoop(BlockingQueue<Message> inbox, BlockingQueue<Message> outbox) {
        log.info("[{}] Worker 启动", name);
        while (running) {
            Message msg;
            try {
                msg = inbox.poll(100, TimeUnit.MILLISECONDS);  // 100ms 周期,允许 check running
            } catch (InterruptedException e) {
                log.info("[{}] Worker 被中断,退出", name);
                Thread.currentThread().interrupt();
                return;
            }
            if (msg == null) continue;  // 无任务,继续轮询
            if (!(msg instanceof Message.Task task)) {
                log.warn("[{}] 收到非 Task 消息: {} (Day 8 暂不处理)", name, msg.getClass().getSimpleName());
                continue;
            }
            try {
                log.info("[{}] 收到 task correlationId={} goal='{}'", name, task.correlationId(), task.goal());
                RunResult rr = run(task.goal());  // 继承自 Agent 的 ReAct 循环
                Message.Result result = new Message.Result(
                    task.correlationId(),
                    rr.finalAnswer(),
                    rr.totalSteps(),
                    rr.totalCostUsd()
                );
                outbox.put(result);  // put 到 outbox (worker → orchestrator)
                log.info("[{}] 返回 result correlationId={} steps={} cost=${}",
                    name, result.correlationId(), result.totalSteps(), result.totalCostUsd());
            } catch (Exception e) {
                log.error("[{}] Worker 跑 task 异常: {}", name, e.getMessage(), e);
                // 异常隔离:不挂线程,继续跑下一个 task
            }
        }
        log.info("[{}] Worker 退出", name);
    }

    /** 优雅停止 / Graceful shutdown. */
    public void shutdown() {
        log.info("[{}] shutdown() 收到", name);
        running = false;
    }

    public String name() { return name; }
}
