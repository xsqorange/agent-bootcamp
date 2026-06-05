package com.agentbootcamp;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 消息 — 一次 LLM 调用的对话单元 / A message in the LLM conversation
 *
 * 4 种角色 / 4 roles:
 * - system:  设定 Agent 人格和行为 / sets agent personality
 * - user:    用户输入 / user input
 * - assistant: 模型回复(含可选 tool_calls) / model reply (may include tool_calls)
 * - tool:    工具执行结果 / tool execution result
 */
public record Message(
    String role,
    String content,
    @JsonProperty("tool_calls") List<ToolCall> toolCalls,
    @JsonProperty("tool_call_id") String toolCallId
) {
    public static Message system(String content) {
        return new Message("system", content, null, null);
    }

    public static Message user(String content) {
        return new Message("user", content, null, null);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content, null, null);
    }

    public static Message assistantWithToolCalls(String content, List<ToolCall> toolCalls) {
        return new Message("assistant", content, toolCalls, null);
    }

    public static Message toolResult(String toolCallId, String content) {
        return new Message("tool", content, null, toolCallId);
    }

    /** 工具调用 / a single tool invocation by the model */
    public record ToolCall(
        String id,
        String type,        // 总是 "function" / always "function"
        FunctionCall function
    ) {}

    public record FunctionCall(
        String name,
        String arguments    // JSON 字符串 / JSON-encoded args
    ) {}
}
