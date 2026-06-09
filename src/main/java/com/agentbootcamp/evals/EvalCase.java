package com.agentbootcamp.evals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 评测用例 (黄金用例) / Evaluation case (golden case) — Day 5
 *
 * 一个 EvalCase = 一个 JSON 文件 evals/cases/&lt;id&gt;.json
 * An EvalCase = one JSON file evals/cases/&lt;id&gt;.json
 *
 * 4 条断言 (SKILL.md Day 5):
 *   1. must_call_tools   ⊆ 实际调用的工具集
 *   2. must_contain_in_final ⊆ finalAnswer (case-insensitive substring)
 *   3. totalSteps &lt;= max_steps
 *   4. totalCostUsd &lt;= max_cost_usd
 *   5. (可选) post_check 验证文件副作用
 *
 * @param id        唯一 ID(也是 trace 文件名)/ unique id (also trace filename)
 * @param prompt    给 Agent 的用户目标 / user goal
 * @param expected  期望(工具 + 答案 + 阈值)
 * @param postCheck 可选副作用检查(空则跳过)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalCase(
    String id,
    String prompt,
    Expected expected,
    PostCheck postCheck
) {

    /** 期望断言 / Expected assertions (4 hard checks) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Expected(
        List<String> mustCallTools,
        List<String> mustContainInFinal,
        int maxSteps,
        double maxCostUsd
    ) {}

    /**
     * 副作用检查 / Side-effect check (optional, runs after Agent finishes)
     * type: "file_equals" | "file_exists" | "file_contains" | null(跳过)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PostCheck(
        String type,
        String path,
        String content
    ) {}

    // ====================================================================
    // 静态工厂 / Static factories
    // ====================================================================

    private static final ObjectMapper JSON = new ObjectMapper()
        // Day 5: JSON 字段 snake_case (must_call_tools) → record 组件 camelCase (mustCallTools)
        .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);

    /** 从 JSON 文件加载 / Load from a JSON file */
    public static EvalCase fromJsonFile(Path file) throws Exception {
        return JSON.readValue(Files.readString(file), EvalCase.class);
    }

    /** 批量加载 evals/cases/ 下所有 .json / Load all .json under evals/cases/ */
    public static List<EvalCase> loadAll(Path casesDir) throws Exception {
        try (var stream = Files.list(casesDir)) {
            return stream
                .filter(p -> p.toString().endsWith(".json"))
                .sorted()
                .map(p -> {
                    try {
                        return fromJsonFile(p);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to load " + p, e);
                    }
                })
                .toList();
        }
    }
}
