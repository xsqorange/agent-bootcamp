# Day 7 收束博客:Project 1 收尾 (周末 12h · 下半场) — 完工 🎉

> **中文**:录 60s demo GIF + 191 行 runbook + README Day 7 章节 + CI badge。**Project 1 (CLI Coding Agent) 完工**,公开仓库 `xsqorange/agent-bootcamp` 双语 README + 5 acceptance + CI + demo 全齐。
>
> **English**: Day 7 — 60s demo GIF + runbook (191 lines) + README Day 7 chapter + CI badge. **Project 1 done**.

---

## 🎯 背景

Day 6 干工程大头,Day 7 干"看得见的产物": demo + runbook + README 收尾。**Week 1 完工,Week 2 上规模**。

---

## 🏗️ 4 大产物

### 1. 60s demo GIF (`docs/demo.gif` 988KB)

```bash
# 1. 后台录 80s mp4
ffmpeg -f gdigrab -framerate 30 -i desktop -t 80 \
  -c:v libx264 -preset ultrafast -crf 28 -y docs/demo.mp4 &
FFMPEG_PID=$!
sleep 3

# 2. 前台跑 demo 命令 (~50s,覆盖 mp4 时长)
./demo-script.sh

# 3. wait ffmpeg
wait $FFMPEG_PID

# 4. mp4 → gif (palette 优化, 60s@12fps 720p ≈ 1MB)
ffmpeg -i docs/demo.mp4 \
  -vf "fps=12,scale=720:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" \
  -loop 0 -y docs/demo.gif
```

### 2. runbook.md (191 行, 8 故障排查)

| # | 故障 | 排查命令 |
|---|---|---|
| 1 | Maven 找不到 | `mvn -v` / `echo $JAVA_HOME` |
| 2 | API key 404 | `echo $MINIMAX_API_KEY \| head -c 10` |
| 3 | LLM 429 | `sleep 60 && retry` |
| 4 | shade plugin WARNING | `mvn package -X \| grep WARNING` |
| 5 | PAT 缺 workflow scope | GitHub Settings → Edit PAT → 勾 workflow |
| 6 | 测试 flake | `./mvnw test` 重跑 3 次 |
| 7 | 工具路径错 | `realpath target/test.txt` |
| 8 | 完全卡死 | `./mvnw -X test > debug.log 2>&1` |

### 3. README 双语

| 段 | 中文 | 英文 |
|---|---|---|
| Quick Start | 安装 + 跑 demo | Install + run demo |
| 架构图 | 5 段架构 + 流程图 | 5 section architecture + flow |
| 5 黄金用例 | TC-1 ~ TC-5 | TC-1 ~ TC-5 |
| 工具列表 | 6 tools | 6 tools |
| 进度日志 | 14 天计划表 | 14-day roadmap |

### 4. CI badge

`[![CI](https://github.com/xsqorange/agent-bootcamp/actions/workflows/build.yml/badge.svg)](...)`

---

## 🐛 3 个 Day 7 真坑

1. **asciinema Windows fcntl 缺失** — pip/winget 装不上,改 `ffmpeg gdigrab` (已在 PATH)
2. **Pagination stat cache stale** — `git update-index --refresh` 救
3. **count.txt 临时文件误存** — 加 `.gitignore` 排除

---

## 📊 Project 1 完工验收 (5 acceptance)

| # | 验收项 | 状态 |
|---|---|---|
| 1 | 公开 GitHub 仓库 + 双语 README | ✅ `xsqorange/agent-bootcamp` |
| 2 | `./myagent "..."` 跑通 5 类任务 | ✅ 5/5 FINAL_ANSWER |
| 3 | 评测 harness ≥ 80% | ✅ 52/52 全过 |
| 4 | 60s demo GIF | ✅ `docs/demo.gif` 988KB |
| 5 | GitHub Actions CI | ✅ build.yml + eval.yml 2 job |

**Week 1 累计**:
- 测试: **52** (32 单元 + 20 E2E)
- Commit: **18** (Day 1-7 各 2-3 commit + merge)
- 烧钱: **~$0.025** (远低于 $20 目标)
- 代码: **~1500 行 Java**

---

## 🚀 Day 8 预告

**多 Agent 入门 / Multi-Agent Intro** — `Orchestrator` + `WorkerAgent` + sealed `Message` interface,1 主 Agent 拆任务派给 worker,BlockingQueue 消息队列。Week 2 开始的标志: **从单 Agent 升级到多 Agent 协作**。