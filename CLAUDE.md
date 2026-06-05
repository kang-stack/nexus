# CLAUDE.md

**仓库根目录：`cat-customer-agent`**（Maven artifactId；磁盘目录若仍为 `icc-customer-agent`，请手动重命名文件夹。历史名 `nexus` 已废弃。）

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Backend (Java / Spring Boot)

```bash
# Chitchat FastPath P99 (server-side, no network)
mvn test -pl user-module/user-knowledge -Dtest=ChitchatFastPathPerformanceTest

# Portal stack (AI messaging)
mvn -pl user-module/user-message -am test

# HTTP benchmark (server running, user JWT)
# PowerShell: $env:ICC_TOKEN="..."; ./scripts/benchmark/chitchat-fastpath.ps1

# Build the entire project
mvn clean package

# Run the application (dev profile, port 8899)
cd bootstrap && mvn spring-boot:run

# Build a specific module
mvn -pl user-module/user-common clean install

# Compile full stack
mvn -pl bootstrap -am compile
```

Unit/integration tests live under `user-message`, `user-knowledge`, etc. Run `mvn test` from the repo root or `mvn -pl user-module/user-message -am test` for the messaging stack.

Module naming map (external docs): `C:/workspace/workrecord/cat-customer-agent/docs/operations/MODULE_ALIAS.md` (`icc-*` / `cs-*` → `user-*`; HTTP paths stay `/api/cs/v1/*`).

The entry point is `bootstrap/src/main/java/cn/cloudcat/customer/CustomerAgentApplication.java`. It scans `cn.cloudcat` and starts on port 8899.

Dev config: `bootstrap/src/main/resources/application-dev.yml` (committed; active with profile `dev`). MySQL `icc_cs`, Redis, Elasticsearch, MinIO, JWT, models.

### Frontend (Vue 3 / JavaScript)

```bash
cd frontend
npm install
npm run dev          # starts at localhost:5173, proxies /api → localhost:8899
npm run build        # Vite production build → dist/
npm run preview      # preview production build locally
npm test             # vitest unit tests
```

**本项目前端禁止使用 TypeScript**，源码均为 `.js` + Vue SFC（`<script setup>`）。勿添加 `vue-tsc` 或 `tsconfig.json`。

Environment variables are in `.env.development` and `.env.production` (`VITE_API_BASE_URL`, `VITE_APP_TITLE`). The Vite proxy target can be overridden via `VITE_API_TARGET` (defaults to `http://localhost:8899`). No linter or test framework is configured.

### Database

Flyway migrates the `icc_cs` MySQL database automatically on startup. Migration files are in `bootstrap/src/main/resources/db/migration/`:

- `V1__init_schema.sql` — full schema DDL + seed data
- `V2__agent_human_support.sql` — human-agent handoff columns
- `V3__mobile_chat_optimization.sql` — mobile chat features
- `V4__acc_user_kb_intent_seed.sql` — knowledge base intent seed data
- `V5__quick_service_rules.sql` — quick service rule engine tables
- Through `V22__acc_user_knowledge_document_v2_scene.sql` — `acc_user_knowledge_document.scene_code` (V2 kb_document alias)
- `V21__message_review_action_template.sql` — message review queue + action templates
- `V20__refund_dmn_and_scene_paths.sql` — REFUND DMN, independent RECHARGE/DEVICE DMN paths
- `V6__structured_conditions.sql` — structured rule conditions
- `V7__rule_response_type.sql` — rule response types (template vs diagnosis)
- `V8__ten_intents_seed.sql` — 10-intent seed data
- `V9__intent_keyword_compound_audit.sql` — compound-intent audit (`intent_keyword_rule` removed in V39, merged into `acc_user_kb_faq_alias`)

## Architecture

A multi-module Maven project (Java 21, Spring Boot 3.5.6) for an AI-powered portable-WiFi customer service platform.

### Module layout

