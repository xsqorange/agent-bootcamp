package com.agentbootcamp;

/**
 * Agent 停止原因 / Why the agent stopped
 *
 * Day 2: 4 种 / Day 2: 4 reasons
 * - FINAL_ANSWER: 模型给出文本答案(没调工具或调完工具后回答)/ model returned text
 * - MAX_STEPS:    达到 --max-steps 上限 / hit max steps
 * - COST_LIMIT:   单次运行成本超 --max-cost / cost limit hit
 * - ERROR:        (Day 3 启用)所有工具执行都失败 / all tools failed
 */
public enum StopReason {
    FINAL_ANSWER,
    MAX_STEPS,
    COST_LIMIT,
    ERROR
}
