# Repository Guidelines

`agent-bootcamp` is a Java 17 / Maven project that builds a CLI LLM coding agent from scratch, plus a small Python stdlib-only MCP client. Follow the conventions below when contributing.
A Chinese companion guide lives at [AGENTS.zh-CN.md](AGENTS.zh-CN.md); keep both files in sync when conventions change.

## Project Structure & Module Organization

Java source root is `src/main/java/com/agentbootcamp/`. Feature sub-packages:

- `agents/` — multi-agent core: `Orchestrator`, `WorkerAgent`, `Message` (sealed hierarchy), plus the 3-agent code-review crew (`ResearcherAgent` / `CriticAgent` / `EditorAgent`) and `CodeReviewOrchestrator` (Day 10)
- `tools/` — seven built-in tools: `GetCurrentTime`, `ReadFile`, `WriteFile`, `Grep`, `Exec`, `SearchKb`, `EditFile`
- `evals/` — `EvalCase` / `EvalResult` records and `EvalHarness` for JSON-driven E2E (Day 5)
- `metrics/` — Micrometer 1.12.5 facade + Prometheus exporter + `CostCalculator` (Day 11)
- `safety/` — Resilience4j `ResilientLlmClient` (CircuitBreaker + Retry + TimeLimiter) and `PromptGuard` (Day 12)
- `testing/` — `ScriptedLlmClient` for replay-based, keyless E2E (Day 12)
- `mcp/` — JSON-RPC 2.0 over stdio server + tool adapter (Day 9)
- `server/` — `HttpServerMain`, the JDK-bundled `com.sun.net.httpserver` wrapper exposing `/health`, `/metrics`, `/api/run` for K8s (Day 13)

`src/main/resources/knowledge/` holds the RAG knowledge base (five Markdown files consumed by `RagIndex`). Tests mirror the main package under `src/test/java/`. Golden eval cases live in `evals/cases/` (10 cases, e.g. `01-write-file-creates.json`); `evals/reports/` is gitignored output. `mcp-client/` is the cross-language Python client (stdlib only, zero dependencies). `k8s/` holds the Kubernetes manifests (Deployment + Service + Secret template) and `docker-compose.yml` orchestrates local container runs with an optional Prometheus profile.

Roadmap: 14-day sprint; Days 1-12 are done, Day 13 (deploy) is in progress on this branch (`day13`), Day 14 (polish & ship) is pending. See [README.md](README.md) for the per-day table.


## Build, Test, and Development Commands

Use the wrapper `./mvnw` (Windows: `mvnw.cmd`).

- `./mvnw compile` — compile main sources (0 warnings required)
- `./mvnw test` — JUnit 5 unit tests, no API key required, ~3-30s depending on `ResilientLlmClient` real-timeout tests
- `./mvnw verify` — full unit + E2E eval, requires `MINIMAX_API_KEY` (or another provider key)
- `./mvnw package` — shaded fat-jar at `target/agent-bootcamp.jar`
- `./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '...'"` — run a single CLI goal
- `./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--server"` — start the Day 13 HTTP server (port 8080)
- `./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.mcp.McpServerMain"` — start the MCP stdio server (Day 9)
- `./demo-script.sh` — 5-command ~60s demo for the recorded GIF (Day 6)
- `python mcp-client/test_e2e.py -v` — cross-language E2E (needs a built jar)
- `docker compose up -d` — local container + optional `--profile monitoring` Prometheus
- `kubectl apply -f k8s/` — production K8s manifests

## Coding Style & Naming Conventions

