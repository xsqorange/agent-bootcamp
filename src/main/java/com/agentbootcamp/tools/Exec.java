package com.agentbootcamp.tools;

import com.agentbootcamp.Tool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 工具 3:执行 shell 命令 / Tool 3: executes a shell command
 *
 * Day 1 验收之三:让模型能跑命令 / Day 1 acceptance: model can run commands
 *
 * ⚠️ 安全警告(Day 1 简化版,无防护)/ SAFETY WARNING (Day 1 simplified, no sandboxing):
 *  - 当前任何 shell 命令都能跑 / any shell command can run
 *  - Day 12 会加白名单 + 超时 + 工作目录限制 / Day 12 adds whitelist + timeout + cwd restriction
 *  - **不要在生产环境用这个工具 / DO NOT use in production as-is**
 */
public class Exec implements Tool {

    /** 5 秒硬超时(Day 1 简单防护)/ 5-second hard timeout (Day 1 minimal guard) */
    private static final long TIMEOUT_SECONDS = 5;

    @Override
    public String name() {
        return "exec";
    }

    @Override
    public String description() {
        return "在 shell 里执行一条命令,返回 stdout(Day 1 无防护,谨慎用)/ Execute a shell command, returns stdout (Day 1: no sandbox, use carefully).";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "command", Map.of(
                    "type", "string",
                    "description", "要执行的 shell 命令(走 sh -c)/ Shell command to run (via sh -c)"
                )
            ),
            "required", List.of("command")
        );
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String cmd = (String) args.get("command");
        if (cmd == null || cmd.isBlank()) {
            return "错误: 缺少 'command' 参数";
        }

        Process p;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            p = new ProcessBuilder("cmd", "/c", cmd).redirectErrorStream(true).start();
        } else {
            p = new ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start();
        }

        // 限时 / time-bounded wait
        if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return "[超时] 命令超过 " + TIMEOUT_SECONDS + " 秒被强制结束";
        }

        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append("\n");
            }
        }
        int exit = p.exitValue();
        if (exit != 0) {
            return "退出码 " + exit + ":\n" + out;
        }
        return out.toString().strip();
    }
}
