# Repository Guidelines

`agent-bootcamp` is a Java 17 / Maven project that builds a CLI LLM coding agent from scratch, plus a small Python stdlib-only MCP client. Follow the conventions below when contributing.

## Project Structure & Module Organization

Java source root is `src/main/java/com/agentbootcamp/`. Feature sub-packages:

- `agents/` — `Orchestrator`, `WorkerAgent`, `Message`, plus the 3-agent crew (`ResearcherAgent`, `CriticAgent`, `EditorAgent`)
- `tools/` — `GetCurrentTime`, `ReadFile`, `WriteFile`, `Grep`, `Exec`, `SearchKb`, `EditFile`
- `metrics/` — Micrometer integration (Day 11)
- `safety/` — Resilience4j + `PromptGuard` (Day 12)
- `testing/` — `ScriptedLlmClient` for replay-based E2E
- `mcp/` — JSON-RPC-over-stdio server (Day 9)

`src/main/resources/knowledge/` holds the RAG knowledge base. Tests mirror the main package under `src/test/java/`. Golden eval cases live in `evals/cases/`; `evals/reports/` is gitignored output. `mcp-client/` is the cross-language Python client.

## Build, Test, and Development Commands

Use the wrapper `./mvnw` (Windows: `mvnw.cmd`).

- `./mvnw compile` — compile main sources
- `./mvnw test` — JUnit 5 unit tests (no API key required)
- `./mvnw verify` — full unit + E2E eval (requires `MINIMAX_API_KEY`)
- `./mvnw package` — shaded fat-jar at `target/agent-bootcamp.jar`
- `./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '...'"` — run a single goal
- `./demo-script.sh` — 5-command ~60s demo for the recorded GIF
- `python mcp-client/test_e2e.py -v` — cross-language E2E (needs a built jar)

## Coding Style & Naming Conventions

- Java 17, `--release 17`. Keep the build at 0 warnings.
- 4-space indentation, no tabs, no wildcard imports.
- Public types and JavaDoc carry bilingual summaries (中文 + English). Match the existing style.
- Classes are PascalCase; methods and fields are camelCase. Tool classes are nouns (`ReadFile`); tool names exposed to the LLM are snake_case (`read_file`, `search_kb`, `get_current_time`).
- When adding constructor parameters, append a higher-arity constructor and delegate down to keep older tests compiling (see `Agent`).
- No project formatter is configured; match the surrounding layout. Do not add new transitive dependencies without a note in `README.md`.

## Testing Guidelines

- Framework: JUnit Jupiter 5.10.3. One `<Subject>Test` class per source class.
- Methods are numbered and bilingual: `void testNN_<topic>()` (e.g. `test12_SearchKbFindsDay3Tools`).
- Use `ScriptedLlmClient` for E2E so the `Build` workflow stays keyless and deterministic.
- New tools must ship with a matching golden case in `evals/cases/<id>-<topic>.json`.
- Run a single test: `./mvnw test -Dtest=AgentTest#test12_SearchKbFindsDay3Tools`.

## Commit & Pull Request Guidelines

- Conventional Commits: `feat(dayN): ...`, `refactor(dayN): ...`, `test(dayN): ...`, `docs(...): ...`, `fix(...): ...`, `chore: ...`. Day merges use `Merge dayN: + <summary>`.
- Subject ≤ 72 chars; bodies are bilingual. Reference the roadmap day in the scope.
- PRs mirror the commit title, describe motivation + change + test evidence (e.g. `mvn test` summary), and link the issue or roadmap day. The `Build` workflow must be green; `Eval (E2E)` runs weekly and is not a merge gate.

## Security & Configuration Tips

- Secrets live in `.env` (gitignored). Load before `./mvnw verify` with `set -a; source .env; set +a` (bash) or the equivalent in your shell.
- Required: `MINIMAX_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`. Optional: `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` / `DEEPSEEK_API_KEY`, plus `LLM_COST_INPUT_PER_1M` / `LLM_COST_OUTPUT_PER_1M` to override cost math.
- Never commit `.env`, `*.key`, or `*.pem`. The `Build` workflow has no secrets — keep tests that need the live LLM opt-in.

## Agent-Specific Instructions

- Tools must implement `Tool` with a stable snake_case `name()`, a short `description()`, and a valid `jsonSchema()`. Tool args are a `Map<String, Object>`.
- Resolve relative paths with `workDir.resolve(raw).normalize()`; only pass through `raw.isAbsolute()` paths unchanged. See `docs/runbook.md` fault 7.
- Append structured `AgentStep` records to `target/trace.jsonl` via `TraceWriter`; do not dump tool I/O to stdout in test mode.
- New capabilities should reference their roadmap day in the class JavaDoc and ship with at least one unit test plus, for tools, one golden eval case.
