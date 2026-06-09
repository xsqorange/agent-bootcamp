package com.agentbootcamp.evals;

import com.agentbootcamp.LlmClient;
import com.agentbootcamp.LlmConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * 评测入口 / Eval runner — Day 5
 *
 * 一次性加载 evals/cases/ 下所有 .json,用 EvalHarness 跑每个 case,
 * 把结果当作 JUnit 动态测试。`mvn verify` 跑这个文件就会跑全部 10 个 case。
 *
 * 设计选择 / Design choices:
 *  - 假设 (assumeTrue) 有 API key,否则 skip 整个 class(跟 AgentTest 同款)
 *  - 用 @TestFactory 动态生成测试,每个 case 1 个测试,fail 信息含 4 条断言
 *  - @AfterAll 打印通过率汇总,作为"Day 5 验收报告"
 *  - **失败时 build 仍 fail** (Q2=A 默认):Junit 5 的 DynamicTest 失败 → surefire 失败 → mvn verify 失败
 *
 * 跑法 / How to run:
 *   set -a && source .env && set +a
 *   ./mvnw verify -Dtest=EvalRunnerTest           # 跑这 10 个 case
 *   ./mvnw verify                                  # 跑全部测试
 *   ./mvnw test -Dtest=EvalRunnerTest              # 也可以用 mvn test,效果一样
 *
 * 验收 / Acceptance (SKILL.md Day 5):
 *   - mvn verify 通过
 *   - 通过率 ≥ 8/10(2 个挂也 OK,留给 Day 6+ 调)
 */
class EvalRunnerTest {

    private static List<EvalCase> cases;
    private static LlmClient llm;
    private static EvalHarness harness;
    private static final List<EvalResult> ALL_RESULTS = new ArrayList<>();

    @BeforeAll
    static void setup() throws Exception {
        // 1. 必须有 API key,否则 skip 整个 class
        boolean hasKey = System.getenv("MINIMAX_API_KEY") != null
                     || System.getenv("OPENAI_API_KEY") != null
                     || System.getenv("DEEPSEEK_API_KEY") != null
                     || System.getenv("DASHSCOPE_API_KEY") != null
                     || System.getenv("ANTHROPIC_API_KEY") != null;
        Assumptions.assumeTrue(hasKey,
            "跳过 EvalRunnerTest:未设置 LLM API key。" +
            "请先 `set -a && source .env && set +a` 或 export 一个 API key。");

        // 2. 加载所有 .json
        Path casesDir = Path.of("evals/cases");
        cases = EvalCase.loadAll(casesDir);
        log("加载 " + cases.size() + " 个评测用例 from " + casesDir.toAbsolutePath());

        // 3. LLM + Harness(共享 LLM,每个 case 跑时再起 Agent)
        LlmConfig config = LlmConfig.fromEnv();
        llm = new LlmClient(config);
        harness = new EvalHarness(
            llm,
            Path.of(".").toAbsolutePath().normalize(),    // workDir = cwd(跟 Main 一致)
            EvalHarness.defaultReportsDir(),                // evals/reports/
            EvalHarness.findKnowledgeDir()                  // 知识库(可能 null)
        );
        log("Harness 就绪: reports=" + EvalHarness.defaultReportsDir().toAbsolutePath()
            + ", knowledge=" + EvalHarness.findKnowledgeDir());
    }

    @AfterAll
    static void report() {
        if (ALL_RESULTS.isEmpty()) return;

        int passed = (int) ALL_RESULTS.stream().filter(EvalResult::passed).count();
        int failed = ALL_RESULTS.size() - passed;
        double totalCost = ALL_RESULTS.stream().mapToDouble(EvalResult::totalCostUsd).sum();
        int totalSteps = ALL_RESULTS.stream().mapToInt(EvalResult::totalSteps).sum();

        System.out.println();
        System.out.println("================ Day 5 Eval Report ================");
        System.out.printf(" 通过: %d / %d (%.0f%%)%n", passed, ALL_RESULTS.size(),
            passed * 100.0 / ALL_RESULTS.size());
        System.out.printf(" 失败: %d%n", failed);
        System.out.printf(" 总步数: %d%n", totalSteps);
        System.out.printf(" 总成本: $%.6f%n", totalCost);
        System.out.println("--------------------------------------------------");
        for (EvalResult r : ALL_RESULTS) {
            System.out.println("  " + r.summary());
        }
        System.out.println("==================================================");
    }

    @TestFactory
    Stream<DynamicTest> runAllEvalCases() {
        return cases.stream().map(c -> dynamicTest(c.id(), () -> {
            log("── 跑 case: " + c.id());
            EvalResult r = harness.runCase(c);
            ALL_RESULTS.add(r);
            log("  → " + r.summary());
            assertTrue(r.passed(),
                "Case '" + c.id() + "' failed:\n  " + r.failureReason());
        }));
    }

    // ====================================================================
    // 日志
    // ====================================================================
    private static void log(String msg) {
        System.out.println("[EvalRunnerTest] " + msg);
    }
}
