package com.agentbootcamp;

import com.agentbootcamp.tools.Exec;
import com.agentbootcamp.tools.GetCurrentTime;
import com.agentbootcamp.tools.Grep;
import com.agentbootcamp.tools.ReadFile;
import com.agentbootcamp.tools.WriteFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent 端到端测试 / Agent end-to-end tests (5 cases)
 *
 * 这些测试调真 LLM,需要 API key 环境变量。
 * These tests call the real LLM; need API key env var.
 *
 * Day 3 新增 5 个 TC(连 Day 2 的 5 个,共 10 个黄金用例)/ Day 3 new 5 TCs (10 total with Day 2's 5):
 *  TC-6: WriteFile 创建新文件 / create new file
 *  TC-7: WriteFile 覆盖已有 / overwrite existing
 *  TC-8: Grep 找到匹配 / find matches
 *  TC-9: Grep 没找到匹配 / no matches
 *  TC-10: 多工具组合 (read + write) / multi-tool combo
 *
 * 用真 LLM(Q3=B 用户选择) / Use real LLM (Q3=B user choice):
 *  - 优点 / pros: 更接近真实使用,能验证 mock 漏掉的细节
 *  - 缺点 / cons: 慢(每个 5-15 秒),依赖网络,可能 flaky
 *  - 跑测试前要 source .env
 *    Before running, do: `set -a && source .env && set +a`
 */
class AgentTest {

    private LlmClient llm;
    private List<Tool> tools;
    private Path workDir;

    @BeforeAll
    static void checkApiKey() {
        // 至少要有一个 key(MINIMAX / OPENAI / DEEPSEEK / ...)
        boolean has = System.getenv("MINIMAX_API_KEY") != null
                   || System.getenv("OPENAI_API_KEY") != null
                   || System.getenv("DEEPSEEK_API_KEY") != null
                   || System.getenv("DASHSCOPE_API_KEY") != null
                   || System.getenv("ANTHROPIC_API_KEY") != null;
        org.junit.jupiter.api.Assumptions.assumeTrue(has,
            "跳过 Agent 端到端测试:未设置 LLM API key。请先 `set -a && source .env && set +a`");
    }

    @BeforeEach
    void setup() throws Exception {
        LlmConfig config = LlmConfig.fromEnv();
        llm = new LlmClient(config);
        workDir = Path.of(".").toAbsolutePath().normalize();
        tools = List.of(
            new GetCurrentTime(),
            new ReadFile(),
            new WriteFile(workDir),
            new Grep(workDir),
            new Exec()
        );
        log("已注册 5 个工具: get_current_time, read_file, write_file, grep, exec");
    }

    // ====================================================================
    // TC-6: WriteFile - 创建新文件
    // ====================================================================
    @Test
    void test6_WriteFileCreatesNewFile() throws Exception {
        Path target = workDir.resolve("target/test-tc6.txt");
        Files.deleteIfExists(target);
        log("TC-6 目标文件: " + target);

        Agent agent = new Agent(llm, tools, null, 5, 0.10);
        RunResult result = agent.run(
            "Use the write_file tool to create a file at target/test-tc6.txt " +
            "with the exact content 'tc6-payload'. Do not call any other tools. " +
            "After writing, reply 'done'."
        );

        log("TC-6 结果: stopReason=" + result.stopReason() + ", steps=" + result.totalSteps() +
            ", answer='" + result.finalAnswer() + "'");

        assertEquals(StopReason.FINAL_ANSWER, result.stopReason());
        assertTrue(Files.exists(target), "TC-6: 文件必须存在");
        assertEquals("tc6-payload", Files.readString(target).trim(),
            "TC-6: 文件内容必须等于 'tc6-payload'");
    }

    // ====================================================================
    // TC-7: WriteFile - 覆盖已有文件
    // ====================================================================
    @Test
    void test7_WriteFileOverwritesExisting() throws Exception {
        Path target = workDir.resolve("target/test-tc7.txt");
        Files.writeString(target, "OLD CONTENT");
        log("TC-7 种子文件内容: OLD CONTENT");

        Agent agent = new Agent(llm, tools, null, 5, 0.10);
        RunResult result = agent.run(
            "Use write_file to overwrite target/test-tc7.txt with the exact content " +
            "'NEW CONTENT - tc7'. Do not read the file first. After writing, reply 'overwritten'."
        );

        log("TC-7 结果: stopReason=" + result.stopReason() + ", steps=" + result.totalSteps() +
            ", answer='" + result.finalAnswer() + "'");

        assertEquals(StopReason.FINAL_ANSWER, result.stopReason());
        assertEquals("NEW CONTENT - tc7", Files.readString(target).trim(),
            "TC-7: 覆盖后内容必须等于 'NEW CONTENT - tc7'");
    }

    // ====================================================================
    // TC-8: Grep - 找到匹配
    // ====================================================================
    @Test
    void test8_GrepFindsMatches() throws Exception {
        Agent agent = new Agent(llm, tools, null, 5, 0.10);
        RunResult result = agent.run(
            "Use the grep tool (NOT read_file) to search for the pattern 'Day 1' in README.md. " +
            "Report how many matching lines you found and show the first match's line number."
        );

        log("TC-8 结果: stopReason=" + result.stopReason() + ", steps=" + result.totalSteps() +
            ", answer='" + result.finalAnswer() + "'");

        assertEquals(StopReason.FINAL_ANSWER, result.stopReason());
        String lower = result.finalAnswer().toLowerCase();
        assertTrue(lower.contains("day 1"),
            "TC-8: 最终答案应提到 'Day 1',实际: " + result.finalAnswer());
    }

    // ====================================================================
    // TC-9: Grep - 找不到匹配
    // ====================================================================
    @Test
    void test9_GrepNoMatches() throws Exception {
        Agent agent = new Agent(llm, tools, null, 5, 0.10);
        RunResult result = agent.run(
            "Use the grep tool to search for the pattern 'xyzzy_no_such_pattern_xyzzy' in README.md. " +
            "Report what you found."
        );

        log("TC-9 结果: stopReason=" + result.stopReason() + ", steps=" + result.totalSteps() +
            ", answer='" + result.finalAnswer() + "'");

        assertEquals(StopReason.FINAL_ANSWER, result.stopReason());
        String lower = result.finalAnswer().toLowerCase();
        // 各种可能说法: 0 / no match / not found / none / nothing
        assertTrue(lower.contains("0")
                || lower.contains("no match")
                || lower.contains("not found")
                || lower.contains("none"),
            "TC-9: 最终答案应说没找到,实际: " + result.finalAnswer());
    }

    // ====================================================================
    // TC-10: 多工具组合 - 读 README 然后写到新文件
    // ====================================================================
    @Test
    void test10_ReadThenWrite() throws Exception {
        Path target = workDir.resolve("target/test-tc10.txt");
        Files.deleteIfExists(target);
        log("TC-10 目标文件: " + target);

        Agent agent = new Agent(llm, tools, null, 8, 0.15);
        RunResult result = agent.run(
            "Step 1: Use read_file to read README.md (the first 2000 characters is enough). " +
            "Step 2: Use write_file to write the FIRST LINE of README.md to target/test-tc10.txt. " +
            "Step 3: Reply 'done'."
        );

        log("TC-10 结果: stopReason=" + result.stopReason() + ", steps=" + result.totalSteps() +
            ", answer='" + result.finalAnswer() + "'");

        assertEquals(StopReason.FINAL_ANSWER, result.stopReason());
        assertTrue(Files.exists(target), "TC-10: 文件必须存在");
        String content = Files.readString(target).trim();
        assertFalse(content.isBlank(), "TC-10: 文件不应为空");
        // README 第一行以 "#" 开头
        assertTrue(content.startsWith("#"),
            "TC-10: 文件应包含 README 第一行(以 # 开头),实际: " + content);
    }

    // ====================================================================
    // 辅助 / Helpers
    // ====================================================================
    private static void log(String msg) {
        System.out.println("[AgentTest] " + msg);
    }
}
