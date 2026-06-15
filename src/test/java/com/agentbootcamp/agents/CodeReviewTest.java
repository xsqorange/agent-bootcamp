package com.agentbootcamp.agents;

import com.agentbootcamp.LlmClient;
import com.agentbootcamp.LlmConfig;
import com.agentbootcamp.TraceWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Day 10: 3 Agent 团队 code review E2E 测试
 *
 * 中文:3 个 E2E 用真 LLM 跑通 3 Agent 协作流程。
 *      - test_researcher_reads_file: 单 worker 跑,验证 Researcher 能读 README
 *      - test_critic_no_tool_call: 单 worker 跑,验证 Critic 纯推理(没用工具)
 *      - test_full_review_pipeline: 3 步流水线完整跑通
 *
 * English: 3 E2E with real LLM:
 *   1. test_researcher_reads_file: Researcher reads README
 *   2. test_critic_no_tool_call: Critic gives analysis without tool calls
 *   3. test_full_review_pipeline: full 3-step pipeline (research → critic → edit)
 *
 * 守门:跟 Day 5 同款,没 MINIMAX_API_KEY 时 skip (不挂 CI)
 */
@EnabledIfEnvironmentVariable(named = "MINIMAX_API_KEY", matches = ".+")
class CodeReviewTest {

    private static LlmClient llm;
    private static TraceWriter trace;
    private static Path workDir;

    @BeforeAll
    static void setup(@TempDir Path tmp) throws Exception {
        llm = new LlmClient(LlmConfig.fromEnv());
        trace = new TraceWriter("target/trace-code-review.jsonl");
        workDir = tmp;
    }

    @Test
    void test_researcher_reads_file() throws Exception {
        ResearcherAgent r = new ResearcherAgent(llm, trace, workDir);
        Orchestrator orch = new Orchestrator(r);
        orch.start();
        try {
            var task = new com.agentbootcamp.agents.Message.Task(
                java.util.UUID.randomUUID().toString(),
                "用 read_file 读 README.md 的前 200 字符,告诉我内容",
                java.util.Map.of()
            );
            var result = orch.submitAndWait(task, 60_000);
            assertNotNull(result.finalAnswer());
            assertTrue(result.finalAnswer().length() > 0);
            System.out.println("[Researcher E2E] steps=" + result.totalSteps() + " cost=$" + result.totalCostUsd());
            System.out.println("[Researcher E2E] answer=" + result.finalAnswer().substring(0, Math.min(200, result.finalAnswer().length())));
        } finally {
            orch.stop();
        }
    }

    @Test
    void test_critic_no_tool_call() throws Exception {
        // Critic 拿 Researcher 的事实总结,做纯推理
        CriticAgent c = new CriticAgent(llm, trace);
        Orchestrator orch = new Orchestrator(c);
        orch.start();
        try {
            var task = new com.agentbootcamp.agents.Message.Task(
                java.util.UUID.randomUUID().toString(),
                "事实: README.md 第 1 行是 '# Agent Bootcamp'。这是一个标题 bug:标题应该简洁,但用了 'Bootcamp' 双语混合。\n"
                    + "另一个事实: README.md 提到 'openjdk version 17' 但 LLM 实际是 MiniMax-M3,版本字符串不一致。\n"
                    + "基于以上事实找 bug。",
                java.util.Map.of()
            );
            var result = orch.submitAndWait(task, 60_000);
            assertNotNull(result.finalAnswer());
            // Critic 应该提到 bug 列表(可能含 HIGH/MEDIUM)
            String answer = result.finalAnswer().toLowerCase();
            System.out.println("[Critic E2E] steps=" + result.totalSteps() + " cost=$" + result.totalCostUsd());
            System.out.println("[Critic E2E] answer=" + result.finalAnswer().substring(0, Math.min(400, result.finalAnswer().length())));
        } finally {
            orch.stop();
        }
    }

    @Test
    void test_full_review_pipeline() throws Exception {
        // 3 worker + CodeReviewOrchestrator 完整流水线
        ResearcherAgent r = new ResearcherAgent(llm, trace, workDir);
        CriticAgent c = new CriticAgent(llm, trace);
        EditorAgent e = new EditorAgent(llm, trace, workDir);
        CodeReviewOrchestrator orch = new CodeReviewOrchestrator(r, c, e);

        // 给个简单 review goal
        String reviewGoal = "review README.md 的前 50 行,看 introduction 段落是否清晰";
        var reviewResult = orch.review(reviewGoal, 60_000);

        // 验证 3 步 finalAnswer 都非空
        assertNotNull(reviewResult.researcherSummary());
        assertFalse(reviewResult.researcherSummary().isEmpty(), "Researcher summary 空");
        assertNotNull(reviewResult.criticBugs());
        assertFalse(reviewResult.criticBugs().isEmpty(), "Critic bugs 空");
        assertNotNull(reviewResult.editorChanges());
        assertFalse(reviewResult.editorChanges().isEmpty(), "Editor changes 空");

        System.out.println("[Pipeline E2E] totalSteps=" + reviewResult.totalSteps()
            + " totalCost=$" + reviewResult.totalCostUsd());
        System.out.println("[Pipeline E2E] researcher=" + reviewResult.researcherSummary().substring(0, Math.min(100, reviewResult.researcherSummary().length())) + "...");
        System.out.println("[Pipeline E2E] critic=" + reviewResult.criticBugs().substring(0, Math.min(100, reviewResult.criticBugs().length())) + "...");
        System.out.println("[Pipeline E2E] editor=" + reviewResult.editorChanges().substring(0, Math.min(100, reviewResult.editorChanges().length())) + "...");
    }
}
