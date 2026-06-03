# Smart Customer Service

AI 智能客服系统 — 基于 LangChain4j + DeepSeek + Spring Boot 3 构建的电商智能客服服务。

## 功能特性

- **FAQ 快速匹配**：13 条关键词规则，< 1ms 响应，命中直接返回
- **情绪检测**：三级分级（mild / moderate / severe），自动追加安抚话术
- **LLM Agent**：LangChain4j AiServices + DeepSeek Function Calling，自动调用工具查询订单/退款/用户信息
- **SSE 流式输出**：按句子逐步推送，提升体验
- **Redis 滑动窗口限流**：ZSET Lua 脚本精确控制，Redis 宕机时 in-memory 自动兜底
- **全链路追踪**：每请求注入 traceId，MDC 传播到 SSE 子线程，日志全链路关联
- **结构化日志**：dev 彩色控制台 / prod JSON 格式 + AsyncAppender
- **日志可视化**：Promtail + Loki + Grafana，预置 Dashboard
- **JVM 监控**：Prometheus + Grafana，CPU/Heap/GC/线程/HTTP QPS/延迟/连接池

## 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 框架 | Spring Boot | 3.4.5 |
| 语言 | Java | 21 |
| AI | LangChain4j + DeepSeek API | 1.0.0 |
| ORM | MyBatis-Plus | 3.5.9 |
| 数据库 | PostgreSQL | 18.4 |
| 缓存 | Redis | 8.8.0 |
| 监控 | Micrometer + Prometheus Server | 3.4.0 |
| 日志收集 | Promtail + Loki + Grafana | 3.6.11 / 3.7.2 / 12.4 |
| 构建 | Maven + Docker 多阶段构建 | — |

## 项目结构

```
smart-customer-service/
├── src/main/java/com/shopmall/cs/
│   ├── agent/          # LangChain4j Agent + Tools（Function Calling）
│   ├── config/         # 配置类（CORS、SSE线程池、TraceId、LangChain4j）
│   ├── controller/     # REST 接口（ChatController）
│   ├── dto/            # 请求/响应 DTO
│   ├── exception/      # 全局异常处理
│   ├── model/          # Entity + Mapper
│   ├── ratelimit/      # Redis 滑动窗口限流 + in-memory 兜底
│   ├── service/        # 业务逻辑（ChatService、SessionService）
│   ├── matcher/        # FAQ 关键词匹配 + 情绪检测
│   └── CsApplication.java
├── src/main/resources/
│   ├── application.yml          # 公共配置
│   ├── application-dev.yml      # 开发环境覆盖
│   ├── application-prod.yml     # 生产环境覆盖
│   ├── logback-spring.xml      # 日志配置（dev/prod 双模式）
│   └── db/init.sql             # 数据库初始化
├── deploy/
│   ├── prometheus/prometheus.yml               # Prometheus scrape 配置
│   ├── loki/config.yaml                         # Loki 配置（tsdb 引擎，30天保留）
│   ├── promtail/promtail-config.yaml            # Promtail 配置（Docker SD + JSON 解析）
│   └── grafana/provisioning/
│       ├── datasources/loki.yaml                # Prometheus + Loki 数据源自动注册
│       └── dashboards/
│           ├── dashboard-provider.yaml          # Dashboard 加载器
│           ├── json/smart-cs-jvm.json          # JVM 监控 Dashboard（CPU/Heap/GC/HTTP/连接池）
│           └── json/smart-cs-logs.json         # 日志监控 Dashboard
├── Dockerfile              # 多阶段构建（eclipse-temurin:21-alpine）
├── docker-compose.yml      # 全栈编排（PG + Redis + App + Prometheus + Loki + Promtail + Grafana）
├── .env                    # 本地开发环境变量（不提交）
├── .env.prod               # 生产环境变量模板（不提交）
└── .env.example            # 环境变量示例（提交）
```

## 快速启动

### 方式一：Docker Compose（生产部署）

```bash
# 1. 克隆项目
git clone <repo-url> && cd smart-customer-service

# 2. 准备环境变量
cp .env.prod .env
vi .env
# 必填项：DB_PASSWORD、REDIS_PASSWORD、DEEPSEEK_API_KEY、GRAFANA_PASSWORD

# 3. 启动所有服务
docker compose up -d

# 4. 查看日志
docker compose logs -f app
```

**服务访问：**

| 服务 | 地址 | 说明 |
|------|------|------|
| 应用 API | `http://<host>:8080` | Spring Boot 服务 |
| 健康检查 | `http://<host>:8080/actuator/health` | Actuator |
| Prometheus 指标 | `http://<host>:8080/actuator/prometheus` | Micrometer |
| Grafana | `http://<host>:3000` | 监控面板（仅本机可访问） |

### 方式二：本地开发

**前提：** JDK 21、PostgreSQL、Redis 已就绪

