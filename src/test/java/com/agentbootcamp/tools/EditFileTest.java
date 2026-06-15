package com.agentbootcamp.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EditFile 单元测试 / EditFile unit tests.
 * 6 个 case: 基本替换 / 多匹配 fail / 0 匹配 fail / replace_all / workDir 越界 / 大文件 fail
 */
class EditFileTest {

    @Test
    void test_basic_replace(@TempDir Path workDir) throws Exception {
        Path file = workDir.resolve("a.txt");
        Files.writeString(file, "hello world\nbye world\n");
        EditFile ef = new EditFile(workDir);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "a.txt");
        args.put("old_string", "hello world");
        args.put("new_string", "hi world");
        String result = ef.execute(args);

        assertTrue(result.contains("edited"), result);
        assertTrue(result.contains("1 occurrence"), result);
        assertEquals("hi world\nbye world\n", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void test_multi_occurrence_fails_without_replace_all(@TempDir Path workDir) throws Exception {
        Path file = workDir.resolve("a.txt");
        Files.writeString(file, "foo bar foo bar foo");
        EditFile ef = new EditFile(workDir);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "a.txt");
        args.put("old_string", "foo");
        args.put("new_string", "FOO");

        Exception ex = assertThrows(IllegalArgumentException.class, () -> ef.execute(args));
        assertTrue(ex.getMessage().contains("出现 3 次"), ex.getMessage());
        // 文件不变
        assertEquals("foo bar foo bar foo", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void test_replace_all_replaces_every_occurrence(@TempDir Path workDir) throws Exception {
        Path file = workDir.resolve("a.txt");
        Files.writeString(file, "foo bar foo bar foo");
        EditFile ef = new EditFile(workDir);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "a.txt");
        args.put("old_string", "foo");
        args.put("new_string", "FOO");
        args.put("replace_all", true);
        ef.execute(args);

        assertEquals("FOO bar FOO bar FOO", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void test_zero_occurrence_fails(@TempDir Path workDir) throws Exception {
        Path file = workDir.resolve("a.txt");
        Files.writeString(file, "hello world");
        EditFile ef = new EditFile(workDir);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "a.txt");
        args.put("old_string", "goodbye");
        args.put("new_string", "hi");

        Exception ex = assertThrows(IllegalArgumentException.class, () -> ef.execute(args));
        assertTrue(ex.getMessage().contains("找不到"), ex.getMessage());
    }

    @Test
    void test_workDir_escape_fails(@TempDir Path workDir) throws Exception {
        EditFile ef = new EditFile(workDir);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "../escape.txt");  // 越出 workDir
        args.put("old_string", "x");
        args.put("new_string", "y");

        Exception ex = assertThrows(SecurityException.class, () -> ef.execute(args));
        assertTrue(ex.getMessage().contains("workDir"), ex.getMessage());
    }

    @Test
    void test_same_old_and_new_fails(@TempDir Path workDir) throws Exception {
        Path file = workDir.resolve("a.txt");
        Files.writeString(file, "hello");
        EditFile ef = new EditFile(workDir);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "a.txt");
        args.put("old_string", "hello");
        args.put("new_string", "hello");

        Exception ex = assertThrows(IllegalArgumentException.class, () -> ef.execute(args));
        assertTrue(ex.getMessage().contains("完全相同"), ex.getMessage());
    }
}
