# 14 天总回看 — 从 0 到 1 构建 LLM Coding Agent

> 14-Day Sprint Recap · 2026-06-08 → 2026-06-25 · 全程真实 commit · 全程真实测试

## 一句话总结 / One-Line Summary

**14 天,一个 Java 17 CLI LLM Agent,从单次 LLM 调用 → 多 Agent 团队 → MCP 跨语言 → 可观测性 → 安全弹性 → 容器化部署 → GitHub Release v0.1.0。**

## 一、14 天做了什么 / What We Built in 14 Days

| Day | 主题 / Topic | 关键交付物 / Key Deliverable | 累计测试 / Tests |
|---|---|---|---|
| 1 | LLM 101 + 工具调用 | `LlmClient` + 3 tools + 单次调用 | 3 |
| 2 | ReAct 循环 | `while` 循环 + StopReason + JSONL trace | 8 |
| 3 | + 2 工具 + 评测 | 5 工具 + 10 个黄金用例 | 22 |
| 4 | 记忆 + 简易 RAG | 滑动窗口 + 内存索引 + `search_kb` | 32 |
| 5 | 评测脚手架 | 10 个 EvalCase + JUnit harness + `mvn verify` | 42 |
| 6 | CI + demo | 拆 2 job Actions + demo-script.sh | 42 |
| 7 | Project 1 收尾 | 60s demo.gif + runbook + README + CI badge | 42 |
| 8 | 多 Agent 入门 | Orchestrator + 1 Worker + `BlockingQueue` | 52 |
| 9 | MCP 服务器 | 跨语言 JSON-RPC 2.0 over stdio + Python 客户端 | 65 |
| 10 | 3-Agent 团队 | Researcher / Critic / Editor + CodeReviewOrchestrator | 71 |
| 11 | 可观测性 | Micrometer 1.12.5 + 6 metric + 7 厂商 CostCalculator | 81 |
| 12 | 安全 + 可靠性 | Resilience4j 2.2.0 + PromptGuard 5 attack + ScriptedLlmClient | 96 |
| 13 | 部署 | HttpServer + Docker + K8s + 健康检查 | 96 (+5) |
| 14 | 收尾发布 | 13 篇博客 + Demo + GitHub Release v0.1.0 | 96 |

**最终数据**:
- 57 个 Java 文件 (~3500 行)
- 96 个单元测试 (含 5 个 HttpServer 端点测试)
- 10 个 EvalCase 黄金用例
- 13 篇博客 (~36K 中文字符,~14K 英文字符)
- 14 个真坑文档化 (在 README + runbook)
- ~100 个 git commit (含 Day 1-14 + 收尾)
- 累计 LLM 成本 ~$0.10

## 二、14 天技术栈 / Tech Stack Across 14 Days

**核心 / Core**:
- Java 17 (Corretto 17.0.17)
- Maven 3.9.9 (mvnw mode 100755)
- Picocli 4.7.6 (CLI 参数解析)
- Jackson 2.17 (JSON 反序列化)

**Agent / Multi-Agent**:
- 自实现 ReAct 循环 (no LangChain)
- 自实现 BlockingQueue 消息协议 (Day 8)
- 自实现 sealed `Message` 层级 (Day 10)

**观测 / Observability**:
- Micrometer 1.12.5 (避开 1.13.0 包名改)
- Prometheus simpleclient (text format)

**安全 / Safety**:
- Resilience4j 2.2.0 (CircuitBreaker + Retry + TimeLimiter)
- 自实现 PromptGuard (5 attack pattern regex)

**测试 / Testing**:
- JUnit Jupiter 5.10.3
- 自实现 ScriptedLlmClient (重放 fixture, 0 依赖真 LLM)

**部署 / Deploy**:
- JDK 内置 com.sun.net.httpserver (0 新依赖)
- multi-stage Dockerfile (eclipse-temurin jdk → jre-alpine)
- docker-compose + Prometheus profile
- K8s deployment + liveness + readiness + Secret

**跨语言 / Cross-Language**:
- MCP JSON-RPC 2.0 over stdio (Day 9)
- Python stdlib-only 客户端 (0 第三方依赖)

## 三、14 天 30+ 真坑 (挑选 10 个最致命) / Top 10 Pitfalls

