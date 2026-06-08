# Java 17 + Maven 常用命令 / Java 17 + Maven Common Commands

## 一次性配置 / One-time Setup (已完成)

本机 Java 17 在 `C:\Users\Admin\.jdks\corretto-17.0.17`,Maven 3.9.9 在 `~/tools/apache-maven-3.9.9`。
两者已通过 `setx` 写入 Windows 用户级环境变量(注册表 `HKEY_CURRENT_USER\Environment`)。

**验证是否生效**:
```bash
java -version    # 17.0.17
mvn --version    # 3.9.9
echo $JAVA_HOME  # C:\Users\Admin\.jdks\corretto-17.0.17
```

## 编译 / Compile

```bash
# 编译主代码
./mvnw compile

# 编译主代码 + 测试代码
./mvnw test-compile

# 清理 + 编译
./mvnw clean compile
```

## 跑 Agent / Run Agent

```bash
# 单次调用
./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--goal '现在几点?'"

# 带 trace
./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" \
  -Dexec.args="--goal '看 README.md 然后总结' --max-steps 5 --trace target/trace.jsonl"

# 只展示工具列表(不调 LLM)
./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.Main" -Dexec.args="--dry-run --goal test"
```

## 跑测试 / Run Tests

```bash
# 跑全部测试
./mvnw test

# 只跑单元测试(不烧 API 钱)
./mvnw test -Dtest='WriteFileTest,GrepTest,MemoryManagerTest,RagIndexTest,SearchKbTest'

# 跑某一个
./mvnw test -Dtest=AgentTest#test11_*

# 详细输出
./mvnw test -X
```

## 打包 / Package

```bash
# 打成 jar (target/agent-bootcamp-0.x.x.jar)
./mvnw package

# 跳过测试
./mvnw package -DskipTests

# 直接跑 jar
java -jar target/agent-bootcamp-0.3.0.jar --goal "..."
```

## 调试 / Debug

```bash
# 在 VSCode 里:F5 启动 launch.json 配置
# 或命令行:JVM 远程调试
./mvnw exec:exec -Dexec.executable="java" -Dexec.args="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 ..."
```

## 常见错误 / Common Errors

- **`java: command not found`** → 重开 shell (env vars 是用户级,新 shell 才生效)
- **`mvnw: Permission denied`** → `chmod +x mvnw` (Linux/Mac)
- **Maven 下载慢** → 配 `~/.m2/settings.xml` 用阿里云镜像
- **UTF-8 BOM 错误** → `config.yaml` 用了 Windows Notepad 编辑会加 BOM,用 VSCode 或 `hermes config edit`

## 性能 / Performance

- **单元测试** < 1 秒
- **端到端测试** (调真 LLM) 每个 5-15 秒
- **5 个端到端测试** 总共 ~45 秒
- **冷启动编译** ~3 秒 (增量编译 < 1 秒)