- Java 17, `--release 17`. Keep the build at 0 warnings.
- 4-space indentation, no tabs, no wildcard imports.
- Public types and JavaDoc carry bilingual summaries (中文 + English). Match the existing style.
- Classes are PascalCase; methods and fields are camelCase. Tool classes are nouns (`ReadFile`); tool names exposed to the LLM are snake_case (`read_file`, `search_kb`, `get_current_time`).
- When adding constructor parameters, append a higher-arity constructor and delegate down to keep older tests compiling (see `Agent`'s 5-/6-/7-/8-arity chain and `LlmClient` → `ResilientLlmClient` decoration).
- Records are preferred for value carriers (`AgentStep`, `Message`, `EvalCase`, `GuardResult`, etc.).
- `-parameters` compiler flag is **mandatory** (already set in `pom.xml`) so Jackson can deserialize record components — see `docs/runbook.md` and README Day 5 pitfall #17.
- No project formatter is configured; match the surrounding layout. Do not add new transitive dependencies without a note in `README.md`.

## Testing Guidelines

- Framework: JUnit Jupiter 5.10.3. One `<Subject>Test` class per source class (16 test classes today).
- Methods are numbered and bilingual: `void testNN_<topic>()` (e.g. `test12_SearchKbFindsDay3Tools`).
- Prefer `ScriptedLlmClient` for E2E so the `Build` workflow stays keyless and deterministic; only the opt-in `Eval (E2E)` workflow touches the live LLM.
- New tools must ship with at least one unit test (under `src/test/java/.../tools/`) **and** a matching golden case in `evals/cases/<id>-<topic>.json` (`EvalHarness` will fail the run otherwise).
- Tests that intentionally wait for a real timeout (e.g. `ResilientLlmClientTest`) are documented and bounded (≤30s wall clock).
- Run a single test: `./mvnw test -Dtest=AgentTest#test12_SearchKbFindsDay3Tools`.
- Run the cross-language Python suite: `python mcp-client/test_e2e.py -v`.

## Commit & Pull Request Guidelines

- Conventional Commits: `feat(dayN): ...`, `refactor(dayN): ...`, `test(dayN): ...`, `docs(...): ...`, `fix(...): ...`, `chore: ...`. Day merges use `Merge dayN: + <summary>`.
- Subject ≤ 72 chars; bodies are bilingual (中文 + English). Reference the roadmap day in the scope.
- PRs mirror the commit title, describe motivation + change + test evidence (e.g. `mvn test` summary), and link the issue or roadmap day. The `Build` workflow must be green; `Eval (E2E)` runs weekly and on manual dispatch and is not a merge gate.
- Use `co-authored-by` trailers (e.g. `Hermes Agent <hermes@local>`) when the commit was produced with AI assistance.

## Security & Configuration Tips

- Secrets live in `.env` (gitignored). Load before `./mvnw verify` with `set -a; source .env; set +a` (bash) or the equivalent in your shell.
- Required: `MINIMAX_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`. Optional: `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` / `DEEPSEEK_API_KEY`, plus `LLM_COST_INPUT_PER_1M` / `LLM_COST_OUTPUT_PER_1M` to override cost math.
- Never commit `.env`, `*.key`, or `*.pem`. The `Build` workflow has no secrets — keep tests that need the live LLM opt-in (e.g. behind `ScriptedLlmClient` or a `@EnabledIfEnvironmentVariable` guard).
- When deploying via K8s, mirror the env file into a Secret (`kubectl create secret generic agent-bootcamp-secrets --from-literal=MINIMAX_API_KEY=...`) and reference it via `envFrom.secretKeyRef` in `k8s/deployment.yaml`; never hard-code keys in YAML.

## Agent-Specific Instructions

- Tools must implement `Tool` (`src/main/java/com/agentbootcamp/Tool.java`) with a stable snake_case `name()`, a short `description()`, a valid `jsonSchema()`, and an `execute(Map<String, Object>)` that throws `Exception` on failure. Tool args are always a `Map<String, Object>`.
- Resolve relative paths with `workDir.resolve(raw).normalize()`; only pass through `raw.isAbsolute()` paths unchanged. See `docs/runbook.md` fault 7.
- Append structured `AgentStep` records to `target/trace.jsonl` via `TraceWriter`; do not dump tool I/O to stdout in test mode (use the JSONL trace instead).
- Multi-agent messages use the sealed `agents.Message` hierarchy (`Task` / `Result` / `Error`); pair requests with their `correlationId` across Orchestrator ↔ Worker `BlockingQueue`s.
- Resilience: `LlmClient` should be wrapped by `ResilientLlmClient` in production paths (Day 12) — call `Main.createLlmClient(config, metrics)` rather than `new LlmClient(config)` directly when you need safe-mode defaults.
- New capabilities should reference their roadmap day in the class JavaDoc and ship with at least one unit test plus, for tools, one golden eval case.

## Where to Read More

- [README.md](README.md) — quick start, 14-day roadmap, per-day pitfalls
- [docs/runbook.md](docs/runbook.md) — 8 common faults + diagnostic commands
- [docs/deploy.md](docs/deploy.md) — Day 13 process / Docker / K8s deployment guide
- [mcp-client/README.md](mcp-client/README.md) — cross-language MCP client protocol notes
- [AGENTS.zh-CN.md](AGENTS.zh-CN.md) — 中文版项目 AI 助手指南 (bilingual mirror of this file)
