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
| 3 | + 2 工具 + 评测 / + 2 tools + evals | 5 工具 + 10 个黄金用例 / 5 tools + 10 golden cases | ✅ |
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

## Day 3 架构 / Day 3 Architecture (+ 2 工具 + 评测)

**Day 2 → Day 3 的本质变化**:`Agent` 从"能调 3 个工具 + 手测 5 个 TC"升级到"能调 5 个工具 + 自动跑 10 个 TC"。

**关键变化 / Key changes**:
- ✏️ **新增 `write_file`**:Agent 从"只读"升级到"能改"
- 🔍 **新增 `grep`**:用 Java 正则跨平台搜文件(不依赖系统 grep)
- 🐛 **修了 2 个 Day 2 bug**:
  - trace 最后一行 `stopReason` 缺失(MAX_STEPS / COST_LIMIT 时不写)
  - `LlmConfig` 不认 `MINIMAX_API_KEY` 命名
- 🧪 **10 个黄金用例**(5 单元 × 2 工具 + 5 端到端)
- 🔧 **新工具 + 新测试共 ~700 行代码**

### 5 个工具一览 / 5 Tools Overview

```
┌─────────────────────────────────────────────────────────────┐
│  Agent (5 tools registered)                                 │
│                                                              │
│  1. get_current_time  Day 1  返当前时间(带时区)/ current time│
│  2. read_file         Day 1  读文件(限 100KB)  / read 100KB  │
│  3. write_file        Day 3  写文件(覆盖,限 1MB)/ write 1MB  │
│  4. grep              Day 3  Java 正则搜文件   / regex search│
│  5. exec              Day 1  shell(5s 超时)    / shell exec  │
└─────────────────────────────────────────────────────────────┘
```

### Day 3 新增文件 / Day 3 New Files

| 文件 / File | 作用 / Purpose |
|---|---|
| `tools/WriteFile.java` | 写文件工具(覆盖,1MB 限制,父目录自动创建) |
| `tools/Grep.java` | Java 正则搜文件工具(支持目录递归 5 层,限 1MB/文件) |
| `src/test/java/.../tools/WriteFileTest.java` | WriteFile 单元测试 (5 cases) |
| `src/test/java/.../tools/GrepTest.java` | Grep 单元测试 (5 cases) |
| `src/test/java/.../AgentTest.java` | 5 个端到端真 LLM 测试 (TC-6 ~ TC-10) |

### Day 3 修改文件 / Day 3 Modified Files

| 文件 / File | 改了什么 / What changed |
|---|---|
| `Agent.java` | 加 `writeSummaryTrace()` helper;COST_LIMIT / MAX_STEPS 时补写 stopReason(修 TC-5 bug) |
| `LlmConfig.java` | 加 `MINIMAX_API_KEY` 支持;识别 `sk-cp-` 前缀自动用 `https://api.minimaxi.com/v1` + `minimax-m3` |
| `Main.java` | 注册 WriteFile + Grep;version 升到 0.3.0 |

### 5 个新增黄金用例 (TC-6 ~ TC-10)

| TC | 目标 / Goal | 预期 / Expected | 验什么 / What it tests |
|---|---|---|---|
| **TC-6** | `用 write_file 创建 target/test-tc6.txt,内容 'tc6-payload'` | 1 step,文件创建,内容 = 'tc6-payload' | **write_file 新建** |
| **TC-7** | `用 write_file 覆盖 target/test-tc7.txt,内容 'NEW CONTENT - tc7'` | 1 step,内容 = 'NEW CONTENT - tc7' | **write_file 覆盖** |
| **TC-8** | `用 grep 搜 README.md 找 'Day 1'` | 1-2 steps,答案含 'Day 1' 和行号 | **grep 找到匹配** |
| **TC-9** | `用 grep 搜 README.md 找不存在的 'xyzzy_...'` | 1-2 steps,答案说没找到 | **grep 找不到** |
| **TC-10** | `读 README.md 第一行,写到 target/test-tc10.txt` | 2-3 steps,文件以 `#` 开头 | **多工具组合** (read + write) |

**跑法 / How to run**:
```bash
# 跑全套 15 个测试(10 单元 + 5 端到端,需 ~45 秒,烧 ~$0.003)
set -a && source .env && set +a
./mvnw test

# 跑某一个测试
./mvnw test -Dtest=AgentTest#test6_WriteFileCreatesNewFile

# 不跑端到端(只跑单元测试,~0.1 秒,不烧钱)
./mvnw test -Dtest='WriteFileTest,GrepTest'
```

### Day 3 修的 2 个 bug

