package com.agentbootcamp.agents;

import com.agentbootcamp.LlmClient;
import com.agentbootcamp.TraceWriter;
import com.agentbootcamp.tools.Exec;
import com.agentbootcamp.tools.Grep;
import com.agentbootcamp.tools.ReadFile;

import java.nio.file.Path;
import java.util.List;

/**
 * Day 10: Researcher — 只读 worker / read-only worker for code review
 *
 * 中文:代码评审流程的"信息收集者"。注册 3 个只读工具 (read_file / grep / exec),
 *      system prompt 强调"只能读,不能写;只总结事实,不给建议"。
 *      实际跑时 Orchestrator 派 task: "读 README.md + git log 总结这个 PR 的变化"。
 *
 * English: Read-only worker for the code-review flow. Has 3 read tools
 *          (read_file / grep / exec). System prompt reinforces "read only, no writes;
 *          summarize facts, do not give opinions" (Critic/Editor's job).
 *
 * 设计要点 / Design points:
 *   - extends WorkerAgent — 复用 inbox/outbox + runLoop 线程模型
 *   - override protected systemPrompt (Agent.java:289 改 private→protected 后才能 override)
 *   - 不注册 write_file/edit_file — Researcher 跑出来如果写文件,说明设计被破坏
 *   - exec 只用来跑 git log/diff/grep 类只读命令 (system prompt 警告)
 */
public class ResearcherAgent extends WorkerAgent {

    public ResearcherAgent(LlmClient llm, TraceWriter trace, Path workDir) {
        super("researcher", llm, List.of(
            new ReadFile(),
            new Grep(workDir),
            new Exec()  // 只用来跑 git log/diff/grep,见 system prompt
        ), trace, 8, 0.20);  // maxSteps=8 给够, maxCost=0.20 (PR review 信息量大)
    }

    @Override
    protected String systemPrompt() {
        return """
            你是 Researcher — 代码评审流程的"信息收集者"。

            你只能使用以下只读工具 (NO write_file, NO edit_file, NO other write tools):
              - read_file(path): 读文件
              - grep(path, pattern): 跨文件搜正则
              - exec(command): 只用来跑 git log / git diff / git show 等只读命令

            严格禁止:
              ❌ 写任何文件 (包括 write_file / edit_file)
              ❌ 跑修改性命令 (git add/commit/push, rm, mv, npm install 等)
              ❌ 给"建议"或"评价" — 这是 Critic 的工作,不是你的

            你的输出格式 (必须):
              1. 列出读过的文件 + 行号范围
              2. 总结代码变化(改了什么、为什么)
              3. 列出 git log/diff 关键信息
              4. 1-3 句话事实总结,不要"应该"/"建议"

            用中文回答,简洁直接。
            """;
    }
}
