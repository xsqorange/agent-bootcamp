package com.agentbootcamp;

import com.agentbootcamp.tools.Exec;
import com.agentbootcamp.tools.GetCurrentTime;
import com.agentbootcamp.tools.ReadFile;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI 入口 — picocli / CLI entry — picocli
 *
 * Day 2 新增 / Day 2 new flags:
 *   --max-steps <N>     最大步数(默认 10)  / max steps (default 10)
 *   --trace <path>      trace.jsonl 路径(默认 target/trace.jsonl) / trace file path
 *   --trace off         禁用 trace(便于调试)/ disable trace
 *
 * 用法 / Usage:
 *   ./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '...'"
 *   ./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '...' --max-steps 3 --trace trace.jsonl"
 *   java -jar target/agent-bootcamp.jar --goal "..." --max-steps 5
 */
@Command(
    name = "myagent",
    mixinStandardHelpOptions = true,
    version = "agent-bootcamp 0.2.0",
    description = "Day 2: ReAct 循环 + StopReason + JSONL trace / ReAct loop with stop reasons and trace."
)
public class Main implements Runnable {

    @Option(names = {"-g", "--goal"},
        description = "用户目标(必填)/ User goal (required)",
        required = true)
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

    @Option(names = {"--dry-run"},
        description = "只打印计划,不调 LLM / Print plan only, skip LLM call",
        defaultValue = "false")
    private boolean dryRun;

    public static void main(String[] args) {
        int rc = new CommandLine(new Main()).execute(args);
        System.exit(rc);
    }

    @Override
    public void run() {
        try {
            log("目标 / Goal: " + goal);
            log("参数 / Args: maxSteps=" + maxSteps + ", maxCost=$" + maxCost + ", trace=" + tracePath);

            if (dryRun) {
                log("DRY RUN — 不调 LLM,只展示工具列表");
                List<Tool> tools = List.of(new GetCurrentTime(), new ReadFile(), new Exec());
                log("已注册 " + tools.size() + " 个工具: " + tools.stream().map(Tool::name).toList());
                log("Agent 配置: maxSteps=" + maxSteps + ", maxCost=$" + maxCost + ", trace=" + tracePath);
                log("(用 --max-steps, --max-cost, --trace 调参;用 --goal 跑真的)");
                return;
            }

            // 1. LLM 配置 / read LLM config from env
            LlmConfig config = LlmConfig.fromEnv();

            // 2. LLM 客户端 / build LLM client
            LlmClient llm = new LlmClient(config);

            // 3. 工具 / register tools
            List<Tool> tools = List.of(
                new GetCurrentTime(),
                new ReadFile(),
                new Exec()
            );
            log("已注册 " + tools.size() + " 个工具: " +
                tools.stream().map(Tool::name).toList());

            // 4. Trace(用 try-with-resources 自动 close)
            try (TraceWriter trace = new TraceWriter(tracePath)) {
                Agent agent = new Agent(llm, tools, trace, maxSteps, maxCost);

                // 5. 跑 / run
                RunResult result = agent.run(goal);

                // 6. 输出 / output
                System.out.println();
                System.out.println("=== Agent 回答 / Agent's answer ===");
                System.out.println(result.finalAnswer());
                System.out.println("====================================");
                System.out.println("统计 / Stats: " + result.summary());
                if (!"off".equalsIgnoreCase(tracePath)) {
                    System.out.println("Trace:  " + Path.of(tracePath).toAbsolutePath());
                }
            }
        } catch (Exception e) {
            System.err.println("执行失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void log(String msg) {
        System.out.println("[main] " + msg);
    }
}