#### Bug A: trace 缺最后一笔 stopReason
- **症状**:Day 2 TC-5 的 trace 末尾 `stopReason: null`(应该 `MAX_STEPS`)
- **根因**:`Agent.run()` 在 `break` 之前没写最后一行带 stopReason 的 trace
- **修法**:
  - `COST_LIMIT` 分支:break 前用 `writeTrace` 补写一次(带 stopReason)
  - `MAX_STEPS` 分支:while 循环结束后调新的 `writeSummaryTrace()` 写一行"印章"行
- **验证**:跑 `--max-steps 1 --goal '...'` 后 `tail target/trace.jsonl` 现在能看到 `step=2, stopReason="MAX_STEPS"` 的 summary 行

#### Bug B: LlmConfig 不认 `MINIMAX_API_KEY`
- **症状**:用户贴的 MiniMax key 只能塞到 `OPENAI_API_KEY` slot 才认
- **修法**:`LlmConfig.fromEnv()` 优先级列表加 `MINIMAX_API_KEY`;`defaultBaseUrl` / `defaultModel` 检测 `sk-cp-` 前缀时自动选 MiniMax 端点
- **副作用**:日志多了 `keyPrefix=sk-cp-Km...` 输出,便于调试

## 配置 LLM Provider / Configuring LLM Providers

默认走 **OpenAI**。要切到别的厂商,改环境变量即可:
Default is **OpenAI**. To switch, just set environment variables:

| Provider | `LLM_BASE_URL` | `LLM_MODEL` | API key env |
|---|---|---|---|
| **OpenAI** | `https://api.openai.com/v1` | `gpt-4o-mini` | `OPENAI_API_KEY` |
| **Anthropic** | `https://api.anthropic.com/v1`(需适配) | `claude-sonnet-4-20250514` | `ANTHROPIC_API_KEY` |
| **DeepSeek** | `https://api.deepseek.com/v1` | `deepseek-chat` | `DEEPSEEK_API_KEY` |
| **通义 Qwen** | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-max` | `DASHSCOPE_API_KEY` |
| **MiniMax (Day 3)** | `https://api.minimaxi.com/v1` | `minimax-m3` | `MINIMAX_API_KEY` (前缀 `sk-cp-`) |
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

## 常见坑 / Pitfalls

### Day 1-2 必看
1. **Java 17 没在 PATH** — 装 JDK 后加到 PATH,或用 IDE 内置
2. **API key 没设** — `export OPENAI_API_KEY=***` 或用 `.env` 加载
3. **网络问题** — 国内用 DeepSeek/通义比 OpenAI 快
4. **Maven 下载慢** — 配 `~/.m2/settings.xml` 用阿里云镜像
5. **忘了 `mvn compile`** — 跑之前先编译

### Day 3 必看
6. **相对路径解析错** — `Paths.get("foo.txt").toAbsolutePath()` 解析到 **JVM 的 `user.dir`**,不是 tool 构造时传的 `workDir`!正确做法:`workDir.resolve(relativePath)`,绝对路径才直接用 `Paths.get()`。**这个 bug 在用 `@TempDir` 单测时才暴露**,生产碰巧 work 是因为 Main 默认 workDir = cwd。
7. **MAX_STEPS / COST_LIMIT 时 trace 缺 stopReason** — `break` 之前要再写一次 `writeTrace()`,或循环结束后写一行 summary。修法见 Day 3 章节 "Bug A"。
8. **`LlmConfig` 不认你的 API key 命名** — 默认认 `OPENAI_API_KEY` / `DEEPSEEK_API_KEY` / `DASHSCOPE_API_KEY` / `ANTHROPIC_API_KEY`,其他名字要么自己加要么塞到 `OPENAI_API_KEY` slot。Day 3 加了 `MINIMAX_API_KEY`。
9. **LLM 返回了 Chain of Thought (`<think>...`)** — 一些模型(MiniMax m3)会把思考过程塞在 content 里,**导致 token 消耗翻倍**!要算成本时记得减掉。
10. **并行工具调用的"假象"** — `multi_tool_use.parallel` 不是模型**故意**并行,是 OpenAI 协议允许 1 个 assistant message 带 N 个 tool_calls,我们用 for 循环依次执行。要"真并行"得用 `ExecutorService` 调 N 个工具(Day 8+ 再优化)。

## 进度记录 / Progress Log

| Day | 日期 | 完成情况 | 笔记 |
|---|---|---|---|
| 1 | 2026-06-05 | ✅ 项目骨架 + LlmClient + 3 tools | 推到 GitHub: `xsqorange/agent-bootcamp` |
| 2 | 2026-06-06 | ✅ ReAct 循环 + StopReason + JSONL trace | 5 个黄金测试用例待跑通 |
| 3 | 2026-06-07 | ✅ + 2 工具 (write_file, grep) + 10 黄金用例 + 修 2 个 Day 2 bug | 15 个测试 (10 单元 + 5 端到端) 全过 |

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
