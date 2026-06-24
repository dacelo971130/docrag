---
title: RAG 文件問答系統 — 環境建置指南與技術要求
version: 2.0
date: 2026-06-24
type: setup-guide
tags:
  - 環境建置
  - 技術要求
  - RAG
  - SpringBoot
  - pgvector
status: 已與專案同步
related:
  - "[[RAG-方案A-SPEC]]"
  - "[[ADR]]"
---

# RAG 文件問答系統 — 環境建置指南與技術要求

> 本檔已與**實際專案狀態**同步（2026-06-24）。與早期草稿不同處：**Java 17**（非 21）、**Maven**（非 Gradle）、**PostgreSQL 18**、資料庫名一律 **docrag**。
> 第三節「啟動流程與背後原理」說明每個指令做什麼、為什麼、怎麼串起來——若你對「跑了指令卻沒看到表」感到困惑，先看那節。

---

## 一、技術要求總表（與專案實際一致）

| 元件 | 實際版本 | 說明 / 注意 |
|------|----------|-------------|
| **JDK** | **Java 17 (LTS)** | 專案以 17 建置。Spring Boot 4.1 的 baseline 就是 Java 17，本機預設 `mvn` 也是 17，**零額外設定**。（原規格表寫 Java 21，但 4.1 floor 是 17；為了與本機環境一致而定為 17。`pom.xml` 的 `<java.version>17</java.version>` 即此意。） |
| **Spring Boot** | **4.1.0** | 基於 Spring Framework 7.0.8。⚠️ 4.x 把自動設定**模組化**了，有坑（見 §3.4 Flyway）。 |
| **建置工具** | **Maven 3.9.x** | 專案用 Maven（`pom.xml`），不是 Gradle。指令一律 `mvn ...`。 |
| **Spring AI** | **2.0.0** | 僅作 adapter 層（embedding / LLM 的 provider 整合），領域層保留自有介面。 |
| **PostgreSQL** | **18** | `docker-compose.yml` 用 `pgvector/pgvector:0.8.2-pg18`，實測連上的是 PostgreSQL 18.4。 |
| **pgvector** | **0.8.2** | 版本下限不可妥協（CVE 修復 + WHERE 過濾的查詢規劃與 HNSW 效能改善、iterative scan）。 |
| **Embedding 模型** | OpenAI `text-embedding-3-small`（1536 維）/ Ollama `nomic-embed-text`（768 維） | 維度必須與 schema 的 `vector(N)` 一致，靠 `EMBEDDING_DIMENSION` 串起來（見 §3.3）。 |
| **LLM** | OpenAI / Anthropic(Claude) / Ollama | 以 `app.llm.bean` 切換（`openAiChatModel` / `anthropicChatModel` / `ollamaChatModel`）。 |
| **快取 / 限流** | Caffeine（預設）/ Redis（`redis` profile） | 單機開發免 Redis；多實例才需 Redis（Bucket4j 分散式）。 |
| **容器** | Docker + Docker Compose | 跑 PostgreSQL + Redis，避免污染本機。 |

---

## 二、Docker Compose：PostgreSQL 18 + Redis

實際的 `docker-compose.yml` 已在專案根目錄（image `pgvector/pgvector:0.8.2-pg18`、DB/帳/密皆 `docrag`）。

啟動與驗證：

```bash
docker compose up -d

# 進入資料庫（注意：DB 名、帳號都是 docrag，不是 ragdb/rag）
docker exec -it docrag-postgres psql -U docrag -d docrag

# 在 psql 內確認 pgvector 版本（擴充由 Flyway migration 自動啟用，這裡只是確認）
SELECT extversion FROM pg_extension WHERE extname = 'vector';
-- 預期看到 0.8.2
```

> 沒有 Docker？也可自行安裝 PostgreSQL 18 + pgvector 擴充，建立 `docrag` 資料庫與 `docrag` 帳號，並讓它聽 `localhost:5432`。

---

## 三、啟動流程與背後原理（重要）

這節回答：**每個指令做什麼、為什麼這樣做、彼此怎麼串起來。**

### 3.1 一鍵啟動

```bash
docker compose up -d     # 1) 起 PostgreSQL + Redis（背景常駐）
mvn spring-boot:run      # 2) 編譯 + 啟動後端
```

`mvn spring-boot:run` 啟動的是**常駐伺服器**：它會停在前景一直印 log、**不會回到命令提示字元**。看到這兩行就代表好了，不是卡住：

```
Tomcat started on port 8080 (http)
Started DocRagApplication in X.XXX seconds
```

之後它就在 `localhost:8080` 等你打 API。要用就**另開一個終端機** `curl`；要停就在它的終端機按 `Ctrl+C`。
（`mvn clean package` 則不同：那是**打包**，會跑完、產生 jar、回到提示字元。）

### 3.2 你「不用」手動建表 —— Flyway 會自動做

`src/main/resources/db/migration/V1__init_schema.sql` 是 **Flyway migration 檔，不是給你在 IntelliJ 手動執行的**。app 啟動時，Flyway 自動：

