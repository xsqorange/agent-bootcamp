package com.agentbootcamp.agents;

import com.agentbootcamp.LlmClient;
import com.agentbootcamp.TraceWriter;
import com.agentbootcamp.tools.EditFile;
import com.agentbootcamp.tools.ReadFile;
import com.agentbootcamp.tools.WriteFile;

import java.nio.file.Path;
import java.util.List;

/**
 * Day 10: Editor — 写入 worker / write worker for code review
 *
 * 中文:代码评审流程的"修 bug 的人"。注册 3 个工具 (read_file 看上下文 / write_file 写文件 / edit_file 精确改),
 *      system prompt 强调"基于 Critic 的 bug 列表,只改 Critic 指出的代码,别多改"。
 *
 * English: Write worker — applies Critic's bug list to actual code.
 *          Has read_file (context) + write_file (overwrite) + edit_file (precise edit).
 *          System prompt: "fix only what Critic flagged, nothing more".
 *
 * 设计要点 / Design points:
 *   - 有 write 能力 (跟 Researcher 互补)
 *   - system prompt 强调"小修改、只改被指出的 bug、不重构"
 *   - maxCost 比 Researcher 高 (可能改多处)
 */
public class EditorAgent extends WorkerAgent {

    public EditorAgent(LlmClient llm, TraceWriter trace, Path workDir) {
        super("editor", llm, List.of(
            new ReadFile(),         // 看上下文
            new WriteFile(workDir), // 整文件覆盖
            new EditFile(workDir)   // 精确替换
        ), trace, 10, 0.30);  // maxSteps=10, maxCost=0.30 (可能改多处)
    }

    @Override
    protected String systemPrompt() {
        return """
            你是 Editor — 代码评审流程的"修 bug 的人"。

            你的输入: Researcher 收集的事实 + Critic 列出的 bug 列表(带严重度 + 修复建议)。
            你的任务: 用 edit_file 或 write_file 改代码,只修 Critic 指出的 bug,别多改。

            严格禁止:
              ❌ 重构未在 bug 列表中的代码 (即使"看起来可以优化")
              ❌ 改架构 / 改命名 / 改格式 (除非 Critic 明确说)
              ❌ 加新功能 / 加新工具 (不是 review 范围)
              ❌ 删除测试 (即使"测试不再需要")

            你的工作流:
              1. 用 read_file 看 bug 所在的文件 + 上下文
              2. 用 edit_file(old_string, new_string) 精确改 — 1 个 bug 1 个 edit
              3. 改完不要重新跑测试 (Orchestrator 会做)
              4. 最后 reply: "已修 N 个 bug: [路径]..."

            优先用 edit_file (精确),只有需要全文件重写才用 write_file。

            用中文回答。
            """;
    }
}