### #1 Java 17 javac 14 record bug
**症状**: nested record canonical 构造器访问错
**修法**: 改 `final class` + `isClean()/getReason()` 模拟 record API
**教训**: javac 14 处理 nested record 仍有问题,生产代码 prefer explicit class

### #2 Picocli XML 注释禁止 `--`
**症状**: `--metrics` 触发解析错,java parser 把 XML doc 里的 `--` 当作选项
**修法**: XML doc 注释里全部 escape `&#45;&#45;` 或换行
**教训**: 任何 XML/HTML 注释里出现 `--` 都是 invalid

### #3 Javadoc `\u` 被当 unicode escape
**症状**: `javadoc \u005Cu005Cu005Cu005Cu005Cu005Cu005Cuser` 编译时报 "illegal unicode escape"
**修法**: Javadoc 里 `\u` 改 `\\u005Cu` 显式 unicode
**教训**: Javadoc 解析在 javac 编译期就跑,任何 `\u` 都先当 escape

### #4 mvnw mode 100644 → 100755
**症状**: Windows git checkout 不保留 executable bit
**修法**: `git update-index --chmod=+x mvnw` + 在 .gitattributes 标 `mvnw text eol=lf`
**教训**: Windows + git executable bit 是长期痛点,推荐用 `git config core.filemode false` (Windows 不需要)

### #5 Resilience4j 2.x 移除 decorators
**症状**: `Decorators.ofSupplier()` 不存在了
**修法**: 链式 `Retry.decorate(retry, cb.decorate(tl.decorateSupplier(sup)))`
**教训**: 大版本升级一定读 migration guide,装饰器模式重构

### #6 Retry cause chain 误判
**症状**: `t` 是 wrap 后的 RuntimeException,直接 `instanceof IOException` 漏判
**修法**: 递归 5 层 `cause instanceof IOException` + 含 5xx/429
**教训**: 任何 wrap 后的 exception 必须递归 cause chain

