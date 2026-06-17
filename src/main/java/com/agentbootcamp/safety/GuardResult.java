package com.agentbootcamp.safety;

/**
 * Day 12: PromptGuard 扫描结果 (普通 class, 不用 record, 避开 Java 17 javac 14 隐式 constructor bug
 * + 静态/实例方法重名冲突).
 */
public final class GuardResult {
    private final boolean clean;
    private final String reason;

    public GuardResult(boolean clean, String reason) {
        this.clean = clean;
        this.reason = reason;
    }

    public static GuardResult clean() { return new GuardResult(true, null); }
    public static GuardResult dirty(String reason) { return new GuardResult(false, reason); }

    public boolean isClean() { return clean; }
    public String getReason() { return reason; }

    @Override public String toString() {
        return "GuardResult{clean=" + clean + ", reason='" + reason + "'}";
    }
}
