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
| 4 | 记忆 + 简易 RAG / Memory + simple RAG | 滑动窗口 + 内存索引 + `search_kb` 工具 | ✅ |
| **5** | **评测脚手架 / Eval harness** | **10 个黄金用例 + JUnit harness + `mvn verify` 全过** | **✅** |
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

## Day 4 架构 / Day 4 Architecture (+ 记忆 + RAG)

**Day 3 → Day 4 的本质变化**:`Agent` 从"**5 个工具 + 10 个 TC**"升级到"**6 个工具 + 42 个测试 + 记忆压缩 + 简易 RAG**"。

**关键变化 / Key changes**:
- 🧠 **新增 `MemoryManager`**:滑动窗口 + 摘要压缩,防止长对话把 context 撑爆
  - 阈值(Q2=C 宽松):`messages.size() > 24` OR `totalTokensIn > 10000`
  - 策略:保留 `[0] system prompt + [1] first user + 最近 8 条`,中间发 LLM 总结,替换成 1 条 system 消息
  - 容错:LLM 抛异常/空响应 → fallback 直接丢弃中间消息
  - 永不压缩 system prompt / 第一条 user(否则 LLM 不知道有这些工具 / 丢失用户最初目标)
- 📚 **新增 `RagIndex`**:简易 RAG 索引(纯内存,keyword TF 评分)
  - 启动时扫 `src/main/resources/knowledge/` 下的所有 .md
  - chunk(Q3=A):按段切(双换行) ≤ 500 字符,> 500 用滑窗(overlap 50)
  - search:keyword overlap 评分,中英文都支持
- 🔍 **新增 `search_kb`**:第 6 个工具
  - 参数:`query`(必填) + `max_results`(1-10,默认 3)
  - 返回:`--- chunk #N in file ---` 格式
- 🧪 **27 个新测试**(7 MemoryManager + 11 RagIndex + 6 SearchKb + 3 E2E TC-11/12/13)
- 📁 **5 个知识库 .md** 文件(overview / tools / progress / java-tips / git-workflow)

### 6 个工具一览 / 6 Tools Overview

```
┌─────────────────────────────────────────────────────────────┐
│  Agent (6 tools registered)                                 │
│                                                              │
│  1. get_current_time  Day 1  返当前时间(带时区) / current time│
│  2. read_file         Day 1  读文件(限 100KB)  / read 100KB   │
│  3. write_file        Day 3  写文件(覆盖,1MB) / write 1MB     │
│  4. grep              Day 3  Java 正则搜文件  / regex search  │
│  5. exec              Day 1  shell(5s 超时)    / shell exec   │
│  6. search_kb         Day 4  内存 RAG 搜知识库 / in-memory RAG │
└─────────────────────────────────────────────────────────────┘
```

### Day 4 新增文件 / Day 4 New Files

| 文件 / File | 作用 / Purpose |
|---|---|
| `MemoryManager.java` | 滑动窗口 + LLM 摘要压缩(`shouldCompress` / `compress` / fallback) |
| `RagIndex.java` | 简易 RAG 索引(段切 chunk + keyword TF 评分 + `search`) |
| `tools/SearchKb.java` | 第 6 个工具(包装 `RagIndex.search`,输出 `--- chunk ---` 格式) |
| `src/main/resources/knowledge/*.md` | 5 个知识库文件(overview / tools / progress / java-tips / git-workflow) |
| `src/test/java/.../MemoryManagerTest.java` | MemoryManager 单元测试 (7 cases) |
| `src/test/java/.../RagIndexTest.java` | RagIndex 单元测试 (11 cases) |
| `src/test/java/.../tools/SearchKbTest.java` | SearchKb 单元测试 (6 cases) |

### Day 4 修改文件 / Day 4 Modified Files