1. 用 `application.yml` 的 datasource 連上 `docrag` 資料庫；
2. 掃描 `classpath:db/migration` 下的 migration（目前只有 `V1`）；
3. 把 `${embedding_dimension}` 換成 `1536`（見 §3.3）；
4. 在一個交易內建立三張表（`documents` / `document_chunks` / `query_history`）+ HNSW 索引；
5. 在 `flyway_schema_history` 記下「v1 已套用」。

啟動 log 會看到（這就是「有建表」的證據）：

```
Flyway ... Database: jdbc:postgresql://localhost:5432/docrag (PostgreSQL 18.x)
Migrating schema "public" to version "1 - init schema"
Successfully applied 1 migration to schema "public", now at version v1
```

之後每次啟動，Flyway 看到 v1 已套用就**跳過**（冪等），不會重複建表。

### 3.3 為什麼用 `${embedding_dimension}` 這個 placeholder

`document_chunks.embedding` 是 `vector(N)`，**N 必須等於 embedding 模型的維度**（OpenAI=1536、Ollama nomic-embed-text=768）。把維度抽成一個設定值，換模型時只改設定、不用改 SQL。串接鏈：

```
.env: EMBEDDING_DIMENSION=1536
  → application.yml: spring.flyway.placeholders.embedding_dimension: ${EMBEDDING_DIMENSION:1536}
    → Flyway 把 V1.sql 的 vector(${embedding_dimension}) 換成 vector(1536)
```

> ⚠️ 這個替換**只在第一次建表時**發生。表建好後改維度，等於換向量空間，必須清掉 `document_chunks` 重建並重新處理文件。
> ⚠️ **不要在 IntelliJ 直接執行 `V1__init_schema.sql`**。IntelliJ 會把 `${embedding_dimension}` 當成它自己的 SQL 參數、跳出「Parameters」對話框要你填值（若真要手動跑，填 `1536`）——但你根本不該手動跑這支檔，那是 Flyway 的工作。要驗證表在不在，請用 §3.5 的查詢。

### 3.4 ⚠️ Spring Boot 4 的坑：Flyway 必須用 starter

Spring Boot 4 把自動設定**模組化**了。**單獨 `flyway-core` 不再會觸發 migration**（Boot 3 可以、Boot 4 不行）。症狀很隱蔽：app 照樣啟動成功、`/actuator/health` 也回 `UP`，但**完全沒有建表**，直到你查表才得到 500（`relation "documents" does not exist`）。

正確的 `pom.xml` 寫法（本專案已修正）：

```xml
<!-- 用 starter，不要只放 flyway-core -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

> 同理，Liquibase 在 Boot 4 也要改用 `spring-boot-starter-liquibase`。凡是「以前只靠第三方依賴就會被自動設定」的功能，Boot 4 多半改成需要對應 starter。

### 3.5 怎麼驗證「表真的建好了」

**方法 A（最快）**：另開終端機打 API——回 `[]` 就代表 `documents` 表存在且可查。

```bash
curl http://localhost:8080/api/documents
# 預期：[]
```

**方法 B（查資料庫）**：用 `psql` 或 IntelliJ 連到 **`docrag`**（不是 `ragdb`！）後查詢（注意這沒有 `${}`，不會跳參數框）：

```sql
SELECT version, description, success FROM flyway_schema_history;  -- 應有 1 / init schema / true
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';
-- 應看到 documents / document_chunks / query_history
```

> 💡 IntelliJ 的資料庫樹是**快取**的：Flyway 建表後，要對該連線按右鍵 **Refresh** 才會顯示新表。而且務必確認你連的是 **docrag** 庫——連到別的庫當然看不到表。

---

## 四、application.yml 連線與設定（實際摘要）

連線與關鍵設定（皆可由環境變數 / `.env` 覆寫）：

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/docrag}
    username: ${DB_USERNAME:docrag}
    password: ${DB_PASSWORD:docrag}
  flyway:
    enabled: true
    locations: classpath:db/migration
    placeholders:
      embedding_dimension: ${EMBEDDING_DIMENSION:1536}   # 注入 vector(N)

app:
  embedding:
    bean: ${APP_EMBEDDING_BEAN:openAiEmbeddingModel}     # openAiEmbeddingModel | ollamaEmbeddingModel
    dimension: ${EMBEDDING_DIMENSION:1536}
  llm:
    bean: ${APP_LLM_BEAN:openAiChatModel}                # openAiChatModel | anthropicChatModel | ollamaChatModel
```

> 金鑰一律走環境變數 / `.env`（且 `.env` 已在 `.gitignore`）。`OPENAI_API_KEY`、`ANTHROPIC_API_KEY` 等絕不寫死、絕不 commit。
> 提醒：`Cannot resolve configuration property 'spring.flyway.placeholders.embedding_dimension'` 是 **IntelliJ 對 map 型別 key 的檢查誤報**，不是真錯誤，Flyway 執行時照樣收得到（上面 §3.2 的 log 已證明）。

---

## 五、本機 LLM 選項（Ollama，可選）

```bash
ollama pull nomic-embed-text   # embedding（768 維）
ollama pull gemma2:2b          # 生成（依機器資源選）
# Ollama 預設在 localhost:11434
```

