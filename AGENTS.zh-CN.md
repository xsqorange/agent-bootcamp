# 项目 AI 助手指南 / Repository Guidelines (中文版)

> **中文**:`agent-bootcamp` 是一个 Java 17 / Maven 项目,从零构建一个可用的 CLI LLM 编程 Agent,并附带一个仅依赖 Python 标准库的 MCP 客户端。贡献前请阅读以下约定。
>
> **English**: `agent-bootcamp` is a Java 17 / Maven project that builds a CLI LLM coding agent from scratch, plus a small Python stdlib-only MCP client. Follow the conventions below when contributing.
> 英文原版:[AGENTS.md](AGENTS.md)。本文件是其中文镜像,约定变更请同步两个文件 / Keep both files in sync.

## 项目结构与模块划分 / Project Structure & Module Organization

Java 源码根目录:`src/main/java/com/agentbootcamp/`。功能子包:

- `agents/` — 多 Agent 核心:`Orchestrator` / `WorkerAgent` / `Message`(`sealed` 层次),Day 10 的 3-Agent 代码评审小组(`ResearcherAgent` / `CriticAgent` / `EditorAgent`)以及 `CodeReviewOrchestrator`
- `tools/` — 七个内置工具:`GetCurrentTime` / `ReadFile` / `WriteFile` / `Grep` / `Exec` / `SearchKb` / `EditFile`
- `evals/` — JSON 驱动的 E2E 评测框架:`EvalCase` / `EvalResult` record + `EvalHarness`(Day 5)
- `metrics/` — Micrometer 1.12.5 门面 + Prometheus 输出 + `CostCalculator`(Day 11)
- `safety/` — Resilience4j `ResilientLlmClient`(熔断 + 重试 + 超时)和 `PromptGuard`(Day 12)
- `testing/` — `ScriptedLlmClient`,无 key 可重放的 E2E 测试桩(Day 12)
- `mcp/` — JSON-RPC 2.0 over stdio 服务端 + 工具适配器(Day 9)
- `server/` — `HttpServerMain`,基于 JDK 自带 `com.sun.net.httpserver` 的 HTTP 服务,提供 `/health` / `/metrics` / `/api/run` 端点供 K8s 探活(Day 13)

`src/main/resources/knowledge/` 存放 RAG 知识库(5 个 Markdown 文件,被 `RagIndex` 加载)。测试代码镜像主包结构,放在 `src/test/java/` 下。黄金评测用例位于 `evals/cases/`(共 10 个,例如 `01-write-file-creates.json`),`evals/reports/` 是 gitignored 输出。`mcp-client/` 是跨语言 Python 客户端(仅依赖 stdlib,零外部依赖)。`k8s/` 存放 Kubernetes 清单(Deployment + Service + Secret 模板),`docker-compose.yml` 用于本地容器编排,可选 Prometheus profile。

**路线图**:14 天速成;Day 1-12 已完成,Day 13(部署)在本分支(`day13`)进行中,Day 14(收尾发布)待开始。详细见 [README.md](README.md) 中的日进度表。


## 构建、测试与开发命令 / Build, Test, and Development Commands

使用 wrapper `./mvnw`(Windows:`mvnw.cmd`)。

- `./mvnw compile` — 编译主源码(必须 0 warning)
- `./mvnw test` — JUnit 5 单元测试,无需 API key,跑 3-30 秒(含 `ResilientLlmClient` 真超时用例)
- `./mvnw verify` — 完整单测 + E2E 评测,需要 `MINIMAX_API_KEY`(或其他厂商 key)
- `./mvnw package` — 打 shade fat-jar 到 `target/agent-bootcamp.jar`
- `./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '...'"` — CLI 跑单个目标
- `./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--server"` — Day 13 启动 HTTP server(默认 8080)
- `./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.mcp.McpServerMain"` — 启动 MCP stdio server(Day 9)
- `./demo-script.sh` — 5 条命令 60 秒演示脚本(Day 6)
- `python mcp-client/test_e2e.py -v` — 跨语言 E2E(需要已构建的 jar)
- `docker compose up -d` — 本地容器 + 可选 `--profile monitoring` Prometheus
- `kubectl apply -f k8s/` — 生产 K8s 部署清单


## 编码风格与命名约定 / Coding Style & Naming Conventions

- Java 17,`--release 17`。构建必须保持 0 warning。
- 4 空格缩进,不用 Tab,不用通配符 import。
- 公共类和 JavaDoc 必须中英双语(中文 + English)。保持与现有风格一致。
- 类名 PascalCase;方法和字段 camelCase。工具类用名词(`ReadFile`);暴露给 LLM 的工具名用 snake_case(`read_file` / `search_kb` / `get_current_time`)。
- 增加构造器参数时,加更高 arity 的构造器并向下 delegate,保证旧测试仍可编译(参考 `Agent` 的 5/6/7/8 参链,以及 `LlmClient` → `ResilientLlmClient` 的装饰)。
- 值载体优先用 record(`AgentStep` / `Message` / `EvalCase` / `GuardResult` 等)。
- `-parameters` 编译选项**必须**(`pom.xml` 已设置),Jackson 反序列化 record 时才能保留字段名 — 见 `docs/runbook.md` 和 README Day 5 坑 #17。
- 没有项目级 formatter,跟随现有风格。**禁止**新增传递性依赖,如需新增请在 `README.md` 中说明。


