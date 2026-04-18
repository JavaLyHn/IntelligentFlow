<div align="center">

# 🌊 IntelligentFlow

**企业级 AI Agent 工作流编排平台**

[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)](https://react.dev/)
[![Python](https://img.shields.io/badge/Python-3.11-3776AB?logo=python&logoColor=white)](https://www.python.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[English](#english) · [快速开始](#-快速开始) · [架构设计](#-架构设计) · [核心特性](#-核心特性) · [部署指南](#-部署指南)

</div>

---

## 📖 项目简介

IntelligentFlow 是一个企业级 AI Agent 工作流编排平台，支持用户通过**可视化拖拽**方式编排大模型节点、插件与逻辑控制流，无需编写代码即可构建复杂的 AI 应用。采用微服务架构，深度集成 LLM、超拟人音频合成、MCP 工具协议等能力，提供从工作流设计、调试到发布的完整生命周期管理。

### ✨ 核心亮点

- 🎨 **可视化流程编排** — 基于 ReactFlow 的拖拽式画布，支持 20+ 节点类型
- 🧠 **多 Agent 协作框架** — Supervisor / Pipeline / Swarm 三种协作拓扑
- 🔧 **MCP 协议集成** — 统一工具调用标准，连接池复用降低 API 开销
- 🔄 **LangGraph4j 有状态编排** — 检查点持久化、条件分支、循环迭代
- 📡 **全链路 SSE 流式响应** — LLM 生成内容实时逐字推送，首字延迟 < 200ms
- 🧩 **Skills 渐进式披露** — 按场景自动激活领域技能模板
- 🗂️ **可逆上下文压缩** — Redis + MinIO 外置大体量上下文，按需回读
- 🔍 **Deep Research Agent** — ReAct Loop + Plan/Search/Synthesize 三阶段流水线

---

## 🏗️ 架构设计

### 系统架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                        User Browser                                  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    Nginx (Static + Reverse Proxy)                     │
└──────────┬───────────────────────────────────────────┬───────────────┘
           │                                           │
           ▼                                           ▼
┌─────────────────────────┐              ┌──────────────────────────────┐
│  Console Frontend       │              │  Console Hub (:8080)         │
│  React + ReactFlow      │              │  Spring Boot 3.5             │
│  Zustand + Ant Design   │◄───SSE──────│  业务中台 / SSE 转发          │
│  Monaco Editor          │              │  用户 / 权限 / Bot 发布       │
└─────────────────────────┘              │  分布式锁 / 限流              │
                                         └──────────┬───────────────────┘
                                                    │ HTTP/SSE
                                                    ▼
                                         ┌──────────────────────────────┐
                                         │  Core Workflow Java (:7880)   │
                                         │  Spring AI + LangGraph4j      │
                                         │  DAG 执行引擎                 │
                                         │  Multi-Agent 协作             │
                                         │  MCP / Skills / Context       │
                                         └──────────┬───────────────────┘
                                                    │
                          ┌─────────────────────────┼────────────────────────┐
                          │                         │                        │
                          ▼                         ▼                        ▼
                   ┌────────────┐          ┌──────────────┐         ┌──────────────┐
                   │  LLM API   │          │  Link 工具    │         │  TTS 服务    │
                   │  OpenAI    │          │  MCP / OpenAPI│         │  讯飞/通义    │
                   │  DeepSeek  │          │  知识库 / RPA  │         │  DashScope   │
                   │  Qwen      │          └──────────────┘         └──────────────┘
                   └────────────┘
                          │
                          ▼
              ┌────────────────────────┐
              │  Infrastructure        │
              │  MySQL 8  Redis 7      │
              │  MinIO    Docker       │
              └────────────────────────┘
```

### 模块说明

| 模块 | 技术栈 | 说明 |
|------|--------|------|
| `console/frontend` | React 18 + TypeScript + Vite + ReactFlow + Zustand | 可视化流程编排前端 |
| `console/backend/hub` | Spring Boot 3.5 + MyBatis-Plus + Redisson + OkHttp | 业务中台（用户/权限/发布/SSE 转发） |
| `console/backend/toolkit` | Spring Boot 3.5 + MyBatis-Plus + SpringDoc | 工具集（工作流版本/知识库/模型管理） |
| `console/backend/commons` | Java 21 + MapStruct + EasyExcel | 公共实体/服务/AOP/分布式锁 |
| `core-workflow-java` | Spring Boot 3.5 + Spring AI 1.1 + LangGraph4j 1.8 | **核心工作流引擎** |
| `core/workflow` | Python + FastAPI + OpenTelemetry | Python 版工作流引擎 |
| `core/agent` | Python + FastAPI | Agent 服务 |
| `core/plugin/link` | Python + FastAPI | 工具调度中心 |
| `core/plugin/aitools` | Python + FastAPI | AI 工具服务（TTS/OCR/ISE） |
| `core/common` | Python + Pydantic + SQLAlchemy | 公共库（审计/认证/OTLP） |

---

## 🚀 核心特性

### 1. DAG 工作流执行引擎

基于 Kahn 算法的拓扑排序确定节点执行顺序，结合 DFS 检测循环依赖。通过 `CachedThreadPool + TTL + synchronized + AtomicInteger + CompletableFuture` 实现复杂的并行分支执行逻辑，确保 50+ 节点规模的工作流在多线程环境下的执行顺序与数据一致性。

```
┌──────┐    ┌─────────┐    ┌──────────┐
│ START├───►│   LLM   ├───►│   TTS    │
└──────┘    └────┬────┘    └──────────┘
                 │
          ┌──────┴──────┐
          ▼             ▼
    ┌──────────┐  ┌──────────┐
    │ Analyzer │  │ Searcher │    ← 并行分支
    └─────┬────┘  └─────┬────┘
          └──────┬──────┘
                 ▼
          ┌──────────┐    ┌──────┐
          │  Writer  ├───►│ END  │
          └──────────┘    └──────┘
```

### 2. LangGraph4j 有状态编排

设计 `GraphBuilder` 将 JSON 工作流配置自动转换为 LangGraph4j `StateGraph`，支持节点注册、边添加、入口/出口自动识别。通过 `RedisCheckpointSaver` 实现检查点持久化，支持工作流中断恢复。

- **WorkflowAgentState** — 工作流全局状态（变量池 + 节点结果 + Token 消耗）
- **NodeExecutorAdapter** — 将 `NodeExecutor` 适配为 LangGraph4j 的 `AsyncNodeAction`
- **NodeExecutorFactory** — 策略模式，按 `NodeTypeEnum` 动态分发节点执行器

### 3. 多 Agent 协作框架

基于 LangGraph4j StateGraph 编排三种协作拓扑：

| 模式 | 流程 | 适用场景 |
|------|------|---------|
| **Supervisor** | 主管拆解任务 → 分派专家 → 汇总结果 | 复杂研究任务 |
| **Pipeline** | Agent A → Agent B → Agent C | 串行流水线处理 |
| **Swarm** | Worker 并行探索 → Judge 综合评判 | 多方向并行研究 |

核心组件：
- `AgentRole` — 角色能力边界定义（Supervisor/Searcher/Analyzer/Writer/Judge）
- `HandoffProtocol` — Agent 间任务交接协议（DELEGATE/RETURN/BROADCAST/AGGREGATE）
- `CollaborationGraph` — 基于 LangGraph4j StateGraph 编排协作拓扑

### 4. MCP 协议统一工具调用

基于 MCP（Model Context Protocol）协议统一工具调用标准，拆分 Link、AITools 等子模块：

- `McpSseClient` — MCP SSE 客户端，支持工具发现与调用
- `McpConnectionPool` — 连接池复用 TCP 连接，降低外部 API 调用开销
- `McpToolExecutor` — 工具执行器，支持参数校验与结果解析

### 5. Skills 渐进式披露机制

实现 Anthropic Claude Code 的 Skills 扩展能力，通过渐进式披露（Progressive Disclosure）按场景自动激活领域技能模板：

- `SkillLoader` — 从资源文件加载技能定义
- `SkillRegistry` — 技能注册中心
- `SkillMatcher` — 三级匹配策略（精确匹配 → Token 匹配 → 字符匹配）
- `SkillExecutor` — 技能执行器

预置技能：Code Review、Data Analysis、PDF Processing、Podcast Writing

### 6. 全链路 SSE 流式响应

基于 SSE 技术设计全链路流式响应机制，支持 LLM 生成内容的实时逐字推送：

- **DualQueueManager** — 双队列架构（接收队列 + 发送队列），解耦生产与消费，保证消息有序
- **Callback Handler** — 节点状态变更、中间结果输出及 Token 消耗统计的实时反馈
- **SseEmitter** — Hub 层 SSE 转发，超时 5 分钟 + 心跳保活

### 7. Deep Research Agent

基于 ReAct Loop 作为主控流程，构建 Plan → Search → Synthesize 三阶段任务流水线：

- `PlanningAgent` — 生成结构化研究计划，持久化为 `plan.md` 文件
- `DeepResearchAgent` — ReAct Loop 自主推理，按步骤拆解与执行
- `LlmPlanExecutor` / `LlmSearchExecutor` / `LlmSynthesizeExecutor` — 三阶段 LLM 驱动执行器

### 8. 可逆上下文压缩

设计 Context Manager 实现可逆上下文压缩机制：

- `ContextStorageService` — Redis + MinIO 双层存储（热数据 Redis + 冷数据 MinIO）
- `ContextManager` — 压缩/解压/片段检索，对话上下文仅保留"路径 + 摘要"
- 支持按需回读与片段级检索，控制上下文规模

### 9. 智能分块 + 并发 TTS

针对文本长度限制问题，设计智能分块算法结合多线程优化长文本 TTS 分片并发处理：

- `SmartTextChunker` — 语义边界感知的智能分块（段落 → 句子 → 字符三级回退）
- `ConcurrentTtsProcessor` — 并行分片处理，CompletableFuture 线程池调度
- `WavAudioMerger` — WAV 音频文件合并

### 10. Spring AI 深度集成

深度集成 Spring AI 框架，通过 `OpenAiChatModel` 与 `OpenAiApi` 标准接口，实现对 OpenAI、DeepSeek、Qwen 的统一抽象与调用：

- 适配器模式统一不同 LLM 提供商
- 兼容模式重写 `baseUrl` 和 `completionsPath`，支持非标准 OpenAI 接口路由
- Function Calling 支持，工具自动注册与调用

---

## 📂 项目结构

```
IntelligentFlow/
├── console/                            # 控制台
│   ├── backend/                        # Java 后端
│   │   ├── hub/                        # 业务中台 (:8080)
│   │   ├── toolkit/                    # 工具集
│   │   └── commons/                    # 公共模块
│   └── frontend/                       # React 前端
│       └── src/
│           ├── pages/                  # 页面
│           │   ├── workflow/           # 工作流编排页（核心）
│           │   ├── chat-page/          # 对话页
│           │   └── space/              # 空间管理
│           └── components/
│               └── workflow/           # 工作流组件
│                   ├── nodes/          # 20+ 可视化节点组件
│                   ├── edges/          # 边组件
│                   ├── store/          # Zustand 状态管理
│                   └── hooks/          # 自定义 Hooks
├── core-workflow-java/                 # Java 工作流引擎 (:7880)
│   └── src/main/java/.../workflow/
│       ├── engine/
│       │   ├── node/                   # 节点执行器（策略模式）
│       │   │   └── impl/
│       │   │       ├── llm/            # LLM 大模型节点
│       │   │       ├── plugin/         # 插件节点
│       │   │       ├── skill/          # Skills 技能节点
│       │   │       ├── research/       # 深度研究节点
│       │   │       └── collaboration/  # 多 Agent 协作节点
│       │   ├── langgraph/              # LangGraph4j 集成
│       │   ├── agent/
│       │   │   ├── research/           # Deep Research Agent
│       │   │   └── collaboration/      # Multi-Agent 协作
│       │   ├── skills/                 # Skills 渐进式披露
│       │   ├── context/                # 上下文管理
│       │   └── integration/
│       │       ├── mcp/                # MCP 协议客户端
│       │       ├── model/              # LLM 统一适配层
│       │       └── plugins/tts/        # TTS 语音合成
│       ├── domain/                     # 领域模型
│       ├── constants/                  # 常量与枚举
│       └── controller/                 # REST API
├── core/                               # Python 核心服务
│   ├── workflow/                       # Python 工作流引擎
│   ├── agent/                          # Agent 服务
│   ├── plugin/
│   │   ├── link/                       # 工具调度中心
│   │   └── aitools/                    # AI 工具服务
│   └── common/                         # 公共库
├── docker/                             # Docker 部署
│   └── IntelligentFlow/
│       ├── docker-compose.yaml
│       ├── Dockerfile.backend
│       ├── Dockerfile.workflow
│       ├── Dockerfile.frontend
│       └── mysql/                      # 数据库初始化脚本
└── scripts/                            # 辅助脚本
```

---

## 🛠️ 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 运行时 |
| Spring Boot | 3.5.4 | 应用框架 |
| Spring AI | 1.1.2 | LLM 集成 |
| LangGraph4j | 1.8.11 | 有状态工作流编排 |
| MyBatis-Plus | 3.5.7 | ORM |
| Redisson | 3.30 | 分布式锁 / 缓存 |
| OkHttp | 4.12.0 | HTTP 客户端 |
| MinIO | 8.5.7 | 对象存储 |
| DashScope SDK | 2.22.3 | 阿里云通义千问 TTS |
| Guava | 33.5.0 | 超时控制 / Rate Limiter |
| TTL | 2.14.5 | 线程池上下文传递 |
| Fastjson2 | 2.0.51 | JSON 序列化 |
| SpringDoc | 2.8 | API 文档 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| React | 18.2 | UI 框架 |
| TypeScript | 5.9 | 类型安全 |
| Vite | 5.4 | 构建工具 |
| ReactFlow | 11.11 | 可视化流程编辑器 |
| Zustand | 5.0 | 状态管理 |
| Ant Design | 5.19 | UI 组件库 |
| Monaco Editor | 0.52 | 代码编辑器 |
| Tailwind CSS | 3.3 | 样式框架 |
| i18next | 23.10 | 国际化（中/英） |
| ECharts | 5.4 | 图表 |

### 基础设施

| 技术 | 版本 | 用途 |
|------|------|------|
| MySQL | 8.0 | 关系型数据库 |
| Redis | 7 | 缓存 / 分布式锁 / 消息队列 |
| MinIO | RELEASE.2025-07 | 对象存储 |
| Docker Compose | 3.8 | 容器编排 |
| Nginx | 1.15 | 反向代理 / 静态资源 |

---

## 🎯 快速开始

### 环境要求

- JDK 21+
- Node.js 18+
- MySQL 8.0+
- Redis 7+
- MinIO（可选，用于对象存储）
- Docker & Docker Compose（推荐）

### 方式一：Docker Compose 一键部署（推荐）

```bash
# 1. 克隆项目
git clone https://github.com/your-username/IntelligentFlow.git
cd IntelligentFlow

# 2. 配置环境变量
cd docker/IntelligentFlow
cp .env.example .env
# 编辑 .env 文件，配置 MySQL 密码、MinIO 凭证、DeepSeek API Key 等

# 3. 一键启动
docker-compose up -d

# 4. 访问应用
# 前端：http://localhost:3000
# 后端 API：http://localhost:8080
# 工作流引擎：http://localhost:7880
# MinIO 控制台：http://localhost:9001
```

### 方式二：本地开发

#### 1. 启动基础设施

```bash
# 仅启动 MySQL + Redis + MinIO
cd docker/IntelligentFlow
docker-compose up -d mysql redis minio
```

#### 2. 启动后端

```bash
# 工作流引擎
cd core-workflow-java
mvn spring-boot:run

# 业务中台
cd console/backend/hub
mvn spring-boot:run
```

#### 3. 启动前端

```bash
cd console/frontend
pnpm install
pnpm dev
```

#### 4. 访问应用

打开浏览器访问 `http://localhost:5173`

---

## 📡 API 概览

### 工作流引擎 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/workflow/chat/stream` | 工作流对话（SSE 流式） |
| POST | `/api/v1/workflow/debug` | 工作流调试 |
| POST | `/api/v1/workflow/node/debug` | 单节点调试 |
| GET | `/api/v1/workflow/node-template` | 获取节点模板列表 |

### 业务中台 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/workflow/chat/stream` | 工作流对话（Hub 转发 SSE） |
| GET | `/workflow/list` | 工作流列表 |
| POST | `/workflow/save` | 保存工作流 |
| POST | `/bot/publish` | Bot 发布 |
| GET | `/model/list` | 模型列表 |

---

## 🧩 工作流节点类型

| 节点类型 | idType | 说明 |
|---------|--------|------|
| 开始节点 | `node-start` | 工作流入口，定义输入变量 |
| 结束节点 | `node-end` | 工作流出口，输出结果 |
| 大模型节点 | `spark-llm` | LLM 调用，含系统/用户提示词 |
| Agent 节点 | `agent` | 智能体，支持策略/工具/MCP |
| 多Agent协作 | `multi-agent` | Supervisor/Pipeline/Swarm 协作 |
| 知识库节点 | `knowledge-base` | 知识库检索 |
| 代码节点 | `ifly-code` | 自定义代码执行 |
| 条件分支 | `if-else` | 条件判断分支 |
| 决策节点 | `decision-making` | 意图识别与分支 |
| 迭代器 | `iteration` | 循环迭代 |
| 插件节点 | `plugin` | 外部工具/插件调用 |
| 数据库节点 | `database` | 数据库操作 |
| 消息节点 | `message` | 消息输出 |
| Skills 节点 | `skill` | 技能自动匹配与执行 |
| 深度研究 | `research` | Deep Research Agent |
| 子流程 | `flow` | 嵌套工作流调用 |
| RPA 节点 | `rpa` | RPA 自动化 |

---

## 🎨 设计模式

本项目在核心模块中广泛运用了经典设计模式：

| 模式 | 应用场景 | 关键类 |
|------|---------|--------|
| **策略模式** | 不同节点类型差异化执行 | `NodeExecutor` + `NodeExecutorFactory` |
| **模板方法** | 节点执行标准流程（重试/超时/回调） | `AbstractNodeExecutor.execute()` |
| **适配器模式** | NodeExecutor → LangGraph4j AsyncNodeAction | `NodeExecutorAdapter` |
| **工厂模式** | 按配置动态创建节点执行器 | `NodeExecutorFactory` |
| **观察者模式** | 节点状态变更实时通知 | `WorkflowMsgCallback` |
| **装饰器模式** | 分布式锁 + 限流 AOP | `@DistributedLock` / `@RateLimit` |
| **责任链模式** | Spring 事件驱动节点调度 | `NodeExecutor` 链式执行 |

---

## 🧪 测试

### 后端测试

```bash
cd core-workflow-java

# 运行全部测试
mvn test

# 运行指定模块测试
mvn test -Dtest="MultiAgentCollaborationTest"
mvn test -Dtest="DeepResearchAgentTest"
mvn test -Dtest="SkillsTest"
mvn test -Dtest="McpToolExecutorTest"
mvn test -Dtest="ContextManagerTest"
```

### 测试覆盖

| 模块 | 测试类 | 测试用例数 |
|------|--------|-----------|
| Multi-Agent 协作 | `MultiAgentCollaborationTest` | 23 |
| Deep Research | `DeepResearchAgentTest` | 12 |
| Skills | `SkillsTest` | 8 |
| MCP | `McpToolExecutorTest` | 10 |
| Context Manager | `ContextManagerTest` | 9 |
| GraphBuilder | `GraphBuilderTest` | 6 |

---

## 🚢 部署指南

### Docker Compose 生产部署

```bash
# 1. 构建并启动所有服务
docker-compose up -d --build

# 2. 查看服务状态
docker-compose ps

# 3. 查看日志
docker-compose logs -f core-workflow-java
docker-compose logs -f console-hub

# 4. 停止所有服务
docker-compose down

# 5. 停止并清除数据卷
docker-compose down -v
```

### 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| Console Frontend | 3000 | 前端页面 |
| Console Hub | 8080 | 业务中台 API |
| Core Workflow Java | 7880 | 工作流引擎 API |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |
| MinIO API | 9000 | 对象存储 API |
| MinIO Console | 9001 | 对象存储控制台 |

---

## 🤝 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 提交 Pull Request

### 开发规范

- Java 代码遵循阿里巴巴 Java 开发手册
- 前端代码使用 ESLint + Prettier 格式化
- 提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范
- 新功能需附带单元测试

---

## 📄 License

本项目基于 [MIT License](LICENSE) 开源。

---

<div align="center">

**IntelligentFlow** © 2024 - Present

</div>
