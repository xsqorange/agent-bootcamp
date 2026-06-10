# Agent Bootcamp Runbook / 故障排查手册

> **中文**:1 页常见故障 + 排查命令。遇到问题先翻这里。
> **English**: 1-page troubleshooting cheat sheet. Check here first when something breaks.

---

## 🔥 故障 1: `MINIMAX_API_KEY` 没设 / 设错 / 失效

**症状**:
- `mvn test` 跑 E2E 时全 skipped
- `mvn exec:java` 报 `401 Unauthorized` / `403 Forbidden`
- 日志:`[LlmConfig] keyPrefix=` 空 / `sk-XXX` 但长度不对

**排查命令**:
```bash
# 1. 看 .env 是否有 key
cat .env | grep -E "API_KEY|LLM_"

# 2. 加载并验证前缀
set -a && source .env && set +a
echo "key 前缀: ${MINIMAX_API_KEY:0:15}"
# 期望: sk-cp-XXXXXXXX (MiniMax 公司,约 125 字符)

# 3. 验 key 真能调通
curl -s -H "Authorization: Bearer $MINIMAX_API_KEY" \
  $LLM_BASE_URL/models | head -3
# 期望: 返回 JSON 数组,不是 401/403
```

**修法**:
- key 没了 → 去 [MiniMax 控制台](https://api.minimaxi.com) 重生一个,贴到 `.env`
- 用了别的厂商 key(如 `sk-...` OpenAI 格式) → 改 `LLM_BASE_URL` 和 `LLM_MODEL`

---

## 🐢 故障 2: Java / Maven 没在 PATH

**症状**:
- `java -version` → `command not found`
- `mvn -version` → `command not found` 或 `JAVA_HOME is not set`

**排查命令**:
```bash
java -version
mvn -version
echo "JAVA_HOME=$JAVA_HOME"
```

**修法** (Windows 11,USER 作用域,免 admin):
```powershell
# 一行搞定
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Users\Admin\.jdks\corretto-17.0.17", "User")
[Environment]::SetEnvironmentVariable("MAVEN_HOME", "C:\Users\Admin\tools\apache-maven-3.9.9", "User")
$env:PATH = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:PATH"
[Environment]::SetEnvironmentVariable("PATH", $env:PATH, "User")
```

Mac / Linux:在 `~/.zshrc` / `~/.bashrc` 加:
```bash
export JAVA_HOME=$HOME/.jdks/corretto-17.0.17
export MAVEN_HOME=$HOME/tools/apache-maven-3.9.9
export PATH=$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH
```

---

## 🚨 故障 3: LLM API 429 Rate Limit

**症状**:
- 跑 10+ 个真 LLM 测试中途报 `429 Token Plan ... 并发过高`
- `mvn verify` 跑 2 次,1 次挂 1 次过 (flake)

**排查命令**:
```bash
# 1. 看 EvalHarness retry 日志
grep "429\|retry\|Retry" target/eval-reports/*.jsonl 2>/dev/null

# 2. 单独跑某个 case 验是否稳
./mvnw test -Dtest=AgentTest#test12_SearchKbFindsDay3Tools
```

**修法**:
- 短期:`mvn verify` 跑通即可,512 跑 3 次过 2 次算 OK (Day 5 README pitfall #19)
- 长期(Day 11+):用 `ScriptedLlmClient` 重放真实 response,无 LLM 依赖
- 根本:Different provider / upgrade plan

---

## 📦 故障 4: Maven shade plugin WARNING (slf4j 重叠)

**症状**:
- `mvn package` 输出:
  ```
  [WARNING] slf4j-api-2.0.13.jar, slf4j-simple-2.0.13.jar define 1 overlapping resource:
    - META-INF/LICENSE.txt
  ```

**影响**:**无**。警告,不影响功能。生成的 jar 能跑。

**修法** (可忽略,Day 7 polish):在 `pom.xml` 的 `maven-shade-plugin` 加 `<filters>`:
```xml
<filter>
  <artifact>*:*</artifact>
  <excludes>
    <exclude>META-INF/*.SF</exclude>
    <exclude>META-INF/*.DSA</exclude>
    <exclude>META-INF/*.RSA</exclude>
  </excludes>
</filter>
```

---

## 🔐 故障 5: GitHub PAT 缺 `workflow` scope

**症状**:
- `git push` 含 `.github/workflows/*.yml` 改动时报:
  ```
  ! [remote rejected] ... (refusing to allow a Personal Access Token to
  create or update workflow `.github/workflows/build.yml` without `workflow` scope)
  ```

**修法**:
- **Classic PAT**:https://github.com/settings/tokens → 找到 token → Edit → 勾 `workflow` scope → Update
- **Fine-grained PAT**:同一页 → Permissions → Repository permissions → Actions:Read and write → 保存
- 改完重推即可,**不用重新 git commit**

---

## 🧪 故障 6: 测试 flake (跑 2 次结果不一样)

**症状**:
- `mvn verify` 跑 3 次,2 次过 1 次挂
- 失败的是 E2E(AgentTest 或 EvalRunnerTest),不是单元

**根因**:LLM 输出不稳定。模型有时说 `write_file`,有时说 `WriteFile`,有时说 `write file`。

**修法**:
- 短期:接受 flake,跑多次取多数(majority voting)
- 中期:断言都做 normalize(去掉 `_`/`-`/空格后匹配),见 `AgentTest.java:251-258`
- 长期(Day 11+):`ScriptedLlmClient` 录真实 response,重放无 LLM 依赖

```bash
# 跑 3 次验稳定度
for i in 1 2 3; do
  echo "--- Run $i ---"
  ./mvnw -B verify 2>&1 | grep -E "Tests run.*Failures" | tail -1
done
```

---

## 🔧 故障 7: 工具相对路径解析错(经典坑)

**症状**:
- 工具读文件时 `Paths.get("foo.txt").toAbsolutePath()` 解析到 **JVM 的 `user.dir`**,不是 tool 构造时传的 `workDir`
- `@TempDir` 单测立刻暴露,生产碰巧 work

**修法**:相对路径必须 `workDir.resolve(raw).normalize()`,绝对路径才直接 `Paths.get()`。**详见 Day 3 README 章节 "Bug A"**。

```java
// ❌ 错
Path p = Paths.get(args.get("path")).toAbsolutePath();

// ✅ 对
Path raw = Paths.get(args.get("path"));
Path p = raw.isAbsolute() ? raw : workDir.resolve(raw).normalize();
```

---

## 🆘 故障 8: 完全卡死 / 不知道啥问题

**救火顺序**:
1. 看最新 trace: `tail -50 target/trace.jsonl | jq .`
2. 看 EvalReport: `cat evals/reports/<id>.jsonl | head -3 | jq .`
3. 看 Maven 详细日志: `./mvnw -X test 2>&1 | tail -100`
4. 重跑单个 case 隔离: `./mvnw test -Dtest=AgentTest#test12_...`
5. 清缓存重建: `./mvnw clean && ./mvnw test`
6. 提 Issue 贴 stack trace

**有用的诊断信息**:
- `git log --oneline -5` — 最近改了什么
- `java -version && mvn -version` — 环境状态
- `uname -a`(Git Bash 跑) — 实际 OS
- `env | grep -E "LLM_|API_KEY"` — 加载的 env

---

*Last updated: 2026-06-10 (Day 7) — 8 个常见故障,5 分钟能看完*
