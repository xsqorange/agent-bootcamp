# 14 天速成:从零到生产可用的 Java 多 Agent 系统

> **中文**:一个 Java/Spring 工程师 14 天从零搭出一个**生产可用**的多 Agent 系统 — 公开 GitHub 仓库 + 双语 README + 88 单元测试 + 12 篇实战博客 + MCP 跨语言互通 + 可观测性 + 安全 + 可靠性。本文是整个系列的开篇,介绍动机、路线图、累计数据、关键工程哲学,以及怎么读这个 12 篇博客系列。
>
> **English**: A 14-day crash course: a Java/Spring engineer builds a **production-ready** multi-agent system from scratch — public GitHub repo + bilingual README + 88 unit tests + 12 hands-on blog posts + MCP cross-language interop + observability + safety + reliability. This is the series intro: motivation, roadmap, cumulative stats, key engineering philosophy, and how to read the 12-blog series.

---

## 🎯 为什么写这个 14 天速成

市面上 Agent 开发教程 2 类:

1. **论文型** — 读 4 篇 arXiv 论文 + 2 本书,1-3 个月讲清楚理论
2. **框架型** — 用 LangGraph / CrewAI / Spring AI Agent,1 周搭 demo,**但生产部署必踩 10+ 坑**

**14 天速成的第三种路径**:
- **Day 1-7** (Week 1): 基础冲刺,单 Agent 可用 (CLI coding agent + ReAct + 6 工具 + 评测)
- **Day 8-14** (Week 2): 规模冲刺,多 Agent + MCP + 可观测性 + 安全 + 部署 + 收尾发布

**关键设计原则**:
- **每天 commit + push**,Day 14 末尾 1 个公开 GitHub 仓库 + 1 篇博客
- **不用框架**,ReAct 循环从零写,LangChain / Spring AI Agent Day 14 后再切换
- **踩坑公开**,每个 Day 都有 3-5 个真坑 + 修法,学 30 个坑 = 30 个教训

---

## 🏗️ 14 天路线图总览

### Week 1:基础冲刺 (Day 1-7)

| Day | 主题 | 关键交付 | 测试数 |
|---|---|---|---|
| **1** | 骨架 + LlmClient + 3 tools | CLI 跑通 + GitHub push | 6 |
| **2** | ReAct 循环 (200 行) | 4 新类型 + JSONL trace + 异常隔离 | 6 |
| **3** | + 2 工具 + 10 黄金用例 | write_file + grep + AgentTest | 15 |
| **4** | 记忆 + 简易 RAG | MemoryManager + RagIndex + SearchKb | 42 |
| **5** | 评测脚手架 | EvalHarness + JSON cases + @TestFactory | 52 |
| **6** | CI 拆 2 job | build.yml + eval.yml + demo-script.sh | 52 |
| **7** | Project 1 收尾 | demo.gif + runbook + README 双语 + CI badge | 52 |

**Week 1 累计**: ~1500 行 Java, ~$0.025 烧钱, **Project 1 完工** (公开仓库可访问)

### Week 2:规模冲刺 (Day 8-14)

| Day | 主题 | 关键交付 | 测试数 |
|---|---|---|---|
| **8** | 多 Agent 入门 | Orchestrator + WorkerAgent + sealed Message | 57 |
| **9** | MCP 服务器 | 自实现 JSON-RPC 2.0 + Python client + 7 跨语言 E2E | 67 |
| **10** | 3 Agent 团队 | Researcher/Critic/Editor + EditFile + 工具白名单 | 62 |
| **11** | 可观测性 + 成本 | Micrometer 1.12.5 + 6 metric + 7 厂商定价 | 68 |
| **12** | 安全 + 可靠性 | PromptGuard 5 attack + Resilience4j + ScriptedLlmClient | **88** |
| **13** | 部署 | Docker + K8s + 健康检查 | (待做) |
| **14** | 收尾发布 | 双语博客 + demo + Release v0.1.0 | (待做) |

**Week 2 累计**: ~500 行 Java (在 Week 1 基础上加), ~$0.060 烧钱, **多 Agent + 上线**

---

## 📊 累计数据 (Day 1-12)