| 文件 / File | 改了什么 / What changed |
|---|---|
| `Agent.java` | 加 6 参构造器(5 参 delegate 保持向后兼容);`run()` 每步前调 `memory.compressIfNeeded()` |
| `Main.java` | 加载 `RagIndex`(classpath → 文件系统兜底);注册 `SearchKb`;加 `--no-memory` flag;version 升到 0.4.0 |

### 3 个新增黄金用例 (TC-11 ~ TC-13)

| TC | 目标 / Goal | 预期 / Expected | 验什么 / What it tests |
|---|---|---|---|
| **TC-11** | 启用 `MemoryManager` 跑简单任务 | `FINAL_ANSWER`,跑通不挂 | **memory 启用不破坏正常流程** |
| **TC-12** | `用 search_kb 找 Day 3 加的工具` | 答案含 `write_file` + `grep` | **RAG 找到正确知识** |
| **TC-13** | `search_kb 查 "Day 3 write_file grep" + 写 target/test-tc13.txt` | 文件存在,内容提到 write_file / day 3 | **RAG + write_file 多工具组合** |

**跑法 / How to run**:
```bash
set -a && source .env && set +a
./mvnw test                                # 跑全部 42 个测试(~71s,烧 ~$0.005)
./mvnw test -Dtest='MemoryManagerTest,RagIndexTest,SearchKbTest'  # 只跑 Day 4 单元(0.1s,不烧钱)
./mvnw test -Dtest=AgentTest#test12_SearchKbFindsDay3Tools       # 单个 E2E
```

## Day 5 架构 / Day 5 Architecture (评测脚手架)

**Day 4 → Day 5 的本质变化**:`Agent` 从"6 工具 + 42 测试"升级到"**JSON 驱动的评测 harness**" —— 10 个黄金用例写在 `evals/cases/*.json`,`mvn verify` 一键跑完,**Day 6+ 调参不再盲调**。

**关键变化 / Key changes**:
- 📁 **新增 `evals/cases/*.json`** ×10 — 黄金用例声明式定义(非工程师也能改)
- ⚙️ **新增 `EvalHarness`** — 跑 Agent + 4 条断言(工具调用 / 答案内容 / 步数 / 成本)+ 可选 `post_check` 文件副作用
- 🧪 **新增 `EvalRunnerTest`** — JUnit 5 `@TestFactory` 动态生成 10 个测试,`mvn verify` 一键跑完
- 🛡️ **EvalHarness 内置 429 retry-with-backoff** — 应对 LLM API 限流,跑评测更稳
- 🐛 **修 3 个 Day 5 真坑**:
  - record 反序列化需要 `-parameters` 编译选项(否则组件名变 `arg0/arg1`)
  - JSON `snake_case` → record `camelCase` 命名策略
  - 多个 case 连续跑触发 LLM 429 rate limit → 加 retry
- 📊 **52 个测试全过(42 Day 1-4 + 10 EvalCase)**, `mvn verify` 一次过

