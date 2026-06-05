package com.agentbootcamp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent — ReAct 循环骨架 / ReAct loop skeleton
 *
 * Day 1 版本:单次 LLM 调用(不循环)/ Day 1: single LLM call (no loop)
 * Day 2 会改成 while 循环,加 StopReason 和预算控制 / Day 2 will turn into a while loop with StopReason + budget
 *
 * Java 锚定 / Java anchor: ReAct 就是一个 while 循环 + switch
 */
public class Agent {
    private static final Logger log = LoggerFactory.getLogger(Agent.class);

    private final LlmClient llm;
    private final Map<String, Tool> tools;

    public Agent(LlmClient llm, List<Tool> tools) {
        this.llm = llm;
        this.tools = tools.stream().collect(Collectors.toMap(Tool::name, t -> t));
        log.info("Agent 初始化: {} 个工具 = {}", tools.size(), this.tools.keySet());
    }

    /**
     * 单次运行(Day 1 验收用)/ Single run (Day 1 acceptance test)
     *
     * Day 2 起会变成这样 / Day 2 will turn this into:
     * <pre>
     * for (int step = 0; step < maxSteps; step++) {
     *     var resp = llm.chat(messages, toolSchemas());
     *     if (resp.hasToolCall()) { ... 执行工具,追加消息 ... }
     *     else return resp.content();
     * }
     * </pre>
     */
    public String runOnce(String userGoal) throws Exception {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(systemPrompt()));
        messages.add(Message.user(userGoal));

        // 第一次 LLM 调用 / first LLM call
        var resp = llm.chat(messages, toolSchemas());
        log.info("LLM 回复: content='{}', toolCalls={}, tokens_in={}, tokens_out={}",
            truncate(resp.content(), 60),
            resp.hasToolCall() ? resp.toolCalls().size() : 0,
            resp.tokensIn(), resp.tokensOut());

        if (!resp.hasToolCall()) {
            return resp.content();
        }

        // Day 1 简化:只执行第一个 tool call,然后第二次 LLM 调用得最终回答
        // Day 1 simplify: execute the first tool call, then second LLM call for final answer
        var tc = resp.toolCalls().get(0);
        log.info("→ 工具调用: {} (id={})", tc.function().name(), tc.id());

        Tool tool = tools.get(tc.function().name());
        if (tool == null) {
            return "[错误] 模型调了未注册的工具: " + tc.function().name();
        }

        Map<String, Object> args = parseArgs(tc.function().arguments());
        String observation;
        try {
            observation = tool.execute(args);
        } catch (Exception e) {
            observation = "[工具执行失败: " + e.getMessage() + "]";
            log.warn("工具执行失败: {}", e.getMessage());
        }
        log.info("← 工具结果: {}", truncate(observation, 100));

        // 把工具调用 + 结果追加到消息,再调一次 LLM
        // Append the tool call + result to messages, call LLM again
        messages.add(Message.assistantWithToolCalls(resp.content(), resp.toolCalls()));
        messages.add(Message.toolResult(tc.id(), observation));

        var finalResp = llm.chat(messages, toolSchemas());
        log.info("最终回复 tokens_in={}, tokens_out={}", finalResp.tokensIn(), finalResp.tokensOut());
        return finalResp.content();
    }

    private String systemPrompt() {
        String toolList = tools.values().stream()
            .map(t -> "- " + t.name() + ": " + t.description())
            .collect(Collectors.joining("\n"));
        return """
            你是一个简洁的编程助手,可以使用以下工具:
            %s

            回答要简短直接,需要时调用工具。完成后给出最终答案。
            """.formatted(toolList);
    }

    /** 把 Tool 列表转成 OpenAI tools[] 格式 / convert Tool list to OpenAI tools[] format */
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
        // 这里依赖 LlmClient 的 JSON,但 import static 不行因为是 private
    }

    private String truncate(String s, int max) {
        if (s == null) return "(null)";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
