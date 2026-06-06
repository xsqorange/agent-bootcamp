package com.agentbootcamp;

import java.util.List;
import java.util.Map;

/**
 * Agent 单步执行的可追溯快照 / Per-step trace record
 *
 * 写盘到 trace.jsonl(一请求一行 JSON,标准 JSONL 格式)
 * Written to trace.jsonl (one JSON object per line, standard JSONL)
 *
 * 字段对应关系:
 *  - step:             1-based 步数
 *  - timestampMs:      写入时间(便于排序/过滤)
 *  - userGoal:         整个 run 的目标(冗余以便单独看一行也懂上下文)
 *  - llmContent:       模型这一步返回的文本(可能为 null / 调工具时)
 *  - toolCalls:        模型想调的工具(原始 JSON arguments)
 *  - executions:       工具执行结果(含成功/失败/耗时)
 *  - tokensIn/Out:     本步消耗的 token
 *  - tokensIn/OutTotal:累计到这步的 token
 *  - costUsdTotal:     累计到这步的美元成本
 *  - stopReason:       仅终止步(最后一)有值
 *  - finalAnswer:      仅 FINAL_ANSWER 时有值
 */
public record AgentStep(
    int step,
    long timestampMs,
    String userGoal,
    String llmContent,
    List<ToolCallRecord> toolCalls,
    List<ToolExecutionRecord> executions,
    Integer tokensIn,
    Integer tokensOut,
    Integer tokensInTotal,
    Integer tokensOutTotal,
    Double costUsdTotal,
    StopReason stopReason,
    String finalAnswer
) {
    /** 模型想调的工具 / tool call the model wants to make */
    public record ToolCallRecord(
        String id,
        String name,
        String argumentsJson
    ) {}

    /** 一次工具执行的结果 / a single tool execution result */
    public record ToolExecutionRecord(
        String toolCallId,
        String name,
        Map<String, Object> args,
        String result,
        boolean ok,
        long durationMs,
        String errorMessage
    ) {}
}
