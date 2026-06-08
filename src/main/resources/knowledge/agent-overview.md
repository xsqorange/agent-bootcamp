# Agent Bootcamp — 项目概览 / Project Overview

**中文**:这是一个 2 周速成项目,用 Java 从零搭一个能用的 LLM 编程 Agent。
**English**: A 2-week learning sprint to build a working LLM coding agent in Java from scratch.

## 14 天路线图 / 14-Day Roadmap

| Day | 主题 / Topic | 交付物 / Deliverable |
|---|---|---|
| 1 | LLM 101 + 工具调用 / Tool calling | LlmClient + 3 tools |
| 2 | ReAct 循环 / ReAct loop | while 循环 + StopReason + JSONL trace |
| 3 | + 2 工具 + 评测 / + 2 tools + evals | 5 工具 + 10 黄金用例 |
| **4** | **记忆 + 简易 RAG / Memory + simple RAG** | **滑动窗口 + 内存索引 + search_kb 工具** |
| 5 | 评测脚手架 / Eval harness | 完整评测 |
| 6-7 | Project 1 收尾 | README + demo + push |
| 8 | 多 Agent 入门 | Orchestrator + 1 worker |
| 9-14 | MCP / 3-Agent / 可观测性 / 安全 / 部署 / 收尾 | 生产级 |

## 5 阶段设计 / 5-Stage Design

```
用户 (Feishu / CLI)
    ↓
Main (picocli / Feishu adapter)
    ↓
Agent (ReAct while 循环)
    ↓
LlmClient (OpenAI 兼容 HTTP)
    ↓
LLM API (OpenAI / DeepSeek / MiniMax / Qwen)
    ↓
tool_calls → 5 个 Tool 执行
    ↓
观察结果回填 messages → 下一轮
```

## 设计原则 / Design Principles

- **简单优先 / Simplicity first**:每个文件 100-300 行,不引复杂框架
- **可观察 / Observable**:每步写 trace.jsonl,崩了能回放
- **跨平台 / Cross-platform**:Java 17 标准库为主,无系统命令依赖
- **可测 / Testable**:Junit 5 + @TempDir,每个工具有单测
- **便宜 / Cheap**:用 gpt-4o-mini / minimax-m3 这类便宜模型
