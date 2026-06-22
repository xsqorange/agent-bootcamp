# Day 9 收束博客:MCP 服务器 + Python 跨语言客户端

> **中文**:自实现 MCP 协议子集 (200 行 Java JSON-RPC 2.0 over stdio) + Python stdlib 0 依赖客户端,**官方 Java SDK 不存在 (2026-06 Maven Central 只有 Kotlin SDK)**。
>
> **English**: Day 9 — self-implemented MCP protocol subset (JSON-RPC 2.0 over stdio) + Python stdlib client. **Official Java SDK does not exist** (2026-06 Maven Central only has Kotlin SDK).

---

## 🎯 背景

Day 8 多 Agent 跑通,Day 9 想跟外部语言协作:
- **Java Agent 想用 Python 写的 ML 工具** → 跨语言调用
- **其它 Agent 想用我们 6 工具** → 跨语言暴露

**MCP** (Model Context Protocol) 是 Anthropic 2024-11 发布的协议,目标是"AI 工具跨语言互操作"。**但官方只发 Kotlin SDK,Java 没有**。

**3 个 Java 实际选项**:
| 选项 | 依赖 | 优劣 |
|---|---|---|
| A. Spring AI MCP | Spring 全家桶 | 已用 Spring 的项目 |
| B. LangChain4j MCP | LangChain4j | 跟 LLM 紧 |
| **C. 自实现 JSON-RPC 2.0** (推荐) | **0 新依赖** | **真懂协议, 跟 Day 1-8 风格一致** |

---

## 🏗️ 3 大组件

### McpServerMain (Java stdio server, ~200 行)

```java
public class McpServerMain {
    public static void main(String[] args) throws IOException {
        Map<String, McpToolAdapter> tools = defaultTools(Path.of("."));
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        BufferedWriter stdout = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));

        String line;
        while ((line = stdin.readLine()) != null) {
            JsonRpcRequest req = JSON.readValue(line, JsonRpcRequest.class);
            JsonRpcResponse resp = switch (req.method()) {
                case "initialize" -> handleInitialize(req);
                case "tools/list"  -> handleToolsList(req, tools);
                case "tools/call"  -> handleToolsCall(req, tools);
                default -> error(req, -32601, "Method not found");
            };
            stdout.write(JSON.writeValueAsString(resp));
            stdout.newLine();
            stdout.flush();  // 必须 flush,line buffering 坑
        }
    }
}
```

### Python stdlib client (`mcp-client/client.py`, ~115 行)

```python
import subprocess, json

def main():
    proc = subprocess.Popen(
        ["java", "-jar", "target/agent-bootcamp.jar", "--mcp-server"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        bufsize=1, encoding="utf-8"  # 行缓冲 + UTF-8
    )
    send(proc, "initialize", {"protocolVersion": "2024-11-05"})
    tools = send(proc, "tools/list", {}).result["tools"]
    print("tools:", [t["name"] for t in tools])
    result = send(proc, "tools/call", {"name": "read_file", "args": {"path": "README.md"}})
    print("result:", result["result"]["content"][0]["text"])
    proc.terminate()
```

### 7 跨语言 E2E 测试 (`test_e2e.py`, 105 行)

**金标准**: Python 写 → Java 调 `write_file` → Python 读回内容**完全一致**

```python
def test_write_file_roundtrip():
    payload = "中文 payload 🚀 " + "x" * 100
    send(proc, "tools/call", {"name": "write_file", "args": {"path": "target/tc.txt", "content": payload}})
    with open("target/tc.txt", encoding="utf-8") as f:
        assert f.read() == payload
```

---

## 🐛 5 个 Day 9 真坑

1. **MCP Java 官方 SDK 不存在** — Maven Central `io/modelcontextprotocol/` 只有 Kotlin SDK
2. **stdio 跨语言缓冲 + 编码** — Python `bufsize=0` 全缓冲 → Java 写出 Python 看不到;**修法**: `bufsize=1, encoding="utf-8"`
3. **main() 跟 E2E 注册工具不一致** — `McpServerMain.main()` 只注册 3 工具,测试期望 4 个;**修法**: 抽 `defaultTools()` 静态方法
4. **.pyc 误提交** — `mcp-client/__pycache__/client.cpython-313.pyc` 进 git;**修法**: `.gitignore` + `git rm --cached`
5. **amend 错对象** — `git commit --amend --no-edit` 默认改 HEAD;**修法**: `git reset --soft HEAD~N` + 重拆

---

## 📊 验收数据

| 指标 | 数字 |
|---|---|
| 新增文件 | 7 (McpProtocol + McpServerMain + McpToolAdapter + 4 测试 + mcp-client/ 3 文件) |
| 新增工具 | 0 (暴露现有 4 工具给 MCP) |
| 总测试 | **67** (60 Java + 7 Python 跨语言) ✅ 全过 |
| 跨语言链路 | Python → Java 37469 字符 read / write 读回一致 / ISO 8601 time |
| Commit | 3 (feat + test + docs) |
| 烧钱 | ~$0.002 |

---

## 🚀 Day 10 预告

**3 Agent 团队 / 3-Agent Crew** — 加 1 个 worker (共 3 个),典型组合:
- **Researcher** (只读): `read_file` / `grep` / `exec("git log")`
- **Critic** (分析): 纯推理,无工具
- **Editor** (写入): `write_file` / `edit_file`

Code review pipeline 端到端: PR diff → Researcher summary → Critic bug 找 → Editor patch → Orchestrator 汇总。