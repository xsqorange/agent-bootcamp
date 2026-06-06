package com.agentbootcamp;

/**
 * Agent.run() 的返回值 / return value of Agent.run()
 *
 * Day 1 的 runOnce() 返回 String — 只能拿到"答了啥"
 * Day 2 的 run() 返回 RunResult — 还能拿到"怎么停的 / 用了多少步 / 多少钱"
 *
 * @param finalAnswer    最终回答(可能是因为 MAX_STEPS/COST_LIMIT 而给出的"我没答完"提示)
 * @param stopReason     停止原因
 * @param totalSteps     实际跑了几步
 * @param totalTokensIn  累计 prompt tokens
 * @param totalTokensOut 累计 completion tokens
 * @param totalCostUsd   累计美元成本
 */
public record RunResult(
    String finalAnswer,
    StopReason stopReason,
    int totalSteps,
    int totalTokensIn,
    int totalTokensOut,
    double totalCostUsd
) {
    /** 简单展示用 / human-readable summary */
    public String summary() {
        return String.format(
            "%s | steps=%d | tokens_in=%d | tokens_out=%d | cost=$%.6f",
            stopReason, totalSteps, totalTokensIn, totalTokensOut, totalCostUsd
        );
    }
}
