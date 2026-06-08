package com.agentbootcamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * MemoryManager — 滑动窗口 + 摘要压缩 / Sliding window + summary compression
 *
 * Day 4 实现 / Day 4 implementation:
 *  - 触发条件 / Triggers: messages.size() > 24 OR totalTokensIn > 10000
 *  - 策略 / Strategy:
 *      保留 (keep): [0] system prompt, [1] first user, last 8 messages
 *      中间 (middle): 发 LLM 总结, 替换成 1 条 system 消息
 *  - 容错 / Fallback: LLM 总结失败 → 直接丢弃中间消息 + 写 warn 日志
 *
 * 关键不变式 / Key invariants:
 *  - 永不压缩 system prompt (里面是工具 schema, 删了 LLM 就不知道有这些工具)
 *  - 永不压缩第一条 user (用户最初目标不能丢)
 *  - 永远 keepLastN >= 4 (保留最近 2 轮 LLM 往返, 让模型能"接着说")
 *
 * 设计权衡 / Trade-offs:
 *  - 简单版: 用 LLM 总结 (有 token 成本, 但语义保真)
 *  - Day 8+ 优化: 用 cheaper 模型总结 / 用规则截断
 *
 * Java 锚定 / Java anchor:
 *  - 不可变返回 (List.copyOf / new ArrayList) — 不修改入参, 防外部状态污染
 *  - try-catch 包 LLM 调 — 让循环不挂 (Day 2 风格)
 */
public class MemoryManager {
    private static final Logger log = LoggerFactory.getLogger(MemoryManager.class);

    /** 消息数阈值 (Q2=C: 宽松策略) / Message count threshold (Q2=C: loose) */
    public static final int MAX_MESSAGES = 24;

    /** Token 累计阈值 (Q2=C: 宽松) / Total tokens-in threshold (Q2=C: loose) */
    public static final int MAX_TOKENS = 10000;

    /** 保留最近 N 条 / Keep most recent N messages */
    public static final int KEEP_LAST_N = 8;

    /**
     * 检查是否应该压缩 / Check if compression is needed.
     *
     * @param msgCount 当前 messages 数量 / current message count
     * @param totalTokensIn 累计 prompt tokens / cumulative prompt tokens
     * @return true = 该压缩
     */
    public boolean shouldCompress(int msgCount, int totalTokensIn) {
        return msgCount > MAX_MESSAGES || totalTokensIn > MAX_TOKENS;
    }

    /**
     * 压缩 messages 列表 / Compress the messages list.
     *
     * @param messages 当前的 messages (不会被修改)
     * @param llm LLM 客户端 (用来调 LLM 做总结)
     * @return 新的 messages list (可能是 List.copyOf 原 list, 如果无需压缩)
     */
    public List<Message> compress(List<Message> messages, LlmClient llm) {
        // 边界 1: 消息太少 (< 3 条), 不压缩
        if (messages.size() <= 2) {
            return List.copyOf(messages);
        }

        // 边界 2: 中间范围为空
        // 保留: [0] system, [1] first user, [size-KEEP_LAST_N .. size-1] 最近 8 条
        int splitEnd = messages.size() - KEEP_LAST_N;
        if (splitEnd <= 2) {
            log.debug("压缩跳过: 中间范围为空 (size={}, keepLast={})",
                messages.size(), KEEP_LAST_N);
            return List.copyOf(messages);
        }

        // 1. 提取要总结的中间消息
        List<Message> middle = messages.subList(2, splitEnd);

        // 2. 拼成文本
        String middleText = formatMessagesForLlm(middle);

        // 3. 调 LLM 总结 (失败 → fallback 到直接丢弃)
        String summary = summarizeWithLlm(middleText, llm);

        // 4. 拼新 list
        List<Message> newMessages = new ArrayList<>(KEEP_LAST_N + 3);
        newMessages.add(messages.get(0));  // system prompt (永不压缩)
        newMessages.add(messages.get(1));  // first user (永不压缩)

        if (summary != null && !summary.isBlank()) {
            newMessages.add(Message.system("[Earlier conversation summary]: " + summary));
        } else {
            log.warn("LLM 总结失败或返回空, fallback: 直接丢弃中间 {} 条消息", middle.size());
        }

        // 追加最近 KEEP_LAST_N 条
        for (int i = splitEnd; i < messages.size(); i++) {
            newMessages.add(messages.get(i));
        }

        log.info("压缩完成: {} → {} (丢 {} + 加 1 summary)",
            messages.size(), newMessages.size(), middle.size());
        return newMessages;
    }

    /**
     * 把 messages 列表拼成可读文本 (给 LLM 看) / Format messages as readable text for the LLM.
     */
    private String formatMessagesForLlm(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            sb.append("[").append(m.role()).append("]: ");
            String content = m.content();
            if (content != null) {
                String c = content.length() > 200
                    ? content.substring(0, 200) + "..."
                    : content;
                sb.append(c);
            }
            List<Message.ToolCall> toolCalls = m.toolCalls();
            if (toolCalls != null && !toolCalls.isEmpty()) {
                sb.append(" [tool calls: ");
                for (Message.ToolCall tc : toolCalls) {
                    sb.append(tc.function().name()).append(", ");
                }
                sb.append("]");
            }
            String toolCallId = m.toolCallId();
            if (toolCallId != null) {
                sb.append(" [tool result id=").append(toolCallId).append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 调 LLM 做总结 / Call LLM to summarize.
     *
     * @return 总结文本, 或 null (失败时)
     */
    private String summarizeWithLlm(String middleText, LlmClient llm) {
        try {
            List<Message> request = List.of(
                Message.system("You are a concise summarizer. Given a conversation excerpt, " +
                    "produce a 1-3 sentence summary focusing on: " +
                    "(1) what the user originally asked, " +
                    "(2) which tools were called and what they returned, " +
                    "(3) any partial conclusions reached. " +
                    "Output ONLY the summary text, no preamble, no markdown."),
                Message.user("Summarize this conversation:\n\n" + middleText)
            );
            LlmClient.LlmResponse resp = llm.chat(request, List.of());
            String content = resp.content();
            if (content == null || content.isBlank()) {
                return null;
            }
            return content.trim();
        } catch (Exception e) {
            log.warn("LLM 总结失败: {}", e.getMessage());
            return null;
        }
    }
}
