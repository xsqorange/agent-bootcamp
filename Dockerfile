# Day 13: Multi-stage Dockerfile (Java 17 Agent Bootcamp)
# 中文: build 阶段用 JDK 编译 + 打 fat-jar, runtime 阶段用 JRE-alpine (~200MB) + HEALTHCHECK
# English: multi-stage build (JDK → JRE-alpine) + Docker HEALTHCHECK

# === Build stage ===
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /src
COPY . /src
# mvnw 在 Windows 提交流程下: (1) mode=100644 丢 +x 位 (2) 换行变成 CRLF,busybox /bin/sh 把 "\r" 当成 shebang 解释器一部分
# → 直接 ./mvnw 报 "/bin/sh: ./mvnw: not found" (exit 127)
# 修法:chmod 加 +x 位 + sed 去 CR
RUN chmod +x mvnw && sed -i 's/\r$//' mvnw && head -1 mvnw
# 编译 + 打 fat-jar (mvn package 跳过 test 节省时间)
RUN ./mvnw -B package -DskipTests

# === Runtime stage ===
FROM eclipse-temurin:17-jre-alpine
# 加 wget 给 HEALTHCHECK 用 (alpine 默认有 busybox wget)
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /src/target/agent-bootcamp.jar /app.jar
EXPOSE 8080
# K8s/Docker 用 /health 探活
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -q -O - http://localhost:8080/health || exit 1
# 启动 HTTP server (--server flag)
ENTRYPOINT ["java","-jar","/app.jar","--server"]
# 默认 port 8080, 可用 docker run -e SERVER_PORT=9090 或 -p 9090:9090 覆盖 (CMD 接 args)
CMD ["--server-port","8080"]
