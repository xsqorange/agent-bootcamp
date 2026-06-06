package com.agentbootcamp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * JSONL trace writer / JSONL 追踪文件写入器
 *
 * 设计目标 / Design goals:
 *  - 追加模式:同一文件可记录多次 run(Day 11 加 session_id 分隔)
 *  - 每写一行立即 flush:Agent 崩了 trace 还在
 *  - 关闭时优雅收尾
 *  - "off" 或 null 字符串 = 禁用(便于测试)
 *
 * Day 3 会加 / Day 3 will add:
 *  - 文件大小轮转(防止 trace 无限增长)
 *  - session_id 自动注入
 *  - 异步写入(不阻塞 Agent 主循环)
 */
public class TraceWriter implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TraceWriter.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final BufferedWriter writer;  // null = 禁用
    private final String path;            // 配置路径(可能为 null)

    public TraceWriter(String path) throws IOException {
        this.path = path;
        if (path == null || path.isBlank() || "off".equalsIgnoreCase(path)) {
            this.writer = null;
            log.info("Trace disabled");
            return;
        }
        Path p = Path.of(path);
        Path parent = p.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.writer = Files.newBufferedWriter(p,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
        log.info("Trace enabled: {}", p.toAbsolutePath());
    }

    /** 写一步(每步 1 行 JSON)/ Write one step (1 line JSON per step) */
    public void writeStep(AgentStep step) {
        if (writer == null) return;
        try {
            String line = JSON.writeValueAsString(step);
            writer.write(line);
            writer.newLine();
            writer.flush();   // 每步立即刷盘,崩了也能回放
        } catch (IOException e) {
            log.warn("Failed to write trace step: {}", e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.flush();
            writer.close();
            log.debug("Trace writer closed: {}", path);
        }
    }
}