| 指标 | 数字 |
|---|---|
| **总测试** | **88 单元** (Day 1-12 + 5 ScriptedLlmClient) ✅ 全过, 0 回归 |
| **总 commit** | 90+ (12 天 × 2-4 commit + merge) |
| **总烧钱** | ~$0.085 (Day 1-12,远低于目标 $20) |
| **总代码行** | ~2000 行 Java (含测试) + 220 行 Python (mcp-client) |
| **新增文件** | 50+ (Java + Python + Markdown + YAML) |
| **公开仓库** | <https://github.com/xsqorange/agent-bootcamp> |
| **MCP 跨语言** | Java server ↔ Python client,7 E2E 全过 |
| **12 篇博客** | ~30,000 中文字符,~63KB Markdown |

---

## 🛠️ 技术栈选择

| 维度 | 选择 | 理由 |
|---|---|---|
| **JDK** | Java 17 (Corretto) | LTS + 17 + record/sealed 现代化 |
| **构建** | Maven 3.9.9 + mvnw | 标准 + 跨平台 + 可执行 wrapper |
| **CLI** | picocli 4.7.x | 单文件 flag 解析,比 Spring Shell 轻 |
| **LLM SDK** | 手写 HTTP (OkHttp) | 跨模型 (OpenAI/Anthropic/MiniMax),不绑死 |
| **JSON** | Jackson 2.16 | 事实标准,record 反序列化稳 |
| **多 Agent** | 自实现 + BlockingQueue | 不用 LangGraph,真懂每行 |
| **MCP** | 自实现 JSON-RPC 2.0 (~200 行) | 官方 Java SDK 不存在,自实现稳 |
| **可观测性** | Micrometer 1.12.5 + Prometheus | 不用 OpenTelemetry,Day 11 太早 |
| **安全** | Resilience4j 2.2.0 + 自写 PromptGuard | Spring Cloud Circuit Breaker 同源 |
| **跨语言** | Python stdlib (subprocess + json) | 0 依赖,任何 Python 3.7+ 可用 |
| **测试** | JUnit Jupiter 5.10 | 行业标准 |

---

## 🐛 Day 1-12 踩过的大坑 (10 个核心)

| # | 坑 | 修法 |
|---|---|---|
| 1 | Java/Maven 不在 PATH | PowerShell `[Environment]::SetEnvironmentVariable('JAVA_HOME', ..., 'User')` 写注册表 |
| 2 | mvnw mode 100644 (CI exit 126) | `git rm --cached` + `update-index --chmod=+x` 重建 |
| 3 | PAT 缺 workflow scope | GitHub Settings → Edit PAT → 勾 workflow |
| 4 | `.gitignore` 行内 # 注释不生效 | # 独立成行,行内当 pattern |
| 5 | asciinema Windows fcntl 缺失 | 改 `ffmpeg gdigrab` 录屏 |
| 6 | 进度日志 "未 push 未 merge" 反复复发 | Day N merge 后立即 grep 自检 + 改进度行 |
| 7 | Micrometer 1.13.0 改包名 | 锁版本 1.12.5 |
| 8 | Resilience4j 2.x 移除 `decorators` | 链式 `Retry.decorate(retry, cb.decorate(sup))` |
| 9 | Retry `t instanceof IOException` 误判 | 递归 5 层 cause chain `isTransient()` |
| 10 | Java 17 nested record canonical bug | `final class` + `isClean()/getReason()` 模拟 record |

**30+ 个其他坑见各 Day 博客**。

---

## 🎓 关键工程哲学

### 1. 生产 0 容忍 ≠ "代码不挂"
> 是 "代码挂之前 **5xx / 超时 / prompt 注入 / 工具路径错 / 上下文爆** 都已经被拦截 + 监控看到 + 自动恢复"。

### 2. 2 周速成的真价值 ≠ "写 2000 行 Java"
> 是 "**踩 30 个坑学 30 个教训**"。每个坑都是 1-2h 真踩真修,博客公开。

### 3. "没用框架" 不是"反框架"
> Day 14 之后,真要切换 LangGraph / Spring AI Agent,**改骨架不动器官** — 因为 200 行 ReAct 循环每行都能解释。

### 4. 防御性编程 > system prompt 限制
> 不靠 "你不许 write_file" 限制 LLM — **靠工具白名单 RUNTIME 过滤** (Day 10 ResearcherAgent 看不到 write_file)。

### 5. 评测从 Day 5 起就是命根子
> 没评测,Day 8+ 调参 = 盲调。Day 5 起每天跑 `mvn verify` 5 类任务,flake 立即知道。

