package com.agentbootcamp.tools;

import com.agentbootcamp.Tool;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 工具 5:Grep(用 Java 正则搜文件)/ Tool 5: search files with Java regex
 *
 * Day 3 验收 / Day 3 acceptance:
 *  - 纯 Java 实现,跨平台(不依赖系统 grep / ripgrep)
 *  - 用 java.util.regex,模型熟悉的"标准正则"
 *  - 1MB 单文件上限(防 LLM context 爆)
 *  - 限 max_results(默认 20)
 *  - 可选 context_lines(默认 0,显示匹配行上下文)
 *
 * 输出格式 / Output format:
 *  --- N matches in path/to/file ---
 *  path/to/file:42:>the matched line here
 *  path/to/file:43: context line
 *  --- M matches in another/file ---
 *  ...
 *
 * 防护 / Safety:
 *  - 路径必须在 working dir 之内
 *  - 单文件 > 1MB 跳过(避免读大文件卡死)
 *  - max_results 上限(避免 context 爆)
 *
 * Java 锚定 / Java anchor:
 *  - Pattern + Matcher = 标准正则
 *  - Files.walkFileTree = 递归遍历(可控、可中断)
 *  - Files.readAllLines = 一次性读(限 1MB,简单粗暴,够 Day 3 用)
 */
public class Grep implements Tool {

    private static final long MAX_FILE_BYTES = 1_048_576;  // 1MB
    private static final int DEFAULT_MAX_RESULTS = 20;
    private static final int MAX_DEPTH = 5;
    private final Path workDir;

    public Grep() {
        this(Path.of(".").toAbsolutePath().normalize());
    }

    public Grep(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String description() {
        return "用正则搜索文件内容(纯 Java 实现)/ Search file contents with a regex (pure Java). " +
               "支持目录递归(限 5 层)/ Supports recursive directory search (depth limit 5).";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "path", Map.of(
                    "type", "string",
                    "description", "搜索路径(文件或目录,递归)/ Path to search (file or dir, recursive)"
                ),
                "pattern", Map.of(
                    "type", "string",
                    "description", "Java 正则表达式 / Java regex pattern"
                ),
                "max_results", Map.of(
                    "type", "integer",
                    "description", "最多返回多少匹配行(默认 20)/ Max matches to return (default 20)"
                ),
                "context_lines", Map.of(
                    "type", "integer",
                    "description", "匹配行前后各显示几行(默认 0)/ Context lines before/after each match (default 0)"
                )
            ),
            "required", List.of("path", "pattern")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String pathStr = (String) args.get("path");
        String patternStr = (String) args.get("pattern");
        int maxResults = args.containsKey("max_results")
            ? ((Number) args.get("max_results")).intValue()
            : DEFAULT_MAX_RESULTS;
        int contextLines = args.containsKey("context_lines")
            ? ((Number) args.get("context_lines")).intValue()
            : 0;

        // 1. 参数校验
        if (pathStr == null || pathStr.isBlank()) {
            return "错误: 缺少 'path' 参数";
        }
        if (patternStr == null || patternStr.isBlank()) {
            return "错误: 缺少 'pattern' 参数";
        }
        if (maxResults <= 0 || maxResults > 200) {
            return "错误: max_results 必须在 1-200 之间 (给的是 " + maxResults + ")";
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
        if (!Files.exists(target)) {
            return "错误: 路径不存在: " + pathStr;
        }

        // 3. 编译正则
        Pattern pattern;
        try {
            pattern = Pattern.compile(patternStr);
        } catch (Exception e) {
            return "错误: 无效的正则表达式 '" + patternStr + "': " + e.getMessage();
        }

        // 4. 收集待搜文件
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(target)) {
            try (Stream<Path> walk = Files.walk(target, MAX_DEPTH)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> isGrepable(p))
                    .forEach(files::add);
            } catch (IOException e) {
                return "错误: 无法遍历目录 " + pathStr + ": " + e.getMessage();
            }
        } else {
            files.add(target);
        }

        // 5. 搜!
        StringBuilder sb = new StringBuilder();
        int totalMatches = 0;

        for (Path file : files) {
            if (totalMatches >= maxResults) break;
            // 跳过太大的文件
            try {
                if (Files.size(file) > MAX_FILE_BYTES) continue;
            } catch (IOException e) {
                continue;
            }
            int fileMatches = searchFile(file, target, pattern, contextLines,
                maxResults - totalMatches, sb);
            totalMatches += fileMatches;
        }

        if (totalMatches == 0) {
            return "0 matches found for pattern: " + patternStr;
        }
        return sb.toString();
    }

    /**
     * 在单个文件里搜。返回匹配数,结果写入 sb。
     * Search a single file. Returns match count, writes results to sb.
     */
    private int searchFile(Path file, Path base, Pattern pattern, int contextLines,
                           int remainingQuota, StringBuilder sb) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            return 0;
        }

        // 先找出所有匹配的行号(1-based)
        List<Integer> matchLineNums = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (pattern.matcher(lines.get(i)).find()) {
                matchLineNums.add(i + 1);  // 1-based
                if (matchLineNums.size() >= remainingQuota) break;
            }
        }

        if (matchLineNums.isEmpty()) return 0;

        String relativePath;
        try {
            relativePath = base.toFile().toPath().relativize(file).toString();
        } catch (Exception e) {
            relativePath = file.toString();
        }

        sb.append("--- ").append(matchLineNums.size())
          .append(" matches in ").append(relativePath).append(" ---\n");

        for (Integer matchLine : matchLineNums) {
            int lineIdx = matchLine - 1;  // back to 0-based
            int start = Math.max(0, lineIdx - contextLines);
            int end = Math.min(lines.size() - 1, lineIdx + contextLines);

            for (int i = start; i <= end; i++) {
                String marker = (i == lineIdx) ? ">" : " ";
                sb.append(relativePath).append(":").append(i + 1).append(":")
                  .append(marker).append(lines.get(i)).append("\n");
            }
        }

        return matchLineNums.size();
    }

    /** 跳过隐藏文件 / target / .git 等 / Skip hidden dirs, target/, .git, etc. */
    private boolean isGrepable(Path p) {
        String s = p.toString();
        // 跳过常见"不该搜"的目录
        return !s.contains("/.git/")
            && !s.contains("/target/")
            && !s.contains("/node_modules/")
            && !s.contains("/.idea/")
            && !s.contains("/.vscode/");
    }
}
