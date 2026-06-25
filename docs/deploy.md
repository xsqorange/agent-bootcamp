# Day 13: 部署文档 / Deployment Guide

> **中文**:把 Agent Bootcamp 从 CLI 升级为 HTTP server + Docker + K8s 部署的生产实践。
>
> **English**: Production deployment guide — from CLI to HTTP server, Docker, and Kubernetes.

---

## 🎯 3 种部署方式 / 3 Deployment Options

| 方式 | 适用 | 工作量 |
|---|---|---|
| **1. 进程模式** (`./mvnw exec:java --server`) | 本地开发 | 5 min |
| **2. Docker** (`docker run -p 8080:8080 agent-bootcamp`) | 单机 / staging | 15 min |
| **3. K8s** (`kubectl apply -f k8s/`) | 生产 | 60 min |

---

## 1️⃣ 进程模式 / Process Mode

```bash
# 编译
./mvnw -B package

# 跑 HTTP server (默认 8080)
java -jar target/agent-bootcamp.jar --server

# 自定义端口
java -jar target/agent-bootcamp.jar --server --server-port 9090

# 验证
curl http://localhost:8080/health
# {"status":"UP","uptimeSec":42}

curl http://localhost:8080/health/liveness
# {"status":"UP"}

curl http://localhost:8080/health/readiness
# {"status":"DOWN","reason":"config-error: ..."}  # 没 .env

curl http://localhost:8080/metrics
# 6 类 Prometheus metric 全可见 (Day 11)

curl -X POST http://localhost:8080/api/run \
  -H "Content-Type: application/json" \
  -d '{"goal":"用 get_current_time 拿当前时间"}'
# {"status":"ACCEPTED","goal":"..."}
```

---

## 2️⃣ Docker 模式 / Docker Mode

### 本地 build + run

```bash
# build (multi-stage, ~3-5 min 首次)
docker build -t agent-bootcamp:latest .

# 跑 (env 从 .env 读)
docker run -d \
  --name agent-bootcamp \
  -p 8080:8080 \
  --env-file .env \
  agent-bootcamp:latest

# 验证 healthcheck
docker ps
# STATUS: Up X minutes (healthy)  ← 健康检查通过
curl http://localhost:8080/health

# logs
docker logs -f agent-bootcamp
```

### docker-compose (推荐)

```bash
# 起 (单 container + Prometheus 可选)
docker compose up -d
curl http://localhost:8080/health

# 起 + Prometheus (--profile monitoring)
docker compose --profile monitoring up -d
# Prometheus UI: http://localhost:9090

# 停
docker compose down
```

**`docker-compose.yml` 关键字段**:
- `ports: "8080:8080"` 暴露端口
- `healthcheck.test: ["CMD", "wget", "-q", "-O", "-", "http://localhost:8080/health"]` 容器级探活
- `resources.limits: cpus: 1.0, memory: 512M` 防吃光
- `profiles: ["monitoring"]` 可选 Prometheus

---

## 3️⃣ K8s 模式 / Kubernetes Mode

### 前置准备 (30 min)

1. **构建 + push 镜像**:
   ```bash
   docker build -t ghcr.io/xsqorange/agent-bootcamp:v0.1.0 .
   docker push ghcr.io/xsqorange/agent-bootcamp:v0.1.0
   ```

2. **创建 Secret** (含 LLM API key, 不 commit 到 git!):
   ```bash
   kubectl create secret generic agent-bootcamp-secrets \
     --from-literal=MINIMAX_API_KEY=sk-cp-your-key
   ```

### 部署 (5 min)

```bash
# 应用清单
kubectl apply -f k8s/deployment.yaml

# 验证
kubectl get pods -l app=agent-bootcamp
# NAME                              READY   STATUS    RESTARTS
# agent-bootcamp-7d4f5b6c8d-abcde  1/1     Running   0

# 看日志
kubectl logs -f -l app=agent-bootcamp

# 端到端探活
kubectl exec -it <pod-name> -- wget -q -O - http://localhost:8080/health
```

### K8s 关键字段

| 字段 | 作用 | 错误做法 |
|---|---|---|
| `livenessProbe /health/liveness` | "loop 没卡死" | 不要查 "model 在响应" — 那是 readiness |
| `readinessProbe /health/readiness` | "config + 依赖都通" | 不要让 pod 一直挂 = 永久不接流量 |
| `resources.limits` cpu:1 mem:1Gi | 防单次会话吃光节点 | 不设 = 一个会话把节点拉爆 |
| `envFrom secretKeyRef` | LLM API key 走 Secret | **不要** 写死 `value: "sk-..."` 在 yaml |
| `strategy: RollingUpdate` | 0 停机升级 | 不配 = Recreate 停机 |

### 升级 / Rolling Update

```bash
# 1. 改 image tag
kubectl set image deployment/agent-bootcamp \
  agent-bootcamp=ghcr.io/xsqorange/agent-bootcamp:v0.2.0

# 2. 看滚动状态
kubectl rollout status deployment/agent-bootcamp

# 3. 回滚 (出问题)
kubectl rollout undo deployment/agent-bootcamp
```

---

## 📊 4 个端点对照表 / 4 Endpoints Reference

| 端点 | 用途 | K8s probe | 返回 |
|---|---|---|---|
| `GET /health` | 总健康 | (无,备用) | 200 + `{"status":"UP","uptimeSec":N}` |
| `GET /health/liveness` | loop 没卡死 | `livenessProbe` | 200 + `{"status":"UP"}` |
| `GET /health/readiness` | 依赖都通 | `readinessProbe` | 200 + `{"status":"UP/DOWN","dependencies":...}` |
| `GET /metrics` | Prometheus scrape | (Sidecar 抓) | 200 + Prometheus text (Day 11 6 metric) |
| `POST /api/run` | 跑 agent (Day 14 集成) | (无) | 202 + `{"status":"ACCEPTED","goal":"..."}` |

---

## 🐛 Day 13 部署踩过的坑 / Day 13 Deployment Pitfalls

1. **`InetSocketAddress(0)` 不返 OS 实际分配 port** — 构造时存 `this.port=0`,`getPort()` 必须返 `server.getAddress().getPort()`
2. **Liveness/Readiness handler 抛异常致 EOF** — JDK 内置 HttpServer 异常 → connection 关,客户端 EOF。**修法**: handler 加 try-catch 兜底
3. **`HEALTHCHECK` 配 alpine 没 wget** — eclipse-temurin:17-jre-alpine 默认无 wget,需 `apk add --no-cache wget`
4. **`docker-compose` profile 默认不起** — Prometheus 用 `profiles: ["monitoring"]` 隔开,默认不拉
5. **Secret 不能 commit** — `k8s/deployment.yaml` 里的 Secret 是模板 (含 REPLACE-ME),真 secret 用 `kubectl create secret` 单独存

---

## 🔗 相关链接

- 代码: <https://github.com/xsqorange/agent-bootcamp>
- Docker Hub / GHCR: 镜像 push 后填入 k8s/deployment.yaml image 字段
- K8s 文档: <https://kubernetes.io/docs/concepts/configuration/secret/>
- Prometheus 文档: <https://prometheus.io/docs/prometheus/latest/configuration/configuration/#scrape_config>