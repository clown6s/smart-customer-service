# smart-customer-service

AI 智能客服系统 — 基于 LangChain4j + DeepSeek + Spring Boot 3 构建的电商智能客服服务。

## 功能特性

- **FAQ 快速匹配**：13 条关键词规则，< 1ms 响应，命中直接返回
- **情绪检测**：三级分级（mild / moderate / severe），自动追加安抚话术
- **LLM Agent**：LangChain4j AiServices + DeepSeek Function Calling，自动调用工具查询订单/退款/用户信息
- **SSE 流式输出**：按句子逐步推送，提升体验
- **Redis 限流**：滑动窗口，默认 60s/10 次
- **链路追踪**：每请求注入 traceId，日志全链路关联

## 技术栈

| 层 | 技术 |
|---|---|
| 框架 | Spring Boot 3.4.5 / Java 21 |
| AI | LangChain4j 1.0 + DeepSeek API |
| ORM | MyBatis-Plus 3.5.9 |
| 数据库 | PostgreSQL 16 |
| 缓存 | Redis 7 |
| 监控 | Micrometer + Prometheus |
| 构建 | Maven + Docker 多阶段构建 |

## 快速启动

### 方式一：Docker Compose（推荐）

```bash
cp .env.example .env
# 编辑 .env，填入 DEEPSEEK_API_KEY 和数据库密码

docker compose up -d
```

访问：http://localhost:8080/actuator/health

### 方式二：本地开发

**前提：** JDK 21、PostgreSQL 16、Redis 7 已就绪

```bash
# 1. 初始化数据库
psql -U postgres -d smart_cs -f src/main/resources/db/init.sql

# 2. 配置环境变量
cp .env.example .env
# 填入 DEEPSEEK_API_KEY 等必填项

# 3. 启动（dev profile 默认）
mvn spring-boot:run
```

## API 文档

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/chat` | JSON 对话，返回完整回复 |
| `POST` | `/api/chat/stream` | SSE 流式对话 |
| `GET` | `/api/health` | 服务健康检查 |
| `GET` | `/actuator/health` | Spring Actuator 健康 |
| `GET` | `/actuator/metrics` | Micrometer 指标 |
| `GET` | `/actuator/prometheus` | Prometheus 格式指标 |

### 请求示例

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message": "我的订单什么时候发货？", "userId": "1"}'
```

```bash
# SSE 流式
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{"message": "查一下我的退款进度", "userId": "1"}'
```

## 配置说明

所有敏感配置通过 `.env` 注入，见 `.env.example`。

| 关键变量 | 说明 |
|---|---|
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥（必填） |
| `DATABASE_URL` | PostgreSQL 连接串 |
| `REDIS_HOST` / `REDIS_PASSWORD` | Redis 连接信息 |
| `SPRING_PROFILES_ACTIVE` | `dev` / `prod` |
| `CORS_ORIGIN` | 跨域允许来源（生产禁用 `*`） |

## 构建 Docker 镜像

```bash
docker build -t smart-customer-service:latest .
```

## 监控

生产环境 Prometheus 采集地址：`GET /actuator/prometheus`

指标包括：JVM 内存/GC、HikariCP 连接池、HTTP 请求 QPS/延迟、自定义业务指标。