### 评测架构图 / Eval Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  mvn verify                                                  │
│      ↓                                                       │
│  EvalRunnerTest (@TestFactory 动态测试)                       │
│      ↓ 加载 evals/cases/*.json                               │
│  List<EvalCase>                                              │
│      ↓ 每个 case                                              │
│  EvalHarness.runCase(case)  ← retry 3x on 429                │
│      ├─ 准备: TraceWriter + 6 工具 + RAG 索引 + Memory       │
│      ├─ Agent.run(case.prompt)  → RunResult                  │
│      ├─ collectCalledTools(trace.jsonl)                      │
│      └─ 4 断言 + post_check → EvalResult                    │
│      ↓                                                       │
│  写 evals/reports/<id>.jsonl (trace,gitignore)              │
│  打印 Day 5 Eval Report 汇总 (通过率 / 总成本 / 总步数)       │
└─────────────────────────────────────────────────────────────┘
```

### 4 条断言 / 4 Hard Assertions

| # | 断言 | 怎么验 | 失败例子 |
|---|---|---|---|
| 1 | `must_call_tools ⊆ calledTools` | 从 `trace.jsonl` 解析 `executions[].name` | 期望 `["write_file"]`,实际没调 |
| 2 | `must_contain_in_final ⊆ finalAnswer` | case-insensitive substring | 期望 `"EXEC-DONE"`,答案没这个字 |
| 3 | `totalSteps ≤ max_steps` | `RunResult.totalSteps()` | 设 5,实际跑了 6 |
| 4 | `totalCostUsd ≤ max_cost_usd` | `RunResult.totalCostUsd()` | 设 0.10,实际烧 0.15 |
| 5 | (可选) `post_check` | `file_exists` / `file_equals` / `file_contains` | 文件没创建 / 内容不匹配 |

### 10 个黄金用例 / 10 Golden Test Cases (TC-1 ~ TC-10)

| TC | id | 目标 / Goal | 验什么 / What it tests | max_steps | max_cost |
|---|---|---|---|---|---|
| 01 | write-file-creates | 创建 `target/eval-tc01.txt` 内容 'eval-tc01-payload' | write_file **新建** | 5 | $0.10 |
| 02 | write-file-overwrites | 覆盖 `target/eval-tc02.txt` 为 'EVAL-TC02-NEW' | write_file **覆盖** | 5 | $0.10 |
| 03 | grep-finds-day1 | grep README.md 找 'Day 1' | grep **找到** + 含 'Day 1' | 5 | $0.10 |
| 04 | grep-finds-nothing | grep 找不存在的 'xyzzy_...' | grep **没找到** | 5 | $0.10 |
| 05 | read-then-write-combo | 读 README 写第一行到新文件 | **多工具组合** (read+write) | 8 | $0.15 |
| 06 | get-current-time | 调 get_current_time | 单工具 + FINAL_ANSWER | 3 | $0.05 |
| 07 | memory-enabled-completes | 启用 MemoryManager 跑简单任务 | **memory 不破坏** | 5 | $0.10 |
| 08 | search-kb-day3 | search_kb 查 Day 3 内容 | **RAG 找到** + 答出 'Day 3' | 5 | $0.10 |
| 09 | search-kb-then-write | search_kb + write_file 写总结 | **RAG + write_file 组合** | 8 | $0.15 |
| 10 | list-java-via-exec | exec 数 .java 文件 | exec 跑命令 + 答数 | 8 | $0.15 |

**跑法 / How to run**:
```bash
set -a && source .env && set +a

# 跑全套 10 个 EvalCase + Day 1-4 单元测试 (~3 分钟,烧 ~$0.012)
./mvnw verify

# 只跑 EvalCase(10 个)
./mvnw test -Dtest=EvalRunnerTest

# 单个 case
./mvnw test -Dtest=EvalRunnerTest  # 配合 IDE 的 -Dtest.method=... (IntelliJ 可,CLI 难)

# 查看 trace
cat evals/reports/01-write-file-creates.jsonl | head -5 | python3 -m json.tool
```

### Day 5 新增文件 / Day 5 New Files

| 文件 / File | 作用 / Purpose |
|---|---|
| `evals/cases/01-write-file-creates.json` | 黄金用例 1 — write_file 新建 |
| `evals/cases/02-write-file-overwrites.json` | 黄金用例 2 — write_file 覆盖 |
| `evals/cases/03-grep-finds-day1.json` | 黄金用例 3 — grep 找到 |
| `evals/cases/04-grep-finds-nothing.json` | 黄金用例 4 — grep 找不到 |
| `evals/cases/05-read-then-write-combo.json` | 黄金用例 5 — 多工具组合 |
| `evals/cases/06-get-current-time.json` | 黄金用例 6 — 单工具 |
| `evals/cases/07-memory-enabled-completes.json` | 黄金用例 7 — memory 兼容 |
| `evals/cases/08-search-kb-day3.json` | 黄金用例 8 — RAG 命中 |
| `evals/cases/09-search-kb-then-write.json` | 黄金用例 9 — RAG + write |
| `evals/cases/10-list-java-via-exec.json` | 黄金用例 10 — exec |
| `src/main/java/com/agentbootcamp/evals/EvalCase.java` | record + JSON 工厂 (snake_case → camelCase) |
| `src/main/java/com/agentbootcamp/evals/EvalHarness.java` | 跑 + 4 断言 + 429 retry (核心 ~200 行) |
| `src/main/java/com/agentbootcamp/evals/EvalResult.java` | record + passed/failed 工厂 |
| `src/test/java/com/agentbootcamp/evals/EvalRunnerTest.java` | JUnit 5 `@TestFactory` 动态测试 |

### Day 5 修改文件 / Day 5 Modified Files

| 文件 / File | 改了什么 / What changed |
|---|---|
| `pom.xml` | `maven-compiler-plugin` 加 `<parameters>true</parameters>`(Day 5 修坑 #1) |
| `.gitignore` | 加 `evals/reports/`(trace 永不 commit) |
| `Main.java` | 暂时未动(后续可加 `--eval` flag 一键跑评测) |

### Day 5 修的 3 个真坑 / 3 Real Bugs Day 5 Fixed

#### 坑 #1: Jackson 反序列化 record 必须有 `-parameters` 编译选项
- **症状**:10 个 case 全部 NPE 挂 `Expected.mustCallTools() is null`
- **根因**:Java 14+ 的 record 组件名在编译时**默认擦除**为 `arg0/arg1`,Jackson 反射找不到字段
- **修法**:`maven-compiler-plugin` 加 `<parameters>true</parameters>` 让组件名保留进 class 文件
- **影响**:Day 1-4 的 record (`RunResult` / `AgentStep` / `LlmResponse`) 都是**构造/序列化**用,不受影响;Day 5 第一次**反序列化** record 才踩到

#### 坑 #2: JSON snake_case ↔ record camelCase
- **症状**:`must_call_tools` JSON 字段对应不上 record 的 `mustCallTools` 组件
- **根因**:Jackson 默认不做命名策略转换
- **修法**:`ObjectMapper` 配 `PropertyNamingStrategies.SNAKE_CASE`

#### 坑 #3: LLM API 429 rate limit
- **症状**:跑 10 个 case 连续,后半截 case 报 `429 Token Plan ... 并发过高`
- **根因**:MiniMax API 对短时间多次请求有限流,`mvn verify` 跑 50+ 测试很容易撞
- **修法**:`EvalHarness.runCase()` 内置 3 次重试,指数退避 5s / 15s,只对 429 重试
- **收益**:`mvn verify` 跑通率从 9/10 提升到 10/10

### Day 5 验收清单 / Day 5 Acceptance

- [x] 10 个黄金用例 JSON 全部存在
- [x] `mvn verify` 跑通,4 断言全生效
- [x] 通过率 10/10 (100%)
- [x] `evals/reports/<id>.jsonl` 每用例如实写盘
- [x] 3 commit 全部推到 `origin/day5`
- [x] README Day 5 详细章节就位
- [x] 总成本 $0.0081 (10 case 跑 2 次,加上 retry)
- [x] 总时长 ~3 分钟 (`mvn verify` 整套)

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

### Day 4 必看
11. **滑动窗口的"丢中间" vs "总结中间"** — Day 4 选总结中间(更保真),但**总结本身有 token 成本**(又要调 LLM)。高吞吐场景下考虑用规则截断(`truncate(messages, keepLastN=8)` 拿掉老内容)省 token。
12. **MemoryManager 永远保留 system + first user** — 这两个是"骨架",system 是工具 schema + 角色,first user 是用户最初目标。任何压缩策略都不能动它们,否则 LLM 会"失忆"或"不知道自己是谁"。
13. **RAG 不引 embedding 的代价** — Day 4 用 keyword TF,优点:无外部依赖、零成本、快;缺点:同义词("工具" vs "tool")、多语言、词形变化都搜不到。Day 8+ 上 embedding 升级。
14. **chunk 切分按段 vs 按字符** — 按段(双换行)优先,语义最干净;但**单段超长会被滑窗切碎**,可能把"概念 A 的描述"和"概念 B 的描述"切到同一个 chunk,搜"概念 A"时会带出概念 B 的内容。
15. **`@TempDir` 又救了一命** — 跟 Day 3 `Paths.get` bug 一样,生产能跑 ≠ 测试能过。Day 4 的 `RagIndexTest.testNullDir()` / `testSearchEmptyQuery()` / `testCompressFallbackOnLlmError` 这类边界 case,**真 API + 真人手测都难暴露**,只有单元测试能抓。
16. **`Agent` 5 参构造器保留是给 Day 1-3 测试用** — 加 memory 是破坏性变更,但通过 5 参 delegate 到 6 参(`memory=null`),**Day 1-3 的 15 个测试一行不用改**。这是"加 feature 不 break 现有用户"的教科书姿势。

### Day 5 必看
17. **`-parameters` 编译选项必加** — 写一个反序列化 record 的代码(比如从 JSON 读 EvalCase)才会踩到。没它 record 组件名变 `arg0/arg1`,Jackson 反射找不到字段。**Day 1-4 没踩因为都是构造/序列化 record**。修法:`maven-compiler-plugin` 加 `<parameters>true</parameters>`。
18. **JSON 命名策略要显式配** — Jackson 默认不做 snake_case ↔ camelCase 转换。JSON 写 `must_call_tools` 就要在 `ObjectMapper` 配 `PropertyNamingStrategies.SNAKE_CASE`,否则 record 组件全 null。
19. **LLM API 429 是日常不是例外** — 跑评测一定要在 harness 里加 retry-with-backoff。Day 5 经验:`mvn verify` 跑 10+ 个真 LLM case 必撞 429,没 retry 评测天天飘。
20. **断言别假设 LLM 答案格式** — 模型说 "WriteFile" / "`write_file`" / "write_file" 都对(语义一致),但断言要 case-insensitive + 不假设具体大小写格式,否则每次 run 都可能飘。
21. **`@TestFactory` 动态测试是 harness 标配** — 比 10 个 `@Test` 方法清爽一万倍,`List<EvalCase>` 加新 case 不用动 Java 代码。`mvn verify` 自动跑新 case,`mvn -Dtest=EvalRunnerTest` 也能全跑。
22. **eval reports 一定要 gitignore** — `evals/reports/<id>.jsonl` 跟普通 trace 一样可能含 prompt 数据,绝不入库。`.gitignore` 加 `evals/reports/` 一劳永逸。

## 进度记录 / Progress Log

| Day | 日期 | 完成情况 | 笔记 |
|---|---|---|---|
| 1 | 2026-06-05 | ✅ 项目骨架 + LlmClient + 3 tools | 推到 GitHub: `xsqorange/agent-bootcamp` |
| 2 | 2026-06-06 | ✅ ReAct 循环 + StopReason + JSONL trace | 5 个黄金测试用例待跑通 |
| 3 | 2026-06-07 | ✅ + 2 工具 (write_file, grep) + 10 黄金用例 + 修 2 个 Day 2 bug | 15 个测试 (10 单元 + 5 端到端) 全过 |
| 4 | 2026-06-08 | ✅ + MemoryManager + RagIndex + search_kb (6 工具) + 5 知识库 .md | 42 个测试 (24 单元 + 8 端到端) 全过, 编译 0 warning, 已 push + merge 到 main |
| **5** | **2026-06-09** | **✅ + EvalHarness + 10 黄金用例 JSON + JUnit 动态测试 + 429 retry** | **52 个测试 (32 单元 + 20 端到端) 全过, mvn verify ~3 分钟, 烧 $0.0081, 已 push + merge 到 main** |

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