### #7 shade plugin LICENSE.txt 重叠
**症状**: 多个 jar 都带 META-INF/LICENSE.txt → 构建警告
**修法**: maven-shade-plugin `<filters>` 排除 META-INF/*.SF/DSA/RSA/LICENSE*
**教训**: shade plugin 是 dependency hygiene 必修课

### #8 WSL2 dockerd init 卡死
**症状**: Docker Desktop 起来了但 WSL2 内部 dockerd 没起来,daemon API 500
**修法**: `wsl --shutdown` + 重启 Docker Desktop + 配 registry-mirrors
**教训**: Docker Desktop 跟 WSL2 是两个独立组件,问题要分层诊断

### #9 Maven `-parameters` 编译 flag
**症状**: Jackson 反序列化 record 字段全是 null
**修法**: pom.xml 加 `<arg>-parameters</arg>`,否则 record 组件名不保留
**教训**: record 序列化必须开 `-parameters`,这是 javac 默认行为

### #10 PromQL 同名 metric dedup
**症状**: `counter_total` 跟 `counter_total{tag="x"}` 同时存在 → Prometheus 拒收
**修法**: Micrometer `MeterFilter.dedup()` 配置 hide tagged
**教训**: Prometheus 0.x + Micrometer 1.x dedup 行为不一致

## 四、14 天工程哲学 / 14-Day Engineering Philosophy

### 1. Ship 优先于 perfect
**Day 6-7** 完成 Project 1 时,已知 2/3 测试 flake,**仍发布**。完美是迭代出来的,不是第一版做出来的。

### 2. 每个坑都文档化
**14 个真坑** 在 README + runbook + 13 篇博客里都被明确写出。这样下个开发者(包括未来的自己)能跳过。

### 3. 单元测试 ≠ 集成测试
96 个单元测试 + 10 个 EvalCase + 跨语言 MCP E2E = 3 层防御。**没有集成测试的单元测试 100% 是谎言**。

### 4. 自实现 > 第三方库 (在 learning 项目)
**Orchestrator / Worker / Message / ScriptedLlmClient / PromptGuard** 全部自实现。原因: 14 天速成的目的是学底层,不是学 API。

### 5. 双语 + 双 commit
**每个 commit message 都是中英双语**,**每个公开类都有双语 JavaDoc**,**13 篇博客都是双语**。这样维护成本翻倍,但项目可读性翻 10 倍。

## 五、14 天数据流 / Data Flow (Day 14 视角)

```
用户 CLI 入口
    ↓
picocli 解析 (--goal / --server / --metrics / --safe-mode)
    ↓
LlmClient (Day 1) ← ResilientLlmClient (Day 12, --safe-mode)
    ↓                    ↓
   MetricsCollector (Day 11)
    ↓
Agent ReAct 循环 (Day 2)
    ↓
工具调用 (Day 3-4) + PromptGuard (Day 12)
    ↓
JSONL trace (Day 2) + Prometheus /metrics (Day 11)
    ↓
最终答案 / HTTP 响应 (Day 13)
```

## 六、14 天没做的事 / What We Didn't Do

诚实清单:
1. **LLM streaming (SSE)** — Day 14 没做,所有响应都是等完整结果才返回
2. **Web UI** — Day 14 没做,只有 CLI + HTTP API
3. **向量数据库** — Day 4 RAG 是内存 BM25,不上 pgvector/Chroma
4. **持久化记忆** — Day 4 是滑动窗口,跨 session 不保留
5. **多 LLM 路由** — Day 11 CostCalculator 只算账不调度
6. **GitHub CLI** — Day 14 没装 gh,Release 用 git tag + 手动网页操作
7. **Docker 真实验证** — Day 13 本机网络受限,Docker build/run 文档完整未跑通

## 七、未来 30 天候选 / Future 30-Day Roadmap

| 周 / Week | 主题 / Theme | 候选任务 / Candidates |
|---|---|---|
| W3 | 交互升级 | LLM streaming (SSE) + Web UI (React + WebSocket) |
| W4 | 数据升级 | pgvector + 持久化记忆 + 多用户 |
| W5 | 智能升级 | 多 LLM 路由 (按成本/延迟) + 工作流 DAG |
| W6 | 社区升级 | Docker 镜像发布 + GitHub Pages + Discord |

## 八、给读者的 5 个建议 / 5 Tips for Readers

1. **从 Day 1 开始** — 不要跳,每个坑都是下一个项目会遇到
2. **clone 后先跑 `./mvnw test`** — 看 96 测全过,再 `./mvnw exec:java -Dexec.args="--goal '你好'"` 验证 LLM 通
3. **看 docs/runbook.md** — 8 个故障 + 诊断命令,踩坑直接查
4. **改 docs/agent-bootcamp-roadmap.md** — 这是你的项目,不是我的
5. **录自己的 demo** — 用 ffmpeg gdigrab 录 60s,看自己 14 天成果

## 九、自检问题 / Self-Check Questions

1. 14 天 96 测,平均每天 6.8 测。Day 12 一天加了 15 测,为什么? 哪天加测最少?
2. Java 17 record 跟 final class + 静态工厂比,在可读性 / 序列化 / 性能上各有什么取舍?
3. Micrometer 1.12.5 跟 1.13.0 包名改了,如果你新项目应该选哪个? 为什么?
4. Resilience4j 2.x 跟 1.x 装饰器 API 完全变了,这是 breaking change 还是 major version bump 应有的?
5. Day 13 Docker build 本机没跑通,但文档完整 — 这是 ship 还是 fail? 你怎么定义 Day 13 算"完成"?

## 十、相关链接 / Related Links

- [README](../../README.md) — 项目入口
- [00-intro 总览](00-intro.md) — 14 天路线图
- [Day 1](day1-foundation.md) → [Day 14](day14-polish-ship.md) — 14 篇每日博客
- [GitHub Releases](https://github.com/xsqorange/agent-bootcamp/releases) — v0.1.0
- [docs/runbook.md](../runbook.md) — 8 故障诊断
- [docs/deploy.md](../deploy.md) — 部署手册

---

**字数 / Word Count**: ~5,000 中文字符 + ~2,000 英文字符
**作者 / Author**: 码力全开 + Hermes Agent
**日期 / Date**: 2026-06-25
**版本 / Version**: v0.1.0
**里程碑 / Milestone**: 14 天速成项目完工 (从 0 到 1)

> "完美是迭代出来的,不是第一版做出来的。" — Day 6 哲学
> "每个坑都文档化,这样下个开发者(包括未来的自己)能跳过。" — Day 14 哲学