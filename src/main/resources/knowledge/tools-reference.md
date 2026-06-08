# 工具参考 / Tools Reference

**Day 1-3 一共 5 个工具,Day 4 加第 6 个。一句话总结每个工具:**

## 1. get_current_time (Day 1)
- **作用 / Purpose**:返回当前时间,带时区
- **参数 / Args**: `timezone` (可选,默认 UTC,IANA 名如 `Asia/Shanghai`)
- **返回 / Returns**: ISO 8601 字符串,如 `2026-06-07T22:30:00+08:00`
- **典型用例 / Use case**: "现在几点?", "Beijing time?"

## 2. read_file (Day 1)
- **作用 / Purpose**:读文件内容
- **参数 / Args**: `path` (相对或绝对,限 100KB)
- **返回 / Returns**: 文件内容字符串
- **典型用例**: "看 README 第 1 行", "读 Agent.java"

## 3. write_file (Day 3 新增)
- **作用 / Purpose**:写文件,覆盖现有内容
- **参数 / Args**: `path`, `content` (限 1MB)
- **返回 / Returns**: "wrote N bytes to path"
- **典型用例**: "Create target/foo.txt with content 'hello'"
- **防护**: 父目录自动创建, 1MB 上限, 路径白名单

## 4. grep (Day 3 新增)
- **作用 / Purpose**:Java 正则搜文件(纯 Java,跨平台)
- **参数 / Args**: `path`, `pattern` (Java regex), `max_results` (默认 20), `context_lines` (默认 0)
- **返回 / Returns**: `--- N matches in file ---` 格式
- **典型用例**: "Find all 'Day 1' in README.md", "Show lines around match"

## 5. exec (Day 1)
- **作用 / Purpose**:执行 shell 命令
- **参数 / Args**: `command` (字符串)
- **返回 / Returns**: stdout + stderr 合并
- **典型用例**: "List files in target/", "git log --oneline"
- **限制**: 5 秒超时, 默认工作目录是项目根

## 6. search_kb (Day 4 新增)
- **作用 / Purpose**:搜知识库(简易 RAG,纯内存,keyword TF)
- **参数 / Args**: `query` (必填), `max_results` (默认 3, 最大 10)
- **返回 / Returns**: top-K chunks, `--- chunk #N in file ---` 格式
- **典型用例**: "Day 3 加了什么工具?", "Java 怎么编译?"
- **知识库位置 / KB location**: `src/main/resources/knowledge/`
- **索引策略 / Indexing**: 按段切 chunk,每段 ≤ 500 字符,overlap 50

## 工具组合常见模式 / Common Tool Combinations

- **读 + 总结**: `read_file` + LLM 总结
- **grep + 读**: `grep` 找位置 + `read_file` 看上下文
- **写 + 读**: `write_file` 创建 + `read_file` 验证
- **search_kb + write_file**: 查文档 + 写笔记
