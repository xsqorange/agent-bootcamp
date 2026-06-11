package com.agentbootcamp.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Day 8: Orchestrator — 拆任务 + 分派 + 汇总 / Multi-agent orchestrator
 *
 * 中文:Orchestrator 持有 inbox (→ Worker) + outbox (← Worker) 2 个独立 queue。
 *      经典 CSP 模式 — 避免 orchestrator 跟 worker 抢同 1 个 queue 导致 race condition。
 * English: Orchestrator holds inbox (→ Worker) + outbox (← Worker) — 2 separate queues.
 *          Classic CSP pattern — avoids orch/worker race on the same queue.
 *
 * 设计要点 / Design points:
 *   - 2 个独立 queue (inbox + outbox) — Day 8 简化版,Day 10+ 可换 per-worker queue
 *   - submitAndWait 用 correlationId 配对 — 允许 1 orch 派 N task 并行 (Day 8 单 task)
 *   - start() 启 worker 线程;stop() 优雅 join (Day 8 不实现 Cancel,只用 shutdown flag)
 *   - queue 无界 (LinkedBlockingQueue 默认 Integer.MAX_VALUE) — 简单,Day 12+ 加有界 + 背压
 *
 * Java 锚定 / Java anchor:
 *   - CSP (Communicating Sequential Processes) — 进程通过 channel 通信,不共享状态
 *   - BlockingQueue 选 LinkedBlockingQueue (无界,FIFO) — Day 12+ 可换 ArrayBlockingQueue
 *   - poll(timeout) 不用 take() — 让循环能周期性 check running 状态
 *
 * 判别标准 / Rule of thumb (Day 8 笔记):
 *   1 个 agent 在 10 步内能干完的,就不要拆成 orchestrator + worker。
 *   拆的场景:N 个独立子任务 / 需要并行 / 需要失败隔离
 */
public class Orchestrator {
    private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);

    private final BlockingQueue<Message> inbox = new LinkedBlockingQueue<>();   // Orch → Worker
    private final BlockingQueue<Message> outbox = new LinkedBlockingQueue<>();  // Worker → Orch
    private final List<WorkerAgent> workers;
    private final List<Thread> workerThreads = new ArrayList<>();
    private volatile boolean started = false;

    public Orchestrator(List<WorkerAgent> workers) {
        this.workers = workers;
    }

    /** 便捷构造器:1 worker / Convenience ctor: 1 worker. */
    public Orchestrator(WorkerAgent worker) {
        this(List.of(worker));
    }

    /** 启动所有 worker 线程 / Start all worker threads. */
    public synchronized void start() {
        if (started) {
            log.warn("Orchestrator 已启动,跳过");
            return;
        }
        for (WorkerAgent w : workers) {
            Thread t = new Thread(() -> w.runLoop(inbox, outbox), "worker-" + w.name());
            t.setDaemon(true);  // daemon: main 线程退时 worker 也退 (避免进程不退出)
            t.start();
            workerThreads.add(t);
        }
        started = true;
        log.info("Orchestrator 启动: {} 个 worker (inbox + outbox = 2 个独立 queue)", workers.size());
    }

    /**
     * 派 1 个 task 并等 result / Submit 1 task and wait for result.
     * @param task 派给 worker 的子任务
     * @param timeoutMs 等 result 的超时
     * @return worker 跑完的 Result
     * @throws InterruptedException 线程被中断
     * @throws java.util.concurrent.TimeoutException 超时
     */
    public Message.Result submitAndWait(Message.Task task, long timeoutMs) throws Exception {
        if (!started) {
            throw new IllegalStateException("Orchestrator 未启动,先调 start()");
        }
        log.info("派 task correlationId={} goal='{}'", task.correlationId(), task.goal());
        inbox.put(task);  // put 到 inbox,worker 会从这里 poll

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            long remaining = deadline - System.currentTimeMillis();
            Message msg = outbox.poll(Math.min(remaining, 100), TimeUnit.MILLISECONDS);
            if (msg == null) continue;
            if (msg instanceof Message.Result r && r.correlationId().equals(task.correlationId())) {
                return r;
            }
            // Day 8 简化:不处理别的 worker 的 result (单 task 场景下不会发生)
            // Day 10+ 需要按 correlationId 配对,丢回 outbox 等下次 poll
            log.warn("收到非预期的 message: {}", msg);
        }
        throw new java.util.concurrent.TimeoutException(
            "Task " + task.correlationId() + " 等 result 超时 (" + timeoutMs + "ms)");
    }

    /** 停止所有 worker / Stop all workers gracefully. */
    public synchronized void stop() {
        log.info("Orchestrator 停止中...");
        for (WorkerAgent w : workers) w.shutdown();
        for (Thread t : workerThreads) {
            try {
                t.join(5000);  // 5s 优雅 join
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        started = false;
        log.info("Orchestrator 已停止");
    }

    public int workerCount() { return workers.size(); }
}
