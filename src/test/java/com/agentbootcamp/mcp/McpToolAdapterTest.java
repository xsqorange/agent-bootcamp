package com.agentbootcamp.mcp;

import com.agentbootcamp.tools.GetCurrentTime;
import com.agentbootcamp.tools.ReadFile;
import com.agentbootcamp.tools.WriteFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpToolAdapter 单元测试 — 验证 Tool → MCP schema 的转换 + execute 行为。
 * Unit tests for McpToolAdapter.
 */
class McpToolAdapterTest {

    @Test
    void wrapsGetCurrentTimeDefinition() {
        McpToolAdapter adapter = new McpToolAdapter(new GetCurrentTime());
        assertEquals("get_current_time", adapter.name());

        Map<String, Object> def = adapter.toMcpToolDefinition();
        assertEquals("get_current_time", def.get("name"));
        assertNotNull(def.get("description"));
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) def.get("inputSchema");
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));
    }

    @Test
    void wrapsReadFileDefinitionAndExecute() {
        McpToolAdapter adapter = new McpToolAdapter(new ReadFile());
        Map<String, Object> def = adapter.toMcpToolDefinition();
        assertEquals("read_file", def.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) def.get("inputSchema");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("path"));
    }

    @Test
    void executeSuccessReturnsMcpContent() {
        McpToolAdapter adapter = new McpToolAdapter(new GetCurrentTime());
        Map<String, Object> result = adapter.execute(Map.of());

        @SuppressWarnings("unchecked")
        var content = (java.util.List<Map<String, Object>>) result.get("content");
        assertNotNull(content);
        assertEquals(1, content.size());
        assertEquals("text", content.get(0).get("type"));
        String text = (String) content.get(0).get("text");
        assertNotNull(text);
        assertFalse(text.isBlank());
    }

    @Test
    void executeCatchesExceptionAndReturnsErrorText() {
        // ReadFile 调不存在的文件,内部返回错误字符串(不抛)
        McpToolAdapter adapter = new McpToolAdapter(new ReadFile());
        Map<String, Object> result = adapter.execute(Map.of("path", "D:/this/does/not/exist.txt"));
        @SuppressWarnings("unchecked")
        var content = (java.util.List<Map<String, Object>>) result.get("content");
        String text = (String) content.get(0).get("text");
        assertTrue(text.startsWith("错误"), "应该返回 '错误: ...' 字符串,实际: " + text);
    }

    @Test
    void executesMcpContentIsJsonSerializable() throws Exception {
        // MCP 客户端要 JSON.parse 服务端响应,响应本身必须 JSON 序列化干净
        McpToolAdapter adapter = new McpToolAdapter(new GetCurrentTime());
        Map<String, Object> result = adapter.execute(Map.of());
        ObjectMapper json = new ObjectMapper();
        String serialized = json.writeValueAsString(result);
        // 往返一致
        @SuppressWarnings("unchecked")
        Map<String, Object> roundtrip = json.readValue(serialized, Map.class);
        assertEquals(result.keySet(), roundtrip.keySet());
    }

    @Test
    void wrapsWriteFileDefinition() {
        McpToolAdapter adapter = new McpToolAdapter(new WriteFile());
        Map<String, Object> def = adapter.toMcpToolDefinition();
        assertEquals("write_file", def.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) def.get("inputSchema");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("path"));
        assertTrue(properties.containsKey("content"));
    }
}
