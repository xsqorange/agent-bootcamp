package com.agentbootcamp;

import com.agentbootcamp.tools.Exec;
import com.agentbootcamp.tools.GetCurrentTime;
import com.agentbootcamp.tools.ReadFile;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

/**
 * CLI 入口 — picocli / CLI entry — picocli
 *
 * 用法 / Usage:
 *   ./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '...'"
 *   或 / or
 *   java -jar target/agent-bootcamp.jar --goal "..."
 */
@Command(
    name = "myagent",
    mixinStandardHelpOptions = true,
    version = "agent-bootcamp 0.1.0",
    description = "Day 1: 一个能调用工具的 LLM Agent / A single-call LLM agent that can invoke tools."
)
public class Main implements Runnable {

    @Option(names = {"-g", "--goal"},
        description = "用户目标(必填)/ User goal (required)",
        required = true)
    private String goal;

    @Option(names = {"--max-cost"},
        description = "单次最大成本(美元,Day 3 启用)/ Max cost per run in USD (Day 3)",
        defaultValue = "1.0")
    private double maxCost;

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
            log("目标: " + goal);

            if (dryRun) {
                log("DRY RUN — 不调 LLM,只展示工具列表");
                return;
            }

            // 1. 读 LLM 配置 / read LLM config from env
            LlmConfig config = LlmConfig.fromEnv();

            // 2. 建 LLM 客户端 / build LLM client
            LlmClient llm = new LlmClient(config);

            // 3. 注册工具 / register tools (Day 1: 3 个)
            List<Tool> tools = List.of(
                new GetCurrentTime(),
                new ReadFile(),
                new Exec()
            );
            log("已注册 " + tools.size() + " 个工具: " +
                tools.stream().map(Tool::name).toList());

            // 4. 建 Agent 跑一次(Day 2 起改成循环)/ build Agent and run once (Day 2 → loop)
            Agent agent = new Agent(llm, tools);
            String answer = agent.runOnce(goal);

            System.out.println();
            System.out.println("=== Agent 回答 / Agent's answer ===");
            System.out.println(answer);
            System.out.println("=================================");
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
