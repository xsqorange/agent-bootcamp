package com.agentbootcamp;

import java.util.Map;

/**
 * 工具 — Agent 可以调用的"动作" / A tool the agent can invoke.
 *
 * 设计原则 / Design principles:
 * - 名字短且 snake_case(模型容易生成)/ short snake_case name (model-friendly)
 * - description 要说清楚"什么时候用"/ description says WHEN to use it
 * - JSON Schema 严格(模型按它生成参数)/ strict JSON Schema
 * - execute 要安全(Day 12 加防护)/ execute is sandboxed (Day 12 adds guards)
 */
public interface Tool {
    /** 工具名,例如 "get_current_time" / tool name, e.g. "get_current_time" */
    String name();

    /** 工具描述 — 模型读这段决定何时调 / description — the model reads this to decide when to call */
    String description();

    /** JSON Schema (OpenAI tools[].parameters) / JSON Schema describing args */
    Map<String, Object> jsonSchema();

    /** 执行工具,返回字符串结果 / execute, return string result */
    String execute(Map<String, Object> args) throws Exception;
}
