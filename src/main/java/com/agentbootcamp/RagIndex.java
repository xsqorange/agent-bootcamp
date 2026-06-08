package com.agentbootcamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * RagIndex — 简易 RAG 索引(纯内存,keyword TF 评分)/ Simple RAG index (in-memory, keyword TF scoring).
 *
 * Day 4 实现 / Day 4 implementation:
 *  - 启动时扫描 knowledgeDir 下的所有 .md 文件
 *  - 按段(双换行)切 chunk,每段 ≤ 500 字符,overlap 50
 *  - search() 用 keyword overlap 评分,返回 top-K
 *
 * 设计选择 / Design choices:
 *  - 不引 embedding/vector DB(Day 4 "简易" 标准,Day 8+ 再升级)
 *  - 不支持动态增删(只读索引)
 *  - 中文按 character 切,英文按 word 切(简化处理,统一 lowercase)
 *  - 停用词不剔除(Day 8+ 优化)
 *
 * Java 锚定 / Java anchor:
 *  - record Chunk 不可变
 *  - List.copyOf() 防止外部修改
 *  - Stream.walk() 递归遍历(限 5 层,跟 Grep 工具一致)
 */
public class RagIndex {
    private static final Logger log = LoggerFactory.getLogger(RagIndex.class);

    /** chunk 目标大小(Q3=A: 500) / target chunk size (Q3=A: 500) */
    public static final int CHUNK_SIZE = 500;

    /** chunk overlap(Q3=A: 50) / chunk overlap (Q3=A: 50) */
    public static final int CHUNK_OVERLAP = 50;

    /** 搜索默认返回数 / default max results */
    public static final int DEFAULT_MAX_RESULTS = 3;

    private final List<Chunk> chunks = new ArrayList<>();

    /**
     * 加载索引 / Load the index from a knowledge directory.
     *
     * @param knowledgeDir 知识库目录(递归扫所有 .md)
     */
    public RagIndex(Path knowledgeDir) {
        if (knowledgeDir == null || !Files.isDirectory(knowledgeDir)) {
            log.warn("知识库目录不存在或不是目录: {}, 索引将为空", knowledgeDir);
            return;
        }
        try (Stream<Path> stream = Files.walk(knowledgeDir, 5)) {
            List<Path> mdFiles = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                .toList();

            for (Path md : mdFiles) {
                String content;
                try {
                    content = Files.readString(md);
                } catch (IOException e) {
                    log.warn("跳过文件 {}: {}", md, e.getMessage());
                    continue;
                }
                String relPath = knowledgeDir.relativize(md).toString();
                int before = chunks.size();
                chunks.addAll(splitIntoChunks(relPath, content));
                log.info("索引: {} → {} 个 chunk", relPath, chunks.size() - before);
            }
            log.info("RagIndex 加载完成: 共 {} 个 chunk (来自 {} 个文件)",
                chunks.size(), mdFiles.size());
        } catch (IOException e) {
            log.warn("扫描知识库失败: {}", e.getMessage());
        }
    }

    /** 构造器(测试用,直接传 chunks)/ Constructor for tests (pass chunks directly) */
    public RagIndex(List<Chunk> chunks) {
        this.chunks.addAll(chunks);
    }

    /**
     * 搜索 / Search.
     *
     * @param query 查询字符串
     * @param maxResults 最多返回多少条
     * @return 排序好的 top-K chunks
     */
    public List<Chunk> search(String query, int maxResults) {
        if (query == null || query.isBlank() || chunks.isEmpty()) {
            return List.of();
        }
        int k = maxResults > 0 ? Math.min(maxResults, chunks.size()) : DEFAULT_MAX_RESULTS;
        Set<String> queryTokens = tokenize(query);

        // 评分:每个 chunk 里包含多少 query token(不去重,允许重复)
        record Scored(Chunk chunk, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (Chunk c : chunks) {
            Set<String> chunkTokens = tokenize(c.content());
            long hits = queryTokens.stream().filter(chunkTokens::contains).count();
            if (hits > 0) {
                // 简单 TF 评分:命中 token 数 / query token 数(归一化)
                scored.add(new Scored(c, (double) hits / queryTokens.size()));
            }
        }

        // 排序 + 取 top K
        return scored.stream()
            .sorted(Comparator.comparingDouble(Scored::score).reversed())
            .limit(k)
            .map(Scored::chunk)
            .toList();
    }

    /** 暴露 chunks 数(测试用)/ Expose chunk count (for tests) */
    public int size() {
        return chunks.size();
    }

    /**
     * 切分 chunk / Split content into chunks.
     *
     * 策略 / Strategy:
     *  1. 先按段(双换行)切
     *  2. 每段 ≤ CHUNK_SIZE → 1 chunk
     *  3. 每段 > CHUNK_SIZE → 滑窗切,每片 CHUNK_SIZE,带 CHUNK_OVERLAP
     */
    static List<Chunk> splitIntoChunks(String file, String content) {
        List<Chunk> out = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return out;
        }
        // 按双换行切段(同时兼容 \r\n\r\n)
        String[] paragraphs = content.split("\\r?\\n\\r?\\n");

        int chunkId = 0;
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.length() <= CHUNK_SIZE) {
                out.add(new Chunk(file, chunkId++, trimmed));
            } else {
                // 滑窗切
                for (int i = 0; i < trimmed.length(); i += CHUNK_SIZE - CHUNK_OVERLAP) {
                    int end = Math.min(trimmed.length(), i + CHUNK_SIZE);
                    String slice = trimmed.substring(i, end);
                    out.add(new Chunk(file, chunkId++, slice));
                    if (end >= trimmed.length()) break;
                }
            }
        }
        return out;
    }

    /**
     * 简单 tokenize / Naive tokenizer.
     *
     * 规则 / Rules:
     *  - 转 lowercase
     *  - 用非字母数字汉字切
     *  - 长度 >= 2 才保留(去掉太短的无意义 token)
     */
    static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        String[] parts = text.toLowerCase(Locale.ROOT)
            .split("[^a-z0-9\\u4e00-\\u9fff]+");
        Set<String> result = new java.util.LinkedHashSet<>();
        for (String p : parts) {
            if (p.length() >= 2) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * 一个知识库 chunk / A knowledge base chunk.
     */
    public record Chunk(String file, int chunkId, String content) {
        /** 用于 search_kb 输出格式 / Used for search_kb output format */
        public String formatted() {
            return "--- chunk #" + chunkId + " in " + file + " ---\n" + content;
        }
    }
}
