# Git 分支工作流 / Git Branch Workflow

## 14 天项目的标准流程 / Standard 14-Day Project Workflow

每天从 `main` 拉当天分支,完成后 `--no-ff` 合并回 `main`。
Each day, branch from main, do the day's work, merge back to main with --no-ff.

```
main   ●─────────────────●────────●────────►
         ╲              ╱ ╲      ╱ ╲
          ╲  day1     ╱   ╲day2╱   ╲day3
           ●─────────●      ●─────●    ...
```

## 每日 5 步 / Daily 5 Steps

```bash
# 1. 切到 main, 拉最新
git checkout main
git pull origin main

# 2. 拉当天分支
git checkout -b day{N}

# 3. 在 day{N} 上工作, 测试通过后
git add -A
git commit -m "feat(day{N}): ..."

# 4. 合并回 main
git checkout main
git merge --no-ff day{N} -m "Merge day{N}: ..."

# 5. 推 main
git push origin main
```

## 3 类 commit 命名 / 3 Commit Naming Conventions

- **`feat(scope): 新功能`** — 加新工具、新功能
- **`test(scope): 测试`** — 单元/E2E 测试
- **`docs(scope): 文档`** — README / JavaDoc

例如 / Examples:
- `feat(day3): + WriteFile + Grep tools, fix trace bug`
- `test(day3): 10 unit + 5 E2E golden cases`
- `docs(day3): Day 3 architecture + 5 pitfalls`

## 关键约定 / Key Conventions

- **每个 Day 独立分支** — `day1` / `day2` / `day3` / ...
- **不破坏现有测试** — 改代码前跑一遍 `./mvnw test` 看基线
- **--no-ff 合并** — 保留分支痕迹, git log 漂亮
- **不推未测试代码** — 跑通测试再 push
- **.env 不入库** — 已经在 .gitignore,绝不 `git add .env`

## 救命命令 / Lifesaving Commands

```bash
# 撤销最后一次 commit(保留改动)
git reset --soft HEAD~1

# 撤销 + 丢改动(危险!)
git reset --hard HEAD~1

# 看漂亮 git log
git log --oneline --graph -10

# 看某文件历史
git log --oneline -- src/main/java/.../Agent.java

# 暂存当前工作,切分支
git stash
git checkout -b experimental
git stash pop
```

## 推送认证 / Push Authentication

Windows 上用 PAT 存到 Windows Credential Manager:
```bash
git config --global credential.helper manager
# 第一次 push 时弹窗,输 PAT
# 之后自动读,不用每次输
```

如果 push 失败:
- 401 → PAT 过期,去 GitHub Settings → Developer settings → PAT 重发
- 403 → 没有权限,确认你是 xsqorange 账号的协作者
- `repository not found` → 远端 URL 错,`git remote -v` 检查

## Day 4 特殊流程 / Day 4 Special Workflow

Day 4 用了**新流程**(用户要求):
- ✅ 拉 day4 分支(从 main)
- ✅ 在 day4 工作
- ❌ **不** push 到远程
- ❌ **不** merge 到 main
- ✅ 全部本地 commit,等用户验收后再 push