```
cat-customer-agent (root pom)
├── bootstrap              # Spring Boot 入口
├── user-module/           # 用户侧业务（包名 cn.cloudcat.user.*）
│   ├── user-common        # 共享内核
│   ├── user-knowledge     # kb_*、寒暄、分支
│   ├── user-session       # UserConversation / UserMessage
│   ├── user-diagnosis     # DMN 诊断
│   ├── user-agent         # ChatAgent Skill 链
│   ├── user-message       # AI 客服消息（对话编排 + RAG）
│   ├── user-portal        # 运营门户（规则/文档/设备）
│   └── …
├── cs-module/             # 独立客服业务层（包名 cn.cloudcat.cs.*）
│   └── cs-assistant
└── ai-module/             # AI 基座（RAG 包名 cn.cloudcat.intelligent.rag.*）
    ├── ai-spi
    ├── ai-llm-gateway
    ├── ai-intent
    └── ai-rag
```

### user-common — Shared foundation

- **Security**: Stateless JWT auth via `SecurityConfig` + `JwtAuthenticationFilter`. All requests require authentication except `/api/portal/auth/**`, `/api/admin/auth/**`, `/druid/**`, `/error`. CSRF disabled, CORS allows all origins with credentials. `SecurityContextHolder` uses `MODE_INHERITABLETHREADLOCAL` so `@Async` methods inherit the security context. A `FilterRegistrationBean` disables auto-registration of `JwtAuthenticationFilter` to prevent double-filtering that would lose the `SecurityContext` during async dispatch.
- **Roles**: `SUPER_ADMIN`, `ADMIN`, `OPERATOR`, `USER`. `/api/admin/**` requires admin roles; `/api/portal/**` requires `USER` or `ADMIN`.
- **JWT tokens**: User tokens valid 24h, admin tokens valid 8h. Claims: `sub` = userId, `role` = role string. The filter converts role → `ROLE_` prefix for Spring Security `GrantedAuthority`.
- **Password encoding**: `BCryptPasswordEncoder`.
- **Async**: `AsyncConfig` enables `@EnableAsync` + `@EnableScheduling`. Defines `eventListenerExecutor`: core=4, max=8, queue=50, `CallerRunsPolicy`, 30s graceful shutdown.
- **Redisson** (`RedissonClient`): Used across modules for more than just locks:
  - `DistributeLockAspect` — AOP aspect for `@DistributeLock` annotation. Uses `RLock` with configurable wait/expire times. Lock key format: `scene + "#" + key`, supports SpEL key expressions.
  - `AgentTaskManager` — `RBucket` for task ownership (TTL-based, key `icc:task:{conversationId}`), `RTopic` for distributed stop-signal pub/sub (`icc:task:stop`).
  - `HumanChatSessionManager` — `RScoredSortedSet` for customer queue (`icc:human:queue`), `RBucket` for session data (`icc:human:session:{conversationId}`, 2-hour TTL).
- **Infrastructure**: Snowflake ID generator, MyBatis-Plus with `MetaObjectHandler` for auto-filling timestamps.
- **Document splitters**: `MarkdownHeaderParentTextSplitter`, `MarkdownHeaderBrotherTextSplitter` (LangChain4j-based) for knowledge base chunking.
- **Knowledge (V2)**: `KnowledgeIntent`, `KbBranch`, `KbReplyScript`, `KbFaqAlias`, chitchat/session templates — DMN + `acc_user_kb_branch` matching (legacy `kb_rule*` / Playbook dropped in Flyway V11).
- **Satisfaction**: `acc_user_satisfaction` + satisfaction card messages on `acc_user_message` (legacy `chat_satisfaction` dropped in V15).

### user-portal — Operations portal (ex user-admin)

