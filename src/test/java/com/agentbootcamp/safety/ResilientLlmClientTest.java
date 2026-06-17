package com.agentbootcamp.safety;

import com.agentbootcamp.LlmClient;
import com.agentbootcamp.LlmConfig;
import com.agentbootcamp.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Day 12: ResilientLlmClient 单元测试.
 * 用手写 fake LlmClient 模拟 4 类行为 (正常 / 5xx / IOException / sleep 超时)
 * (避免引入 Mockito 依赖).
 *
 * 4 cases:
 *  1. delegate.chat() 正常 → 不触发 resilience
 *  2. delegate.chat() 连续失败 → CircuitBreaker open
 *  3. delegate.chat() sleep 12s → TimeLimiter 10s 超时
 *  4. delegate.chat() 1 次 IOException + 第 2 次成功 → Retry 救回
 */
class ResilientLlmClientTest {

    // 简单 fake LlmClient (不调真 LLM),通过 constructor 不实际建 HttpClient
    static class FakeLlmClient extends LlmClient {
        final AtomicInteger callCount = new AtomicInteger(0);
        // 控制行为: 返回 content (成功) 或抛 exception (失败) 或 sleep (超时)
        Runnable behavior;

        FakeLlmClient(Runnable behavior) {
            super(new LlmConfig("fake-key", "http://fake", "fake-model", 10));
            this.behavior = behavior;
        }

        @Override
        public LlmResponse chat(List<Message> messages, List<Map<String, Object>> tools) throws Exception {
            callCount.incrementAndGet();
            behavior.run();
            return new LlmResponse("ok response", null, 100, 50);
        }
    }

    @Test
    void chat_succeeds_passesThrough() throws Exception {
        // delegate.chat() 正常: 不触发 resilience, 返 1 次结果
        FakeLlmClient fake = new FakeLlmClient(() -> {});  // no-op
        ResilientLlmClient resilient = new ResilientLlmClient(fake);
        try {
            LlmClient.LlmResponse resp = resilient.chat(List.of(), null);
            assertNotNull(resp);
            assertEquals(1, fake.callCount.get(), "delegate 应只调 1 次 (无 retry)");
            assertEquals("ok response", resp.content());
        } finally {
            resilient.close();
        }
    }

    @Test
    void chat_retryOnIOException_recoversOnSecondAttempt() throws Exception {
        // 第 1 次 IOException, 第 2 次成功: Retry 救回
        AtomicInteger attempt = new AtomicInteger(0);
        FakeLlmClient fake = new FakeLlmClient(() -> {
            if (attempt.incrementAndGet() == 1) {
                throw new RuntimeException(new java.net.ConnectException("Connection refused"));
            }
            // 第 2 次: no-op (成功)
        });
        ResilientLlmClient resilient = new ResilientLlmClient(fake);
        try {
            LlmClient.LlmResponse resp = resilient.chat(List.of(), null);
            assertNotNull(resp);
            assertEquals(2, fake.callCount.get(), "delegate 应调 2 次 (1 fail + 1 success)");
        } finally {
            resilient.close();
        }
    }

    @Test
    void chat_circuitBreakerOpens_afterRepeatedFailures() throws Exception {
        // 连续 IOException 失败: CircuitBreaker sliding window 10 calls, 50% 失败率触发 open
        FakeLlmClient fake = new FakeLlmClient(() -> {
            throw new RuntimeException(new java.net.ConnectException("Connection refused"));
        });
        ResilientLlmClient resilient = new ResilientLlmClient(fake);
        try {
            // 连续调 10 次, 每次内部 retry 3 次, 实际 delegate 被调 30 次, 全部失败
            for (int i = 0; i < 10; i++) {
                try { resilient.chat(List.of(), null); } catch (Exception e) { /* expected */ }
            }
            // CircuitBreaker 应 open, 后续调用立即抛 CallNotPermittedException (无调 delegate)
            int callsBefore = fake.callCount.get();
            assertThrows(Exception.class, () -> resilient.chat(List.of(), null));
            // 调 delegate 次数应该不增加 (circuit open, 立即拒绝)
            assertTrue(fake.callCount.get() <= callsBefore + 1, "Circuit open 后应少调 delegate");
        } finally {
            resilient.close();
        }
    }

    @Test
    void chat_timeLimiter_cancelsLongRunningCall() throws Exception {
        // delegate.sleep 12s, TimeLimiter 10s 应触发
        FakeLlmClient fake = new FakeLlmClient(() -> {
            try { Thread.sleep(12000); } catch (InterruptedException e) { throw new RuntimeException(e); }
        });
        ResilientLlmClient resilient = new ResilientLlmClient(fake);
        try {
            long startMs = System.currentTimeMillis();
            Exception ex = assertThrows(Exception.class, () -> resilient.chat(List.of(), null));
            long elapsed = System.currentTimeMillis() - startMs;
            // TimeLimiter 10s + 一点点重试延迟, 应在 10-15s 之间完成
            assertTrue(elapsed < 15000, "应在 15s 内超时 (实际: " + elapsed + "ms)");
            assertTrue(ex.getMessage() != null && ex.getMessage().contains("timed out"),
                "异常应含 'timed out': " + ex.getMessage());
        } finally {
            resilient.close();
        }
    }
}
