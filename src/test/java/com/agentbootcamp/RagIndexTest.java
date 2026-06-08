package com.agentbootcamp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RagIndex 单元测试 / RagIndex unit tests.
 *
 * 测 5 个 case:
 *  1. 加载 .md 文件 + 切分 chunk
 *  2. 段切分(< CHUNK_SIZE) + 滑窗切分(> CHUNK_SIZE)
 *  3. tokenize (中英文都支持)
 *  4. search 返回 top-K 相关 chunks
 *  5. 边界: 空 query / 索引为空 / maxResults 越界
 */
class RagIndexTest {

    @Test
    void testLoadFromDirectory(@TempDir Path tempDir) throws IOException {
        // 写 2 个 .md 文件(一个在根,一个在子目录)
        Files.writeString(tempDir.resolve("a.md"), "# A\n\nFirst paragraph.\n\nSecond paragraph.");
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub").resolve("b.md"), "B content here");

        RagIndex idx = new RagIndex(tempDir);
        assertTrue(idx.size() > 0, "应加载到至少 1 个 chunk, 实际: " + idx.size());
    }

    @Test
    void testSplitIntoChunksSmall() {
        // 每段 < 500 → 每段 1 个 chunk
        String content = "Para 1.\n\nPara 2.\n\nPara 3.";
        List<RagIndex.Chunk> chunks = RagIndex.splitIntoChunks("test.md", content);
        assertEquals(3, chunks.size());
        assertEquals("Para 1.", chunks.get(0).content());
        assertEquals("Para 2.", chunks.get(1).content());
        assertEquals("Para 3.", chunks.get(2).content());
        // chunkId 递增
        assertEquals(0, chunks.get(0).chunkId());
        assertEquals(1, chunks.get(1).chunkId());
        assertEquals(2, chunks.get(2).chunkId());
    }

    @Test
    void testSplitIntoChunksLarge() {
        // 1 段 > 500 字符 → 滑窗切
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            big.append("word").append(i).append(" ");
        }
        String content = big.toString();  // 约 600 字符

        List<RagIndex.Chunk> chunks = RagIndex.splitIntoChunks("big.md", content);
        assertTrue(chunks.size() >= 2, "长段应被切成多片, 实际: " + chunks.size());

        // 第一片应该是 500 字符
        assertEquals(500, chunks.get(0).content().length(),
            "第一片应为 CHUNK_SIZE (500)");

        // 第二片应包含 overlap
        if (chunks.size() >= 2) {
            assertTrue(chunks.get(1).content().length() > 0);
            // overlap 检查: 第一片后 50 字符 == 第二片前 50 字符
            String first = chunks.get(0).content();
            String second = chunks.get(1).content();
            String firstTail = first.substring(first.length() - RagIndex.CHUNK_OVERLAP);
            String secondHead = second.substring(0, RagIndex.CHUNK_OVERLAP);
            assertEquals(firstTail, secondHead, "应有 " + RagIndex.CHUNK_OVERLAP + " 字符 overlap");
        }
    }

    @Test
    void testTokenizeBilingual() {
        // 中英文 + 标点
        Set<String> tokens = RagIndex.tokenize("Hello world, 你好 世界! Day 4 starts.");
        assertTrue(tokens.contains("hello"));
        assertTrue(tokens.contains("world"));
        assertTrue(tokens.contains("你好"));
        assertTrue(tokens.contains("世界"));
        assertTrue(tokens.contains("day"));
        assertTrue(tokens.contains("starts"));
        // 标点和大写不应包含
        assertFalse(tokens.contains("Hello")); // lowercase 后
        assertFalse(tokens.contains(","));     // 标点
    }

    @Test
    void testSearchReturnsRelevant() {
        // 用 package-private 构造器直接传 chunks(测试可控)
        List<RagIndex.Chunk> chunks = List.of(
            new RagIndex.Chunk("a.md", 0, "Day 1 was about LLM basics and project setup."),
            new RagIndex.Chunk("b.md", 0, "Day 3 added write_file and grep tools."),
            new RagIndex.Chunk("c.md", 0, "Java 17 uses Amazon Corretto distribution."),
            new RagIndex.Chunk("d.md", 0, "Random text without any relevant keywords.")
        );
        RagIndex idx = new RagIndex(chunks);

        // 搜 "Day 3 write_file" → b.md 排第一 (3 hits)
        // a.md 也排得上 (1 hit: "day")
        // c.md / d.md 0 hit, 不返回
        List<RagIndex.Chunk> results = idx.search("Day 3 write_file", 3);
        assertEquals(2, results.size(), "只有 2 个 chunk 命中, 实际: " + results.size());
        assertEquals("b.md", results.get(0).file(),
            "b.md (含 Day 3 + write_file) 应排第一");
        assertEquals("a.md", results.get(1).file(),
            "a.md (含 day) 应排第二");
    }

    @Test
    void testSearchBilingualRelevance() {
        // 中文查询应该匹配中文 chunk
        List<RagIndex.Chunk> chunks = List.of(
            new RagIndex.Chunk("zh.md", 0, "Agent 是个 LLM 编程助手"),
            new RagIndex.Chunk("en.md", 0, "Agent is a coding helper"),
            new RagIndex.Chunk("other.md", 0, "今天天气不错")
        );
        RagIndex idx = new RagIndex(chunks);

        List<RagIndex.Chunk> results = idx.search("Agent 助手", 3);
        assertFalse(results.isEmpty());
        assertEquals("zh.md", results.get(0).file(),
            "中文查询应优先匹配中文 chunk");
    }

    @Test
    void testSearchEmptyQuery() {
        List<RagIndex.Chunk> chunks = List.of(
            new RagIndex.Chunk("a.md", 0, "Some content")
        );
        RagIndex idx = new RagIndex(chunks);

        assertTrue(idx.search(null, 3).isEmpty(), "null query 应返回空");
        assertTrue(idx.search("", 3).isEmpty(), "空 query 应返回空");
        assertTrue(idx.search("   ", 3).isEmpty(), "blank query 应返回空");
    }

    @Test
    void testSearchNoMatch() {
        List<RagIndex.Chunk> chunks = List.of(
            new RagIndex.Chunk("a.md", 0, "Apple banana cherry")
        );
        RagIndex idx = new RagIndex(chunks);

        assertTrue(idx.search("xyzzy_no_such_word", 3).isEmpty(),
            "完全没匹配时应返回空");
    }

    @Test
    void testSearchEmptyIndex() {
        RagIndex idx = new RagIndex(List.of());
        assertTrue(idx.search("anything", 3).isEmpty(),
            "空索引搜任何东西都应返回空");
    }

    @Test
    void testLoadFromNonExistentDir(@TempDir Path tempDir) {
        // 传个不存在的子目录
        Path nonexistent = tempDir.resolve("does-not-exist");
        RagIndex idx = new RagIndex(nonexistent);
        assertEquals(0, idx.size(), "不存在的目录 → 空索引, 不抛异常");
    }

    @Test
    void testNullDir() {
        // 显式转 Path 类型, 避免跟 List<Chunk> 构造器歧义
        RagIndex idx = new RagIndex((Path) null);
        assertEquals(0, idx.size(), "null 目录 → 空索引, 不抛异常");
    }
}
