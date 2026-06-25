# Day 14 — 收尾发布：博客 + Demo + Release v0.1.0

> 14 天速成 · 第 14 天 (收官) / 14-Day Sprint · Day 14 (Final)

## 一、背景 / Background

Day 13 把代码装进容器 + K8s,**功能上"能上线"了**。
Day 14 做"**怎么让 1000 个开发者愿意点 Star**"——博客、demo 录屏、GitHub Release、release badge、最终路线图 ⏳→✅。

**为什么 Day 14 不能省**:
- 写完代码 ≠ 完成项目
- 没有 demo gif 没人会跑
- 没有 Release 标签没人知道这是稳定版
- 没有路线图 ⏳→✅ 没人知道你完工了

## 二、Day 14 的 6 件事 / Day 14 Six Things

| # | 任务 / Task | 交付物 / Deliverable |
|---|---|---|
| 1 | 写本博客 (`day14-polish-ship.md`) | 双语,~2.5K 字 |
| 2 | 写 14 天总回看 (`14-day-recap.md`) | 双语,~5K 字 |
| 3 | 录 90s 终极 demo (`docs/demo-v0.1.0.gif`) | ffmpeg gdigrab |
| 4 | GitHub Release `v0.1.0` (tag + release notes) | gh CLI + 自动生成 |
| 5 | README release badge + 路线图 ⏳→✅ | 1 commit |
| 6 | 清理 (`.bak` / `.env` / `.gitignore`) | 0 commit (verify only) |

## 三、90s 终极 demo 怎么录 / How To Record 90s Final Demo

用 ffmpeg gdigrab (Windows 唯一靠谱方案,asciinema 跑不了):

```bash
# 后台启 ffmpeg 录 desktop
ffmpeg -f gdigrab -framerate 30 -i desktop -t 90 \
  -c:v libx264 -preset ultrafast -crf 28 \
  -y docs/demo-v0.1.0.mp4 &

# 前台跑 5 个命令 demo
./mvnw exec:java -Dexec.args="--goal '列出所有 14 天的关键文件'"
./mvnw exec:java -Dexec.args="--server --server-port 8080" &
curl http://localhost:8080/health
curl http://localhost:8080/metrics
./mvnw test -q

# 等 ffmpeg 完
```

→ mp4 → gif (palettegen 优化,720p 12fps ~1.5MB):
```bash
ffmpeg -i demo-v0.1.0.mp4 \
  -vf "fps=12,scale=720:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" \
  -loop 0 -y docs/demo-v0.1.0.gif
```

## 四、GitHub Release v0.1.0 怎么发 / How To Release v0.1.0

**1. 准备 release notes** (基于 14 天 commit 自动生成):
```bash
git log v0.0.0..v0.1.0 --oneline > /tmp/release-notes.txt
```

**2. 创 tag + push**:
```bash
git tag -a v0.1.0 -m "agent-bootcamp v0.1.0 — 14-day sprint complete"
git push origin v0.1.0
```

**3. 创 GitHub Release** (用户有 PAT workflow scope):
```bash
gh release create v0.1.0 \
  --title "agent-bootcamp v0.1.0 — 14-day sprint complete" \
  --notes-file /tmp/release-notes.txt \
  --target main
```

## 五、Release Notes 自动生成 / Release Notes Auto-Generation

**格式**: Conventional Commits → 5 类 (Features / Bug Fixes / Docs / Tests / Chore)

```
## Features (Day 1-14)
- Agent ReAct 循环 + 7 tools + 记忆 + RAG
- Orchestrator + Worker 多 Agent
- MCP 跨语言 JSON-RPC 2.0
- 3-Agent 团队 (Researcher/Critic/Editor)
- Micrometer 1.12.5 6 metric + 7 厂商 CostCalculator
- Resilience4j 2.2.0 (CircuitBreaker + Retry + TimeLimiter)
- PromptGuard 5 attack pattern
- HttpServerMain 5 端点 (健康检查 + Prometheus)
- Docker multi-stage + docker-compose + K8s

## Bug Fixes (累计 ~15 个)
- Java 17 javac 14 record bug
- picocli XML 注释禁止 `--`
- Javadoc `\u` unicode escape 误判
- mvnw mode 100644 → 100755
- Resilience4j 2.x 移除 decorators
- Retry cause chain 误判
- shade plugin LICENSE.txt 重叠
- WSL2 dockerd init 卡死
- ... (跨 14 天 30+ 个真坑)

## Docs
- README 双语 (~5000 字)
- 13 篇博客 (00-intro + day1-13)
- docs/runbook.md 8 故障
- docs/deploy.md 部署手册
- AGENTS.md + AGENTS.zh-CN.md

## Tests
- 96 单元测试全过 (mvn test)
- 10 EvalCase 黄金用例
- 跨语言 MCP E2E (Java + Python stdlib)
- 5 HttpServerMain 端点测试
```

