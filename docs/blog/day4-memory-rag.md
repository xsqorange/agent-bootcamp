# Day 4 收束博客:记忆 + 简易 RAG

> **中文**:Agent 对话历史太长会爆 context window,加滑动窗口 + LLM 摘要压缩;`RagIndex` 内存 keyword TF + `SearchKb` 工具让 Agent 能搜知识库。**0 数据库,0 向量,0 外部依赖**。
>
> **English**: Day 4 — MemoryManager (sliding window + LLM summary) + RagIndex (in-memory keyword TF) + SearchKb tool. **Zero DB, zero vectors, zero external deps**.

---

## 🎯 背景

Day 1-3 跑通后,实际用发现两个问题:
1. **多轮对话 context 爆** — 10 轮后 message 列表 50+ 条,token 5k+,LLM 变慢 + 贵
2. **Agent 不知道项目知识** — 让它"加 SearchKb 工具",它会瞎猜,不查 README

Day 4 装 **Memory + RAG**,跟 Day 1-3 风格一致:**纯 Java + 内存**,不引 ChromaDB / Pinecone / pgvector。

---

## 🏗️ 3 大组件

### MemoryManager (滑动窗口 + LLM 摘要)

```java
public List<Message> compress(List<Message> messages, LlmClient llm) {
    if (messages.size() <= 2) return List.copyOf(messages);  // 太短
    int splitEnd = messages.size() - KEEP_LAST_N;            // 保留最近 8
    if (splitEnd <= 2) return List.copyOf(messages);

    List<Message> middle = messages.subList(2, splitEnd);
    String summary = summarizeWithLlm(formatMessagesForLlm(middle), llm);  // 失败 → null
    List<Message> out = new ArrayList<>();
    out.add(messages.get(0));  // system prompt 永不压缩
    out.add(messages.get(1));  // 第一条 user 永不压缩
    if (summary != null) {
        out.add(Message.system("[Earlier conversation summary]: " + summary));
    }
    for (int i = splitEnd; i < messages.size(); i++) out.add(messages.get(i));
    return out;
}
```

**触发阈值**:`msg > 24 || tokens > 10000` (C 宽松,便宜模型友好)

### RagIndex (内存 keyword TF)

```java
public List<Chunk> search(String query, int maxResults) {
    Set<String> queryTokens = tokenize(query);
    return chunks.stream()
        .map(c -> new Scored(c, countIntersection(queryTokens, tokenize(c.content())) / (double) queryTokens.size()))
        .filter(s -> s.score() > 0)
        .sorted(Comparator.comparingDouble(Scored::score).reversed())
        .limit(maxResults)
        .map(Scored::chunk)
        .toList();
}
```

### SearchKb 工具 (第 6 个)

```json
{
  "name": "search_kb",
  "description": "Search the knowledge base for relevant chunks",
  "parameters": {
    "query": {"type": "string", "required": true},
    "max_results": {"type": "integer", "default": 3, "min": 1, "max": 10}
  }
}
```

---

## 💻 关键设计

| 决策 | 选项 | 理由 |
|---|---|---|
| **chunk 大小** | 500 字符 / 50 overlap | 100-200 tokens/chunk,既不碎也不大;10% 冗余防切碎 |
| **chunking 策略** | 按段滑窗 | 知识库 .md 按段切,段内超长才滑窗 |
| **knowledge 来源** | `src/main/resources/knowledge/*.md` | 跟代码一起 commit,版本可追溯 |
| **加载 fallback** | classpath → 文件系统 | 打包后从 classpath 加载,开发用文件系统 |

---

## 🐛 3 个 Day 4 真坑

1. **Record 字段是 private** — `m.content` 编译错,必须 `m.content()` (Java 17 record accessor)
2. **构造器歧义** — `new RagIndex(null)` 不知道想要 `RagIndex(Path)` 还是 `RagIndex(List<Chunk>)`,**修法**: 显式转型 `(Path) null`
3. **RAG 测试过度期望** — 写"搜 3 个应该返 3",但实际只 2 个 chunk 命中,**修法**: assertEquals 命中数量,不是 maxResults

---

## 📊 验收数据

| 指标 | 数字 |
|---|---|
| 新增文件 | 6 (MemoryManager + RagIndex + SearchKb + 3 个测试 + knowledge/ 5 个 .md) |
| 新增工具 | 1 (search_kb, 第 6 个) |
| 总测试 | **42** (24 单元 + 8 E2E) ✅ 全过,0 回归 |
| Commit | 3 (`feat(day4): + Memory + RagIndex` + `test(day4): + 27 单元` + `docs(day4)`) |
| 烧钱 | ~$0.006 |

---

## 🚀 Day 5 预告

**评测脚手架 / Eval Harness** — Day 1-4 8 个真 LLM E2E 已经够 5 个黄金用例,Day 5 把它们 JSON 化 (`evals/cases/*.json`) + JUnit 5 `@TestFactory` 动态生成测试 + 修 3 个真坑 (`-parameters` / snake_case / 429 retry),**从此调参有数据**。