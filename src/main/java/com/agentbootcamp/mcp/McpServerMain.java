package com.agentbootcamp.mcp;

import com.agentbootcamp.tools.Exec;
import com.agentbootcamp.tools.GetCurrentTime;
import com.agentbootcamp.tools.ReadFile;
import com.agentbootcamp.tools.WriteFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP server (stdio transport, JSON-RPC 2.0).
 * 监听 stdin,接受一行一个 JSON 请求,返回一行一个 JSON 响应。
 *
 * <p><b>协议</b>: MCP (Model Context Protocol) JSON-RPC 2.0 over stdio。
 * 跟 Python {@code mcp} PyPI 包 (官方 MCP SDK) 协议级兼容。
 *
 * <p><b>支持方法 / Methods</b>:
 * <ul>
 *   <li>{@code initialize} — 握手,返回 server info + capabilities</li>
 *   <li>{@code tools/list} — 返回所有工具 schema (name / description / inputSchema)</li>
 *   <li>{@code tools/call} — 调工具,返回 result (content array of text)</li>
 *   <li>{@code ping} — 健康检查</li>
 *   <li>{@code notifications/initialized} — client 通知 (无响应)</li>
 * </ul>
 *
 * <p><b>跑法 / Run</b>:
 * <pre>{@code
 *   # 后台启动(配合 Python client 用)
 *   java -cp target/agent-bootcamp.jar com.agentbootcamp.mcp.McpServerMain
 *
 *   # 或开发模式
 *   ./mvnw exec:java -Dexec.mainClass="com.agentbootcamp.mcp.McpServerMain"
 * }</pre>
 * stdin 进 JSON, stdout 出 JSON, 日志走 stderr (不污染 stdout 协议流)。
 *
 * <p><b>Day 9 决策</b>: 不用官方 Java MCP SDK (Maven Central 2026-06 还只有 Kotlin SDK),
 * 自己实现协议子集 (~150 行),完全控制 + 无外部依赖 + 跨语言互通验证。
 */
public class McpServerMain {
    private static final Logger LOG = LoggerFactory.getLogger(McpServerMain.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SERVER_NAME = "agent-bootcamp-mcp";
    private static final String SERVER_VERSION = "0.1.0";
    /** MCP 协议版本 (2024-11-05 是当前 spec 推荐) */
    private static final String PROTOCOL_VERSION = "2024-11-05";

    public static void main(String[] args) throws Exception {
        // 注册 4 个工具 (跟 Day 1-3 Main 注册一致 + write_file 来自 Day 3)
        Path workDir = Path.of(".").toAbsolutePath().normalize();
        Map<String, McpToolAdapter> tools = new LinkedHashMap<>();
        tools.put("get_current_time", new McpToolAdapter(new GetCurrentTime()));
        tools.put("read_file", new McpToolAdapter(new ReadFile()));
        tools.put("write_file", new McpToolAdapter(new WriteFile(workDir)));
        tools.put("exec", new McpToolAdapter(new Exec()));

        LOG.info("MCP server '{}' v{} 启动,注册 {} 个工具: {}",
            SERVER_NAME, SERVER_VERSION, tools.size(), tools.keySet());

        runServer(tools, System.in, System.out);
        LOG.info("MCP server 关闭");
    }

    /**
     * 跑 server 主循环(包可见便于单测注入 InputStream/OutputStream)。
     * Runs the main server loop. Package-visible for unit testing with custom streams.
     */
    static void runServer(Map<String, McpToolAdapter> tools,
                          java.io.InputStream inStream,
                          java.io.OutputStream outStream) throws Exception {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                String response = handleRequest(line, tools);
                if (response != null) {
                    out.write(response);
                    out.newLine();
                    out.flush();
                }
            }
        }
    }

    /**
     * 处理一行 JSON-RPC 2.0 请求,返回响应 JSON (一行) 或 null (notification).
     * Handles a single JSON-RPC 2.0 request line. Returns response JSON (one line) or null for notifications.
     */
    static String handleRequest(String requestJson, Map<String, McpToolAdapter> tools) {
        Map<String, Object> req;
        try {
            req = JSON.readValue(requestJson, Map.class);
        } catch (Exception e) {
            return errorResponse(null, -32700, "Parse error: " + e.getMessage());
        }
        Object idObj = req.get("id");
        String method = (String) req.get("method");
        Object paramsObj = req.get("params");

        try {
            if (method == null) {
                return errorResponse(idObj, -32600, "Missing method");
            }
            switch (method) {
                case "initialize":
                    return initializeResponse(idObj);
                case "tools/list":
                    return toolsListResponse(idObj, tools);
                case "tools/call":
                    return toolsCallResponse(idObj, paramsObj, tools);
                case "ping":
                    return successResponse(idObj, Map.of());
                case "notifications/initialized":
                    LOG.info("client initialized (notification, no response)");
                    return null;
                default:
                    return errorResponse(idObj, -32601, "Method not found: " + method);
            }
        } catch (Exception e) {
            LOG.error("handleRequest 异常 method={}", method, e);
            return errorResponse(idObj, -32603, "Internal error: " + e.getMessage());
        }
    }

    // ------------------- 5 个 MCP 方法实现 -------------------

    private static String initializeResponse(Object id) {
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        Map<String, Object> toolsCap = Map.of("listChanged", false);
        Map<String, Object> capabilities = Map.of("tools", toolsCap);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serverInfo", serverInfo);
        result.put("capabilities", capabilities);
        result.put("protocolVersion", PROTOCOL_VERSION);
        return successResponse(id, result);
    }

    private static String toolsListResponse(Object id, Map<String, McpToolAdapter> tools) {
        List<Map<String, Object>> toolDefs = new ArrayList<>();
        for (McpToolAdapter t : tools.values()) {
            toolDefs.add(t.toMcpToolDefinition());
        }
        return successResponse(id, Map.of("tools", toolDefs));
    }

    @SuppressWarnings("unchecked")
    private static String toolsCallResponse(Object id, Object paramsObj, Map<String, McpToolAdapter> tools) {
        if (!(paramsObj instanceof Map)) {
            return errorResponse(id, -32602, "Missing params for tools/call");
        }
        Map<String, Object> params = (Map<String, Object>) paramsObj;
        String toolName = (String) params.get("name");
        if (toolName == null) {
            return errorResponse(id, -32602, "Missing 'name' in params");
        }
        McpToolAdapter tool = tools.get(toolName);
        if (tool == null) {
            return errorResponse(id, -32602, "Tool not found: " + toolName);
        }
        Map<String, Object> args = (Map<String, Object>) params.getOrDefault("arguments", Map.of());
        Map<String, Object> result = tool.execute(args);
        return successResponse(id, result);
    }

    // ------------------- JSON-RPC 2.0 响应包装 -------------------

    private static String successResponse(Object id, Object result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("result", result);
        return toJson(resp);
    }

    private static String errorResponse(Object id, int code, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("error", err);
        return toJson(resp);
    }

    private static String toJson(Object obj) {
        try {
            return JSON.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response: " + e.getMessage(), e);
        }
    }
}
