# Day 1 收束博客:从零开始的 Java Coding Agent

> **中文**:用 Java 17 + Spring Boot 之外的纯 Java + Maven + picocli,搭一个能调 LLM 的 CLI 工具,3 个工具 (get_current_time / read_file / exec),推到 GitHub。
>
> **English**: Day 1 — build a CLI coding agent in Java from scratch: LLM client + 3 tools + picocli CLI, push to GitHub.

---

## 🎯 背景

Java/Spring 工程师想转 Agent 开发,**不要纠结** — Day 1 直接搭骨架:

| 项 | 选择 |
|---|---|
| **JDK** | Java 17 (Corretto) |
| **构建** | Maven 3.9.9 + mvnw |
| **CLI** | picocli 4.7.x (单文件 flag 解析) |
| **LLM SDK** | 手写 HTTP (OkHttp) — **不要** 引 Spring AI 起步太重 |
| **JSON** | Jackson |
| **测试** | JUnit 5.10 |

**2 周速成的关键**: Day 1 **必须 commit + push 一次**,不是"Day 5 才 commit"。`xsqorange/agent-bootcamp` 公开仓库从 Day 1 起就活。

---

## 🏗️ 方案架构

```
Main (picocli)
  └─ Agent
       ├─ LlmClient (HTTP 调 LLM)
       └─ Tool Registry (3 tools)
            ├─ GetCurrentTime
            ├─ ReadFile
            └─ Exec
```

**关键决策**:
- **Tool 接口 = function calling 协议映射**:`Tool.name()` / `description()` / `jsonSchema()` / `execute()` 4 字段 = OpenAI/Anthropic function calling 4 字段
- **LlmClient 手写 HTTP** 而非引 SDK — 跨模型 (OpenAI/Anthropic/MiniMax) 通用
- **每个 Tool 一类**,不用 enum + switch — 易扩展 (Day 3 加 WriteFile/Grep 零改 Main)

---

## 💻 核心代码

**Tool 接口**:
```java
public interface Tool {
  String name();
  String description();
  Map<String, Object> jsonSchema();   // OpenAI tools[].parameters
  String execute(Map<String, Object> args) throws Exception;
}
```

**LlmClient.chat()**:
```java
public LlmResponse chat(List<Message> messages, List<Map<String,Object>> tools) {
    Map<String, Object> req = Map.of(
        "model", config.model(),
        "messages", messages,
        "tools", tools,
        "tool_choice", "auto"
    );
    HttpRequest httpReq = HttpRequest.newBuilder()
        .uri(URI.create(config.baseUrl() + "/chat/completions"))
        .header("Authorization", "Bearer " + config.apiKey())
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(req)))
        .build();
    HttpResponse<String> httpResp = HttpClient.newHttpClient().send(httpReq, BodyHandlers.ofString());
    return parseResponse(JSON.readValue(httpResp.body(), LlmResponse.class));
}
```

**Main.runSingle()**:
```java
LlmClient llm = new LlmClient(LlmConfig.fromEnv());
List<Tool> tools = List.of(new GetCurrentTime(), new ReadFile(workDir), new Exec(workDir));
Agent agent = new Agent(llm, tools);
String answer = agent.chat(userGoal);
System.out.println(answer);
```

---

## 🐛 7 个 Day 1 真坑

1. **Java/Maven 不在 PATH** — Windows Git Bash `~/.bashrc` 不够,要用 PowerShell `[Environment]::SetEnvironmentVariable('JAVA_HOME', ..., 'User')` 写注册表
2. **Maven 下载源用错** — `https://repo.maven.apache.org/maven2/`,**不要** `dlcdn.apache.org` / `archive.apache.org`
3. **PAT 没 `workflow` scope** — GitHub Actions 推 workflow 必踩,Edit PAT 勾 workflow
4. **Paths.get(rel).toAbsolutePath() 解析到 `user.dir`** — 不是 tool 构造时传的 `workDir`,Day 1 写代码可忽略,Day 3 `@TempDir` 单测会立刻暴露
5. **LLM CoT 翻倍 token** — MiniMax m3 把 `<think>...` 塞 `content`,算成本时减掉
6. **没 `gh` CLI** — 用户没装,GitHub push 需 PAT + `git push` 手动
7. **picocli `--` 误判** — `@Option(names = "--xxx")` 里写 `--` 触发解析错,改 `-` 或 `‐` (破折号)

---

## 📊 验收数据

| 指标 | 数字 |
|---|---|
| 新增文件 | 6 (LlmClient + LlmConfig + Message + Main + 3 Tools) |
| 单元测试 | 3 (1 ReadFile + 1 GetCurrentTime + 1 Exec) |
| 真 LLM 端到端 | 3 (TC-1/2/3: 拿时间 + 读 README + 跑 ls) |
| 总测试 | **6** (3 单元 + 3 E2E) ✅ 全过 |
| Commit | 2 (`feat(day1): 骨架 + 3 tools` + `docs(day1): README + 进度日志`) |
| 烧钱 | ~$0.001 |
| Push GitHub | ✅ `xsqorange/agent-bootcamp` (公开) |

---

## 🚀 Day 2 预告

**ReAct 循环从零开始 / ReAct Loop from Scratch** — 写 150-200 行的 ReAct 循环,**不要用框架**。Day 2 是整个 14 天最核心的一天,之后所有 Agent 都基于这个循环。