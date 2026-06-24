# RAG 文件問答系統專案分析與快速上手手冊

本文件旨在協助您快速理解本專案的架構設計、核心流程、進行安全敏感資訊審查，並提供手把手的本機運行與測試指南。

---

## 一、專案核心架構與設計分析

本專案是一個依據 **RAG 方案 A** 技術規格實作的**向量文件問答系統後端**。其架構設計重點在於**工程深度**與 **AI 基礎建設的隔離防護**，以防範大型語言模型（LLM）的幻覺，並確保系統的可擴展性。

### 1. 技術棧與相依性
*   **語言與核心框架**：Java 17、Spring Boot 4.1.0。
*   **AI 整合**：Spring AI 2.0.0（僅限於 adapter 層作為與 OpenAI/Ollama 串接工具，見 [ProviderConfig.java](file:///d:/project/demo/claude/docrag/src/main/java/com/casper/docrag/provider/ProviderConfig.java)）。
*   **資料庫與向量檢索**：PostgreSQL 18 + pgvector，使用 **HNSW** 向量索引（見 [V1__init_schema.sql](file:///d:/project/demo/claude/docrag/src/main/resources/db/migration/V1__init_schema.sql)）。
*   **快取與分散式限流**：Caffeine (單機快取) / Redis (分散式快取/限流) + Bucket4j。
*   **文件解析**：Apache PDFBox（解析 PDF 檔案）。

### 2. 關鍵架構決策 (ADR) 與代碼對照
*   **三介面隔離（DIP，ADR-005）**：
    領域層不直接耦合具體的 AI 服務提供商，而是透過三個核心介面進行反轉：
    *   `EmbeddingProvider`（文字向量化，實作為 [SpringAiEmbeddingProvider.java](file:///d:/project/demo/claude/docrag/src/main/java/com/casper/docrag/provider/embedding/SpringAiEmbeddingProvider.java)）
    *   `VectorStore`（向量存取，實作為 [PgVectorStore.java](file:///d:/project/demo/claude/docrag/src/main/java/com/casper/docrag/provider/vectorstore/PgVectorStore.java)）
    *   `LlmProvider`（語言模型生成，實作為 [SpringAiLlmProvider.java](file:///d:/project/demo/claude/docrag/src/main/java/com/casper/docrag/provider/llm/SpringAiLlmProvider.java)）
*   **向量儲存客製化（ADR-001/007）**：
    專案並未使用 Spring AI 內建的 `vector_store` 表結構，而是使用 `JdbcClient` 實作自有的 [PgVectorStore.java](file:///d:/project/demo/claude/docrag/src/main/java/com/casper/docrag/provider/vectorstore/PgVectorStore.java)。這能實現更緊密的業務關聯（外鍵 `document_id` 串接級聯刪除）與自訂檢索門檻。
*   **Grounding 四層防線（ADR-010）**：
    為避免 LLM 胡言亂語（幻覺），系統建立了以下四道防線：
    1.  **檢索門檻 (minSimilarity)**：低於相似度的片段在 SQL 語法中即丟棄（[PgVectorStore.java](file:///d:/project/demo/claude/docrag/src/main/java/com/casper/docrag/provider/vectorstore/PgVectorStore.java#L74-L101)）。若無任何片段通過門檻，將**直接短路返回固定訊息**，不呼叫 LLM（省成本且防幻覺）。
    2.  **範圍過濾 (documentScope)**：可限定僅檢索特定文件 ID 的內容。
    3.  **Prompt 約束**：要求模型僅根據提供的片段作答，若資訊不足直接明說。
    4.  **後處理引用驗證**：[GroundingService.java](file:///d:/project/demo/claude/docrag/src/main/java/com/casper/docrag/query/GroundingService.java) 會驗證回答中標記的引用（例如 `[1]`）是否確實在檢索到的片段中。

---

## 二、敏感資訊與安全性檢查

由於本專案將公開部署至網路上，我們對專案目錄進行了全盤敏感資訊審查：

### 1. 審查結果
*   **API 金鑰 (API Key)**：無洩漏。OpenAI 金鑰以 `${OPENAI_API_KEY:sk-placeholder}` 佔位符形式定義於 [application.yml](file:///d:/project/demo/claude/docrag/src/main/resources/application.yml#L43)，且環境變數範例 [env.example](file:///d:/project/demo/claude/docrag/.env.example) 僅填入 `sk-replace-me`，確保了金鑰安全。
*   **資料庫認證**：無洩漏。預設的資料庫帳密為本機開發帳密 `docrag/docrag`（定義於 [docker-compose.yml](file:///d:/project/demo/claude/docrag/docker-compose.yml#L9-L11)），在實際生產部署時可透過環境變數 `DB_USERNAME` 與 `DB_PASSWORD` 進行覆寫。
*   **Git 忽略設定**：[.gitignore](file:///d:/project/demo/claude/docrag/.gitignore) 中已妥善加入 `.env`、`target/`、`.idea/` 等開發環境與機密資訊檔案。

### 2. 開放網路安全建議
> [!IMPORTANT]
> 1. **切勿提交 `.env` 檔**：確認 `.env` 沒有被強制加入 Git 追蹤（專案目前的 Git 狀態很乾淨，請維持此狀態）。
> 2. **生產環境金鑰**：在雲端或伺服器部署時，必須透過環境變數注入真實的 `OPENAI_API_KEY`、`DB_PASSWORD`，切勿將真實金鑰寫入任何程式碼或 YAML 中。
> 3. **存取碼啟用**：若要公開提供服務但只允許特定用戶存取，可在生產環境設置 `APP_ACCESS_CODE`。啟用後，所有問答 API（`/api/query/**`）均需在 Header 帶上 `X-Access-Code`，否則會返回 `401 Unauthorized`（見 [RateLimitInterceptor.java](file:///d:/project/demo/claude/docrag/src/main/java/com/casper/docrag/ratelimit/RateLimitInterceptor.java)）。

---

## 三、手把手快速上手指引 (Quickstart)

請按照以下步驟在您的本機電腦（Windows 環境）上啟動並測試本專案。

### 步驟 1：確認環境要求
*   **Java Runtime**: **JDK 17**（Spring Boot 4.1 baseline；本機預設 JDK 即可，無需 toolchain / `JAVA_HOME`）
*   **Docker Desktop**: 用於啟動資料庫與 Redis。
*   **Maven**: 用於專案建置（若無本機 Maven，可使用專案目錄附帶的 `./mvnw` 腳本）。

### 步驟 2：啟動 Docker 相依服務
開啟您的終端機（PowerShell 或 CMD），在專案根目錄下執行：
```bash
docker compose up -d
```
這將在背景啟動兩個容器：
1.  **PostgreSQL 18 (附帶 pgvector 擴充功能)**：埠位 `5432`。
2.  **Redis**：埠位 `6379`（快取與限流使用）。

### 步驟 3：設定本機環境變數
1.  在專案根目錄下，複製範本檔案建立 `.env`：
    ```bash
    cp .env.example .env
    ```
2.  編輯 `.env`，並將 `OPENAI_API_KEY` 替換為您真實的 OpenAI 金鑰：
    ```env
    OPENAI_API_KEY=sk-your-real-openai-api-key-here
    ```
    *如果您希望全本機運行（離線），您可以參考 README 中有關 Ollama 的配置。*

### 步驟 4：編譯並啟動專案（JDK 17）
本機預設 `mvn` 即為 Java 17，直接執行即可，**無需設定 `JAVA_HOME` 或 toolchain**。

> ⚠️ 啟動前 PostgreSQL 必須在線（步驟 2）；啟動時 **Flyway 會自動建表**（原理見《環境建置指南》§3）。

```powershell
mvn spring-boot:run
```

啟動後控制台會**持續顯示 log**（常駐伺服器，不會回到命令提示字元），預設埠位 `8080`。看到 `Started DocRagApplication` 即就緒——要打 API 請**另開一個終端機**。

---

## 四、API 測試與調用範例

後端啟動後，您可以使用 `curl` 或 Postman 等工具來測試完整的功能流程。

### 1. 上傳文件（以 PDF 為例）
系統會非同步解析該文件、切塊並計算 embedding 存入向量庫。
```bash
curl -F "file=@/path/to/your/document.pdf" http://localhost:8080/api/documents
```
*回應範例 (HTTP 202 Accepted)：*
```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING"
}
```

### 2. 檢查文件處理狀態
當 status 變為 `READY` 時，代表該文件已完成向量化，可以開始提問。
```bash
curl http://localhost:8080/api/documents
```

### 3. 發起 SSE 串流問答
提問時，系統會從向量庫中檢索出相似的片段作為上下文，並以 Server-Sent Events (SSE) 形式逐字返回答案。
```bash
curl -N -X POST http://localhost:8080/api/query/stream \
  -H "Content-Type: application/json" \
  -d "{\"question\":\"請總結這份文件的重點\"}"
```
*   **限定單一文件查詢**：如果只想針對剛才上傳的文件提問，可帶入 `documentScope`：
    ```bash
    curl -N -X POST http://localhost:8080/api/query/stream \
      -H "Content-Type: application/json" \
      -d "{\"question\":\"此文件中的結論是什麼？\", \"documentScope\":\"550e8400-e29b-41d4-a716-446655440000\"}"
    ```

### 4. 取得查詢歷史紀錄
查詢歷史會完整紀錄每次提問的端到端延遲、以及各階段的延遲細分（如向量化延遲、檢索延遲、LLM 生成延遲）：
```bash
curl http://localhost:8080/api/query/history
```

### 5. 刪除文件
若欲移除文件及其對應的向量片段：
```bash
curl -X DELETE http://localhost:8080/api/documents/550e8400-e29b-41d4-a716-446655440000
```
*(由於資料庫有設定級聯刪除，此操作會一併清除 `document_chunks` 中與該文件相關的所有向量資料。)*

---

祝您開發愉快！如果有任何問題，隨時歡迎提出。
