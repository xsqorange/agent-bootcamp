package com.agentbootcamp.evals;

import com.agentbootcamp.Agent;
import com.agentbootcamp.AgentStep;
import com.agentbootcamp.LlmClient;
import com.agentbootcamp.LlmConfig;
import com.agentbootcamp.MemoryManager;
import com.agentbootcamp.RagIndex;
import com.agentbootcamp.RunResult;
import com.agentbootcamp.StopReason;
import com.agentbootcamp.Tool;
import com.agentbootcamp.TraceWriter;
import com.agentbootcamp.tools.Exec;
import com.agentbootcamp.tools.GetCurrentTime;
import com.agentbootcamp.tools.Grep;
import com.agentbootcamp.tools.ReadFile;
import com.agentbootcamp.tools.SearchKb;
import com.agentbootcamp.tools.WriteFile;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 评测驱动 / Eval driver — Day 5
 *
 * 跑一个 EvalCase:
 *   1. 启 Agent,tools 全集(6 工具)+ 可选 Memory
 *   2. 4 条断言: must_call_tools / must_contain_in_final / max_steps / max_cost_usd
 *   3. (可选) post_check 验证文件副作用
 *   4. trace 写到 evals/reports/&lt;id&gt;.jsonl(每步 1 行)
 *
 * 失败时 **不抛异常**,而是把错误装进 EvalResult.failureReason() —
 * 让 EvalRunnerTest 能在一次 run 里跑完 10 个 case 然后一起报。
 */
public class EvalHarness {

    private final LlmClient llm;
    // 注意: tools 不保存为 field,每次 runCase 重新建一份(每个 case 隔离,避免 RAG 索引复用)
    private final Path workDir;
    private final Path reportsDir;
    private final Path knowledgeDir;

    /**
     * @param llm         共享 LLM 客户端(每个 case 都建一个新 Agent,复用同一个 llm)
     * @param workDir     Agent 工作目录(默认 cwd,测试时可用 @TempDir)
     * @param reportsDir  trace 输出目录(默认 evals/reports/)
     * @param knowledgeDir Day 4 知识库目录(null = 不加载)
     */
    public EvalHarness(LlmClient llm, Path workDir, Path reportsDir, Path knowledgeDir) {
        this.llm = llm;
        this.workDir = workDir;
        this.reportsDir = reportsDir;
        this.knowledgeDir = knowledgeDir;
    }

