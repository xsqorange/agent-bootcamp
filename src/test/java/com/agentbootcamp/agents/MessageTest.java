package com.agentbootcamp.agents;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Day 8: Message record + sealed 穷尽 + correlationId 唯一性
 *
 * 5 单元测试:
 *  TC-14: Task 自动生成 correlationId
 *  TC-15: Task 显式 correlationId 不被覆盖
 *  TC-16: Result 字段正确
 *  TC-17: sealed 编译期穷尽 (switch expression 强制覆盖 3 个 case)
 *  TC-18: correlationId 在 N 个 task 间唯一
 */
class MessageTest {

    @Test
    void test14_TaskAutoGeneratesCorrelationId() {
        Message.Task task = new Message.Task("goal-x", Map.of());
        assertNotNull(task.correlationId(), "TC-14: Task 应自动生成 correlationId");
        assertEquals(36, task.correlationId().length(), "TC-14: UUID 长度 36");
        assertEquals("goal-x", task.goal());
        assertEquals(Map.of(), task.args());
    }

    @Test
    void test15_TaskExplicitCorrelationIdIsPreserved() {
        String fixed = "fixed-id-123";
        Message.Task task = new Message.Task(fixed, "goal-y", Map.of("k", "v"));
        assertEquals(fixed, task.correlationId(), "TC-15: 显式 correlationId 不被覆盖");
        assertEquals("goal-y", task.goal());
        assertEquals(Map.of("k", "v"), task.args());
    }

    @Test
    void test16_ResultFieldsArePreserved() {
        Message.Result r = new Message.Result("cid-1", "answer", 7, 0.0123);
        assertEquals("cid-1", r.correlationId());
        assertEquals("answer", r.finalAnswer());
        assertEquals(7, r.totalSteps());
        assertEquals(0.0123, r.totalCostUsd(), 1e-9);
    }

    @Test
    void test17_SealedInterfaceExhaustiveInstanceof() {
        // sealed 编译期穷尽 — instanceof 链覆盖所有 3 个子类
        // (Java 17 的 switch pattern matching 仍是 preview,这里用 instanceof)
        Message msg1 = new Message.Task("a", Map.of());
        Message msg2 = new Message.Result("b", "ans", 1, 0.001);
        Message msg3 = new Message.Cancel("c", "timeout");

        for (Message m : new Message[]{msg1, msg2, msg3}) {
            String label;
            if (m instanceof Message.Task t) {
                label = "Task(" + t.correlationId() + ")";
            } else if (m instanceof Message.Result r) {
                label = "Result(" + r.correlationId() + ")";
            } else if (m instanceof Message.Cancel c) {
                label = "Cancel(" + c.correlationId() + "," + c.reason() + ")";
            } else {
                throw new AssertionError("TC-17: sealed Message 出现未知子类: " + m.getClass());
            }
            assertTrue(label.startsWith("Task(") || label.startsWith("Result(") || label.startsWith("Cancel("),
                "TC-17: label 应是 Task/Result/Cancel 之一: " + label);
        }
    }

    @Test
    void test18_CorrelationIdUniqueAcrossNTasks() {
        int N = 1000;
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < N; i++) {
            ids.add(new Message.Task("goal-" + i, Map.of()).correlationId());
        }
        assertEquals(N, ids.size(), "TC-18: " + N + " 个 Task 应有 " + N + " 个 unique correlationId");
        // 验证是 UUID 格式 (8-4-4-4-12)
        String sample = ids.iterator().next();
        assertTrue(sample.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
            "TC-18: correlationId 应是 UUID 格式: " + sample);
    }
}