- **Document pipeline**: Upload → MinIO → MinerU conversion → splitting → ES embedding (`DocumentProcessService`, `FileProcessServiceFactory`).
- **Controllers**: `PortalDocumentController`, `DeviceController`, `PortalAccountController`, `PortalAuthController`, `KbConfigPortalController`.
- **XXL-Job**: `DocumentCompensationJob`.
- **Java 包**: `cn.cloudcat.user.portal.*`；HTTP `/api/cs/v1/admin/*`（路径未改，兼容前端）。

### user-message — AI customer messaging (ex user-portal)

**Java 包结构（`cn.cloudcat.user.message.*`）**：

| 包 | 职责 |
|----|------|
| `user.message.chat` | `UserChatController`、`UserChatTurnOrchestrator`、`MultiMessageOrchestrator` |
| `user.message.agent.skill` | 各 `ChatSkill` 实现 |
| `user.message.diagnosis` | `DiagnosisPipeline` |
| `user.message.policy` / `user.message.utterance` | Turn Policy、话语理解 |
| `user.message.rag` | `RagApplicationService` |
| `user.message.ai` | LLM 路由、意图识别 |
| `user.message.auth` | `MessageAuthController`（`/api/cs/v1/auth`） |

- **HTTP API (V2)**: `UserChatController` at `/api/cs/v1/chat/*` — primary user chat surface.
  - `POST /sessions` — create conversation (optional `{ "problem" }` for quick-service entry)
  - `POST /sessions/{id}/stream` — SSE chat stream body `{ text, quickServiceCode? }` (primary)
  - `GET /sessions/{id}/stream?message=` — SSE legacy query param
  - `POST /sessions/{id}/stop` — cancel in-flight AI task
  - `GET /sessions/{id}/messages` — message history (`acc_user_message`)
  - `GET /sessions/{id}` — session status + idle timer
  - `POST /sessions/{id}/satisfaction` — satisfaction submit
  - `POST /sessions/{id}/actions` — device/system actions (incl. `TRANSFER_TO_HUMAN`)
  - `POST /sessions/{id}/human` — explicit human transfer
  - Legacy `/api/portal/chat/*` removed (Phase 5 hard cut).
- **V2 modules**: `user-session` (state machine, tables `acc_user_conversation`/`acc_user_message`), `user-diagnosis` (Camunda DMN), `user-agent` (ChatAgent skill chain). Orchestration: `UserChatTurnOrchestrator` + `UserRagFallbackService` → `RagApplicationService` (LangChain4j ES RAG → `acc_user_message`).
- **ChatAgent Skill chain (order)**: `DialoguePrepare(8)` → `Chitchat(10)` → `Emotion(11)` → `Synonym(12)` → `Intent(13)` → `Facts(20)` → `RuleDiagnosis(21)` → `KnowledgeBranch(22)` → `RagFallback(30)` → post-phase `Closing(40+)`. Utterance/Turn Policy + optional LLM policy in prepare; compound intents ≤2 per turn.
- **Ops API alias**: `/api/cs/admin/*` mirrors `/api/admin/*` for messages + kb-config (OPERATOR role).
- **Agent layer**: `ChatAgent` skill chain only (`MobileCustomerServiceAgent` / Playbook removed).
- **RAG pipeline** (`ai-rag`): `WifiQueryRouter` → `WifiQueryTransformer` → `WifiElasticsearchContentRetriever` → `ProgressAwareContentAggregator`. Reranking via two-stage fallback: ONNX local BGE first, then Qwen3 DashScope (`RerankerRoutingService`). Orchestration + streaming: `RagApplicationService` in `user-message`.
- **SSE events**: `AgentResponse` in `user-common` (`cn.cloudcat.user.common.sse`).
- **Multi-model routing**: `ModelRoutingService` / `FastModelRoutingService` — 4-tier routing (DeepSeek → Qwen → OpenAI → Ollama) with priority/fallback. Fast models handle intent recognition and query rewrite. Configured via `icc.model.fast` in YAML.
- **Human handoff**: Dual-channel architecture — customers receive agent messages via SSE (`/api/portal/human/stream`, `Flux<String>`), agents connect via raw Spring WebSocket at `/ws/human/agent` (`HumanChatWebSocketHandler`, `TextWebSocketHandler`). `HumanChatSessionManager` manages the customer queue and session state in Redis. A static bridge in `HumanChatController.deliverToCustomer()` routes WebSocket agent messages to the customer's SSE sink.
- **Chat persistence**: `acc_user_conversation` / `acc_user_message` (V2). `UserChatMemoryStore` implements LangChain4j `ChatMemoryStore` for RAG context. Legacy `chat_*` tables dropped in Flyway V15.
- **Prompt templates** in `user-module/user-message/src/main/resources/prompts/`:
  - `intent-recognition-multi-prompt.txt`
  - `wifi-account-plan-prompt.txt`
  - `wifi-after-sales-prompt.txt`
  - `wifi-chat-general-prompt.txt`
  - `wifi-device-guidance-prompt.txt`
  - `wifi-human-service-prompt.txt`
  - `wifi-network-troubleshooting-prompt.txt`