切換到全本機：在 `.env` 設 `APP_EMBEDDING_BEAN=ollamaEmbeddingModel`、`APP_LLM_BEAN=ollamaChatModel`、`EMBEDDING_DIMENSION=768`（**需重建 document_chunks**，因為向量維度改變）。這正是 SPEC 把 `EmbeddingProvider.dimensions()` 抽象出來的原因。

---

## 六、開發環境檢查清單

- [ ] **JDK 17** 安裝且 `java -version` 為 17（本機預設即可，無需 toolchain / `JAVA_HOME`）
- [ ] Docker Desktop 運行中
- [ ] `docker compose up -d` 起得來；`docker exec -it docrag-postgres psql -U docrag -d docrag` 連得進去
- [ ] `SELECT extversion ...` 回傳 `0.8.2`
- [ ] `pom.xml` 用 **`spring-boot-starter-flyway`**（不是只有 `flyway-core`）
- [ ] `mvn spring-boot:run` 啟動 log 出現 `Migrating schema ... version "1 - init schema"` 與 `Successfully applied 1 migration`
- [ ] `curl http://localhost:8080/api/documents` 回 `[]`（表存在）
- [ ] API 金鑰走環境變數，`.env` 已在 `.gitignore`
- [ ] （選）Ollama 安裝且模型拉好，維度已對應調整

---

## 七、常見環境問題與排查

| 症狀 | 可能原因 | 解法 |
|------|----------|------|
| app 啟動成功、health UP，但**查表 500 / 沒看到表** | Boot 4 模組化：只放了 `flyway-core`，Flyway autoconfig 沒上線 | 改用 **`spring-boot-starter-flyway`**（§3.4）。本專案已修。 |
| IntelliJ 跑 `V1__init_schema.sql` 跳「Parameters」要填 `embedding_dimension` | 把 `${...}` 當成 IntelliJ 的 SQL 參數 | **別手動跑 migration**；要驗證就查表（§3.5）。真要跑就填 `1536`。 |
| 連進資料庫看不到表 | 連到 `ragdb`/別的庫，或 IntelliJ 沒 Refresh | 連 **`docrag`** 庫；對連線按右鍵 Refresh |
| `Cannot resolve configuration property spring.flyway.placeholders.*` | IntelliJ 對 map key 的檢查誤報 | 忽略即可，非執行期錯誤 |
| `mvn spring-boot:run` 看起來「跑不動」 | 它是常駐伺服器，停在前景印 log = 正常 | 看到 `Started DocRagApplication` 即就緒；另開終端 `curl` |
| `Web server failed to start. Port 8080 was already in use` | 已有一個實例在跑 | 別開兩個；停掉舊的（`Ctrl+C` 或 `Stop-Process -Id <pid>`）再啟動 |
| `type "vector" does not exist` | 擴充沒啟用或裝在別的 schema | migration 內已 `CREATE EXTENSION IF NOT EXISTS vector`；檢查 `\dx vector` |
| 維度不匹配錯誤 | embedding 維度 ≠ `vector(N)` | 對齊 `EMBEDDING_DIMENSION` 與模型，換模型需重建 chunks |
| 查詢走 Seq Scan 不走索引 | 資料量小 / 選擇性 | 資料夠多時規劃器才選 HNSW；用 `EXPLAIN ANALYZE` 看 |

---

## 八、技術要求的「為什麼」（面試素材）

- **為何 Java 17 而非 21**：Spring Boot 4.1 的 baseline 就是 17，本機預設工具鏈也是 17——選 17 讓「零額外設定就能建置」成立，避免為了用不到的新語言特性而引入 toolchain/JDK 切換的摩擦。需要時把 `<java.version>` 調回 21 也只是一行。
- **為何 pgvector 而非 Pinecone/Qdrant**：同一個 PostgreSQL 實例、無同步層、無額外帳單，向量與關聯資料同庫。百萬級以下是務實選擇；介面已抽象，規模成長可換。
- **為何 HNSW 而非 IVFFlat**：查詢效能較佳、建索引無需訓練步驟、可建在空表上；代價是建索引較慢較吃記憶體。
- **為何用 Flyway 管 schema**：版本化、可重現、團隊一致；啟動即自動套用，環境之間不會「我這台有表你那台沒有」。也因此**不該手動跑 migration SQL**——一旦手動跑，就會跟 Flyway 的版本歷史對不上。
- **為何 cosine 距離**：文字 embedding 普遍以 cosine 衡量語意接近度，對應 `vector_cosine_ops`。

---

## 待辦（動工前再驗證一次）

- [x] 確認 Spring Boot 4 需 `spring-boot-starter-flyway` 才會跑 migration（已驗證並修正）
- [ ] 確認 pgvector 最新 patch（github.com/pgvector/pgvector releases，目前 0.8.2）
- [ ] 確認所選 embedding 模型維度，據此設 `EMBEDDING_DIMENSION` 與 `vector(N)`
- [ ] 換 Ollama 時記得重建 `document_chunks`（維度 1536 → 768）