## 六、README release badge 怎么加 / Release Badge

```markdown
[![Release](https://img.shields.io/github/v/release/xsqorange/agent-bootcamp)](https://github.com/xsqorange/agent-bootcamp/releases/latest)
[![CI](https://github.com/xsqorange/agent-bootcamp/actions/workflows/build.yml/badge.svg)](https://github.com/xsqorange/agent-bootcamp/actions)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
```

**位置**: README 顶部,项目名下一行。

## 七、最终清理 / Final Cleanup

| 检查项 / Check | 命令 / Command |
|---|---|
| `.env` 没提交 | `git check-ignore .env` |
| `.bak` 文件残留 | `find . -name "*.bak" -not -path "./target/*"` |
| `target/` 在 .gitignore | `git check-ignore target/` |
| 大文件没意外提交 | `git rev-list --objects --all \| git cat-file --batch-check='%(objectsize) %(restname)' \| sort -nr \| head -10` |
| 仓库大小 | `du -sh .git` |

## 八、Day 14 真坑 / Day 14 Real Pitfalls

1. **GitHub CLI 没装** — 用户 6 月初没装 gh,前面 14 天都没用上。Release 需要 PAT workflow scope + `gh auth login`
2. **asciinema Windows 跑不起来** — 缺 fcntl 模块,改 ffmpeg gdigrab
3. **ffmpeg 录屏 mp4 → gif 太大** — palettegen 优化,12fps 720p ~1.5MB (无优化 ~5MB)
4. **git tag push 失败** — PAT 没 workflow scope → 403,需 repo scope 也勾上
5. **Release notes 中文乱码** — Windows console 默认 GBK,`CHCP 65001` 或 powershell 强制 UTF-8

## 九、验收清单 / Day 14 Acceptance

- [x] Day 13 博客 (`docs/blog/day13-deploy.md`)
- [x] 本博客 (`docs/blog/day14-polish-ship.md`)
- [x] 14 天总回看博客 (`docs/blog/14-day-recap.md`)
- [x] 路线图 Day 13/14 ⏳ → ✅
- [x] AGENTS.md Day 13/14 状态更新
- [x] 清理 + .env 没提交验证
- [ ] 90s 终极 demo gif (本机网络/磁盘空间允许时录)
- [ ] GitHub Release v0.1.0 tag + release

## 十、Day 14 完成后 / After Day 14

**项目从 0 到 1 完成**:
- 57 个 Java 文件 (~3500 行)
- 96 个单元测试 (含 5 个 HttpServer 端点测试)
- 10 个 EvalCase 黄金用例
- 13 篇博客 (~36K 中文字符)
- 8 个真坑文档化 (Day 6-14)
- Docker + K8s 部署清单完整
- 跨语言 MCP E2E (Java + Python stdlib)

**未来 Day 15+ 候选** (用户可选):
- Day 15: 加 LLM streaming (SSE)
- Day 16: 加 Web UI (React + WebSocket)
- Day 17: 加多 LLM 路由 (按成本/延迟)
- Day 18: 加 RAG 升级 (向量数据库)
- Day 19: 加工作流引擎 (DAG + 持久化)

## 十一、自检问题 / Self-Check Questions

1. Release notes 跟 CHANGELOG.md 区别? GitHub Release 是不是取代 CHANGELOG?
2. 为什么 git tag 必须 `-a` (annotated) 不是 lightweight? 两者 git log 区别?
3. ffmpeg gdigrab 比 asciinema 在 Windows 上有什么优势? 录 90s vs 60s 哪个更适合 README hero?
4. Release badge URL 怎么自动跟随 latest tag? 是不是用 `releases/latest` 就行?
5. 项目完工后,README 应该按什么顺序放: logo / badge / 一句话介绍 / 路线图 / 快速开始 / 文档 / License?

## 十二、相关链接 / Related Links

- [Day 13 博客](day13-deploy.md) — Docker + K8s
- [14 天总回看](14-day-recap.md) — 完整 14 天精华
- [00-intro 总览](00-intro.md) — 路线图 + 数据
- [GitHub Releases](https://github.com/xsqorange/agent-bootcamp/releases)

---

**字数 / Word Count**: ~3,000 中文字符 + ~1,000 英文字符
**Commit**: 多个 feat/docs/chore on day14 branch
**测试 / Tests**: 96 全过 (Day 13 完工)
**里程碑 / Milestone**: 从"能跑"到"能发布"