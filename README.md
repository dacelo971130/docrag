# docrag — RAG 文件問答系統（方案 A 後端）

依 [RAG 方案 A 技術規格 v1.2](docs/SPEC.md) 實作的向量文件知識庫**全後端**：上傳文件 → 向量化儲存 → 自然語言提問 → 檢索相關片段 → LLM 串流生成回答。後端為核心、展現工程深度；前端刻意保持輕薄（本倉庫不含前端）。

```
使用者 ──上傳/提問──▶ Spring Boot 後端 ──JDBC──▶ PostgreSQL 18 + pgvector
                          │  ├─HTTP──▶ Embedding Provider (OpenAI / Ollama)
                          │  └─HTTP──▶ LLM Provider (OpenAI / Ollama / Claude)
                          └─SSE 串流──▶ 前端逐字顯示
```

## 技術棧

| 層 | 技術 | 版本 |
|----|------|------|
| 語言 / 執行期 | Java | 17 |
| 框架 | Spring Boot | 4.1.0 |
| AI 整合層 | Spring AI（adapter 層，ADR-007） | 2.0.0 |
| 資料 / 向量 | PostgreSQL + pgvector | 16 / HNSW |
| 快取 / 限流 | Caffeine（單機）· Redis + Bucket4j（分散式） | Bucket4j 8.19 |
| 文件解析 | Apache PDFBox | 3.0.x |
| 串流 | Spring MVC + `SseEmitter`（ADR-006） | — |

> 規格表原列 Java 21；本專案實際以 **Java 17** 建置（Spring Boot 4.1 baseline 即為 Java 17，與本機環境一致、零額外設定）。

## 核心設計

### 三介面隔離（DIP，ADR-005）

所有外部能力以領域介面抽象，業務邏輯不直接耦合具體 provider；切換 OpenAI ↔ Ollama 僅需改設定。

| 介面 | 職責 | 實作 |
|------|------|------|
| `EmbeddingProvider` | 文字向量化 | `SpringAiEmbeddingProvider` →（裝飾）`CachingEmbeddingProvider` |
| `VectorStore` | 向量寫入 / 檢索 | `PgVectorStore`（自有 pgvector schema，非 Spring AI 內建表） |
| `LlmProvider` | 答案生成（串流 / 非串流） | `SpringAiLlmProvider` |
| `DocumentProcessor` | 解析→分塊→embedding→儲存 | `DefaultDocumentProcessor` |

Spring AI 只存在於 adapter 層（`provider/`），領域層（`domain/`）保留自有介面（ADR-007）。Provider 以設定挑選（`app.embedding.bean` / `app.llm.bean`），由 `ProviderConfig` 以 bean 名稱注入對應的 Spring AI 模型。

### Grounding 四層防線（ADR-010）

回答鎖在知識庫內、抑制幻覺：

1. **檢索門檻** `minSimilarity`：低於相似度的片段在 SQL 即丟棄（`PgVectorStore`）。
2. **Scope 過濾** `documentScope`：限定檢索範圍（`PgVectorStore`）。
3. **Prompt 約束**：指示模型僅依片段作答、資訊不足即明說找不到（`PromptBuilder`）。
4. **後處理引用驗證**：檢查回答標註的 `[k]` 引用是否在檢索範圍內（`GroundingService`）。

> 防線 1+2 控制 LLM 看得到什麼；防線 3+4 控制怎麼用。檢索全數低於門檻時直接回固定訊息、**不呼叫 LLM**（省成本又防幻覺）。

## 快速開始

### 1. 啟動相依服務（PostgreSQL + Redis）

```bash
docker compose up -d
```

（無 Docker 也可：自備 PostgreSQL 18 並安裝 pgvector 擴充；Redis 僅 `redis` profile 需要。）

### 2. 設定環境變數

```bash
cp .env.example .env      # 填入 OPENAI_API_KEY 等
# 或直接以環境變數提供，見 .env.example
```

最少需要：`OPENAI_API_KEY`（用雲端生成）。資料庫預設連 `localhost:5432/docrag`（docker compose 已備妥）。

### 3. 啟動後端

需求：**JDK 17**（Spring Boot 4.1 baseline）。你的預設 `mvn` 已是 Java 17，直接執行即可，無需設定 `JAVA_HOME` 或 toolchain。

> ⚠️ 啟動前 **PostgreSQL 必須在線**（見步驟 1）。app 啟動時 Flyway 會連 `localhost:5432` 建表；連不到資料庫就會啟動失敗（這通常就是「跑不動」的原因）。

