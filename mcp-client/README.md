# agent-bootcamp MCP Client (Python)

> **中文**:跨语言 MCP 客户端,纯 Python stdlib,跟 `agent-bootcamp-mcp` Java server 通信。
> **English**: Cross-language MCP client (stdlib only), talks to the Java `McpServerMain`.

## 跑通先决条件 / Prerequisites

1. **Java server jar 已 build**:
   ```bash
   ./mvnw -B package -DskipTests
   ```
2. **Python 3.7+** (用了 `pathlib` / `subprocess` / `json` 三个 stdlib 模块,**不**需要 `pip install`)

## 用法 / Usage

### 列出所有工具
```bash
python mcp-client/client.py list
```

输出 (示例):
```json
[
  { "name": "read_file", "description": "...", "inputSchema": {...} },
  { "name": "write_file", ... },
  { "name": "get_current_time", ... }
]
```

### 调用工具
```bash
# 读 README
python mcp-client/client.py call read_file '{"path": "README.md"}'

# 写文件
python mcp-client/client.py call write_file '{"path": "target/from-python.txt", "content": "hello-from-python"}'

# 拿当前时间
python mcp-client/client.py call get_current_time '{}'

# 健康检查
python mcp-client/client.py ping
```

### 端到端测试
```bash
# 跑全部 7 个跨语言 E2E 用例
python mcp-client/test_e2e.py -v
```

期望输出:
```
test_01_initialize_handshake ... ok
test_02_list_tools_returns_3 ... ok
test_03_read_file_via_python ... ok
  ✓ Python → Java read_file 读出 12345 字符
test_04_write_file_via_python ... ok
  ✓ Python → Java write_file → Python 读回一致
test_05_get_current_time ... ok
  ✓ Python → Java get_current_time 返回: 2026-06-14T20:48:19+08:00
test_06_ping ... ok
test_07_unknown_tool_error ... ok
```

## 协议 / Protocol

**JSON-RPC 2.0 over stdio** (一行一个 JSON,UTF-8 编码,无 HTTP)。

**支持方法 / Methods**:
| Method | 方向 | 用途 |
|---|---|---|
| `initialize` | client → server | 握手,返回 serverInfo + capabilities + protocolVersion |
| `notifications/initialized` | client → server | 通知 server 握手完成 (无响应) |
| `tools/list` | client → server | 返回所有工具 schema |
| `tools/call` | client → server | 调工具,返回 `{ content: [{ type: "text", text: "..." }] }` |
| `ping` | client → server | 健康检查 |

**MCP protocol version**: `2024-11-05`

## 跨语言互通证据

`test_e2e.py` 的 7 个用例,逐个证明链路无丢失:

| # | 用例 | 链路 |
|---|---|---|
| 1 | `test_03_read_file_via_python` | Python → Java server → Java `ReadFile.execute()` → README.md |
| 2 | `test_04_write_file_via_python` | Python → Java server → Java `WriteFile.execute()` → 磁盘 → Python 读回 |
| 3 | `test_05_get_current_time` | Python → Java server → Java `GetCurrentTime.execute()` → 返回时间字符串 |

如果任一环节 JSON 序列化/反序列化出错,测试就会挂。**Day 9 完工 = 跨语言互通验证 = MCP 协议核心达成**。

## 为什么不用官方 `mcp` PyPI 包?

- 官方 `mcp` PyPI 包还在 0.x 快速迭代 (2026-06 月 1.0 还没出),API 经常变
- **stdlib only** = 0 依赖,任何 Python 3.7+ 装上就能跑,适合教学 + 演示
- 协议层跟官方 `mcp` 包完全一致 (JSON-RPC 2.0 + stdio),可以无缝切换

详见 [README.md 顶层 Day 9 章节](../README.md#day-9-架构--day-9-architecture-mcp-服务器--跨语言工具互通)。
