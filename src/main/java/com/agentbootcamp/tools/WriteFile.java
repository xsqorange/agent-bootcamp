package com.agentbootcamp.tools;

import com.agentbootcamp.Tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/**
 * 工具 4:写文件 / Tool 4: writes a file
 *
 * Day 3 验收 / Day 3 acceptance:
 *  - Agent 能改文件(从"只看"升级到"能改")
 *  - 默认覆盖(Day 12 会加"敏感路径 confirm"模式)
 *  - 1MB 限制(防 LLM 一次性把 context 写爆)
 *  - 父目录不存在会自动创建
 *  - 失败返回字符串,不抛异常(Day 2 风格:循环不挂)
 *
 * 防护 / Safety:
 *  - 必须有 path + content 参数
 *  - 路径必须在 working dir 之内(防越界,Day 12 还会再加固)
 *  - 文件大小 1MB 上限
 *  - 失败 → 返回 "ERROR: ..." 字符串,而不是抛异常
 *
 * Java 锚定 / Java anchor:
 *  - Files.writeString(..., CREATE, TRUNCATE_EXISTING, WRITE) = "覆盖"语义
 *  - Path.normalize() 防 ../ 越界
 *  - Files.createDirectories(parent) = mkdir -p
 */
public class WriteFile implements Tool {

    private static final long MAX_BYTES = 1_048_576; // 1MB
    private final Path workDir;

    public WriteFile() {
        this(Path.of(".").toAbsolutePath().normalize());
    }

    public WriteFile(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "写入文件(覆盖现有内容)。父目录不存在会自动创建。/ Write to a file (overwrites). Creates parent dirs if needed.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "path", Map.of(
                    "type", "string",
                    "description", "目标文件路径(相对 working dir 或绝对)/ Target file path (relative to working dir or absolute)"
                ),
                "content", Map.of(
                    "type", "string",
                    "description", "要写入的完整内容 / Full content to write"
                )
            ),
            "required", List.of("path", "content")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String pathStr = (String) args.get("path");
        Object contentObj = args.get("content");

        // 1. 参数校验
        if (pathStr == null || pathStr.isBlank()) {
            return "错误: 缺少 'path' 参数";
        }
        if (contentObj == null) {
            return "错误: 缺少 'content' 参数";
        }
        String content = contentObj.toString();
        byte[] contentBytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        if (contentBytes.length > MAX_BYTES) {
            return "错误: 内容太大 (" + contentBytes.length + " bytes),超过 1MB 上限。请分批写。";
        }

        // 2. 路径解析 + 安全检查
        // 关键:相对路径要 resolve 到 workDir,而不是 user.dir
        // Key: relative paths resolve against workDir, NOT user.dir
        Path target;
        try {
            Path raw = Paths.get(pathStr);
            target = raw.isAbsolute()
                ? raw.normalize()
                : workDir.resolve(raw).normalize();
        } catch (Exception e) {
            return "错误: 无效路径 '" + pathStr + "': " + e.getMessage();
        }
        if (!target.startsWith(workDir)) {
            return "错误: 路径 '" + pathStr + "' 越出 working dir (" + workDir + ")";
        }

        // 3. 确保父目录存在
        Path parent = target.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (Exception e) {
                return "错误: 无法创建父目录 " + parent + ": " + e.getMessage();
            }
        }

        // 4. 写入(覆盖)
        try {
            Files.writeString(target, content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            return "wrote " + contentBytes.length + " bytes to " + pathStr;
        } catch (Exception e) {
            return "错误: 写入失败 " + pathStr + ": " + e.getMessage();
        }
    }
}