```bash
mvn spring-boot:run
```

只想打包驗證（不需資料庫）或以 jar 執行：

```bash
mvn clean package
java -jar target/docrag-0.1.0.jar
```

啟動時 Flyway 自動建表（embedding 維度由 `EMBEDDING_DIMENSION` 注入，預設 1536）。

### 4. 試打 API

### CMD
```bash
# 驗證 embedding API（Spring Boot 之後打這個端點）
curl http://localhost:11434/api/embeddings \
  -d '{"model": "nomic-embed-text", "prompt": "測試一段文字"}'

# 上傳文件（非同步處理，回 202 + documentId）
curl -F "file=@your.pdf" http://localhost:8080/api/documents

# 查狀態（READY 後即可提問）
curl http://localhost:8080/api/documents

# 串流問答（SSE）
curl -N -X POST http://localhost:8080/api/query/stream \
  -H "Content-Type: application/json" \
  -d '{"question":"這份文件在講什麼？"}'

# 限定單一文件範圍（防線 2）
curl -N -X POST http://localhost:8080/api/query/stream \
  -H "Content-Type: application/json" \
  -d '{"question":"...","documentScope":"<documentId>"}'

# 查詢歷史
curl http://localhost:8080/api/query/history

# 刪除文件（chunks 由 FK 串接刪除）
curl -X DELETE http://localhost:8080/api/documents/<documentId>
```

PowerShell
```powershell
# 驗證 embedding API
Invoke-RestMethod -Uri http://localhost:11434/api/embeddings -Method Post `
  -Body '{"model": "nomic-embed-text", "prompt": "測試一段文字"}' -ContentType "application/json"

# 上傳文件（202 + documentId）
curl.exe -F "file=@your.pdf" http://localhost:8080/api/documents

# 查狀態（READY 後即可提問）
Invoke-RestMethod -Uri http://localhost:8080/api/documents

# 串流問答（SSE）
curl.exe -N -X POST http://localhost:8080/api/query/stream `
  -H "Content-Type: application/json" `
  -d '{\"question\":\"這份文件在講什麼？\"}'

# 限定單一文件範圍（防線 2）
curl.exe -N -X POST http://localhost:8080/api/query/stream `
  -H "Content-Type: application/json" `
  -d '{\"question\":\"...\",\"documentScope\":\"<documentId>\"}'

# 查詢歷史
Invoke-RestMethod -Uri http://localhost:8080/api/query/history

# 刪除文件
Invoke-RestMethod -Uri http://localhost:8080/api/documents/<documentId> -Method Delete
```

Git Bash
```bash
# 驗證 embedding API（若回 404，改用新端點 /api/embed 且欄位改 input）
curl http://localhost:11434/api/embeddings \
  -d '{"model": "nomic-embed-text", "prompt": "測試一段文字"}'

# 上傳文件（非同步處理，回 202 + documentId）
curl -F "file=@your.pdf" http://localhost:8080/api/documents

# 查狀態（READY 後即可提問）
curl http://localhost:8080/api/documents

# 串流問答（SSE）
curl -N -X POST http://localhost:8080/api/query/stream \
  -H "Content-Type: application/json" \
  -d '{"question":"這份文件在講什麼？"}'

# 限定單一文件範圍（防線 2）
curl -N -X POST http://localhost:8080/api/query/stream \
  -H "Content-Type: application/json" \
  -d '{"question":"...","documentScope":"<documentId>"}'

# 查詢歷史
curl http://localhost:8080/api/query/history

# 刪除文件（chunks 由 FK 串接刪除）
curl -X DELETE http://localhost:8080/api/documents/<documentId>
```

## 切換 Provider（OpenAI ↔ Ollama）

僅需改設定（ADR-005 驗收）。本機全離線（embedding + 生成皆 Ollama）：

```bash
APP_EMBEDDING_BEAN=ollamaEmbeddingModel
APP_LLM_BEAN=ollamaChatModel
EMBEDDING_DIMENSION=768          # nomic-embed-text 維度，需重建資料庫
OLLAMA_BASE_URL=http://localhost:11434
```

> 換 embedding 維度等於換向量空間，需清掉舊 `document_chunks` 重新處理。

## 限流與快取後端

