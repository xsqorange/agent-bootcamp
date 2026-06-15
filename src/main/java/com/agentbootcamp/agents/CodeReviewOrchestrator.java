package com.agentbootcamp.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Day 10: Code Review Orchestrator — 3 步流水线编排
 *
 * 中文:研究 → 评审 → 修复 的 3 步 PR review 流程。每个 worker 用 1 个独立 Orchestrator,
 *      这样 task 一定能路由到指定 worker (Day 8 Orchestrator 是"任一 worker 抢"模式)。
 *
 * English: 3-step pipeline: research → critic → edit.
 *          Each step uses 1 dedicated Orchestrator (1 worker : 1 Orchestrator)
 *          so the task is always routed to the correct worker
 *          (Day 8 Orchestrator uses "any worker grabs" model).
 *
 * 设计要点 / Design points:
 *   - 3 worker × 3 Orchestrator = 3 独立 (inbox, outbox) 对
 *   - 串行 3 步: step 2 等 step 1, step 3 等 step 2
 *   - review 流程的 goal 模板: 给 step 1 完整 prompt, 后续 step 拼上一步 finalAnswer
 *   - ReviewResult record 把 3 步结果 + 总成本打包
 *
 * Java 锚定 / Java anchor:
 *   - 每个 Orchestrator 自己启线程 (start() 内 new Thread per worker)
 *   - 3 个 Orchestrator 共 3 个 worker 线程
 *   - stop() 关闭所有 worker 线程,释放资源
 */
public class CodeReviewOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(CodeReviewOrchestrator.class);

    private final ResearcherAgent researcher;
    private final CriticAgent critic;
    private final EditorAgent editor;
    private final Orchestrator orchR, orchC, orchE;

    public CodeReviewOrchestrator(ResearcherAgent r, CriticAgent c, EditorAgent e) {
        this.researcher = r;
        this.critic = c;
        this.editor = e;
        this.orchR = new Orchestrator(r);
        this.orchC = new Orchestrator(c);
        this.orchE = new Orchestrator(e);
    }

    /**
     * 跑完整 review 流程。3 步串行,每步有独立 timeout。
     * @param reviewGoal 给 Researcher 的原始目标(如 "review src/main/java/.../Agent.java")
     * @param perStepTimeoutMs 每步 timeout
     */
    public ReviewResult review(String reviewGoal, long perStepTimeoutMs) throws Exception {
        log.info("=== Code Review 启动 ===");
        orchR.start();
        orchC.start();
        orchE.start();
        try {
            // === Step 1: Researcher 收集事实 ===
            String step1Goal = "读以下相关文件 + git log,然后总结变化:\n---\n" + reviewGoal
                + "\n---\n只列事实,不给建议。";
            var task1 = new Message.Task(UUID.randomUUID().toString(), step1Goal, Map.of());
            log.info("[step 1/3] Researcher 派发 (goal={} 字符)", step1Goal.length());
            var r1 = orchR.submitAndWait(task1, perStepTimeoutMs);
            log.info("[step 1/3] ✅ done steps={} cost=${}", r1.totalSteps(), r1.totalCostUsd());

            // === Step 2: Critic 基于事实找 bug ===
            String step2Goal = "Researcher 的事实总结:\n---\n" + r1.finalAnswer()
                + "\n---\n基于以上事实找 bug,严格按你 system prompt 格式输出 (Bug 列表 + 严重度统计 + 总结)。";
            var task2 = new Message.Task(UUID.randomUUID().toString(), step2Goal, Map.of());
            log.info("[step 2/3] Critic 派发 (基于 researcher 总结, {} 字符)", r1.finalAnswer().length());
            var r2 = orchC.submitAndWait(task2, perStepTimeoutMs);
            log.info("[step 2/3] ✅ done steps={} cost=${}", r2.totalSteps(), r2.totalCostUsd());

            // === Step 3: Editor 基于 bug 列表修代码 ===
            String step3Goal = "Researcher 事实:\n" + r1.finalAnswer()
                + "\n\nCritic bug 列表:\n" + r2.finalAnswer()
                + "\n\n按 bug 列表用 edit_file 修代码,只改 Critic 指出的,别多改。";
            var task3 = new Message.Task(UUID.randomUUID().toString(), step3Goal, Map.of());
            log.info("[step 3/3] Editor 派发 (基于 critic 列表, {} 字符)", r2.finalAnswer().length());
            var r3 = orchE.submitAndWait(task3, perStepTimeoutMs);
            log.info("[step 3/3] ✅ done steps={} cost=${}", r3.totalSteps(), r3.totalCostUsd());

            double totalCost = r1.totalCostUsd() + r2.totalCostUsd() + r3.totalCostUsd();
            int totalSteps = r1.totalSteps() + r2.totalSteps() + r3.totalSteps();
            log.info("=== Code Review 完成: totalSteps={}, totalCost=${} ===", totalSteps, totalCost);

            return new ReviewResult(
                r1.finalAnswer(),
                r2.finalAnswer(),
                r3.finalAnswer(),
                totalSteps,
                totalCost
            );
        } finally {
            orchR.stop();
            orchC.stop();
            orchE.stop();
        }
    }

    /** 暴露 3 个 worker 名 (测试断言用). */
    public String researcherName() { return researcher.name(); }
    public String criticName() { return critic.name(); }
    public String editorName() { return editor.name(); }

    /**
     * Review 全流程结果。
     * @param researcherSummary Researcher 收集的事实 (1+ 段)
     * @param criticBugs Critic 找的 bug 列表 (Markdown)
     * @param editorChanges Editor 改的代码 (edit_file 调用记录 + 总结)
     * @param totalSteps 3 worker 累计步数
     * @param totalCostUsd 3 worker 累计成本
     */
    public record ReviewResult(
        String researcherSummary,
        String criticBugs,
        String editorChanges,
        int totalSteps,
        double totalCostUsd
    ) {}
}
