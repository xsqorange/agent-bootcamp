# Day 6 收束博客:Project 1 收尾 (周末 12h · 上半场)

> **中文**:6h 干工程大头 — 修 Day 4/5 mismatch / 拆 GitHub Actions / 跑 5 acceptance / 备 demo 脚本,工作流模式从"严格本地"切到"commit 后 push + merge"。
>
> **English**: Day 6 — Day 4/5 progress log mismatch fix, GitHub Actions 2-job split, 5 acceptance passes, demo-script.sh. Workflow mode: local-only → push+merge.

---

## 🎯 背景

Day 5 跑完 52 测试,mvn verify 飘红 1 次 flake,进度日志有 mismatch (Day 4/5 写"未 push 未 merge"实际已 push + merge)。Day 6 把工程收口 + 拆 CI + 备 demo 脚本。

**6 大任务** (6h):
1. 修 Day 4/5 mismatch (15 min)
2. 拆 GitHub Actions 为 2 job (90 min)
3. 跑 `mvn verify` 验 52 测试 (15 min)
4. 跑 5 类任务 acceptance (60 min)
5. 备 demo 脚本 (`demo-script.sh`) (60 min)
6. README Day 6 章节 + commit + push + merge (90 min)

---

## 🏗️ GitHub Actions 拆 2 Job

| Job | 触发 | 跑什么 | 需 secrets |
|---|---|---|---|
| `build` | push + PR + schedule | `./mvnw -B test` (~30s) | ❌ |
| `eval` | workflow_dispatch + 周日 cron | `./mvnw -B verify` (~3min) | ✅ `MINIMAX_API_KEY` |

**为什么拆**:
- 单 job `mvn verify` 在 PR 阶段因 E2E flake 飘红
- 拆后 `build` 必跑 30s smoke,`eval` 按需手动触发

**build.yml**:
```yaml
name: build
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - run: chmod +x mvnw && ./mvnw -B test
```

---

## 💻 5 类任务 acceptance (60 min)

```bash
# TC-1: 拿时间
./mvnw -q exec:java -Dexec.mainClass="com.agentbootcamp.Main" \
  -Dexec.args="--goal '用 get_current_time 拿当前时间,然后说 done'"

# TC-2: 读 README
-Dexec.args="--goal '读 README.md 第一行,告诉我内容'"

# TC-3: 写文件
-Dexec.args="--goal '用 write_file 创建 target/tc3.txt,内容 hello'"

# TC-4: grep 搜索
-Dexec.args="--goal 'grep 搜 README 找 Day 1,告诉我行号'"

# TC-5: 工具组合
-Dexec.args="--goal '用 search_kb 找 memory 压缩,然后用 grep 在代码里搜'"

# 5/5 都返 FINAL_ANSWER ✅
```

`demo-script.sh` (78 行): 5 demo 命令一键跑

---

## 🐛 4 个 Day 6 真坑

1. **PAT 缺 `workflow` scope** — 推 `.github/workflows/*.yml` 必踩,Edit PAT 勾 workflow
2. **mvnw mode 100644** — CI Linux runner exit 126,`git rm --cached` + `update-index --chmod=+x` 重建
3. **`.gitignore` 行内注释不生效** — `#` 必须独立成行,行内 `#` 当 pattern 一部分
4. **shade plugin LICENSE.txt 重叠 WARNING** — Day 6-9 遗留,Day 12 收尾修

---

## 📊 验收数据

| 指标 | 数字 |
|---|---|
| 新增文件 | 4 (`.github/workflows/build.yml` + `eval.yml` + `demo-script.sh` + 进度日志 commit) |
| 总测试 | **52** (32 单元 + 20 E2E) ✅ 全过 (有 1 flake) |
| 5 acceptance | **5/5** ✅ FINAL_ANSWER |
| Commit | 3 (`fix(day6): mismatch` + `feat(day6): CI 2 job` + `docs(day6)`) |
| 烧钱 | ~$0.0043 |
| mvn verify | ~3 min |

---

## 🚀 Day 7 预告

**Project 1 收尾 (下半场)**: 录 60s demo GIF (`ffmpeg gdigrab` Windows 验证) + 写 1 页 runbook (191 行 8 故障排查) + README Day 7 章节 + CI badge,**Project 1 完工**。