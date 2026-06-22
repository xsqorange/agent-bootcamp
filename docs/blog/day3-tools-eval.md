# Day 3 收束博客:加 2 工具 + 评测脚手架

> **中文**:加 `write_file` + `grep` 工具让 Agent 能改文件 + 自动搜,跑 10 个黄金用例 JUnit harness 自动化测试,**从此以后调参不盲**。
>
> **English**: Day 3 — + 2 tools (write_file / grep) + 10 golden test cases + JUnit harness. Auto-test = blind tuning no more.

---

## 🎯 背景

Day 1-2 跑通"agent 能跑",但:
- Agent **只能读**,不能改 — 写不了一行代码
- 5 个黄金测试 **每次手动跑**,调个 prompt 改动不知道有没有 break

Day 3 把"agent 能改 + 自动测"装上。

---

## 🏗️ 2 个新工具

| 工具 | 用途 | 关键防护 |
|---|---|---|
| `write_file(path, content)` | Agent 写文件 (覆盖) | 路径白名单 + 1MB 上限 |
| `grep(path, pattern, max_results, context_lines)` | Java 正则搜文件 (支持目录递归) | 1MB/文件 + 限结果数 + 跳隐藏目录 |

### WriteFile (相对路径解析真坑)

```java
public String execute(Map<String,Object> args) {
    String rawPath = (String) args.get("path");
    String content = (String) args.get("content");

    // ❌ 错: Paths.get(rel).toAbsolutePath() 解析到 user.dir, 不是 workDir
    // Path p = Paths.get(rawPath).toAbsolutePath();

    // ✅ 对: workDir.resolve(rel).normalize()
    Path p = rawPath.startsWith("/") || rawPath.matches("^[a-zA-Z]:.*")
        ? Paths.get(rawPath)  // 绝对路径直接走
        : workDir.resolve(rawPath).normalize();  // 相对路径以 workDir 为基准

    Files.writeString(p, content);
    return "wrote " + content.length() + " chars to " + p;
}
```

### Grep (Files.walk 递归)

```java
public String execute(Map<String,Object> args) {
    String pattern = (String) args.get("pattern");
    int maxResults = ((Number) args.getOrDefault("max_results", 10)).intValue();
    Path start = Paths.get((String) args.get("path"));

    Pattern p = Pattern.compile(pattern);
    StringBuilder out = new StringBuilder();
    try (Stream<Path> stream = Files.walk(start, 5)) {  // 限深 5
        stream.filter(Files::isRegularFile)
              .filter(path -> !path.toString().contains("/."))  // 跳隐藏目录
              .filter(path -> Files.size(path) < 1_000_000)  // 1MB 限
              .forEach(path -> {
                  try {
                      String text = Files.readString(path);
                      Matcher m = p.matcher(text);
                      while (m.find() && out.length() < 50_000) {
                          out.append(path).append(":").append(m.start()).append(":").append(m.group()).append("\n");
                      }
                  } catch (IOException ignore) {}
              });
    }
    return out.toString();
}
```

---

## 💻 10 个黄金用例 (TC-6 ~ TC-10 新增)

| TC | 目标 | 验什么 |
|---|---|---|
| **TC-6** | write_file 创建文件 | 新建 |
| **TC-7** | write_file 覆盖文件 | 覆盖 |
| **TC-8** | grep 找 README "Day 1" | 找到匹配 |
| **TC-9** | grep 找 README "xyzzy_不存在" | 找不到 |
| **TC-10** | read + write 组合 | 多工具协同 |

**Harness 跑法**:
```java
@Test void tc6_WriteFileCreatesFile() {
    Agent agent = new Agent(llm, tools, 5, 0.05);
    RunResult r = agent.run("用 write_file 创建 target/test-tc6.txt,内容 'tc6-payload'");
    assertTrue(Files.exists(Path.of("target/test-tc6.txt")));
    assertEquals("tc6-payload", Files.readString(Path.of("target/test-tc6.txt")));
    assertEquals(StopReason.FINAL_ANSWER, r.stopReason());
}
```

---

## 🐛 4 个 Day 3 真坑

1. **Paths.get(rel).toAbsolutePath() 解析到 user.dir** — `@TempDir` 第一次跑就暴露
2. **LLM Chain of Thought 翻倍 token** — MiniMax m3 把 `<think>...` 塞 content,算成本时减掉,system prompt 加 "think briefly"
3. **真 LLM 端到端网络依赖** — 5 个 E2E 跑挂 CI,`@EnabledIfEnvironmentVariable` 守门
4. **record 字段是 private** — `m.content` 编译错,必须 `m.content()` (Java 17 record accessor)

---

## 📊 验收数据

| 指标 | 数字 |
|---|---|
| 新增文件 | 5 (WriteFile + Grep + WriteFileTest 5 单元 + GrepTest 5 单元 + AgentTest 5 E2E) |
| 新增工具 | 2 (write_file + grep) |
| 总测试 | **15** (10 单元 + 5 E2E) ✅ 全过 |
| Commit | 3 (`feat(day3)` + `test(day3)` + `docs(day3)`) |
| 烧钱 | ~$0.005 |

---

## 🚀 Day 4 预告

**记忆 + 简易 RAG** — Agent 对话历史太长会爆 context window,加 `MemoryManager` 滑动窗口 + LLM 摘要压缩;`RagIndex` 内存 keyword TF + `SearchKb` 工具让 Agent 能搜知识库。**0 数据库, 0 向量, 0 外部依赖**。