## 测试指南 / Testing Guidelines

- 框架:JUnit Jupiter 5.10.3。每个源类对应一个 `<Subject>Test`(目前共 16 个测试类)。
- 方法名带编号且双语:`void testNN_<topic>()`(例如 `test12_SearchKbFindsDay3Tools`)。
- 优先用 `ScriptedLlmClient` 写 E2E,让 `Build` workflow 保持无 key、可重现;只有手动触发的 `Eval (E2E)` workflow 才用真实 LLM。
- 新工具必须配套至少一个单元测试(放在 `src/test/java/.../tools/`)**加**一份黄金用例 `evals/cases/<id>-<topic>.json`(`EvalHarness` 会因此 fail)。
- 故意等真实超时的测试(如 `ResilientLlmClientTest`)必须加文档说明且单测 wall clock 控制在 30s 内。
- 跑单个测试:`./mvnw test -Dtest=AgentTest#test12_SearchKbFindsDay3Tools`。
- 跑跨语言 Python 套件:`python mcp-client/test_e2e.py -v`。


## 提交与 PR 规范 / Commit & Pull Request Guidelines

- Conventional Commits:`feat(dayN): ...` / `refactor(dayN): ...` / `test(dayN): ...` / `docs(...): ...` / `fix(...): ...` / `chore: ...`。Day merge 用 `Merge dayN: + <summary>`。
- Subject ≤ 72 字符;body 中英双语。scope 里必须带路线图日编号。
- PR 标题与 commit 标题一致,描述动机 + 改动 + 测试证据(如 `mvn test` 结果摘要),并关联 issue 或路线图日。`Build` workflow 必须绿;`Eval (E2E)` 每周一次或手动触发,不是 merge gate。
- 借助 AI 生成的 commit 建议加 `co-authored-by` trailer(如 `Hermes Agent <hermes@local>`)。


## 安全与配置提示 / Security & Configuration Tips

- 密钥放 `.env`(已 gitignore)。跑 `./mvnw verify` 前加载:`set -a; source .env; set +a`(bash)或你 shell 的等价写法。
- 必填:`MINIMAX_API_KEY` / `LLM_BASE_URL` / `LLM_MODEL`。可选:`OPENAI_API_KEY` / `ANTHROPIC_API_KEY` / `DEEPSEEK_API_KEY`,以及 `LLM_COST_INPUT_PER_1M` / `LLM_COST_OUTPUT_PER_1M` 自定义成本单价。
- **永远不要** commit `.env` / `*.key` / `*.pem`。`Build` workflow 无密钥 — 需要真实 LLM 的测试请用 `ScriptedLlmClient` 或 `@EnabledIfEnvironmentVariable` 守门。
- K8s 部署时,把环境变量镜像成 Secret(`kubectl create secret generic agent-bootcamp-secrets --from-literal=MINIMAX_API_KEY=...`),在 `k8s/deployment.yaml` 用 `envFrom.secretKeyRef` 引用;YAML 里**绝对不要**硬编码 key。


## Agent 相关指示 / Agent-Specific Instructions

- 工具必须实现 `Tool`(`src/main/java/com/agentbootcamp/Tool.java`):稳定 snake_case 的 `name()`、简明的 `description()`、合法的 `jsonSchema()`,以及 `execute(Map<String, Object>)`(失败时抛 `Exception`)。参数永远是 `Map<String, Object>`。
- 相对路径必须用 `workDir.resolve(raw).normalize()`;只有 `raw.isAbsolute()` 才直接 `Paths.get()`。详见 `docs/runbook.md` 故障 7。
- 通过 `TraceWriter` 把结构化 `AgentStep` 追加到 `target/trace.jsonl`;测试模式下**不要**把工具 I/O 直接打到 stdout(改用 JSONL trace)。
- 多 Agent 消息用 `agents.Message` 的 `sealed` 层次(`Task` / `Result` / `Error`);Orchestrator ↔ Worker 通过 `BlockingQueue` 通信时,靠 `correlationId` 配对请求/响应。
- 可靠性:`LlmClient` 在生产路径上必须由 `ResilientLlmClient` 装饰(Day 12)— 需要安全模式默认值时,调 `Main.createLlmClient(config, metrics)` 而不是直接 `new LlmClient(config)`。
- 新能力在类 JavaDoc 中标注路线图日编号,配套至少一个单元测试;若是工具,再加一份黄金评测用例。


## 延伸阅读 / Where to Read More

- [README.md](README.md) — 快速开始、14 天路线图、每日踩坑
- [docs/runbook.md](docs/runbook.md) — 8 个常见故障 + 排查命令
- [docs/deploy.md](docs/deploy.md) — Day 13 进程 / Docker / K8s 三种部署方式
- [mcp-client/README.md](mcp-client/README.md) — 跨语言 MCP 客户端协议说明
- [AGENTS.md](AGENTS.md) — English version of this file (bilingual mirror)
