package com.agentbootcamp.testing;

import com.agentbootcamp.LlmClient;
import com.agentbootcamp.LlmConfig;
import com.agentbootcamp.Message;
import com.agentbootcamp.Agent;
import com.agentbootcamp.TraceWriter;
import com.agentbootcamp.tools.GetCurrentTime;
import com.agentbootcamp.tools.ReadFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Day 12 任务 3: ScriptedLlmClient 单元测试.
 * 5 cases: 预录顺序 / 脚本耗尽 / Agent 集成 / callCount / 与真 LlmClient 接口兼容.
 */
class ScriptedLlmClientTest {

    @Test
    void chat_replaysScriptInOrder() throws Exception {
        // 预录 3 个 response,第 N 次 chat() 返第 N 个
        var resp1 = new LlmClient.LlmResponse("first", null, 10, 5);
        var resp2 = new LlmClient.LlmResponse("second", null, 20, 10);
        var resp3 = new LlmClient.LlmResponse("third", null, 30, 15);
        var scripted = new ScriptedLlmClient(List.of(resp1, resp2, resp3));

        assertEquals("first", scripted.chat(List.of(), null).content());
        assertEquals("second", scripted.chat(List.of(), null).content());
        assertEquals("third", scripted.chat(List.of(), null).content());
        assertEquals(3, scripted.getCallCount());
        assertEquals(3, scripted.getScriptSize());
    }

    @Test
    void chat_exhaustedScript_returnsFallback() throws Exception {
        // 脚本只有 1 个,第 2 次返 fallback
        var scripted = new ScriptedLlmClient(List.of(
            new LlmClient.LlmResponse("first", null, 10, 5)
        ));
        assertEquals("first", scripted.chat(List.of(), null).content());
        var fallback = scripted.chat(List.of(), null);
        assertTrue(fallback.content().contains("exhausted"), "fallback 应含 'exhausted'");
        assertEquals(2, scripted.getCallCount());
    }

    @Test
    void emptyScript_firstCallReturnsFallback() throws Exception {
        // 空脚本 (默认构造)
        var scripted = new ScriptedLlmClient();
        var fallback = scripted.chat(List.of(), null);
        assertTrue(fallback.content().contains("exhausted"));
        assertEquals(0, scripted.getScriptSize());
    }

    @Test
    void chat_integrationWithAgent_runsWithoutRealLlm() throws Exception {
        // 关键集成测试: ScriptedLlmClient + Agent + 真实 GetCurrentTime 工具
        // 不调真 LLM, 但工具真的跑 (GetCurrentTime 返当前时间)
        var toolCall1 = new Message.ToolCall(
            "call_1",
            "function",
            new Message.FunctionCall("get_current_time", "{}")
        );
        var scripted = new ScriptedLlmClient(List.of(
            // Response 1: LLM 决定调 get_current_time
            new LlmClient.LlmResponse("calling time tool", List.of(toolCall1), 50, 20),
            // Response 2: LLM 看到工具结果, 返 final answer
            new LlmClient.LlmResponse("done", null, 30, 10)
        ));

        List<com.agentbootcamp.Tool> tools = List.of(new GetCurrentTime());
        // TraceWriter 接受 String path,不用 Path.of
        TraceWriter trace = new TraceWriter("target/test-trace-scripted.jsonl");
        Agent agent = new Agent(scripted, tools, trace, 5, 1.0, null, null, null);

        var result = agent.run("test goal");
        // 验证 agent 跑完, 调了 2 次 LLM, 没抛
        assertEquals(2, scripted.getCallCount(), "Agent 应调 2 次 LLM (tool call + final answer)");
        assertNotNull(result);
        assertNotNull(result.finalAnswer());
    }

    @Test
    void consumedResponses_matchesCallCount() throws Exception {
        var scripted = new ScriptedLlmClient(List.of(
            new LlmClient.LlmResponse("a", null, 1, 1),
            new LlmClient.LlmResponse("b", null, 1, 1)
        ));
        scripted.chat(List.of(), null);
        scripted.chat(List.of(), null);
        assertEquals(2, scripted.getConsumedResponses().size());
        assertEquals("a", scripted.getConsumedResponses().get(0).content());
        assertEquals("b", scripted.getConsumedResponses().get(1).content());
    }
}
