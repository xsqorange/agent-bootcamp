package com.agentbootcamp.metrics;

/**
 * Day 11: 按 model 算 LLM 成本 (USD).
 *
 * 中文:不同 LLM 的价格不同,按 1M token 单价算每调用的 USD cost。
 *      维护一个简易价格表,后续加新 model 改这里即可。
 * English: Compute USD cost per LLM call based on model-specific token pricing.
 *
 * 已知价格 / Known pricing (per 1M tokens, 2026-06):
 * - gpt-4o-mini: input $0.15, output $0.60
 * - gpt-4o: input $2.50, output $10.00
 * - deepseek-chat: input $0.14, output $0.28
 * - qwen-max: input $2.00, output $6.00
 * - minimax-m3: input $0.10, output $0.20
 * - ollama/*: free (本地)
 */
public class CostCalculator {

    /** Per-model 定价 (USD per 1M tokens). inputPrice/outputPrice */
    private record Pricing(double inputPer1M, double outputPer1M) {}

    private static final java.util.Map<String, Pricing> PRICE_TABLE = new java.util.HashMap<>();
    static {
        PRICE_TABLE.put("gpt-4o-mini", new Pricing(0.15, 0.60));
        PRICE_TABLE.put("gpt-4o", new Pricing(2.50, 10.00));
        PRICE_TABLE.put("gpt-4-turbo", new Pricing(10.00, 30.00));
        PRICE_TABLE.put("gpt-3.5-turbo", new Pricing(0.50, 1.50));
        PRICE_TABLE.put("deepseek-chat", new Pricing(0.14, 0.28));
        PRICE_TABLE.put("qwen-max", new Pricing(2.00, 6.00));
        PRICE_TABLE.put("qwen-plus", new Pricing(0.80, 2.00));
        PRICE_TABLE.put("minimax-m3", new Pricing(0.10, 0.20));
        // Ollama 本地模型:免费
        PRICE_TABLE.put("ollama", new Pricing(0.0, 0.0));
    }

    /**
     * 算 1 次 LLM 调用的 USD 成本
     * @param model 模型名
     * @param tokensIn 输入 token
     * @param tokensOut 输出 token
     * @return USD 成本 (0.0 if model unknown or local)
     */
    public static double compute(String model, long tokensIn, long tokensOut) {
        if (model == null || model.isEmpty()) return 0.0;
        // 简化匹配:小写前缀
        String key = model.toLowerCase();
        for (var entry : PRICE_TABLE.entrySet()) {
            if (key.startsWith(entry.getKey())) {
                Pricing p = entry.getValue();
                return (tokensIn * p.inputPer1M + tokensOut * p.outputPer1M) / 1_000_000.0;
            }
        }
        // 未知 model:返回 0 (避免 silently 算错)
        return 0.0;
    }

    /**
     * 是否有该 model 的定价 (false = 免费/未知,应单独标记)
     */
    public static boolean hasKnownPricing(String model) {
        if (model == null) return false;
        String key = model.toLowerCase();
        return PRICE_TABLE.keySet().stream().anyMatch(key::startsWith);
    }
}
