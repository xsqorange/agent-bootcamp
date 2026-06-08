package com.agentbootcamp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryManager 单元测试 / MemoryManager unit tests.
 *
 * 测 5 个 case:
 *  1. shouldCompress 边界 (<=24 且 <=10000 → false)
 *  2. shouldCompress 消息数触发 (>24 → true)
 *  3. shouldCompress token 触发 (>10000 → true)
 *  4. compress 产出结构正确 (system + first user + summary + last 8)
 *  5. compress LLM 失败 → fallback (无 summary, 直接丢中间)
 */
class MemoryManagerTest {

    /** FakeLlm — 返回固定 response, 不发真请求 / FakeLlm — returns fixed response, no real call */
    static class FakeLlm extends LlmClient {
        private final String response;
        FakeLlm(String response) {
            super(new LlmConfig("test-key", "https://test", "test-model", 60));
            this.response = response;
        }
        @Override
        public LlmResponse chat(List<Message> msgs, List<Map<String, Object>> tools) {
            return new LlmResponse(response, null, 0, 0);
        }
    }

    /** FailingLlm — chat() 抛异常 / FailingLlm — chat() throws */
    static class FailingLlm extends LlmClient {
        FailingLlm() {
            super(new LlmConfig("test-key", "https://test", "test-model", 60));
        }
        @Override
        public LlmResponse chat(List<Message> msgs, List<Map<String, Object>> tools) throws Exception {
            throw new RuntimeException("test failure");
        }
    }

    /** BlankLlm — chat() 返回空 / BlankLlm — chat() returns blank */
    static class BlankLlm extends LlmClient {
        BlankLlm() {
            super(new LlmConfig("test-key", "https://test", "test-model", 60));
        }
        @Override
        public LlmResponse chat(List<Message> msgs, List<Map<String, Object>> tools) {
            return new LlmResponse("", null, 0, 0);
        }
    }

    @Test
    void testShouldNotCompressWhenBelowThreshold() {
        MemoryManager m = new MemoryManager();
        // 边界: ≤ 24 messages 且 ≤ 10000 tokens → 不压缩
        assertFalse(m.shouldCompress(0, 0));
        assertFalse(m.shouldCompress(2, 100));
        assertFalse(m.shouldCompress(10, 5000));
        assertFalse(m.shouldCompress(24, 10000));  // 边界值, NOT 触发
    }

    @Test
    void testShouldCompressWhenManyMessages() {
        MemoryManager m = new MemoryManager();
        // 25 messages → 触发
        assertTrue(m.shouldCompress(25, 100));
        assertTrue(m.shouldCompress(100, 200));
    }

    @Test
    void testShouldCompressWhenManyTokens() {
        MemoryManager m = new MemoryManager();
        // > 10000 tokens → 触发 (即使消息数少)
        assertTrue(m.shouldCompress(3, 10001));
        assertTrue(m.shouldCompress(20, 50000));
    }

    @Test
    void testCompressStructure() {
        // 20 messages: [system, first user, ass2..ass19]
        List<Message> messages = buildLongMessages(20);
        MemoryManager m = new MemoryManager();
        FakeLlm fake = new FakeLlm("User asked X. Tool Y returned Z.");

        List<Message> compressed = m.compress(messages, fake);

        // 期望: 2 (preserved) + 1 (summary) + 8 (last 8) = 11
        assertEquals(11, compressed.size(), "压缩后应该是 11 条");

        // [0] system 保留
        assertEquals(messages.get(0), compressed.get(0));

        // [1] first user 保留
        assertEquals(messages.get(1), compressed.get(1));

        // [2] summary system message
        assertEquals("system", compressed.get(2).role());
        String summary = compressed.get(2).content();
        assertTrue(summary.startsWith("[Earlier conversation summary]"),
            "summary 应该有 [Earlier conversation summary] 前缀, 实际: " + summary);
        assertTrue(summary.contains("User asked X"), "summary 应包含 LLM 返回的内容");

        // [3..10] 是 messages[12..19] (最后 8 条)
        for (int i = 0; i < 8; i++) {
            assertEquals(messages.get(12 + i), compressed.get(3 + i),
                "最近 8 条应原样保留 (i=" + i + ")");
        }
    }

    @Test
    void testCompressFallbackOnLlmError() {
        List<Message> messages = buildLongMessages(20);
        MemoryManager m = new MemoryManager();
        FailingLlm failing = new FailingLlm();

        List<Message> compressed = m.compress(messages, failing);

        // LLM 抛异常 → fallback: 没有 summary, 直接丢中间
        assertEquals(10, compressed.size(), "Fallback 时应该是 10 条 (system + first user + 8 recent)");

        assertEquals(messages.get(0), compressed.get(0));
        assertEquals(messages.get(1), compressed.get(1));

        // 后续 8 条是 messages[12..19]
        for (int i = 0; i < 8; i++) {
            assertEquals(messages.get(12 + i), compressed.get(2 + i));
        }
    }

    @Test
    void testCompressFallbackOnBlankLlmResponse() {
        // LLM 返回空字符串 → 也应该 fallback (跟抛异常同样处理)
        List<Message> messages = buildLongMessages(20);
        MemoryManager m = new MemoryManager();
        BlankLlm blank = new BlankLlm();

        List<Message> compressed = m.compress(messages, blank);

        assertEquals(10, compressed.size(), "Blank response 也 fallback, 应是 10 条");
        // 没 summary
        for (Message msg : compressed) {
            String content = msg.content();
            if (content != null) {
                assertFalse(content.startsWith("[Earlier conversation summary]"),
                    "Blank LLM 不应插入 summary");
            }
        }
    }

    @Test
    void testCompressNoOpWhenTooFew() {
        // 消息数 <= 2 → 不压缩
        List<Message> messages = List.of(
            Message.system("sys"),
            Message.user("u1")
        );
        MemoryManager m = new MemoryManager();
        FakeLlm fake = new FakeLlm("ignored");

        List<Message> compressed = m.compress(messages, fake);

        assertEquals(2, compressed.size());
        assertEquals(messages.get(0), compressed.get(0));
        assertEquals(messages.get(1), compressed.get(1));
    }

    /** 构造 N 条消息: [system, first user, ass2..assN] */
    private List<Message> buildLongMessages(int n) {
        List<Message> list = new ArrayList<>();
        list.add(Message.system("System prompt " + n));
        list.add(Message.user("User's first goal"));
        for (int i = 2; i < n; i++) {
            list.add(Message.assistant("Assistant message " + i));
        }
        return list;
    }
}
