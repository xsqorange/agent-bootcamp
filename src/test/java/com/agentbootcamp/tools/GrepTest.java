package com.agentbootcamp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Grep 工具单元测试 / Grep tool unit tests
 *
 * 测 4 个 case:
 *  1. 简单单文件 grep 找到匹配
 *  2. 单文件 grep 找不到匹配 → "0 matches"
 *  3. 目录递归 grep
 *  4. context_lines 显示匹配行上下文
 */
class GrepTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void seedFiles() throws Exception {
        // 文件 1: 包含多行匹配
        Files.writeString(tempDir.resolve("readme.md"), """
            # Project
            This is line 2.
            Day 2 is the best day.
            Random stuff here.
            Day 2 again on line 4.
            """);
        // 文件 2: 在子目录
        Path sub = Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(sub.resolve("notes.txt"), """
            Some notes.
            Day 3 starts now.
            """);
        // 文件 3: 没匹配
        Files.writeString(tempDir.resolve("nothing.md"), "nothing matches here");
    }

    @Test
    void testGrepFindsMatchesInSingleFile() {
        Grep g = new Grep(tempDir);

        Map<String, Object> args = new HashMap<>();
        args.put("path", "readme.md");
        args.put("pattern", "Day 2");

        String result = g.execute(args);

        assertTrue(result.contains("2 matches in"), "应该找到 2 个匹配,实际: " + result);
        assertTrue(result.contains("Day 2 is the best day"));
        assertTrue(result.contains("Day 2 again"));
    }

    @Test
    void testGrepReturnsZeroWhenNoMatch() {
        Grep g = new Grep(tempDir);

        Map<String, Object> args = new HashMap<>();
        args.put("path", "readme.md");
        args.put("pattern", "xyzzy_no_such_string");

        String result = g.execute(args);

        assertEquals("0 matches found for pattern: xyzzy_no_such_string", result);
    }

    @Test
    void testGrepRecursiveInDirectory() {
        Grep g = new Grep(tempDir);

        Map<String, Object> args = new HashMap<>();
        args.put("path", ".");
        args.put("pattern", "Day [23]");

        String result = g.execute(args);

        // readme.md 里有 2 个 Day 2 匹配,notes.txt 里有 1 个 Day 3 匹配
        assertTrue(result.contains("readme.md"), "应包含 readme.md");
        assertTrue(result.contains("notes.txt") || result.contains("sub"), "应包含子目录的 notes.txt");
    }

    @Test
    void testGrepContextLines() {
        Grep g = new Grep(tempDir);

        Map<String, Object> args = new HashMap<>();
        args.put("path", "readme.md");
        args.put("pattern", "Day 2 is");
        args.put("context_lines", 1);

        String result = g.execute(args);

        // 应该看到匹配行 + 它的前一行 + 后一行
        assertTrue(result.contains("This is line 2"), "应显示匹配前一行");
        assertTrue(result.contains("Day 2 is the best day"), "应显示匹配行");
        assertTrue(result.contains("Random stuff here"), "应显示匹配后一行");
    }

    @Test
    void testGrepInvalidRegex(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("real.txt"), "anything");
        Grep g = new Grep(dir);

        Map<String, Object> args = new HashMap<>();
        args.put("path", "real.txt");
        args.put("pattern", "[unclosed");

        String result = g.execute(args);

        assertTrue(result.startsWith("错误: 无效的正则表达式"));
    }
}