### frontend — Vue 3 SPA

- **Routes**:
  - `/login` — user login
  - `/chat` — customer chat (`views/Chat.vue`, mobile-first layout)
  - `/admin/login` — admin login
  - `/admin/dashboard`, `/admin/documents`, `/admin/rules`, `/admin/kb-config`, `/admin/messages`, `/admin/turn-traces`, `/admin/message-reviews`, `/admin/devices`, `/admin/accounts` — admin views
- **State**: Pinia stores（`auth.js`, `chat.js`, `toast.js`）。Auth store holds JWT token and role info; router guards redirect based on auth state and role.
- **API layer**:
  - `src/api/chat.js` — user chat REST + SSE (`chatApi`, `streamChat`) → `/api/cs/chat/*`
  - `src/api/index.js` — Axios instance with JWT interceptor; `authApi`, `adminApi`
- **Components**: `src/components/chat/*` — navbar, message list, input, quick-service bar, etc.
- **Vite proxy**: The dev server proxy at `/api` → `localhost:8899` injects `Accept: text/event-stream` on `/stream` requests and disables response buffering for SSE (`no-cache`, `keep-alive`, `X-Accel-Buffering: no`).
- **Key dependencies**: Vue 3 (Composition API), Pinia, Vue Router, Axios, Marked (Markdown rendering).

### Key external dependencies

| Service | Purpose |
|---------|---------|
| MySQL | Primary database (Druid connection pool) |
| Redis | Caching + Redisson distributed locks, pub/sub, sorted sets |
| Elasticsearch | Vector/knowledge base search + embeddings |
| MinIO | Document file storage (bucket: icc-documents) |
| DeepSeek / Qwen / OpenAI API | LLM backends for chat and text processing |
| MinerU API | Document format conversion (PDF/Word/Excel → Markdown) |
| XXL-Job | Distributed scheduled tasks (optional in dev) |

### Configuration pattern

- `application.yml` — shared settings, port 8899, `chat.progress.enabled` toggle for SSE progress updates
- `application-dev.yml` — local dev (committed). Production uses `application-pro.yml` with env vars.
- `application-pro.yml` — production overrides, credentials via env vars, XXL-Job enabled, log level `cn.cloudcat: INFO`, LangChain4j logging off.
- `icc.model.fast` — task-to-model mapping for lightweight tasks (intent-recognition, query-rewrite) using faster/cheaper models.
- `icc.reranker` — two-tier reranker config: ONNX BGE local model + Qwen3 DashScope cloud fallback.
- Environment variables for secrets: `DEEPSEEK_API_KEY`, `QWEN_API_KEY`, `OPENAI_API_KEY`, `ES_USERNAME`, `ES_PASSWORD`
- No logback XML file exists — logging is configured entirely through Spring Boot's YAML `logging.level` keys.
- Project settings live in `.claude/settings.local.json`. Design docs live outside the repo at `C:/workspace/workrecord/cat-customer-agent/docs/`.