| 後端 | 啟用方式 | 用途 |
|------|----------|------|
| 記憶體（預設） | `app.ratelimit.backend=memory`、`spring.cache.type=caffeine` | 單機開發，免 Redis |
| Redis 分散式 | `SPRING_PROFILES_ACTIVE=redis` | 多實例共享限額與快取（ADR-009） |

`redis` profile 會將限流切到 `RedisRateLimiter`（Bucket4j + Lettuce）、快取切到 Redis。
選用存取碼：設 `APP_ACCESS_CODE` 後，`/api/query/**` 需帶 `X-Access-Code` 標頭。

## 分階段交付對照（Milestones）

| 里程碑 | 內容 | 狀態 |
|--------|------|------|
| M1 最小端到端 | 上傳→解析→分塊→embedding→pgvector；可提問 | ✅ |
| M2 串流 + 多文件 | SSE 串流、`documentScope` 限範圍 | ✅ |
| M3 檢索品質 + 可觀測性 | 延遲分解（embedding/retrieval/llm）入庫、Actuator/Micrometer | ✅（延遲分解 + metrics；reranking 為後續） |
| M4 可靠性 + 成本 | LLM 重試 + 串流降級、embedding 快取、token 成本記錄 | ✅ |

## 設定參考（`app.*`）

| 設定 | 預設 | 說明 |
|------|------|------|
| `app.embedding.bean` / `.dimension` | `openAiEmbeddingModel` / `1536` | Embedding 模型 bean 與維度 |
| `app.llm.bean` | `openAiChatModel` | 生成模型 bean |
| `app.llm.max-retries` / `.fallback-message` | `2` / … | 重試次數與降級訊息（M4） |
| `app.chunking.chunk-size` / `.overlap` | `512` / `64` | 固定分塊大小與重疊（近似 token，ADR-004） |
| `app.query.top-k` / `.min-similarity` | `5` / `0.75` | 檢索筆數與門檻（防線 1） |
| `app.query.post-validation` | `true` | 後處理引用驗證（防線 4） |
| `app.ratelimit.*` | 每 IP 20/分、全域 200/分 | 限流參數（ADR-009） |

## 測試

```bash
mvn test
```

涵蓋分塊邏輯、prompt 組裝、grounding 引用驗證，以及查詢流程的門檻行為（檢索為空 → 不呼叫 LLM、回固定訊息）。皆為純單元測試，免 DB / API key 即可離線執行（業務邏輯與外部 IO 分離，§7）。

## 專案結構

```
src/main/java/com/casper/docrag/
├─ domain/            領域介面與模型（EmbeddingProvider / VectorStore / LlmProvider / DocumentProcessor）
├─ provider/          adapter 層：Spring AI（embedding/llm）+ PgVectorStore（向量庫）
├─ document/          解析（PDFBox/Markdown）、分塊、處理管線、文件服務
├─ query/             查詢編排、Prompt 組裝、Grounding 驗證
├─ persistence/       JdbcClient repositories（documents / query_history）
├─ ratelimit/         Bucket4j 限流（記憶體 / Redis）+ 攔截器
├─ web/               REST 控制器、DTO、全域例外處理
├─ config/            設定（AppProperties / Async / Web）
└─ support/           工具（向量字面值、token 估算、雜湊、CJK、重試）
src/main/resources/db/migration/   Flyway schema（embedding 維度以 placeholder 注入）
```

## 設計決策

完整 ADR 見 [docs/ADR.md](docs/ADR.md)（001–010：pgvector、HNSW、SSE、分塊、三介面、串流並發、Spring AI、本機 LLM、限流、grounding），含 Context / Decision / Consequences 與面試敘事。

幾項實作取捨補充：

- **向量庫自寫而非用 Spring AI 內建 store**：規格 §3.1 的 `VectorStore` 介面（帶 `minSimilarity`/`documentScope` 與相似度分數）與 §3.3 客製 schema（`document_chunks` 外鍵、`chunk_index`）和 Spring AI 內建 `vector_store` 表不一致，故以 JdbcClient 直連 pgvector 實作，Spring AI 僅供 `EmbeddingModel`/`ChatModel`（符合 ADR-007）。
- **分塊近似 token**：未引入 BPE tokenizer（§7 不引入非必要依賴），以「CJK 字元 = 1 token、拉丁詞 = 1 token」近似固定大小分塊，對中英文皆穩定可測；token 精確化留待 M3（ADR-004）。
- **限流預設記憶體、Redis 為水平擴展選項**：兩者皆 Bucket4j，介面 `RateLimiter` 隔離，`redis` profile 一鍵切換（ADR-009）。