    /** 跑一个 EvalCase,返回结果 / Run one EvalCase, return EvalResult */
    public EvalResult runCase(EvalCase c) {
        Exception lastException = null;
        // 最多重试 2 次(应对 LLM 429 rate limit),用指数退避 5s / 15s
        // 2 retries max (for LLM 429), exponential backoff 5s / 15s
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (attempt > 0) {
                    long waitSec = (long) Math.pow(3, attempt) * 5;  // 5s, 15s
                    System.out.println("[EvalHarness] " + c.id() + " 重试 #" + attempt
                        + ",等 " + waitSec + "s...");
                    Thread.sleep(waitSec * 1000);
                }
                return runCaseOnce(c);
            } catch (Exception e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                // 只对 429 rate limit 重试,其他错误直接 fail
                if (!msg.contains("429") && !msg.toLowerCase().contains("rate limit")) {
                    return EvalResult.failed(c.id(), "EXCEPTION: " + e.getClass().getSimpleName()
                        + ": " + e.getMessage(), null, 0, 0.0, List.of());
                }
            }
        }
        return EvalResult.failed(c.id(), "EXCEPTION (3 attempts): "
            + (lastException != null ? lastException.getClass().getSimpleName()
                + ": " + lastException.getMessage() : "unknown"),
            null, 0, 0.0, List.of());
    }

    /** 跑一次的实现(被 runCase 加 retry 包装)/ Single attempt (wrapped by runCase with retry) */
    private EvalResult runCaseOnce(EvalCase c) throws Exception {
        // 1. 准备 trace 路径 evals/reports/<id>.jsonl
        Files.createDirectories(reportsDir);
        Path tracePath = reportsDir.resolve(c.id() + ".jsonl");
        // 清空旧 trace(每次重跑覆盖)
        Files.deleteIfExists(tracePath);

        // 2. 准备 RAG 索引(每个 case 都建一个,内存便宜)
        RagIndex ragIndex = new RagIndex(knowledgeDir);

        // 3. 准备工具列表(6 个,跟 AgentTest 保持一致)
        List<Tool> tools = List.of(
            new GetCurrentTime(),
            new ReadFile(),
            new WriteFile(workDir),
            new Grep(workDir),
            new Exec(),
            new SearchKb(ragIndex)
        );

        // 4. 启 Agent + trace
        EvalCase.Expected exp = c.expected();
        try (TraceWriter trace = new TraceWriter(tracePath.toString())) {
            Agent agent = new Agent(llm, tools, trace,
                exp.maxSteps(), exp.maxCostUsd(),
                new MemoryManager());   // Day 5: 评测默认启用 memory

            // 5. 跑
            RunResult result = agent.run(c.prompt());

            // 6. 收集实际调用的工具集(从 trace 解析)
            List<String> calledTools = collectCalledTools(tracePath);

            // 7. 4 条断言 + 可选 post_check
            return evaluate(c, result, calledTools);
        }
    }

    // ====================================================================
    // 4 条断言 + post_check
    // ====================================================================

    private EvalResult evaluate(EvalCase c, RunResult result, List<String> calledTools) {
        List<String> failures = new ArrayList<>();
        EvalCase.Expected exp = c.expected();

        // 断言 1: must_call_tools ⊆ calledTools
        for (String required : exp.mustCallTools()) {
            if (!calledTools.contains(required)) {
                failures.add("MUST_CALL_TOOLS missing: " + required
                    + " (actual: " + calledTools + ")");
            }
        }

        // 断言 2: must_contain_in_final ⊆ result.finalAnswer() (case-insensitive)
        String answerLower = result.finalAnswer() != null
            ? result.finalAnswer().toLowerCase() : "";
        for (String phrase : exp.mustContainInFinal()) {
            if (!answerLower.contains(phrase.toLowerCase())) {
                failures.add("MUST_CONTAIN_IN_FINAL missing: '" + phrase
                    + "' (answer: '" + truncate(result.finalAnswer(), 80) + "')");
            }
        }

        // 断言 3: totalSteps <= max_steps
        if (result.totalSteps() > exp.maxSteps()) {
            failures.add("MAX_STEPS exceeded: " + result.totalSteps() + " > " + exp.maxSteps());
        }

        // 断言 4: totalCostUsd <= max_cost_usd
        if (result.totalCostUsd() > exp.maxCostUsd()) {
            failures.add("MAX_COST_USD exceeded: $" + String.format("%.6f", result.totalCostUsd())
                + " > $" + String.format("%.6f", exp.maxCostUsd()));
        }

        // 断言 5 (可选): post_check
        if (c.postCheck() != null && c.postCheck().type() != null) {
            String postFailure = checkPost(c.postCheck());
            if (postFailure != null) failures.add("POST_CHECK: " + postFailure);
        }

        if (failures.isEmpty()) {
            return EvalResult.passed(c.id(), result, calledTools);
        } else {
            return EvalResult.failed(c.id(), String.join(" | ", failures),
                result.stopReason(), result.totalSteps(),
                result.totalCostUsd(), calledTools);
        }
    }

    /**
     * 验证副作用 / Verify side effect
     * 返回 null = 通过,非 null = 失败原因
     */
    private String checkPost(EvalCase.PostCheck pc) {
        try {
            Path p = workDir.resolve(pc.path());
            switch (pc.type()) {
                case "file_exists" -> {
                    if (!Files.exists(p)) return "file does not exist: " + p;
                    return null;
                }
                case "file_equals" -> {
                    if (!Files.exists(p)) return "file does not exist: " + p;
                    String actual = Files.readString(p).trim();
                    String expected = pc.content() != null ? pc.content().trim() : "";
                    if (!actual.equals(expected)) {
                        return "file content mismatch: expected='" + truncate(expected, 60)
                            + "', actual='" + truncate(actual, 60) + "'";
                    }
                    return null;
                }
                case "file_contains" -> {
                    if (!Files.exists(p)) return "file does not exist: " + p;
                    String actual = Files.readString(p);
                    if (!actual.contains(pc.content())) {
                        return "file does not contain '" + pc.content() + "': "
                            + truncate(actual, 80);
                    }
                    return null;
                }
                default -> {
                    return "unknown post_check type: " + pc.type();
                }
            }
        } catch (Exception e) {
            return "post_check exception: " + e.getMessage();
        }
    }

    /**
     * 从 trace.jsonl 解析实际调用的工具名集合
     * Parse actual called tool names from trace.jsonl
     *
     * Day 5 取巧:每行 JSON 有 "executions" 数组,每个 execution 有 "name" 字段。
     * 用 Jackson 直接读,不引新依赖。
     */
    private List<String> collectCalledTools(Path tracePath) {
        List<String> tools = new ArrayList<>();
        try {
            if (!Files.exists(tracePath)) return tools;
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            for (String line : Files.readAllLines(tracePath)) {
                if (line.isBlank()) continue;
                try {
                    var node = mapper.readTree(line);
                    var execs = node.path("executions");
                    if (execs.isArray()) {
                        for (var exec : execs) {
                            String name = exec.path("name").asText(null);
                            if (name != null && !name.isBlank()) tools.add(name);
                        }
                    }
                } catch (Exception ignore) {
                    // 单行解析失败不挂整体
                }
            }
        } catch (Exception e) {
            // trace 不存在或读失败 → 返回空,断言会失败,信息已经在 MUST_CALL_TOOLS 里
        }
        return tools.stream().distinct().collect(Collectors.toList());
    }

    // ====================================================================
    // 便捷工厂 / Convenience factories
    // ====================================================================

    /** 默认 reports 目录 = evals/reports / Default reports dir */
    public static Path defaultReportsDir() {
        return Path.of("evals/reports");
    }

    /** 找知识库目录(从 Main.java / AgentTest 复制)/ Find knowledge dir (cloned from Main/AgentTest) */
    public static Path findKnowledgeDir() {
        // 1. classpath
        try {
            URL url = EvalHarness.class.getResource("/knowledge/");
            if (url != null && "file".equals(url.getProtocol())) {
                Path p = Path.of(url.toURI());
                if (Files.isDirectory(p)) return p;
            }
        } catch (URISyntaxException | IllegalArgumentException e) {
            // fall through
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

    private static String truncate(String s, int max) {
        if (s == null) return "(null)";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
