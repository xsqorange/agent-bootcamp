"""
agent-bootcamp MCP client (Python, stdlib only).
Spawns the Java McpServerMain as a subprocess and talks JSON-RPC 2.0 over stdio.

Day 9 跨语言客户端 — 纯 stdlib (subprocess + json) 不依赖 mcp PyPI 包,
跟 Java server 协议级兼容 (Model Context Protocol 2024-11-05 spec 子集)。
"""
import json
import subprocess
import sys
from typing import Any, Optional


class McpClient:
    """Spawns Java McpServerMain, speaks JSON-RPC 2.0 over stdio.

    Usage:
        client = McpClient()
        info = client.initialize()              # 握手
        tools = client.list_tools()              # 列工具
        result = client.call_tool("read_file",   # 调工具
                                  {"path": "README.md"})
        client.close()
    """

    def __init__(
        self,
        jar_path: str = "target/agent-bootcamp.jar",
        main_class: str = "com.agentbootcamp.mcp.McpServerMain",
        java_bin: str = "java",
    ):
        self.proc = subprocess.Popen(
            [java_bin, "-cp", jar_path, main_class],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,           # line-buffered
            encoding="utf-8",
        )
        self._next_id = 1

    def _send(self, method: str, params: Optional[Any] = None) -> dict:
        req_id = self._next_id
        self._next_id += 1
        msg = {"jsonrpc": "2.0", "id": req_id, "method": method}
        if params is not None:
            msg["params"] = params
        line = json.dumps(msg, ensure_ascii=False)
        self.proc.stdin.write(line + "\n")
        self.proc.stdin.flush()
        resp_line = self.proc.stdout.readline()
        if not resp_line:
            stderr = self.proc.stderr.read() or "<empty>"
            raise RuntimeError(f"server closed unexpectedly. stderr: {stderr[:500]}")
        return json.loads(resp_line)

    def initialize(self) -> dict:
        return self._send("initialize", {})["result"]

    def list_tools(self) -> list:
        return self._send("tools/list", {})["result"]["tools"]

    def call_tool(self, name: str, arguments: Optional[dict] = None) -> dict:
        result = self._send("tools/call", {"name": name, "arguments": arguments or {}})
        if "error" in result:
            raise RuntimeError(f"tool {name} error: {result['error']}")
        return result["result"]

    def ping(self) -> dict:
        return self._send("ping", {})["result"]

    def close(self):
        try:
            if self.proc.stdin:
                self.proc.stdin.close()
        except Exception:
            pass
        try:
            self.proc.wait(timeout=2)
        except Exception:
            self.proc.kill()


# ---------- CLI 入口 / CLI entry ----------
if __name__ == "__main__":
    import argparse
    p = argparse.ArgumentParser(description="agent-bootcamp MCP client")
    p.add_argument("command", choices=["list", "call", "ping"],
                   help="list 列工具, call 调工具, ping 健康检查")
    p.add_argument("tool_name", nargs="?", help="call 时需要的工具名")
    p.add_argument("args_json", nargs="?", default="{}",
                   help="call 时需要的参数 JSON, 如 '{\"path\": \"README.md\"}'")
    p.add_argument("--jar", default="target/agent-bootcamp.jar", help="Java server jar 路径")
    p.add_argument("--java", default="java", help="Java 可执行文件")
    args = p.parse_args()

    client = McpClient(args.jar, java_bin=args.java)
    try:
        if args.command == "list":
            client.initialize()  # 握手(可选,但更规范)
            tools = client.list_tools()
            print(json.dumps(tools, indent=2, ensure_ascii=False))
        elif args.command == "call":
            client.initialize()
            result = client.call_tool(args.tool_name, json.loads(args.args_json))
            for c in result.get("content", []):
                if c.get("type") == "text":
                    print(c.get("text", ""))
        elif args.command == "ping":
            result = client.ping()
            print(json.dumps(result, ensure_ascii=False))
    finally:
        client.close()
