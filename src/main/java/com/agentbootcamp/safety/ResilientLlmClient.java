package com.agentbootcamp.safety;

import com.agentbootcamp.LlmClient;
import com.agentbootcamp.LlmConfig;
import com.agentbootcamp.Message;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Day 12: Resilience4j 装饰 LlmClient (CircuitBreaker + Retry + TimeLimiter).
 *
 * 中文:不修改 LlmClient 本身, 用装饰模式包装 chat() 调用:
 *      - TimeLimiter 10s 超时 (防 LLM 挂死/网络阻塞)
 *      - CircuitBreaker 5xx/IOException 熔断 (5/10 失败 → open 30s → half-open 3 calls)
 *      - Retry 指数退避 1s/2s/4s (仅对 transient 错误重试 3 次)
 *      链顺序: TimeLimiter → CircuitBreaker → Retry → delegate.chat
 * English: Decorate LlmClient.chat() with Resilience4j decorators (each module's own decorateXxx API).
 *      Chain order: TimeLimiter → CircuitBreaker → Retry → delegate.chat.
 *
 * 用法 / Usage:
 *   LlmClient raw = new LlmClient(config);
 *   LlmClient resilient = new ResilientLlmClient(raw);  // 实际返回 LlmClient (向上转型)
 *   LlmResponse resp = resilient.chat(messages, tools);  // 自动 timeout + circuit breaker + retry
 */
public class ResilientLlmClient extends LlmClient {
    private static final Logger log = LoggerFactory.getLogger(ResilientLlmClient.class);

    private final LlmClient delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter timeLimiter;
    private final ScheduledExecutorService scheduler;

    public ResilientLlmClient(LlmClient delegate) {
        // 调 LlmClient 单参构造, 复用其 config (但实际用 delegate 不 super.chat)
        super(extractConfig(delegate));
        this.delegate = delegate;
        // CircuitBreaker: 50% 失败率 / 滑动窗口 10 calls / open 30s / half-open 3 calls
        this.circuitBreaker = CircuitBreaker.of("llmClient",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(Exception.class)  // 记录所有 Exception(包括 5xx + IOException)
                .build());
        // Retry: 3 attempts, 指数退避 1s/2s/4s
        this.retry = Retry.of("llmClient",
            RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(1000, 2.0))
                .retryOnException(t -> isTransient(t))
                .build());
        // TimeLimiter: 10s 单次调用超时
        this.timeLimiter = TimeLimiter.of("llmClient",
            TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .build());
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    /**
     * 从 LlmClient 实例拿 config (用 reflection 访问 private 字段).
     * 失败 fallback 到 default LlmConfig.
     */
    private static LlmConfig extractConfig(LlmClient llm) {
        try {
            java.lang.reflect.Field f = LlmClient.class.getDeclaredField("config");
            f.setAccessible(true);
            return (LlmConfig) f.get(llm);
        } catch (Exception e) {
            return new LlmConfig("dummy", "http://localhost", "dummy", 10);
        }
    }

    /**
     * 调用 LLM,带 Resilience4j 装饰.
     * 链顺序: TimeLimiter → CircuitBreaker → Retry → delegate.chat
     */
    @Override
    public LlmResponse chat(List<Message> messages, List<Map<String, Object>> tools) throws Exception {
        // 1. 构造 wrapped supplier (delegate.chat 把 checked exception 包装)
        Supplier<LlmClient.LlmResponse> wrappedChat = () -> {
            try {
                return delegate.chat(messages, tools);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        // 2. Retry → CircuitBreaker 链
        Supplier<LlmClient.LlmResponse> retriedAndBroken =
            Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, wrappedChat));
        // 3. TimeLimiter 用 Future 包裹, 强制 10s 超时
        Callable<LlmClient.LlmResponse> callable = () -> retriedAndBroken.get();
        java.util.concurrent.Future<LlmClient.LlmResponse> future = scheduler.submit(callable);
        try {
            return timeLimiter.executeFutureSupplier(() -> future);
        } catch (Exception e) {
            if (e instanceof TimeoutException) {
                future.cancel(true);
                log.warn("LLM call 超时 10s (TimeLimiter 触发)");
                throw new RuntimeException("LLM call timed out after 10s", e);
            }
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(e);
        }
    }

    // 测试 / 监控用
    public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    public Retry getRetry() { return retry; }
    public TimeLimiter getTimeLimiter() { return timeLimiter; }
    public LlmClient getDelegate() { return delegate; }

    /**
     * 判断异常是否是 transient (可重试): IOException / 5xx / 429 (递归检查 cause 链).
     */
    static boolean isTransient(Throwable t) {
        Throwable cur = t;
        for (int i = 0; i < 5 && cur != null; i++) {
            if (cur instanceof java.io.IOException) return true;  // ConnectException / SocketException 等
            String msg = cur.getMessage();
            if (msg != null) {
                if (msg.contains("429") || msg.contains("5")) return true;  // 5xx 状态
            }
            cur = cur.getCause();
        }
        return false;
    }

    public void close() {
        scheduler.shutdown();
    }
}
