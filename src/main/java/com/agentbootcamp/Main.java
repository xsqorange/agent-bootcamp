package com.agentbootcamp;

import com.agentbootcamp.agents.Message;
import com.agentbootcamp.agents.Orchestrator;
import com.agentbootcamp.agents.WorkerAgent;
import com.agentbootcamp.tools.Exec;
import com.agentbootcamp.tools.GetCurrentTime;
import com.agentbootcamp.tools.Grep;
import com.agentbootcamp.tools.ReadFile;
import com.agentbootcamp.tools.SearchKb;
import com.agentbootcamp.tools.WriteFile;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI 入口 — picocli / CLI entry — picocli
 *
 * Day 4 新增 / Day 4 new:
 *  - 加载知识库 (RagIndex) 从 classpath:/knowledge/ 或文件系统兜底
 *  - 注册第 6 个工具: search_kb
 *  - Agent 启用 MemoryManager (滑动窗口 + 摘要压缩, 阈值 24/10000)
 *
 * 用法 / Usage:
 *   ./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '...'"
 *   ./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '...' --max-steps 5 --trace off"
 */
@Command(
    name = "myagent",
    mixinStandardHelpOptions = true,
    version = "agent-bootcamp 0.5.0",
    description = "Day 8: 6 tools + memory + RAG + multi-agent (Orchestrator + Worker via BlockingQueue). " +
                  "6 tools + memory + simple RAG + 1 orchestrator + 1 worker."
)
public class Main implements Runnable {

    @Option(names = {"-g", "--goal"},
        description = "用户目标(必填)/ User goal (required)",
        required = false)
    private String goal;

    @Option(names = {"--max-steps"},
        description = "最大步数(默认 ${DEFAULT-VALUE})/ Max steps (default ${DEFAULT-VALUE})",
        defaultValue = "10")
    private int maxSteps;

    @Option(names = {"--max-cost"},
        description = "单次最大成本 USD(默认 ${DEFAULT-VALUE})/ Max cost per run in USD (default ${DEFAULT-VALUE})",
        defaultValue = "1.0")
    private double maxCost;

    @Option(names = {"--trace"},
        description = "JSONL trace 路径,'off' 禁用(默认 ${DEFAULT-VALUE})/ JSONL trace path, 'off' to disable (default ${DEFAULT-VALUE})",
        defaultValue = "target/trace.jsonl")
    private String tracePath;

    @Option(names = {"--no-memory"},
        description = "禁用 MemoryManager (默认启用)/ Disable MemoryManager (default enabled)",
        defaultValue = "false")
    private boolean noMemory;

    @Option(names = {"--dry-run"},
        description = "只打印计划,不调 LLM / Print plan only, skip LLM call",
        defaultValue = "false")
    private boolean dryRun;

    @Option(names = {"--multi-agent"},
        description = "启用多 Agent 模式(Day 8): Orchestrator + 1 Worker / Enable multi-agent mode",
        defaultValue = "false")
    private boolean multiAgent;

    @Option(names = {"--task-goal"},
        description = "派给 worker 的子任务(仅 --multi-agent)/ Sub-task dispatched to worker (multi-agent only)")
    private String taskGoal;

    @Option(names = {"--worker-timeout"},
        description = "worker 等 result 超时 ms(默认 ${DEFAULT-VALUE})/ Worker result timeout ms (default ${DEFAULT-VALUE})",
        defaultValue = "60000")
    private long workerTimeoutMs;

    @Option(names = {"-m", "--metrics"},
        description = "Day 11: 输出 Prometheus 格式 metrics 到指定文件 (默认 'off' 不输出)/ Output Prometheus metrics to file (default 'off')",
        defaultValue = "off")
    private String metricsPath;

    @Option(names = {"--safe-mode"},
            description = "Day 12: 用 Resilience4j 包装 LlmClient (CircuitBreaker 5xx + Retry + TimeLimiter 10s)/ Wrap LlmClient with Resilience4j (default enabled)",
            defaultValue = "true")
        private boolean safeMode;

        @Option(names = {"--server"},
            description = "Day 13: 起 HTTP server (替代 CLI 跑 goal) — 端点 /health /metrics /api/run / Start HTTP server instead of running goal (for K8s deploy)",
            defaultValue = "false")
        private boolean serverMode;

        @Option(names = {"--server-port"},
            description = "Day 13: HTTP server 端口 (默认 8080) / HTTP server port",
            defaultValue = "8080")
        private int serverPort;

        public static void main(String[] args) {
            int rc = new CommandLine(new Main()).execute(args);
            System.exit(rc);
        }

