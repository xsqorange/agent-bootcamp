package com.agentbootcamp.tools;

import com.agentbootcamp.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Day 10: EditFile — 改文件指定位置的工具 (类似 sed -i 但更安全)
 *
 * 中文:基于 "old_string + new_string" 的精确替换。Agent 给出 old_string,工具找到
 *      第一次出现的位置替换为 new_string。如果 old_string 不存在,抛错。
 *      如果出现多次,默认 fail (避免意外替换),可加 replace_all 显式允许。
 *
 * English: sed-i-like edit tool, safer because:
 *          - explicit old_string + new_string (LLM 必须提供具体内容)
 *          - path 白名单 (workDir 越界防护)
 *          - 1MB 单文件上限
 *          - 出现 0 次 fail,出现 >1 次 fail (除非 replace_all=true)
 *
 * 设计要点 / Design points:
 *   - 跟 WriteFile 用同样的 workDir 防护 (Day 3 pitfall #6 路径解析)
 *   - new String[](0) 替换是 "删 old_string" 的写法
 *   - 如果 old_string 跟 new_string 完全相同,工具会拒绝 (无意义操作)
 */
public class EditFile implements Tool {

    private static final long MAX_FILE_SIZE = 1_000_000L;  // 1MB
    private final Path workDir;
    private final ObjectMapper json = new ObjectMapper();

    public EditFile(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() { return "edit_file"; }

    @Override
    public String description() {
        return "Edit a file by replacing old_string with new_string (like sed -i). "
            + "Args: path (str), old_string (str), new_string (str), replace_all (bool, default false). "
            + "Fails if old_string not found, or if found >1 times (unless replace_all=true).";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string", "description", "文件路径(相对 workDir 或绝对)"));
        props.put("old_string", Map.of("type", "string", "description", "要替换的原文本"));
        props.put("new_string", Map.of("type", "string", "description", "新文本"));
        props.put("replace_all", Map.of("type", "boolean", "description", "允许多次替换 (默认 false 严格 1 次)",
            "default", false));
        schema.put("properties", props);
        schema.put("required", List.of("path", "old_string", "new_string"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String pathStr = (String) args.get("path");
        String oldString = (String) args.get("old_string");
        String newString = (String) args.get("new_string");
        boolean replaceAll = Boolean.TRUE.equals(args.get("replace_all"));

        // 验证
        if (pathStr == null || oldString == null || newString == null) {
            throw new IllegalArgumentException(
                "edit_file 需要 path / old_string / new_string,实际: " + args);
        }
        if (oldString.equals(newString)) {
            throw new IllegalArgumentException(
                "edit_file: old_string 和 new_string 完全相同,无操作");
        }
        if (oldString.isEmpty()) {
            throw new IllegalArgumentException(
                "edit_file: old_string 不能为空");
        }

        // 路径解析(跟 WriteFile 同款,workDir 防护)
        Path raw = Paths.get(pathStr);
        Path target = raw.isAbsolute() ? raw : workDir.resolve(raw).normalize();
        if (!target.startsWith(workDir)) {
            throw new SecurityException(
                "edit_file: path 越出 workDir 防护 (workDir=" + workDir + ", path=" + target + ")");
        }
        if (!Files.exists(target)) {
            throw new NoSuchFileException(
                "edit_file: 文件不存在: " + target);
        }
        long size = Files.size(target);
        if (size > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                "edit_file: 文件过大(" + size + " bytes),上限 " + MAX_FILE_SIZE);
        }

        // 读 + 替换 + 写
        String content = Files.readString(target, StandardCharsets.UTF_8);
        int occurrences = countOccurrences(content, oldString);
        if (occurrences == 0) {
            throw new IllegalArgumentException(
                "edit_file: old_string 在文件中找不到");
        }
        if (occurrences > 1 && !replaceAll) {
            throw new IllegalArgumentException(
                "edit_file: old_string 出现 " + occurrences + " 次,需要 replace_all=true 或调整 old_string 更精确");
        }

        String newContent = replaceAll
            ? content.replace(oldString, newString)
            : content.replaceFirst(java.util.regex.Pattern.quote(oldString), java.util.regex.Matcher.quoteReplacement(newString));
        Files.writeString(target, newContent, StandardCharsets.UTF_8);
        return "edited " + target + " (" + occurrences + " occurrence" + (replaceAll ? "s, all replaced" : " replaced") + ")";
    }

    /** 数 old_string 在 content 里出现几次 (不重叠). */
    private static int countOccurrences(String content, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = content.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
