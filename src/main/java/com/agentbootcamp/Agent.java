package com.agentbootcamp;

import com.agentbootcamp.LlmClient.LlmResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent — ReAct 循环 / ReAct loop (Day 2)
 *
 * 心智模型 / Mental model:
 *   while (没停) {
 *       思考 = LLM(messages, tools)        // 模型决策
 *       if (没工具调用) 返回思考.content   // 最终答案
 *       观察 = 工具们(思考.toolCalls)     // 执行动作
 *       把观察塞回 messages                // 喂给下一步模型
 *   }
 *
 * 关键变化(Day 1 → Day 2):
 *  - runOnce()  → run() 返回 RunResult(成本/步数/停止原因)
 *  - 加了 StopReason 终止条件
 *  - 加了 TraceWriter,每步写 trace.jsonl
 *  - 支持一个 step 调多个工具(parallel tool calls)
 *  - 加了 maxSteps / maxCost 双重护栏
 *
 * Java 锚定 / Java anchor:
 *   Agent = 状态机(messages)+ while 循环 + switch(stopReason)
 */
public class Agent {
    private static final Logger log = LoggerFactory.getLogger(Agent.class);

    private final LlmClient llm;
    private final Map<String, Tool> tools;
    private final TraceWriter trace;       // 可为 null
    private final int maxSteps;
    private final double maxCostUsd;
    private final MemoryManager memory;    // Day 4 新增, 可为 null

    /**
     * 便捷构造器(5 参) — 不带 memory / Convenience constructor (5-param) — no memory.
     * 向后兼容 Day 1-3 的测试。
     */
    public Agent(LlmClient llm, List<Tool> tools, TraceWriter trace,
                 int maxSteps, double maxCostUsd) {
        this(llm, tools, trace, maxSteps, maxCostUsd, null);
    }

    /**
     * 完整构造器(6 参)— Day 4 / Full constructor (6-param) — Day 4.
     *
     * @param memory MemoryManager 实例, null = 不压缩
     */
    public Agent(LlmClient llm, List<Tool> tools, TraceWriter trace,
                 int maxSteps, double maxCostUsd, MemoryManager memory) {
        this.llm = llm;
        this.tools = tools.stream().collect(Collectors.toMap(Tool::name, t -> t));
        this.trace = trace;
        this.maxSteps = maxSteps;
        this.maxCostUsd = maxCostUsd;
        this.memory = memory;
        log.info("Agent 初始化: tools={}, maxSteps={}, maxCost=${}, memory={}",
            this.tools.keySet(), maxSteps, maxCostUsd,
            memory != null ? "enabled" : "disabled");
    }

    /**
     * 完整 ReAct 循环 / Full ReAct loop
     *
     * 终止条件(任一):
     *  - 模型返回纯文本(FINAL_ANSWER)
     *  - step >= maxSteps (MAX_STEPS)
     *  - 累计成本 > maxCostUsd (COST_LIMIT)
     */
    public RunResult run(String userGoal) throws Exception {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(systemPrompt()));
        messages.add(Message.user(userGoal));

        int step = 0;
        int totalTokensIn = 0;
        int totalTokensOut = 0;
        double totalCostUsd = 0.0;
        StopReason stopReason = null;
        String finalAnswer = null;

