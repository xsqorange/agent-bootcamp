# Agent Bootcamp / Agent 训练营

> **A 2-week learning sprint to build a working LLM coding agent in Java from scratch.**
> **两周速成:从零用 Java 构建一个能用的 LLM 编程 Agent。**

[![Java 17](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-orange.svg)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

## 简介 / Overview

**中文**:**目标:两周内(每天 6 小时)从零搭一个能用的 CLI 编程 Agent,然后扩展到多 Agent、生产部署**。

**English**: **Goal: in 2 weeks (6 hr/day), build a working CLI coding agent from scratch, then extend to multi-agent and production deployment.**

## 14 天路线图 / 14-Day Roadmap

| Day | 主题 / Topic | 交付物 / Deliverable | 状态 |
|---|---|---|---|
| 1 | LLM 101 + 工具调用 / LLM 101 + Tool calling | `LlmClient` + 3 tools + 单次调用 / single call | ✅ |
| 2 | ReAct 循环 / ReAct loop | `while` 循环 + StopReason + JSONL trace | ✅ |
| 3 | + 2 工具 + 评测 / + 2 tools + evals | 5 工具 + 10 个黄金用例 / 5 tools + 10 golden cases | ⏳ |
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
| `Main.java` | picocli 入口,解析 `--goal`/`--max-steps`/`--trace` 参数 / CLI entry, parses flags |
| `LlmClient.java` | OpenAI 兼容协议的 HTTP 客户端 / HTTP client for OpenAI-compatible APIs |
| `LlmConfig.java` | 环境变量配置(API key / base URL / model) / Env-var config |
| `Tool.java` | 工具接口(所有 Tool 都实现这个)/ Tool interface |
| `Message.java` | 4 种消息类型 record(system / user / assistant / tool) |
| `Agent.java` | **Day 2**:完整 ReAct `while` 循环 / Full ReAct loop |
| `AgentStep.java` | **Day 2**:一步执行的可追溯快照 / one-step trace record |
| `RunResult.java` | **Day 2**:run() 返回值(含停止原因 + 步数 + 成本) |
| `StopReason.java` | **Day 2**:4 种停止原因枚举 / 4 stop-reason enum |
| `TraceWriter.java` | **Day 2**:JSONL 追加写入器 / JSONL append writer |
| `tools/GetCurrentTime.java` | 工具 1:返回当前时间 / Tool 1: returns current time |
| `tools/ReadFile.java` | 工具 2:读文件(限 100KB)/ Tool 2: reads files (100KB cap) |
| `tools/Exec.java` | 工具 3:执行 shell 命令(5s 超时)/ Tool 3: shell exec (5s timeout) |

## Day 2 架构 / Day 2 Architecture (ReAct 循环)

**Day 1 → Day 2 的本质变化**:`Agent.runOnce()` 单次调用 → `Agent.run()` 完整 while 循环。

```
   ┌──────────────────────────────────────────────────┐
   │  while (step < maxSteps && cost < maxCost) {     │
   │      1. 思考: LLM(messages, tools)               │
   │      2. if (没 tool_calls) return content         │
   │      3. 行动: 工具们(并行)                       │
   │      4. 观察: 把结果塞回 messages                 │
   │      5. trace.writeStep(...)                      │
   │  }                                                │
   │  stopReason ∈ {FINAL_ANSWER, MAX_STEPS, ...}     │
   └──────────────────────────────────────────────────┘
```

### Day 2 新增 CLI 参数 / Day 2 New CLI Flags

```bash
# 默认(10 步, $1 上限, 写到 target/trace.jsonl)
./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '...'"

# 自定义
./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '...' --max-steps 5 --max-cost 0.1 --trace my.jsonl"

# 关掉 trace
./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '...' --trace off"
```

### Trace 文件格式 / Trace File Format

每步 1 行 JSON,追加写入 `target/trace.jsonl`:
```json
{"step":1,"timestampMs":1717...,"userGoal":"现在几点?","llmContent":null,
 "toolCalls":[{"id":"call_abc","name":"get_current_time","argumentsJson":"{}"}],
 "executions":[{"toolCallId":"call_abc","name":"get_current_time","args":{},
   "result":"2026-06-06T10:30:00+08:00","ok":true,"durationMs":12,"errorMessage":null}],
 "tokensIn":145,"tokensOut":23,"tokensInTotal":145,"tokensOutTotal":23,
 "costUsdTotal":0.000035,"stopReason":null,"finalAnswer":null}
```

调试时:`tail -f target/trace.jsonl | jq .` 实时看每步。
**Why JSONL?**(而不是 JSON 数组):
- 边写边看,不用等 Agent 跑完
- 崩了也能恢复(断点续跑)
- 大 trace 不会一次性吃满内存

### 5 个黄金测试用例 / 5 Golden Test Cases

Day 2 验收 = 跑通这 5 个 case(每个 30 秒 - 1 分钟):

| TC | 目标 / Goal | 预期 / Expected | 验什么 / What it tests |
|---|---|---|---|
| **TC-1** | `现在几点(用 Asia/Shanghai 时区)?` | 1 step,1 `get_current_time` → final answer | 单工具 + FINAL_ANSWER |
| **TC-2** | `读 README.md 第 1 行` | 1 step,1 `read_file` → final answer | 单 read_file + FINAL_ANSWER |
| **TC-3** | `告诉我现在几点,然后读 README.md 第 1 行` | 1 step,2 tools 并行 → 2 step final answer | **并行工具调用** + 2 step |
| **TC-4** | `看 README.md,然后用 1 句话总结` | 1 step `read_file` + 2 step final answer | **多步决策**(读→总结) |
| **TC-5** | `--max-steps 1 --goal '看 README.md 然后总结'` | 1 step,MAX_STEPS 终止 | **停止条件**触发 |

跑法(以 DeepSeek 为例)/ How to run (DeepSeek example):
```bash
export DEEPSEEK_API_KEY="sk-..."
export LLM_BASE_URL="https://api.deepseek.com/v1"
export LLM_MODEL="deepseek-chat"

# TC-1: 单工具
./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '现在几点(Asia/Shanghai)?' --max-steps 5"

# TC-2: 单 read_file
./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '读 README.md 第 1 行' --max-steps 5"

# TC-3: 并行(模型自己决定调 2 个工具)
./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '现在几点(Asia/Shanghai)以及读 README.md 第 1 行' --max-steps 5"

# TC-4: 多步(读→总结)
./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '看 README.md 然后用 1 句话总结' --max-steps 5"

# TC-5: max-steps 终止
./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '看 README.md 然后总结' --max-steps 1"
```

**跑通任意 3 个 = Day 2 验收通过** ✓

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
2. **跟着做**:
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
| 2 | 2026-06-06 | ✅ ReAct 循环 + StopReason + JSONL trace | 5 个黄金测试用例待跑通 |

## 贡献 / Contributing

这是提供给有一定Java基础想面向Agent开发学习的项目,但欢迎:
- Issue 提问 / questions
- PR 修 typo / typo fixes
- Star ⭐ 鼓励 / for encouragement

## 许可证 / License

[MIT](LICENSE) © 2026 码力全开 (xsqorange)

## 作者 / Author

**码力全开** — Java/Spring 工程师转 Agent 开发
- GitHub: [@xsqorange](https://github.com/xsqorange)
- Email: `maliquankai123@gmail.com`

## 致谢 / Acknowledgments

- Hermes Agent — 编排这一切的 AI 助手
- Anthropic / OpenAI / DeepSeek / Qwen / MiniMax — LLM 提供方
