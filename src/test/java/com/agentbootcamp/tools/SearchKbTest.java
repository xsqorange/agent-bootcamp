package com.agentbootcamp.tools;

import com.agentbootcamp.RagIndex;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SearchKb 工具单元测试 / SearchKb tool unit tests.
 *
 * 测 4 个 case:
 *  1. schema 正确 (name / description / query 必填 / max_results integer)
 *  2. 空 query → 错误
 *  3. 索引为空 → "0 matches"
 *  4. 正常查询 → 返回 chunk 格式
 */
class SearchKbTest {

    @Test
    void testSchema() {
        SearchKb tool = new SearchKb(new RagIndex(List.of()));
        assertEquals("search_kb", tool.name());
        assertNotNull(tool.description());
        assertTrue(tool.description().contains("knowledge"));

        Map<String, Object> schema = tool.jsonSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("query"));
        assertTrue(props.containsKey("max_results"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("query"), "query 必填");
    }

    @Test
    void testEmptyQuery() {
        SearchKb tool = new SearchKb(new RagIndex(List.of()));
        assertTrue(tool.execute(Map.of("query", "")).startsWith("错误"));
        assertTrue(tool.execute(Map.of("query", "   ")).startsWith("错误"));
        // query 不存在
        assertTrue(tool.execute(Map.of()).startsWith("错误"));
    }

    @Test
    void testEmptyIndex() {
        SearchKb tool = new SearchKb(new RagIndex(List.of()));
        String result = tool.execute(Map.of("query", "anything"));
        assertTrue(result.contains("0 matches"), "空索引应返回 '0 matches'");
    }

    @Test
    void testNormalSearch() {
        List<RagIndex.Chunk> chunks = List.of(
            new RagIndex.Chunk("a.md", 0, "Day 3 added write_file tool."),
            new RagIndex.Chunk("b.md", 0, "Java 17 is great.")
        );
        SearchKb tool = new SearchKb(new RagIndex(chunks));

        Map<String, Object> args = new HashMap<>();
        args.put("query", "write_file");
        args.put("max_results", 2);

        String result = tool.execute(args);

        assertTrue(result.contains("a.md"), "应找到 a.md");
        assertTrue(result.contains("write_file"), "chunk 内容应在结果中");
        assertTrue(result.startsWith("--- chunk #"), "输出应以 '--- chunk #' 开头");
    }

    @Test
    void testMaxResultsOutOfRange() {
        SearchKb tool = new SearchKb(new RagIndex(List.of()));
        // max_results > 10
        String result = tool.execute(Map.of("query", "x", "max_results", 100));
        assertTrue(result.startsWith("错误"), "max_results > 10 应报错");
    }

    @Test
    void testNullIndex() {
        // 即使传 null index, 工具也不能崩
        SearchKb tool = new SearchKb(null);
        String result = tool.execute(Map.of("query", "anything"));
        assertTrue(result.contains("0 matches"), "null index 应返回 '0 matches'");
    }
}
