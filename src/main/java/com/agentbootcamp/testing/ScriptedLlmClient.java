package com.agentbootcamp.testing;

import com.agentbootcamp.LlmClient;
import com.agentbootcamp.LlmConfig;
import com.agentbootcamp.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Day 12 任务 3: ScriptedLlmClient - 重放预录 LLM response,治 LLM 评测 flake.
 *
 * 中文:CI 跑端到端 eval 时不依赖真 LLM (无 API key, 无 429, 无 5xx flake),
 *      用预录的 LlmResponse 列表按顺序返回. 第 N 次 chat() 返列表第 N 个 response.
 *      List 空时返 fallback (默认 "ok"). 跟 ResilientLlmClient 同款 extends LlmClient,
 *      Main / Agent 零代码改.
 * English: Replay pre-recorded LLM responses for deterministic CI eval (no API key, no 429 flake).
 *      Nth chat() returns Nth response from script. Falls back to default when script exhausted.
 *
 * 用法 / Usage:
 *   List<LlmResponse> script = List.of(
 *     new LlmResponse("read README", List.of(toolCallReadFile), 100, 50),
 *     new LlmResponse("done", null, 50, 20)
 *   );
 *   LlmClient scripted = new ScriptedLlmClient(script);
 *   Agent agent = new Agent(scripted, ...);  // Agent 零改
 *
 *   // 验证跑完
 *   assertEquals(2, scripted.getCallCount());
 *   assertEquals(2, scripted.getResponses().size());  // 全部用完
 */
public class ScriptedLlmClient extends LlmClient {
    private static final Logger log = LoggerFactory.getLogger(ScriptedLlmClient.class);

    private final List<LlmResponse> script;
    private int callCount = 0;
    private final List<LlmResponse> consumed = new ArrayList<>();

    /**
     * 构造带脚本的 ScriptedLlmClient.
     * @param script 预录的 LlmResponse 列表 (按顺序消费)
     */
    public ScriptedLlmClient(List<LlmResponse> script) {
        super(new LlmConfig("scripted-dummy-key", "http://scripted.invalid", "scripted-model", 10));
        this.script = List.copyOf(script);
    }

    /**
     * 构造空脚本的 ScriptedLlmClient (每次返 defaultResponse,用于简单测试).
     */
    public ScriptedLlmClient() {
        this(List.of());
    }

    @Override
    public LlmResponse chat(List<Message> messages, List<Map<String, Object>> tools) {
        callCount++;
        LlmResponse response;
        if (callCount - 1 < script.size()) {
            response = script.get(callCount - 1);
            log.info("[ScriptedLlmClient] call #{} → 预录 response (content len={})",
                callCount, response.content() != null ? response.content().length() : 0);
        } else {
            // 脚本耗尽,返 fallback (简单 "ok" 终止)
            response = new LlmResponse("[script exhausted]", null, 10, 5);
            log.warn("[ScriptedLlmClient] call #{} → 脚本耗尽 (script size={}), 返 fallback",
                callCount, script.size());
        }
        consumed.add(response);
        return response;
    }

    public int getCallCount() { return callCount; }
    public List<LlmResponse> getConsumedResponses() { return List.copyOf(consumed); }
    public int getScriptSize() { return script.size(); }
}
