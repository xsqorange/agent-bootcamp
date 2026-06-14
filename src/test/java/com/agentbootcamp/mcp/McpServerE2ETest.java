package com.agentbootcamp.mcp;

import com.agentbootcamp.tools.ReadFile;
import com.agentbootcamp.tools.WriteFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpServerMain 端到端测试 (Java 端):用 ByteArrayInputStream 喂请求,
 * ByteArrayOutputStream 收响应,验 JSON-RPC 2.0 over stdio 协议正确。
 *
 * <p>这个测试**模拟** Python client 的行为 (一行一个 JSON 请求),
 * 证明协议层正确,跨语言互通就有底。
 */
class McpServerE2ETest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 跑 server 处理一个请求(模拟 stdin/stdout 一次性交互)
     *  Runs server with a single request, returns response. */
    private String runOneRequest(String requestJson) throws Exception {
        Map<String, McpToolAdapter> tools = new LinkedHashMap<>();
        tools.put("read_file", new McpToolAdapter(new ReadFile()));
        tools.put("write_file", new McpToolAdapter(new WriteFile(
            Path.of(".").toAbsolutePath().normalize())));
        tools.put("get_current_time", new McpToolAdapter(new com.agentbootcamp.tools.GetCurrentTime()));

        // 拼成多行(模拟 client 可能连续发多个请求,server 也答多行)
        ByteArrayInputStream in = new ByteArrayInputStream((requestJson + "\n").getBytes("UTF-8"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        McpServerMain.runServer(tools, in, out);
        // 拿到最后一行非空响应
        String all = out.toString("UTF-8").trim();
        return all.isEmpty() ? "" : all.split("\n")[0];
    }

    @Test
    void initializeHandshake() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        String resp = runOneRequest(req);
        assertFalse(resp.isEmpty());
        @SuppressWarnings("unchecked")
        Map<String, Object> r = JSON.readValue(resp, Map.class);
        assertEquals("2.0", r.get("jsonrpc"));
        assertEquals(1, ((Number) r.get("id")).intValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) r.get("result");
        @SuppressWarnings("unchecked")
        Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
        assertEquals("agent-bootcamp-mcp", serverInfo.get("name"));
        assertEquals("0.1.0", serverInfo.get("version"));
        assertNotNull(result.get("capabilities"));
        assertNotNull(result.get("protocolVersion"));
    }

    @Test
    void toolsListReturns3Tools() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";
        String resp = runOneRequest(req);
        @SuppressWarnings("unchecked")
        Map<String, Object> r = JSON.readValue(resp, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) r.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        assertEquals(3, tools.size());
        // 顺序由 LinkedHashMap 决定: read_file / write_file / get_current_time
        assertEquals("read_file", tools.get(0).get("name"));
        assertEquals("write_file", tools.get(1).get("name"));
        assertEquals("get_current_time", tools.get(2).get("name"));
        // 每个 tool 都有 schema
        for (Map<String, Object> t : tools) {
            assertNotNull(t.get("description"));
            assertNotNull(t.get("inputSchema"));
        }
    }

    @Test
    void toolsCallReadFile() throws Exception {
        // 读真实存在的 README.md
        String req = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\"," +
            "\"params\":{\"name\":\"read_file\",\"arguments\":{\"path\":\"README.md\"}}}";
        String resp = runOneRequest(req);
        @SuppressWarnings("unchecked")
        Map<String, Object> r = JSON.readValue(resp, Map.class);
        assertNull(r.get("error"), "unexpected error: " + r.get("error"));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) r.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        String text = (String) content.get(0).get("text");
        assertTrue(text.contains("Agent Bootcamp"), "should contain 'Agent Bootcamp', got: " + text.substring(0, Math.min(100, text.length())));
    }

    @Test
    void toolsCallWriteFile() throws Exception {
        // 写一个临时文件,然后读回来验内容一致
        Path tmp = Path.of("target/test-mcp-write.txt");
        Files.createDirectories(tmp.getParent());
        String req = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\"," +
            "\"params\":{\"name\":\"write_file\",\"arguments\":{\"path\":\"target/test-mcp-write.txt\"," +
            "\"content\":\"MCP-CROSS-LANG-OK\"}}}";
        String resp = runOneRequest(req);
        @SuppressWarnings("unchecked")
        Map<String, Object> r = JSON.readValue(resp, Map.class);
        assertNull(r.get("error"));
        // 验证文件确实写入了
        String content = Files.readString(tmp);
        assertEquals("MCP-CROSS-LANG-OK", content);
        Files.deleteIfExists(tmp);
    }

    @Test
    void unknownMethodReturnsJsonRpcError() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"nope/missing\"}";
        String resp = runOneRequest(req);
        @SuppressWarnings("unchecked")
        Map<String, Object> r = JSON.readValue(resp, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) r.get("error");
        assertNotNull(error);
        assertEquals(-32601, error.get("code"));  // Method not found
    }

    @Test
    void unknownToolReturnsError() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\"," +
            "\"params\":{\"name\":\"unknown_tool\",\"arguments\":{}}}";
        String resp = runOneRequest(req);
        @SuppressWarnings("unchecked")
        Map<String, Object> r = JSON.readValue(resp, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) r.get("error");
        assertNotNull(error);
        assertTrue(error.get("message").toString().contains("not found"));
    }

    @Test
    void parseErrorOnInvalidJson() throws Exception {
        String req = "this is not json {{{";
        String resp = runOneRequest(req);
        @SuppressWarnings("unchecked")
        Map<String, Object> r = JSON.readValue(resp, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) r.get("error");
        assertNotNull(error);
        assertEquals(-32700, error.get("code"));  // Parse error
    }

    @Test
    void notificationReturnsEmptyResponse() throws Exception {
        // notifications/initialized 是 client→server 通知,不应有响应
        String req = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        String resp = runOneRequest(req);
        assertEquals("", resp, "notification should not produce response line");
    }
}
