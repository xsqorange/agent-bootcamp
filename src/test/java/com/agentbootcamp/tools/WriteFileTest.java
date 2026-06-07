package com.agentbootcamp.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WriteFile 工具单元测试 / WriteFile tool unit tests
 *
 * 测 3 个 case:
 *  1. 写新文件
 *  2. 覆盖已有文件
 *  3. 错误路径(越界 / 缺参数 / 内容过大)
 *
 * 用 @TempDir 提供临时工作目录,跟真实 workDir 解耦。
 */
class WriteFileTest {

    @Test
    void testWriteNewFile(@TempDir Path tempDir) throws Exception {
        WriteFile wf = new WriteFile(tempDir);
        Path target = tempDir.resolve("subdir/hello.txt");

        Map<String, Object> args = new HashMap<>();
        args.put("path", "subdir/hello.txt");
        args.put("content", "Hello from WriteFile!");

        String result = wf.execute(args);

        assertEquals("wrote 21 bytes to subdir/hello.txt", result);
        assertTrue(Files.exists(target));
        assertEquals("Hello from WriteFile!", Files.readString(target));
    }

    @Test
    void testOverwriteExistingFile(@TempDir Path tempDir) throws Exception {
        Path existing = tempDir.resolve("over.txt");
        Files.writeString(existing, "OLD CONTENT");

        WriteFile wf = new WriteFile(tempDir);

        Map<String, Object> args = new HashMap<>();
        args.put("path", "over.txt");
        args.put("content", "NEW CONTENT");

        String result = wf.execute(args);

        assertTrue(result.startsWith("wrote 11 bytes"));
        assertEquals("NEW CONTENT", Files.readString(existing),
            "覆盖后内容必须等于新内容,不能是 OLD 和 NEW 的拼接");
    }

    @Test
    void testErrorOnPathEscape(@TempDir Path tempDir) {
        WriteFile wf = new WriteFile(tempDir);

        Map<String, Object> args = new HashMap<>();
        args.put("path", "../escape.txt");
        args.put("content", "should fail");

        String result = wf.execute(args);

        assertTrue(result.startsWith("错误"), "越界路径必须返回错误");
        assertTrue(result.contains("越出 working dir"), "错误消息要说明是越界");
    }

    @Test
    void testErrorOnMissingContent(@TempDir Path tempDir) {
        WriteFile wf = new WriteFile(tempDir);

        Map<String, Object> args = new HashMap<>();
        args.put("path", "foo.txt");
        // 故意不传 content

        String result = wf.execute(args);

        assertTrue(result.startsWith("错误: 缺少 'content' 参数"));
    }

    @Test
    void testErrorOnOversizedContent(@TempDir Path tempDir) {
        WriteFile wf = new WriteFile(tempDir);

        // 1.5MB 字符串
        String huge = "a".repeat(1_500_000);

        Map<String, Object> args = new HashMap<>();
        args.put("path", "huge.txt");
        args.put("content", huge);

        String result = wf.execute(args);

        assertTrue(result.startsWith("错误: 内容太大"));
    }
}
