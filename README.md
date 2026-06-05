# Agent Bootcamp / Agent 训练营

> **A 2-week learning sprint to build a working LLM coding agent in Java from scratch.**
> **两周速成:从零用 Java 构建一个能用的 LLM 编程 Agent。**

[![Java 17](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-orange.svg)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

## 简介 / Overview

**中文**:这是 `agent-dev-crash-course` 技能的 Day 1 起步项目。**目标:两周内(每天 6 小时)从零搭一个能用的 CLI 编程 Agent,然后扩展到多 Agent、生产部署**。

**English**: This is the Day 1 starter project for the `agent-dev-crash-course` skill. **Goal: in 2 weeks (6 hr/day), build a working CLI coding agent from scratch, then extend to multi-agent and production deployment.**

## 14 天路线图 / 14-Day Roadmap

| Day | 主题 / Topic | 交付物 / Deliverable |
|---|---|---|
| 1 | LLM 101 + 工具调用 / LLM 101 + Tool calling | `LlmClient` + 3 tools + 单次调用 / single call |
| 2 | ReAct 循环 / ReAct loop | 150 行循环,3 工具跑通 / 150-line loop with 3 tools |
| 3 | + 2 工具 + JSONL trace | 5 工具 + trace 文件 / 5 tools + trace file |
| 4 | 记忆 + 简易 RAG / Memory + simple RAG | 滑动窗口 + pgvector |
| 5 | 评测脚手架 / Eval harness | 10 个黄金用例 / 10 golden cases |
| 6-7 | Project 1 收尾 / Project 1 wrap-up | README + demo GIF + GitHub push |
| 8 | 多 Agent 入门 / Multi-agent intro | Orchestrator + 1 worker |
| 9 | MCP 服务器 / MCP server | 跨语言工具互通 / cross-language tool interop |
| 10 | 3-Agent 团队 / 3-agent crew | Researcher / Critic / Editor |
| 11 | 可观测性 / Observability | OpenTelemetry + 成本统计 / cost tracking |
| 12 | 安全 + 可靠性 / Safety + reliability | Resilience4j + prompt injection 防护 |
| 13 | 部署 / Deploy | Docker + K8s + 健康检查 / health check |
| 14 | 收尾发布 / Polish & ship | 双语博客 + demo + Release / bilingual blog + demo + release |

完整计划:见 [`agent-dev-crash-course`](https://github.com/nousresearch/hermes-agent) 技能。

## 快速开始 / Quick Start

### 前置要求 / Prerequisites

- **Java 17+**(本项目用 Amazon Corretto 17 验证) / Java 17+
- **Maven 3.9+**(项目自带 `mvnw`,无需系统装) / Maven 3.9+ (project ships `mvnw`)
- **LLM API Key**:OpenAI / Anthropic / DeepSeek / 通义 Qwen / Ollama(任选一个) / Any OpenAI-compatible provider

### 安装 / Install

```bash
# 克隆 / clone
git clone https://github.com/xsqorange/agent-bootcamp.git
cd agent-bootcamp

# 配置 API key / configure API key
export OPENAI_API_KEY="sk-..."              # OpenAI
# 或 / or
export ANTHROPIC_API_KEY="sk-ant-..."       # Anthropic
# 或 / or
export DEEPSEEK_API_KEY="sk-..."            # DeepSeek (国内性价比高 / good for China)

# 编译 / compile
./mvnw compile

# 跑 / run (单次 LLM 调用,Day 1 验收)
./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '列一下 src 目录下的所有 .java 文件'"
# 或编译后直接跑 / or run the shaded jar
./mvnw package
java -jar target/agent-bootcamp.jar --goal "列一下 src 目录下的所有 .java 文件"
```

### 在 VSCode 里打开 / Open in VSCode

```bash
code .
```

推荐安装的扩展(打开项目时会自动提示)/ Recommended extensions (auto-prompted):
- **Extension Pack for Java**(`vscjava.vscode-java-pack`)
- **Maven for Java**(`vscjava.vscode-maven`)
- **Spring Boot Extension Pack**(Day 8+ 用 / for Day 8+)

## Day 1 架构 / Day 1 Architecture

```
用户指令 (--goal "...")
    ↓
Main (picocli)        ← CLI 入口
    ↓
Agent                 ← ReAct 循环 (Day 1 是单次调用,Day 2 起循环)
    ↓
LlmClient             ← HTTP 客户端 (OpenAI 兼容协议)
    ↓
LLM API (OpenAI/DeepSeek/Qwen/Ollama)
    ↓
返回 tool_calls → ToolRegistry 调度 → Tool 执行
    ↓
观察结果回填 messages → 模型生成最终回答
```

### Day 1 关键文件 / Day 1 Key Files

| 文件 / File | 作用 / Purpose |
|---|---|
| `Main.java` | picocli 入口,解析 `--goal` 参数 / CLI entry, parses `--goal` |
| `LlmClient.java` | OpenAI 兼容协议的 HTTP 客户端 / HTTP client for OpenAI-compatible APIs |
| `Tool.java` | 工具接口(所有 Tool 都实现这个)/ Tool interface |
| `Agent.java` | ReAct 循环骨架(Day 1 单步,Day 2 循环)/ ReAct loop skeleton |
| `tools/GetCurrentTime.java` | 工具 1:返回当前时间 / Tool 1: returns current time |
| `tools/ReadFile.java` | 工具 2:读文件 / Tool 2: reads files |
| `tools/Exec.java` | 工具 3:执行 shell 命令 / Tool 3: executes shell commands |

## 配置 LLM Provider / Configuring LLM Providers

默认走 **OpenAI**。要切到别的厂商,改环境变量即可:
Default is **OpenAI**. To switch, just set environment variables:

| Provider | `LLM_BASE_URL` | `LLM_MODEL` | API key env |
|---|---|---|---|
| **OpenAI** | `https://api.openai.com/v1` | `gpt-4o-mini` | `OPENAI_API_KEY` |
| **Anthropic** | `https://api.anthropic.com/v1`(需适配) | `claude-sonnet-4-20250514` | `ANTHROPIC_API_KEY` |
| **DeepSeek** | `https://api.deepseek.com/v1` | `deepseek-chat` | `DEEPSEEK_API_KEY` |
| **通义 Qwen** | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-max` | `DASHSCOPE_API_KEY` |
| **Ollama(本地)/ local** | `http://localhost:11434/v1` | `qwen2.5` | (无需 / not needed) |

```bash
# DeepSeek 例子 / DeepSeek example
export DEEPSEEK_API_KEY="sk-..."
export LLM_BASE_URL="https://api.deepseek.com/v1"
export LLM_MODEL="deepseek-chat"
./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal 'hello'"
```

## 学习路径 / Learning Path

1. **本仓库**:`agent-bootcamp` — 14 天代码
2. **配套技能**(在 Hermes Agent 里):
   - `agent-dev-crash-course` — 2 周速成版(中英双语)
   - `agent-dev-learning` — 10 周完整版(中英双语)
3. **跟着做**:
   - Day 1-2:读完本仓库的 `LlmClient.java` + `Agent.java` + 3 个 Tool
   - Day 3-5:加 `WriteFile.java` / `Grep.java`,加 JSONL trace
   - Day 6-7:写 README、加 5 个黄金评测用例、推 GitHub
   - Day 8+:看 `agent-dev-crash-course` Day 8-14 计划

## 常见坑(Day 1 必看)/ Pitfalls (Day 1 must-read)

1. **Java 17 没在 PATH** — 装 JDK 后加到 PATH,或用 IDE 内置
2. **API key 没设** — `export OPENAI_API_KEY=...`,或写 `.env` 加载
3. **网络问题** — 国内用 DeepSeek/通义比 OpenAI 快
4. **Maven 下载慢** — 配 `~/.m2/settings.xml` 用阿里云镜像
5. **忘了 `mvn compile`** — 跑之前先编译

## 进度记录 / Progress Log

| Day | 日期 | 完成情况 | 笔记 |
|---|---|---|---|
| 1 | 2026-06-05 | ✅ 项目骨架 + LlmClient + 3 tools | 推到 GitHub: `xsqorange/agent-bootcamp` |

## 贡献 / Contributing

这是个人学习项目,但欢迎:
- Issue 提问 / questions
- PR 修 typo / typo fixes
- Star ⭐ 鼓励 / for encouragement

## 许可证 / License

[MIT](LICENSE) © 2026 码力全开 (xsqorange)

## 作者 / Author

**码力全开** — Java/Spring 工程师转 Agent 开发
- GitHub: [@xsqorange](https://github.com/xsqorange)
- Email: `xsqorange@gmail.com`
- 学习路径: [`agent-dev-crash-course`](../agent-dev-crash-course) 2 周速成版

## 致谢 / Acknowledgments

- [`agent-dev-crash-course`](https://github.com/nousresearch/hermes-agent) — 2 周速成路径
- [`agent-dev-learning`](https://github.com/nousresearch/hermes-agent) — 10 周完整路径
- Hermes Agent — 编排这一切的 AI 助手
- Anthropic / OpenAI / DeepSeek / Qwen — LLM 提供方