```bash
# 1. 初始化数据库
psql -U root -d smart_cs -f src/main/resources/db/init.sql

# 2. 配置环境变量
cp .env.example .env
vi .env
# 填入 DEEPSEEK_API_KEY、DATABASE_URL、REDIS_HOST 等

# 3. 启动（默认 dev profile）
mvn spring-boot:run
```

本地开发 `.env` 示例：

```env
# 数据库连接（本地开发地址）
DATABASE_URL=jdbc:postgresql://10.211.55.20:5432/smart_cs
DB_USERNAME=root
DB_PASSWORD=your_local_db_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# DeepSeek
DEEPSEEK_API_KEY=sk-your-key-here

# CORS（开发环境）
CORS_ORIGIN=*

# Profile
SPRING_PROFILES_ACTIVE=dev
```

## API 文档

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/chat` | JSON 对话，返回完整回复 |
| `POST` | `/api/chat/stream` | SSE 流式对话 |
| `GET` | `/api/health` | 服务健康检查 |
| `GET` | `/actuator/health` | Spring Actuator 健康状态 |
| `GET` | `/actuator/metrics` | Micrometer 指标 |
| `GET` | `/actuator/prometheus` | Prometheus 格式指标 |

### 请求示例

```bash
# JSON 对话
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message": "我的订单什么时候发货？", "userId": "1"}'

# SSE 流式
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{"message": "查一下我的退款进度", "userId": "1"}'
```

## 环境变量

敏感信息通过 `.env` 文件注入，非敏感参数走 `application.yml` 默认值。

| 变量 | 必填 | 说明 | 默认值 |
|------|------|------|--------|
| `DEEPSEEK_API_KEY` | **是** | DeepSeek API 密钥 | 无（缺省直接启动失败） |
| `DB_PASSWORD` | **是** | PostgreSQL 密码 | — |
| `REDIS_PASSWORD` | **是** | Redis 密码 | — |
| `GRAFANA_PASSWORD` | **是** | Grafana 管理密码 | — |
| `CORS_ORIGIN` | 否 | 允许的跨域来源 | 空（生产建议设具体域名） |
| `DATABASE_URL` | 否 | PostgreSQL 连接串 | `jdbc:postgresql://10.211.55.20:5432/smart_cs` |
| `REDIS_HOST` | 否 | Redis 地址 | `localhost` |
| `SPRING_PROFILES_ACTIVE` | 否 | 激活的 profile | `dev` |

## 日志与监控

### 日志链路

App JSON stdout → Promtail（Docker SD 采集）→ Loki（tsdb 存储，30天保留）→ Grafana（可视化）

**日志监控 Dashboard（Smart CS 日志监控）：**
- ERROR & WARN 日志实时查看
- 日志级别分布趋势
- ERROR 速率（按 logger 分类）
- 按 TraceId 全链路追踪

### 指标监控链路

App `/actuator/prometheus` → Prometheus Server（15s 采集，30天保留）→ Grafana（可视化）

**JVM 监控 Dashboard（Smart CS JVM 监控）：**

| 面板 | 指标 |
|------|------|
| 概览 | CPU 使用率、Heap 已用、活跃线程、运行时间、连接池等待、打开 FD |
| JVM 内存 | Heap 内存使用趋势（G1 Eden/Old）、Non-Heap（Metaspace + Compressed Class） |
| GC & 线程 | GC 暂停时间、线程状态分布（RUNNABLE/WAITING/TIMED_WAITING） |
| HTTP 请求 | QPS（按 URI/Method/Status）、延迟百分位（p50/p95/p99/avg） |
| 连接池 | HikariCP 活跃/空闲/等待连接数、连接获取/创建耗时 |

## 架构说明

```
                    ┌──────────────┐
                    │   Client     │
                    └──────┬───────┘
                           │ HTTP/SSE
                    ┌──────▼───────┐
                    │  ChatController │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ RateLimit │ │  Chat    │ │ Session  │
        │ (Redis   │ │  Service │ │ Service  │
        │  ZSET)   │ │          │ │ (@Async) │
        └──────────┘ └────┬─────┘ └──────────┘
                         │
                    ┌────▼─────┐
                    │  Agent   │
                    │(LangChain│
                    │ 4j +    │
                    │ DeepSeek)│
                    └────┬─────┘
                         │ Function Calling
              ┌──────────┼──────────┐
              ▼          ▼          ▼
        ┌──────────┐ ┌────────┐ ┌────────┐
        │ Order    │ │ Refund │ │  User  │
        │ Tool     │ │  Tool  │ │  Tool  │
        └──────────┘ └────────┘ └────────┘
```

## 构建镜像

```bash
docker build -t smart-customer-service:latest .
```

多阶段构建：`eclipse-temurin:21-jdk-alpine`（编译）→ `eclipse-temurin:21-jre-alpine`（运行，非 root 用户）。