### 6. 装饰模式 > 修改核心类
> Day 12 `ResilientLlmClient extends LlmClient` 装饰,LlmClient 0 改,`--safe-mode false` 走原 LlmClient 向后兼容 Day 1-11。

### 7. CI 是 Day 1 就该有的事
> Day 6 拆 GitHub Actions 为 2 job (build 无 key / eval 有 key),公开仓库 0 凭证也能跑 smoke。

---

## 🚀 怎么读这个 12 篇博客系列

**推荐顺序** (按时间或按主题):

### 按时间读 (推荐新人, 2-3 天读完)

```
day1 → day2 → day3 → day4 → day5 → day6 → day7 → day8 → day9 → day10 → day11 → day12
 (基础冲刺 7 篇)                              (规模冲刺 5 篇)
```

### 按主题读 (推荐有经验, 1 天选读)

- **想看 ReAct 循环怎么写**: `day1`, `day2`, `day3`
- **想看 memory / RAG 怎么搞**: `day4`, `day5`
- **想看 CI / demo**: `day6`, `day7`
- **想看多 Agent 协作**: `day8`, `day10`
- **想看 MCP / 跨语言**: `day9`
- **想看可观测性**: `day11`
- **想看生产安全 + 可靠性**: `day12`

### 每篇博客固定结构

1. 🎯 背景 (为什么这天必做)
2. 🏗️ 方案 (组件图 + 关键设计)
3. 💻 核心代码 (完整可跑片段)
4. 🐛 真坑 (3-5 个当天踩到的具体坑 + 修法)
5. 📊 验收数据 (测试 / commit / 烧钱)
6. 🚀 Day N+1 预告 (1 句话锚点下一天)

---

## 🎯 适用人群 / 不适用

### ✅ 适用

- **Java/Spring 后端工程师**想转 Agent 开发
- **只有 2 周** (请假 / 跳槽冲刺 / 副业 MVP)
- 接受"**先跑起来再优化**",不愿花数周看论文
- 想**可上线的作品**,不是理论理解
- 想学"**14 天踩 30 个坑**"的实战经验

### ❌ 不适用

- 完全没 Java 背景 (先去补 1 周 Java + Spring)
- 想做研究 / 写论文 (去看 10 周版 + arXiv)
- 公司要求"必须用 LangGraph/CrewAI" (本系列用原生 SDK,Day 14 后再切)
- 不接受 2 周每天 6h 高强度 (改成 10 周版 `agent-dev-learning`)

---

## 🔗 相关链接

### 代码与文档

- **公开仓库**: <https://github.com/xsqorange/agent-bootcamp>
- **README 双语**: <https://github.com/xsqorange/agent-bootcamp/blob/main/README.md>
- **AGENTS.md** (AI 助手指南): <https://github.com/xsqorange/agent-bootcamp/blob/main/AGENTS.md>
- **12 篇博客目录**: `docs/blog/day1-foundation.md` ~ `day12-safety-reliability.md` + 本文 `00-intro.md`

### 累计数据可视化

| 文件 | 链接 |
|---|---|
| **60s demo GIF** | `docs/demo.gif` (988KB) |
| **runbook 故障排查** | `docs/runbook.md` (191 行, 8 类故障) |
| **MCP 跨语言 demo** | `mcp-client/test_e2e.py` (105 行, 7 E2E) |
| **6 metric Prometheus 抓取** | `./mvnw exec:java --metrics target/m.txt` |

### 发布平台 (1 稿多投)

- **中文**: 掘金 (juejin.cn) / SegmentFault / 知乎 / 公众号
- **英文**: Dev.to / Medium / Hashnode
- **个人品牌**: Hugo / Hexo + GitHub Pages

---

## 📅 进度

- ✅ Day 1-12 完成 (12/14 = 86%)
- ⏳ Day 13 部署 (Docker + K8s + 健康检查)
- ⏳ Day 14 收尾发布 (博客发平台 + Release v0.1.0 + LinkedIn/Twitter)

**Day 1-12 累计**: 88 单元测试 / 90+ commit / ~$0.085 / ~2000 行 Java / 12 篇博客

---

**作者 / Author**: 码力全开 (`maliquankai123@gmail.com`)
**License**: MIT
**发表平台 / Publishing**: 同步发布于掘金 + Dev.to + Medium + GitHub Pages (1 周内)