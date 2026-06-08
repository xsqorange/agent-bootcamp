package com.agentbootcamp.tools;

import com.agentbootcamp.RagIndex;
import com.agentbootcamp.Tool;

import java.util.List;
import java.util.Map;

/**
 * 工具 6:知识库搜索 / Tool 6: search the knowledge base.
 *
 * Day 4 实现 / Day 4 implementation:
 *  - 拿 query + max_results, 调 RagIndex.search()
 *  - 把 top-K chunks 拼成可读文本返回
 *
 * 输出格式 / Output format (跟 grep 工具保持一致):
 *  --- chunk #N in <file> ---
 *  <content>
 *  --- chunk #M in <file> ---
 *  <content>
 *
 * 防护 / Safety:
 *  - 空 query / max_results 越界 都返回错误字符串
 *  - 索引为空(没知识库) 返回 "0 matches"
 *
 * Java 锚定 / Java anchor:
 *  - Tool 接口的 4 个方法 (name/description/jsonSchema/execute)
 *  - RagIndex 不可变,execute 不会修改
 */
public class SearchKb implements Tool {

    private final RagIndex index;

    public SearchKb(RagIndex index) {
        this.index = index;
    }

    @Override
    public String name() {
        return "search_kb";
    }

    @Override
    public String description() {
        return "Search the local knowledge base (RAG) for relevant chunks. " +
               "Use when the user asks about project documentation, past decisions, " +
               "or any topic likely covered in knowledge/*.md files. " +
               "Returns up to N matching chunks with file reference.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "query", Map.of(
                    "type", "string",
                    "description", "搜索关键词或问题 / search keywords or question"
                ),
                "max_results", Map.of(
                    "type", "integer",
                    "description", "最多返回几条 (默认 3, 最大 10) / max results (default 3, max 10)"
                )
            ),
            "required", List.of("query")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String query = (String) args.get("query");
        if (query == null || query.isBlank()) {
            return "错误: 缺少 'query' 参数";
        }
        int maxResults = args.containsKey("max_results")
            ? ((Number) args.get("max_results")).intValue()
            : RagIndex.DEFAULT_MAX_RESULTS;
        if (maxResults <= 0 || maxResults > 10) {
            return "错误: max_results 必须在 1-10 之间 (给的是 " + maxResults + ")";
        }

        if (index == null || index.size() == 0) {
            return "0 matches: 知识库为空(没找到 knowledge/*.md 文件)";
        }

        List<RagIndex.Chunk> results = index.search(query, maxResults);
        if (results.isEmpty()) {
            return "0 matches found in knowledge base for query: " + query;
        }

        StringBuilder sb = new StringBuilder();
        for (RagIndex.Chunk c : results) {
            sb.append(c.formatted()).append("\n");
        }
        return sb.toString().trim();
    }
}