        while (step < maxSteps) {
            step++;
            log.info("=== Step {}/{} ===", step, maxSteps);

            // 0. Day 4: 检查是否需要压缩 messages(防止 context 爆)
            //    触发条件: messages 数 > 24 OR totalTokensIn > 10000
            if (memory != null && memory.shouldCompress(messages.size(), totalTokensIn)) {
                log.info("Memory 压缩触发: messages={}, tokens={}", messages.size(), totalTokensIn);
                messages = memory.compress(messages, llm);
            }

            // 1. 思考:调 LLM
            LlmClient.LlmResponse resp = llm.chat(messages, toolSchemas());

            int tokensIn  = resp.tokensIn()  != null ? resp.tokensIn()  : 0;
            int tokensOut = resp.tokensOut() != null ? resp.tokensOut() : 0;
            totalTokensIn  += tokensIn;
            totalTokensOut += tokensOut;
            totalCostUsd   += estimateCost(tokensIn, tokensOut);

            log.info("LLM 回复: content='{}', toolCalls={}, tokens_in={}, tokens_out={}, cost=${}",
                truncate(resp.content(), 50),
                resp.hasToolCall() ? resp.toolCalls().size() : 0,
                tokensIn, tokensOut, String.format("%.6f", totalCostUsd));

            // 2. 检查:是否最终答案
            if (!resp.hasToolCall()) {
                stopReason = StopReason.FINAL_ANSWER;
                finalAnswer = resp.content();
                writeTrace(step, userGoal, resp, null, null,
                    tokensIn, tokensOut, totalTokensIn, totalTokensOut, totalCostUsd,
                    stopReason, finalAnswer);
                break;
            }

            // 3. 行动:执行所有工具调用(支持并行)
            List<AgentStep.ToolExecutionRecord> executions = new ArrayList<>();
            List<AgentStep.ToolCallRecord> callRecords = new ArrayList<>();

            for (Message.ToolCall tc : resp.toolCalls()) {
                callRecords.add(new AgentStep.ToolCallRecord(
                    tc.id(), tc.function().name(), tc.function().arguments()));

                AgentStep.ToolExecutionRecord exec = executeOneTool(tc);
                executions.add(exec);
                log.info("  → {} (id={}, ok={}, {}ms): {}",
                    tc.function().name(), tc.id(), exec.ok(), exec.durationMs(),
                    truncate(exec.result(), 60));
            }

            // 4. 观察:把 assistant 调用 + 每个工具结果都加进 messages
            messages.add(Message.assistantWithToolCalls(resp.content(), resp.toolCalls()));
            for (AgentStep.ToolExecutionRecord exec : executions) {
                messages.add(Message.toolResult(exec.toolCallId(), exec.result()));
            }

            // 5. 记录这一步
            writeTrace(step, userGoal, resp, callRecords, executions,
                tokensIn, tokensOut, totalTokensIn, totalTokensOut, totalCostUsd,
                null, null);

            // 6. 检查成本护栏
            if (totalCostUsd > maxCostUsd) {
                stopReason = StopReason.COST_LIMIT;
                log.warn("成本超限: ${} > ${}", totalCostUsd, maxCostUsd);
                // 补写最后一步的 trace,带 stopReason(Day 3 修 TC-5 缺最后一笔的 bug)
                writeTrace(step, userGoal, resp, callRecords, executions,
                    tokensIn, tokensOut, totalTokensIn, totalTokensOut, totalCostUsd,
                    stopReason, null);
                break;
            }
        }

        // 走到这说明没 break 出来 → MAX_STEPS
        if (stopReason == null) {
            stopReason = StopReason.MAX_STEPS;
            log.warn("达到最大步数: {}", maxSteps);
            // 补写一行 summary trace,带 stopReason(Day 3 修 TC-5 缺最后一笔的 bug)
            writeSummaryTrace(step + 1, userGoal, totalTokensIn, totalTokensOut, totalCostUsd, stopReason);
        }
        if (finalAnswer == null) {
            finalAnswer = String.format(
                "[Agent stopped by %s after %d steps. cost=$%.6f]",
                stopReason, step, totalCostUsd);
        }

