package com.agentbootcamp.tools;

import com.agentbootcamp.Tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 工具 2:读文件 / Tool 2: reads a file
 *
 * Day 1 验收之二:让模型能看代码 / Day 1 acceptance: model can read code
 * Day 3 会加白名单 + 大文件截断 / Day 3 will add path whitelist + truncation
 */
public class ReadFile implements Tool {

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "读取文件内容。返回字符串,失败时返回错误信息。/ Reads a file's content. Returns string, or error message on failure.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "path", Map.of(
                    "type", "string",
                    "description", "文件路径(相对或绝对)/ File path (relative or absolute)"
                )
            ),
            "required", List.of("path")
        );
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String pathStr = (String) args.get("path");
        if (pathStr == null || pathStr.isBlank()) {
            return "错误: 缺少 'path' 参数";
        }
        Path p = Path.of(pathStr);
        if (!Files.exists(p)) {
            return "错误: 文件不存在: " + p.toAbsolutePath();
        }
        if (Files.isDirectory(p)) {
            return "错误: 是目录不是文件: " + p.toAbsolutePath();
        }
        // 简单大小限制 / simple size cap
        long size = Files.size(p);
        if (size > 100_000) {
            return "错误: 文件太大 (" + size + " bytes),请用更具体的文件";
        }
        return Files.readString(p);
    }
}
