package com.agentbootcamp.mcp;

import com.agentbootcamp.Tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 Java {@link Tool} 接口适配成 MCP (Model Context Protocol) 工具定义。
 * Adapter from Java {@link Tool} to MCP tool definition.
 *
 * <p><b>MCP 工具 schema / MCP tool schema</b>:
 * <pre>{@code
 * { "name": "read_file",
 *   "description": "Read a file's content",
 *   "inputSchema": { "type": "object", "properties": {...}, "required": [...] } }
 * }</pre>
 *
 * <p>我们的 {@code Tool.jsonSchema()} 已经是 OpenAI tools[].parameters 格式 (JSON Schema 子集),
 * MCP 兼容 (顶层 type/properties/required 一样),直接复用。
 *
 * <p>Day 9 决策: 不用官方 Java MCP SDK (2026-06 还是 Kotlin-only),自己实现协议子集
 * (JSON-RPC 2.0 over stdio, 跟 Python {@code mcp} PyPI 包协议级兼容)。
 */
public class McpToolAdapter {

    private final Tool tool;

    public McpToolAdapter(Tool tool) {
        this.tool = tool;
    }

    public String name() {
        return tool.name();
    }

    public Tool tool() {
        return tool;
    }

    /**
     * 转 MCP 工具定义 (返回 LinkedHashMap 保 key 顺序,方便 debug).
     * Returns MCP tool definition.
     */
    public Map<String, Object> toMcpToolDefinition() {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("name", tool.name());
        def.put("description", tool.description());
        def.put("inputSchema", tool.jsonSchema());
        return def;
    }

    /**
     * 调底层 Java 工具,返回 MCP 工具结果 (string content).
     * Returns MCP tool result: {@code { content: [{ type: "text", text: "..." }] }}.
     *
     * <p>故意吞所有异常并返回错误字符串 (跟 Java Tool.execute() 风格一致),
     * 避免 MCP server 因为单个工具崩了整体挂掉。
     */
    public Map<String, Object> execute(Map<String, Object> args) {
        String resultText;
        try {
            resultText = tool.execute(args);
        } catch (Exception e) {
            resultText = "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        Map<String, Object> textContent = new LinkedHashMap<>();
        textContent.put("type", "text");
        textContent.put("text", resultText);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(textContent));
        return result;
    }
}