        RunResult result = new RunResult(finalAnswer, stopReason, step,
            totalTokensIn, totalTokensOut, totalCostUsd);
        log.info("=== Done: {} ===", result.summary());
        return result;
    }

    // ===== 内部方法 / Internals =====

    /** 执行一个工具调用 / Execute one tool call */
    private AgentStep.ToolExecutionRecord executeOneTool(Message.ToolCall tc) {
        long start = System.nanoTime();

        // 解析参数(失败也不让循环挂掉)/ parse args (don't let parse failure kill the loop)
        Map<String, Object> args;
        try {
            args = parseArgs(tc.function().arguments());
        } catch (Exception e) {
            long dur = (System.nanoTime() - start) / 1_000_000;
            log.warn("工具参数 JSON 解析失败: {}: {}", tc.function().arguments(), e.getMessage());
            return new AgentStep.ToolExecutionRecord(
                tc.id(), tc.function().name(), Map.of(),
                "[error: invalid args JSON: " + e.getMessage() + "]",
                false, dur, "parse error");
        }

        Tool tool = tools.get(tc.function().name());

        if (tool == null) {
            long dur = (System.nanoTime() - start) / 1_000_000;
            return new AgentStep.ToolExecutionRecord(
                tc.id(), tc.function().name(), args,
                "[error: unknown tool: " + tc.function().name() + "]",
                false, dur, "unknown tool");
        }

        try {
            String result = tool.execute(args);
            long dur = (System.nanoTime() - start) / 1_000_000;
            return new AgentStep.ToolExecutionRecord(
                tc.id(), tc.function().name(), args, result, true, dur, null);
        } catch (Exception e) {
            long dur = (System.nanoTime() - start) / 1_000_000;
            log.warn("工具执行失败: {}: {}", tc.function().name(), e.getMessage());
            return new AgentStep.ToolExecutionRecord(
                tc.id(), tc.function().name(), args,
                "[error: " + e.getMessage() + "]",
                false, dur, e.getMessage());
        }
    }

    /** 估算单步成本(美元)/ Estimate step cost in USD */
    private double estimateCost(int tokensIn, int tokensOut) {
        // 默认按 gpt-4o-mini:$0.15/1M input,$0.60/1M output
        // Override with LLM_COST_INPUT_PER_1M / LLM_COST_OUTPUT_PER_1M
        double inputPer1M  = 0.15;
        double outputPer1M = 0.60;
        String in  = System.getenv("LLM_COST_INPUT_PER_1M");
        String out = System.getenv("LLM_COST_OUTPUT_PER_1M");
        if (in  != null && !in.isBlank())  inputPer1M  = Double.parseDouble(in);
        if (out != null && !out.isBlank()) outputPer1M = Double.parseDouble(out);
        return tokensIn  * inputPer1M  / 1_000_000.0
             + tokensOut * outputPer1M / 1_000_000.0;
    }

    /** 写 trace(trace 为 null 时跳过)/ Write trace (skip if null) */
    private void writeTrace(int step, String goal, LlmClient.LlmResponse resp,
                            List<AgentStep.ToolCallRecord> calls,
                            List<AgentStep.ToolExecutionRecord> execs,
                            int tIn, int tOut, int tInTotal, int tOutTotal,
                            double cost, StopReason sr, String answer) {
        if (trace == null) return;
        try {
            trace.writeStep(new AgentStep(
                step, System.currentTimeMillis(), goal,
                resp.content(),
                calls != null ? calls : List.of(),
                execs != null ? execs : List.of(),
                tIn, tOut, tInTotal, tOutTotal, cost,
                sr, answer
            ));
        } catch (Exception e) {
            log.warn("写 trace 失败: {}", e.getMessage());
        }
    }

    /**
     * 写一行"run 总结"trace(用于 MAX_STEPS / COST_LIMIT 终止时)
     * Write a run-summary trace line (used when run ends due to MAX_STEPS / COST_LIMIT).
     *
     * 这行的语义:不是"一步",而是"印章"——告诉 trace 消费者"这次 run 是因为 X 终止的"
     * Semantics: not a "step", but a "seal" — tells the consumer "this run ended due to X".
     */
    private void writeSummaryTrace(int summaryStep, String goal,
                                   int tInTotal, int tOutTotal,
                                   double costTotal, StopReason sr) {
        if (trace == null) return;
        try {
            trace.writeStep(new AgentStep(
                summaryStep, System.currentTimeMillis(), goal,
                "[reached " + sr + "]",   // llmContent 占位,表示"这是总结行"
                List.of(),                 // 无 tool calls
                List.of(),                 // 无 executions
                0, 0,                      // 本步无 token
                tInTotal, tOutTotal, costTotal,
                sr, null                   // finalAnswer 留给 RunResult
            ));
        } catch (Exception e) {
            log.warn("写 summary trace 失败: {}", e.getMessage());
        }
    }

    protected String systemPrompt() {
        String toolList = tools.values().stream()
            .map(t -> "- " + t.name() + ": " + t.description())
            .collect(Collectors.joining("\n"));
        return """
            你是一个简洁的编程助手,可以使用以下工具:
            %s

            回答要简短直接,需要时调用工具。完成后给出最终答案。
            """.formatted(toolList);
    }

    /** 把 Tool 列表转成 OpenAI tools[] 格式 */
    private List<Map<String, Object>> toolSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (Tool tool : tools.values()) {
            Map<String, Object> fn = new HashMap<>();
            fn.put("name", tool.name());
            fn.put("description", tool.description());
            fn.put("parameters", tool.jsonSchema());
            schemas.add(Map.of("type", "function", "function", fn));
        }
        return schemas;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String json) throws Exception {
        if (json == null || json.isBlank()) return Map.of();
        return LlmClient.JSON.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    private String truncate(String s, int max) {
        if (s == null) return "(null)";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
