package com.agentbootcamp.agents;

import com.agentbootcamp.LlmClient;
import com.agentbootcamp.TraceWriter;

import java.util.List;

/**
 * Day 10: Critic — 纯推理 worker / pure-reasoning worker for code review
 *
 * 中文:代码评审流程的"bug 猎手"。**零工具** — 只能基于 Researcher 给的事实推理。
 *      system prompt 强化"NO tools, think and reply" (多数 LLM 没工具会想用,要严防)。
 *
 * English: Pure reasoning worker — zero tools. Thinks based on facts the Researcher
 *          collected. System prompt must strongly enforce "no tool calls" because
 *          most LLMs try to call tools when none are given.
 *
 * 设计要点 / Design points:
 *   - 注册 0 工具 (List.of() 空)
 *   - system prompt 显式说"你没有工具,只能用文字推理"
 *   - 跟 Researcher 配对: Researcher 给事实 → Critic 找 bug → Editor 改
 *
 * Java 锚定 / Java anchor:
 *   - WorkerAgent 构造器接受 List<Tool>, 传空 list 表示"无工具"
 *   - LLM 收到空工具列表 (tools schema 是空数组) 时, 只能生成 assistant text 响应
 */
public class CriticAgent extends WorkerAgent {

    public CriticAgent(LlmClient llm, TraceWriter trace) {
        super("critic", llm, List.of(),  // 零工具!
            trace, 5, 0.10);  // maxSteps=5 (没工具,1 步就完), maxCost=0.10
    }

    @Override
    protected String systemPrompt() {
        return """
            你是 Critic — 代码评审流程的"bug 猎手"。

            **关键: 你没有任何工具 (you have NO tools)**。你只能基于 Researcher 提供的事实,
            用纯文字推理找潜在 bug、风格问题、安全漏洞、性能隐患。

            你的输入: Researcher 已经读了代码 + git diff + 列出了事实。
            你的输出: bug 列表 + 严重度 (CRITICAL/HIGH/MEDIUM/LOW) + 修复建议(不写代码)。

            严格禁止:
              ❌ 试图调用任何工具 (你没有工具可用)
              ❌ 读文件 (Researcher 已经读了)
              ❌ 编造文件路径或代码(只基于 Researcher 给的事实判断)
              ❌ 给 "代码看起来 OK" 这种没用的输出 (必须找到具体问题或显式说"无问题")

            输出格式 (必须):
              ## Bug 列表
              1. [HIGH] 文件:行号 — 描述(1 句话)
              2. [MEDIUM] ...
              ## 严重度统计
              - CRITICAL: 0
              - HIGH: 1
              - MEDIUM: 0
              - LOW: 0
              ## 总结
              1-2 句话整体评价。

            用中文回答。
            """;
    }
}
