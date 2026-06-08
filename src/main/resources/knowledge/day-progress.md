# Day 1-3 进度回顾 / Day 1-3 Progress Recap

## Day 1 — 项目骨架 + LLM 101 (2026-06-05) ✅

**做了什么 / What was done**:
- `pom.xml` + `.gitignore` + `README.md` + `LICENSE`
- Maven Wrapper 3.9.9 (装在 `~/tools/apache-maven-3.9.9`)
- 9 个 Java 文件: `Main` / `Agent` (runOnce) / `LlmClient` / `LlmConfig` / `Message` / `Tool` / 3 tools
- 推到 GitHub: `xsqorange/agent-bootcamp`

**关键决定 / Key decisions**:
- Java 17 (Amazon Corretto)
- picocli (CLI) + Jackson (JSON) + JUnit 5 (test)
- OpenAI 兼容协议 (适用任何 vendor)

**Day 1 验收 / Day 1 acceptance**:
- 编译干净 + 单次 LLM 调用成功 + 推到 GitHub

## Day 2 — ReAct 循环 + StopReason + JSONL trace (2026-06-06) ✅

**做了什么**:
- 加 4 个新类型: `StopReason` (enum) / `AgentStep` (record) / `RunResult` (record) / `TraceWriter`
- `Agent.runOnce()` → `Agent.run()` 完整 while 循环
- 加 3 个 CLI 参数: `--max-steps` / `--max-cost` / `--trace`
- 5 个黄金测试用例 (TC-1 ~ TC-5)

**关键决定**:
- JSONL 追加写(每行 flush,崩了不丢)
- 4 种 StopReason: FINAL_ANSWER / MAX_STEPS / COST_LIMIT / ERROR
- 工具异常隔离(失败 → 错误字符串,不让循环挂)

**Day 2 验收**:
- 跑通 5 个 TC 任意 3 个

## Day 3 — + 2 工具 + 10 黄金用例 (2026-06-07) ✅

**做了什么**:
- 加 `write_file` (覆盖写入, 1MB 上限) + `grep` (Java regex, 跨平台)
- 修 2 个 Day 2 bug: trace 缺 stopReason / LlmConfig 不认 MINIMAX_API_KEY
- 10 个单元测试 (WriteFile 5 + Grep 5)
- 5 个端到端真 LLM 测试 (TC-6 ~ TC-10)
- **15/15 全过** in 46 秒, 成本 ~$0.003 USD

**关键坑 / Key pitfalls learned**:
- Windows 路径 bug: `Paths.get(rel).toAbsolutePath()` 用 `user.dir` 不是 `workDir`!
- LLM Chain-of-Thought (`<think>...`) 翻倍 token
- 并行工具调用不是真并行,是 1 个 assistant msg 带 N 个 tool_calls

## Day 3 工具总数: 5 个 (Day 4 会变 6 个)

| # | 工具 | 加在哪天 | 用途 |
|---|---|---|---|
| 1 | get_current_time | Day 1 | 返时间 |
| 2 | read_file | Day 1 | 读文件 |
| 3 | exec | Day 1 | shell |
| 4 | write_file | Day 3 | 写文件 |
| 5 | grep | Day 3 | 搜文件 |
| 6 | search_kb | **Day 4** | 搜知识库 (RAG) |