        @Override
        public void run() {
            // Day 13: --server 模式不要求 --goal,只有 CLI 跑 goal 模式才必须
            if (!serverMode && (goal == null || goal.isBlank())) {
                System.err.println("错误: 必须传 --goal (或用 --server 起 HTTP server)");
                System.err.println("Error: --goal is required (or pass --server to start HTTP server)");
                CommandLine.usage(this, System.err);
                System.exit(2);
            }
            // Day 13: --server 模式: 启 HTTP server 替代 CLI
            if (serverMode) {
                runHttpServer();
                return;
            }
            try {
                log("目标 / Goal: " + goal);
                log("参数 / Args: maxSteps=" + maxSteps + ", maxCost=$" + maxCost
                    + ", trace=" + tracePath + ", memory=" + (noMemory ? "off" : "on")
                    + ", multiAgent=" + multiAgent);

            // 0. 加载知识库 (Day 4)
            Path knowledgeDir = findKnowledgeDir();
            RagIndex ragIndex = new RagIndex(knowledgeDir);
            log("知识库: " + (knowledgeDir != null ? knowledgeDir.toAbsolutePath() : "(not found, empty index)")
                + " → " + ragIndex.size() + " chunks");

            if (dryRun) {
                log("DRY RUN — 不调 LLM,只展示工具列表");
                List<Tool> tools = buildTools(ragIndex);
                log("已注册 " + tools.size() + " 个工具: " + tools.stream().map(Tool::name).toList());
                log("Agent 配置: maxSteps=" + maxSteps + ", maxCost=$" + maxCost
                    + ", trace=" + tracePath + ", memory=" + (noMemory ? "off" : "on"));
                log("(用 --max-steps, --max-cost, --trace, --no-memory 调参;用 --goal 跑真的)");
                return;
            }

            // 分支: multi-agent (Day 8) vs single-agent (Day 1-7)
            if (multiAgent) {
                runMultiAgent(ragIndex);
            } else {
                runSingleAgent(ragIndex);
            }
        } catch (Exception e) {
            System.err.println("执行失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
         * Day 13: 起 HTTP server (替代 CLI 跑 goal). K8s/docker-compose 用 /health 探活, /metrics scrape.
         */
        private void runHttpServer() {
            com.agentbootcamp.metrics.MetricsCollector metrics = new com.agentbootcamp.metrics.MetricsCollector();
            var server = new com.agentbootcamp.server.HttpServerMain(serverPort, metrics);
            try {
                server.start();
                log("Day 13 HTTP server 起: http://0.0.0.0:" + serverPort);
                log("端点: /health /health/liveness /health/readiness /metrics");
                log("按 Ctrl+C 停止 / Press Ctrl+C to stop");
                // 阻塞主线程
                Thread.currentThread().join();
            } catch (Exception e) {
                log("HTTP server 启动失败: " + e.getMessage());
                System.exit(1);
            } finally {
                server.stop();
            }
        }

        /**
         * Day 1-7: 单 Agent 跑 / Single Agent run.
         */
        private void runSingleAgent(RagIndex ragIndex) throws Exception {
        LlmConfig config = LlmConfig.fromEnv();
        com.agentbootcamp.metrics.MetricsCollector metrics = new com.agentbootcamp.metrics.MetricsCollector();
        LlmClient llm = createLlmClient(config, metrics);
        List<Tool> tools = buildTools(ragIndex);
        log("已注册 " + tools.size() + " 个工具: " + tools.stream().map(Tool::name).toList());
        MemoryManager memory = noMemory ? null : new MemoryManager();
        log("Memory: " + (memory != null
            ? "enabled (threshold: msgs>24 OR tokens>10000)"
            : "disabled"));

        try (TraceWriter trace = new TraceWriter(tracePath)) {
            // Day 12 任务 1: PromptGuard 集成 - 工具执行后自动 scan + wrap
            com.agentbootcamp.safety.PromptGuard promptGuard = new com.agentbootcamp.safety.PromptGuard();
            Agent agent = new Agent(llm, tools, trace, maxSteps, maxCost, memory, metrics, promptGuard);
            RunResult result = agent.run(goal);
            System.out.println();
            System.out.println("=== Agent 回答 / Agent's answer ===");
            System.out.println(result.finalAnswer());
            System.out.println("====================================");
            System.out.println("统计 / Stats: " + result.summary());
            if (!"off".equalsIgnoreCase(tracePath)) {
                System.out.println("Trace:  " + Path.of(tracePath).toAbsolutePath());
            }
            // Day 11: 打印 metrics summary, 写 Prometheus text 到 --metrics 文件
            System.out.println(com.agentbootcamp.metrics.MetricsReporter.printSummary(metrics));
            if (!"off".equalsIgnoreCase(metricsPath)) {
                Path mp = Path.of(metricsPath);
                java.nio.file.Files.writeString(mp, metrics.scrape());
                System.out.println("Metrics (Prometheus): " + mp.toAbsolutePath());
            }
        }
    }

    /**
     * Day 8: Orchestrator + 1 Worker 跑 / Orchestrator + 1 Worker run.
     * 派 1 个 task(默认用 --goal,也可用 --task-goal 覆盖)给 worker,等 result。
     */
    private void runMultiAgent(RagIndex ragIndex) throws Exception {
        LlmConfig config = LlmConfig.fromEnv();
        com.agentbootcamp.metrics.MetricsCollector metrics = new com.agentbootcamp.metrics.MetricsCollector();
        LlmClient llm = createLlmClient(config, metrics);
        List<Tool> tools = buildTools(ragIndex);
        MemoryManager memory = noMemory ? null : new MemoryManager();
        log("已注册 " + tools.size() + " 个工具: " + tools.stream().map(Tool::name).toList());
        log("Memory: " + (memory != null ? "enabled" : "disabled"));
        log("Multi-agent: 1 Orchestrator + 1 Worker");

        // worker trace 写到独立文件,避免和 Main 的 trace 冲突
        String workerTrace = "off".equalsIgnoreCase(tracePath) ? "off" : "target/worker-trace.jsonl";
        try (TraceWriter trace = "off".equalsIgnoreCase(workerTrace)
                ? null
                : new TraceWriter(workerTrace)) {
            WorkerAgent worker = new WorkerAgent("worker-1", llm, tools, trace, maxSteps, maxCost, memory);
            Orchestrator orch = new Orchestrator(worker);
            orch.start();
            try {
                String subGoal = taskGoal != null ? taskGoal : goal;
                Message.Task task = new Message.Task(subGoal, java.util.Map.of());
                Message.Result result = orch.submitAndWait(task, workerTimeoutMs);

                System.out.println();
                System.out.println("=== Orchestrator 收到 / Orchestrator received ===");
                System.out.println("Task correlationId: " + task.correlationId());
                System.out.println("Task goal:          " + task.goal());
                System.out.println();
                System.out.println("=== Worker 回答 / Worker's answer ===");
                System.out.println(result.finalAnswer());
                System.out.println("====================================");
                System.out.println("统计 / Stats: steps=" + result.totalSteps() + ", cost=$" + result.totalCostUsd());
                if (!"off".equalsIgnoreCase(workerTrace)) {
                    System.out.println("Worker trace: " + Path.of(workerTrace).toAbsolutePath());
                }
            } finally {
                orch.stop();
            }
        }
    }

    /**
     * Day 12: 根据 --safe-mode flag 包装 LlmClient.
     * safe-mode=true (默认) → ResilientLlmClient (CircuitBreaker + Retry + TimeLimiter)
     * safe-mode=false → 原始 LlmClient (向后兼容 Day 1-11 行为)
     * @param metrics Day 11 MetricsCollector, 注入到 ResilientLlmClient 让 CB/Retry/TL 状态暴露
     */
    private LlmClient createLlmClient(LlmConfig config, com.agentbootcamp.metrics.MetricsCollector metrics) {
        LlmClient raw = new LlmClient(config);
        if (safeMode) {
            log("Safe mode: ENABLED (Resilience4j: CircuitBreaker 50% + Retry 1s/2s/4s + TimeLimiter 10s, metrics=Day11)");
            return new com.agentbootcamp.safety.ResilientLlmClient(raw, metrics);
        } else {
            log("Safe mode: DISABLED (raw LlmClient, no resilience)");
            return raw;
        }
    }

    /** 构造 6 工具列表(Day 4) / Build the 6-tool list */
    private List<Tool> buildTools(RagIndex ragIndex) {
        return List.of(
            new GetCurrentTime(),
            new ReadFile(),
            new WriteFile(),
            new Grep(),
            new Exec(),
            new SearchKb(ragIndex)   // Day 4 新增
        );
    }

    /**
     * 找知识库目录 / Find the knowledge base directory.
     * 优先级 / Priority:
     *   1. classpath:/knowledge/  (生产: mvn package 后 target/classes/knowledge/)
     *   2. src/main/resources/knowledge/  (开发模式)
     *   3. target/classes/knowledge/  (mvn compile 后)
     *   4. knowledge/  (项目根)
     *   5. null → RagIndex 兜底为空索引
     */
    private static Path findKnowledgeDir() {
        // 1. classpath
        try {
            URL url = Main.class.getResource("/knowledge/");
            if (url != null && "file".equals(url.getProtocol())) {
                Path p = Path.of(url.toURI());
                if (Files.isDirectory(p)) return p;
            }
        } catch (URISyntaxException | IllegalArgumentException e) {
            // 忽略,继续兜底
        }
        // 2-4. 文件系统候选
        Path[] candidates = {
            Path.of("src/main/resources/knowledge"),
            Path.of("target/classes/knowledge"),
            Path.of("knowledge")
        };
        for (Path p : candidates) {
            if (Files.isDirectory(p)) return p;
        }
        return null;
    }

    private static void log(String msg) {
        System.out.println("[main] " + msg);
    }
}
