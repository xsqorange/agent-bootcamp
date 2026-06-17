package com.agentbootcamp.safety;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Day 12: Prompt injection 防护 (5 known attacks).
 *
 * 中文:工具返回内容会被原样塞回 LLM messages, 攻击者可利用此通道注入
 *      "ignore previous instructions" / "<system>..." / 编码绕过 等恶意指令。
 *      PromptGuard 在工具输出塞回 LLM 之前扫一遍, 发现 5 种已知 attack 就告警。
 *      同时用 <user_data> 包裹工具输出, 防止 LLM 把它当 system prompt。
 *
 * English: Tool outputs are fed back to the LLM as user messages, opening a prompt
 *      injection channel. PromptGuard scans tool output for 5 known attack patterns
 *      and wraps output in <user_data> tags to prevent LLM confusion.
 *
 * 5 known attack patterns / 5 已知攻击模式:
 *   1. system override   - "ignore previous instructions" / 中文 "忽略前述指令"
 *   2. role override      - "you are now a ..."
 *   3. system tag         - <system>...</system> 试图覆盖 system prompt
 *   4. shell injection    - "rm -rf" / "curl http://evil.com" / "wget"
 *   5. encoding bypass    - base64 50+ 字符 / unicode 转义序列 (\u005CuXXXX)
 *
 * 用法 / Usage:
 *   GuardResult result = PromptGuard.scan("read_file", toolOutput);
 *   if (!result.clean()) log.warn("Prompt injection detected: {}", result.reason());
 *   String safeOutput = PromptGuard.wrap("read_file", toolOutput);
 *   // 把 safeOutput 塞回 LLM messages 列表
 */
public class PromptGuard {
    private static final Logger log = LoggerFactory.getLogger(PromptGuard.class);

    private record AttackPattern(String name, Pattern regex) {}

    /** 5+ 类已知 attack pattern / 5+ known attack patterns */
    private static final List<AttackPattern> PATTERNS = List.of(
        // 1. system override (英文)
        new AttackPattern("system-override-en",
            Pattern.compile("(?i)(ignore|disregard|forget|skip|bypass)\\s+(previous|all|above|prior)\\s+(instructions?|prompts?|rules?|context)")),
        // 1b. system override (中文 + 多语言)
        new AttackPattern("system-override-zh",
            Pattern.compile("(忽略|无视|丢弃|跳过|绕过|覆写|忽略前述|忽略以上).{0,20}(指令|命令|说明|规则|上下文|提示)")),
        // 2. role override
        new AttackPattern("role-override",
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an)\\s+[a-z]+")),
        // 3. system tag injection (开闭 tag 都匹配: <system>...</system>)
        new AttackPattern("system-tag",
            Pattern.compile("</?\\s*(system|assistant|tool|function)\\s*>")),
        // 4. shell injection
        new AttackPattern("shell-injection",
            Pattern.compile("(?i)(rm\\s+-rf|curl\\s+https?://|wget\\s+https?://|bash\\s+-c|sh\\s+-c|powershell\\s+-)")),
        // 5a. base64 长字符串 (50+ 字符)
        new AttackPattern("base64-bypass",
            Pattern.compile("[A-Za-z0-9+/]{50,}={0,2}")),
        // 5b. unicode escape
        new AttackPattern("unicode-escape",
            Pattern.compile("\\\\u[0-9a-fA-F]{4}"))
    );

    /**
     * 扫描工具输出是否含 prompt injection.
     * @param toolName 工具名 (用于日志)
     * @param toolOutput 工具返回内容
     * @return GuardResult 包含是否 clean + 第一个匹配的攻击 pattern 名
     */
    public static GuardResult scan(String toolName, String toolOutput) {
        if (toolOutput == null || toolOutput.isEmpty()) {
            return GuardResult.clean();
        }
        for (AttackPattern ap : PATTERNS) {
            if (ap.regex.matcher(toolOutput).find()) {
                log.warn("[PromptGuard] Tool '{}' output 命中 attack pattern '{}' (输出含可疑指令/编码/标签,LLM 可能被劫持)",
                    toolName, ap.name);
                return GuardResult.dirty(ap.name);
            }
        }
        return GuardResult.clean();
    }

    /**
     * 包裹工具输出,防止 LLM 误读为 system prompt.
     * 包裹后:Llama 看到 "<user_data tool="read_file">...</user_data>" 明确知道是 user data.
     */
    public static String wrap(String toolName, String output) {
        if (output == null) return "";
        return "<user_data tool=\"" + toolName + "\">\n" + output + "\n</user_data>";
    }

    // GuardResult 移到外面做 top-level record (Java 17 nested record 访问限制,作外层 record 更稳)

    // 内部 test 用:列出所有 attack patterns
    static List<String> patternNames() {
        List<String> names = new ArrayList<>();
        for (AttackPattern ap : PATTERNS) names.add(ap.name);
        return names;
    }
}
