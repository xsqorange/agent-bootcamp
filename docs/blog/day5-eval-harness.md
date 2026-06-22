# Day 5 收束博客:评测脚手架 + 10 黄金用例 JSON

> **中文**:把 8 个真 LLM E2E 用例 JSON 化 + JUnit 5 `@TestFactory` 动态生成测试 + 修 3 个真坑 (`-parameters` / snake_case / 429 retry),**从此调参有数据**。
>
> **English**: Day 5 — EvalHarness + 10 golden cases JSON + JUnit @TestFactory + 429 retry-with-backoff. Blind tuning no more.

---

## 🎯 背景

Day 4 跑 8 个真 LLM E2E 测试,**手动跑**:
```bash
mvnw test -Dtest=AgentTest#tc6_*
```

改 prompt 后不知道有没有 break 别的。Day 5 把"手动跑"升级到"自动跑 + 失败时 trace 留底 + 多个 case 跑不撞 429"。

**为什么用 JSON**:
- 评测用例跟代码分离 → **非工程师也能写**
- `evals/cases/01-list-java-files.json` 一行 grep 看完整 prompt
- 加新 case = 加 1 个 JSON 文件,**不改 Java**

---

## 🏗️ 黄金用例 JSON 结构

```json
{
  "id": "01-list-java-files",
  "prompt": "List all .java files in src/main",
  "expected": {
    "must_call_tools": ["exec"],
    "must_contain_in_final": ["Main.java", "Agent.java"],
    "max_steps": 5,
    "max_cost_usd": 0.05
  },
  "post_check": {
    "files_created": []
  }
}
```

**4 大断言**:
| 断言 | 验什么 |
|---|---|
| `must_call_tools` | LLM 必须调过列表里每个工具 |
| `must_contain_in_final` | 最终输出必须含所有字符串 |
| `max_steps` / `max_cost_usd` | 超限即 fail |
| `post_check` | 跑完看文件副作用 |

---

## 💻 EvalHarness.runCase()

```java
public CaseResult runCase(JsonNode caseNode) {
    // 429 retry-with-backoff (5s, 15s)
    for (int attempt = 1; attempt <= 3; attempt++) {
        try {
            Agent agent = new Agent(llm, tools, maxSteps, maxCostUsd);
            RunResult result = agent.run(caseNode.get("prompt").asText());

            List<String> calledTools = result.trace().stream()
                .flatMap(s -> s.toolCalls().stream())
                .map(ToolCall::name).distinct().toList();

            // 1. must_call_tools
            for (String required : expected.mustCallTools()) {
                if (!calledTools.contains(required)) return CaseResult.fail("missing tool: " + required);
            }
            // 2. must_contain_in_final
            String final = result.finalAnswer() != null ? result.finalAnswer() : "";
            for (String needle : expected.mustContainInFinal()) {
                if (!final.contains(needle)) return CaseResult.fail("missing text: " + needle);
            }
            // 3. cost / steps
            if (result.totalCostUsd() > expected.maxCostUsd()) return CaseResult.fail("cost > limit");
            if (result.steps() > expected.maxSteps()) return CaseResult.fail("steps > limit");
            // 4. post_check
            for (String file : postCheck.filesCreated()) {
                if (!Files.exists(Path.of(file))) return CaseResult.fail("missing file: " + file);
            }
            return CaseResult.ok();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("429") && attempt < 3) {
                Thread.sleep(5000L * attempt);  // 5s, 10s, 15s
                continue;
            }
            throw e;
        }
    }
}
```

**JUnit `@TestFactory` 动态生成**:
```java
@TestFactory Stream<DynamicTest> allCases() {
    return cases.stream().map(c -> DynamicTest.dynamicTest(c.get("id").asText(),
        () -> assertTrue(harness.runCase(c).isOk()));
    );
}
```

---

## 🐛 3 个 Day 5 真坑

1. **Jackson 反序列化 record 必须 `-parameters` 编译选项** — Java 14+ record 组件名默认擦除为 `arg0/arg1`,Jackson 找不到字段。**修法**: `maven-compiler-plugin` 加 `<parameters>true</parameters>`
2. **JSON snake_case ↔ record camelCase** — `must_call_tools` 对应不上 `mustCallTools`。**修法**: ObjectMapper 配 `PropertyNamingStrategies.SNAKE_CASE`
3. **LLM API 429 rate limit** — 跑 10 个 case 连续撞 429。**修法**: EvalHarness 3 attempts + 指数退避 5s/15s

---

## 📊 验收数据

| 指标 | 数字 |
|---|---|
| 新增文件 | 14 (`EvalHarness.java` + `EvalRunnerTest.java` + `evals/cases/01-10*.json` + trace 目录) |
| 新增用例 | 10 (`01-list-java-files` ~ `10-multi-tool-combo`) |
| 总测试 | **52** (32 单元 + 20 E2E) ✅ 全过 |
| mvn verify | ~3 min, 烧 $0.0081 |
| Commit | 3 (`feat(day5): EvalHarness` + `test(day5): EvalRunnerTest @TestFactory` + `docs(day5)`) |

---

## 🚀 Day 6 预告

**Project 1 收尾 (周末 12h)**: 修 Day 4/5 进度日志 mismatch / 拆 GitHub Actions 为 2 job (build 无 key / eval 有 key) / 跑 5 acceptance / 备 demo 脚本 / README Day 6 章节,**Project 1 (CLI Agent) 完工**。