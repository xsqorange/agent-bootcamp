package com.agentbootcamp.evals;

import com.agentbootcamp.RunResult;
import com.agentbootcamp.StopReason;

import java.util.List;

/**
 * 评测结果 / Eval result — Day 5
 *
 * @param caseId         黄金用例 ID
 * @param passed         是否通过(所有断言 + post_check 都过)
 * @param failureReason  失败原因(通过时为 null)
 * @param stopReason     Agent.run() 的停止原因
 * @param totalSteps     Agent 跑了几步
 * @param totalCostUsd   Agent 烧了多少美元
 * @param calledTools    实际调用的工具集(去重)
 */
public record EvalResult(
    String caseId,
    boolean passed,
    String failureReason,
    StopReason stopReason,
    int totalSteps,
    double totalCostUsd,
    List<String> calledTools
) {

    /** 通过 / Passed */
    public static EvalResult passed(String id, RunResult r, List<String> calledTools) {
        return new EvalResult(id, true, null,
            r.stopReason(), r.totalSteps(), r.totalCostUsd(), calledTools);
    }

    /** 失败 / Failed */
    public static EvalResult failed(String id, String reason,
                                    StopReason sr, int steps, double cost,
                                    List<String> calledTools) {
        return new EvalResult(id, false, reason, sr, steps, cost, calledTools);
    }

    /** 一行展示 / One-line summary */
    public String summary() {
        return String.format("[%s] %s | steps=%d | cost=$%.6f | tools=%s%s",
            caseId,
            passed ? "PASS" : "FAIL",
            totalSteps,
            totalCostUsd,
            calledTools,
            passed ? "" : " | reason=" + failureReason
        );
    }
}
