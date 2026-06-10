#!/usr/bin/env bash
# =============================================================================
# Agent Bootcamp — Demo Script / 演示脚本
# =============================================================================
#
# 中文:这个脚本用于 Day 7 录 60 秒 demo GIF(用 asciinema + agg)。
# 跑法 / Usage:
#   1. set -a && source .env && set +a   # 加载 MINIMAX_API_KEY 等
#   2. ./demo-script.sh                  # 跑 5 个 demo 命令(~60 秒)
#   3. asciinema rec demo.cast           # 录屏
#   4. agg demo.cast demo.gif            # 转 GIF(Day 7)
#
# English: This script is for recording the Day 7 60-second demo GIF
# (via asciinema + agg). Run order is tuned for ~60 sec total.
# =============================================================================

set -e

# Load API key / 加载 API key
if [ -f .env ]; then
  set -a; source .env; set +a
fi

# Sanity check / 健全性检查
if [ -z "$MINIMAX_API_KEY" ]; then
  echo "❌ MINIMAX_API_KEY 没设。运行: set -a && source .env && set +a"
  exit 1
fi

# Common flags / 通用参数(每条命令都加 max-steps/cost 防止失控)
FLAGS="--max-steps 5 --max-cost 0.10"

echo ""
echo "==============================================="
echo " Demo 1/5: get_current_time / 单工具"
echo "==============================================="
./mvnw -q exec:java -Dexec.mainClass="com.agentbootcamp.Main" \
    -Dexec.args="--goal '用 get_current_time 拿 Asia/Shanghai 当前时间,然后说 done' $FLAGS"

echo ""
echo "==============================================="
echo " Demo 2/5: read_file / 读 README 第 1 行"
echo "==============================================="
./mvnw -q exec:java -Dexec.mainClass="com.agentbootcamp.Main" \
    -Dexec.args="--goal '用 read_file 读 README.md 第 1 行,然后告诉我那行内容' $FLAGS"

echo ""
echo "==============================================="
echo " Demo 3/5: write_file / 写文件"
echo "==============================================="
./mvnw -q exec:java -Dexec.mainClass="com.agentbootcamp.Main" \
    -Dexec.args="--goal '用 write_file 创建 target/demo-output.txt 内容 DEMO-WRITE-OK,然后说 done' $FLAGS"
echo "--- 验证文件 ---"
cat target/demo-output.txt

echo ""
echo "==============================================="
echo " Demo 4/5: grep / 搜匹配"
echo "==============================================="
./mvnw -q exec:java -Dexec.mainClass="com.agentbootcamp.Main" \
    -Dexec.args="--goal '用 grep 搜 README.md 找 Day 1 关键词,告诉我匹配了几行' $FLAGS"

echo ""
echo "==============================================="
echo " Demo 5/5: search_kb / RAG 知识库查询"
echo "==============================================="
./mvnw -q exec:java -Dexec.mainClass="com.agentbootcamp.Main" \
    -Dexec.args="--goal '用 search_kb 查 Day 3 加了什么工具,然后用一句话总结' $FLAGS"

echo ""
echo "==============================================="
echo " Bonus: EvalHarness 跑全部 10 个黄金用例"
echo "==============================================="
./mvnw -B verify 2>&1 | tail -5

echo ""
echo "✅ 演示完成! 累计 ~60 秒, 烧 ~$0.005"
