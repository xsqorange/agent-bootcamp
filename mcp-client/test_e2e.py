"""
跨语言 E2E 测试 / Cross-language E2E tests:
Python client → Java McpServerMain → Java Tool → 文件系统

证明 MCP 协议子集 (JSON-RPC 2.0 over stdio) 跨语言互通无误。

跑法 / Run:
  1. ./mvnw -B package -DskipTests   # 生成 target/agent-bootcamp.jar
  2. python mcp-client/test_e2e.py -v
"""
import json
import os
import sys
import unittest
from pathlib import Path

# 允许从 mcp-client/ 目录 import client
sys.path.insert(0, str(Path(__file__).parent))
from client import McpClient


class McpCrossLangE2E(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        jar = Path("target/agent-bootcamp.jar")
        if not jar.exists():
            raise unittest.SkipTest(
                f"{jar} 不存在,先跑 ./mvnw -B package -DskipTests (从项目根)"
            )
        cls.client = McpClient(str(jar))

    @classmethod
    def tearDownClass(cls):
        cls.client.close()

    def test_01_initialize_handshake(self):
        info = self.client.initialize()
        self.assertEqual(info["serverInfo"]["name"], "agent-bootcamp-mcp")
        self.assertEqual(info["serverInfo"]["version"], "0.1.0")
        self.assertIn("tools", info["capabilities"])
        self.assertEqual(info["protocolVersion"], "2024-11-05")

    def test_02_list_tools_returns_4(self):
        tools = self.client.list_tools()
        self.assertEqual(len(tools), 4, f"应返回 4 个工具,实际 {len(tools)}")
        names = [t["name"] for t in tools]
        for expected in ("get_current_time", "read_file", "write_file", "exec"):
            self.assertIn(expected, names, f"缺少工具 {expected},实际: {names}")
        for t in tools:
            self.assertIn("description", t)
            self.assertIn("inputSchema", t)

    def test_03_read_file_via_python(self):
        """Python client 调 Java server 读 README.md,内容含 'Agent Bootcamp'"""
        result = self.client.call_tool("read_file", {"path": "README.md"})
        content = result["content"]
        self.assertEqual(len(content), 1)
        self.assertEqual(content[0]["type"], "text")
        text = content[0]["text"]
        self.assertIn("Agent Bootcamp", text,
            f"README 应含 'Agent Bootcamp',实际前 100 字符: {text[:100]}")
        print(f"\n  ✓ Python → Java read_file 读出 {len(text)} 字符")

    def test_04_write_file_via_python(self):
        """Python client 调 Java server 写文件,然后 Python 读回验内容"""
        path = "target/test-cross-lang-write.txt"
        if os.path.exists(path):
            os.remove(path)
        result = self.client.call_tool("write_file", {
            "path": path,
            "content": "PYTHON-CALLED-MCP-OK"
        })
        text = result["content"][0]["text"]
        self.assertIn("wrote", text, f"应含 'wrote',实际: {text}")
        # 用 Python 读回 (跟 Java 跨语言验证)
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
        self.assertEqual(content, "PYTHON-CALLED-MCP-OK",
            f"Python 读回内容不一致,实际: {content}")
        os.remove(path)
        print(f"\n  ✓ Python → Java write_file → Python 读回一致")

    def test_05_get_current_time(self):
        result = self.client.call_tool("get_current_time", {})
        text = result["content"][0]["text"]
        # ISO 8601 含 T 分隔符
        self.assertTrue("T" in text or ":" in text,
            f"get_current_time 应返回时间字符串,实际: {text}")
        print(f"\n  ✓ Python → Java get_current_time 返回: {text}")

    def test_06_ping(self):
        result = self.client.ping()
        self.assertEqual(result, {})

    def test_07_unknown_tool_error(self):
        """调不存在的工具,Java server 应返回 JSON-RPC error(通过 stdout 协议层)"""
        with self.assertRaises(RuntimeError) as ctx:
            self.client.call_tool("nonexistent_tool_xyz", {})
        self.assertIn("not found", str(ctx.exception))


if __name__ == "__main__":
    unittest.main(verbosity=2)
