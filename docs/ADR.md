# 架構決策記錄（ADR）

依方案 A 規格 §8 展開。每則含 Context / Decision / Consequences / Alternatives 與面試敘事。

---

## ADR-001：向量儲存用 pgvector（非專用向量庫）

- **Context**：需向量相似度檢索；初期資料量小、團隊熟悉關聯式資料庫，且向量與業務關聯資料（文件、片段、歷史）天然同源。
- **Decision**：用 PostgreSQL 16 + pgvector，向量與關聯資料同一個庫。`VectorStore` 介面抽象，未來可換 Qdrant。
- **Consequences**：超大規模（數千萬向量、高 QPS）時不如專用庫；但省下額外基礎設施與跨庫一致性問題，單一交易即可維護文件↔片段一致。
- **Alternatives**：Qdrant / Milvus / Weaviate（功能強但多一套維運）。
- **敘事**：「先用 pgvector 把端到端跑通並守住資料一致性，把『換專用庫』的成本透過 `VectorStore` 介面降到最低——這是 YAGNI 與可演進性的平衡。」

## ADR-002：ANN 索引用 HNSW（非 IVFFlat）

- **Context**：pgvector 支援 IVFFlat 與 HNSW 兩種近似最近鄰索引。
- **Decision**：用 HNSW + `vector_cosine_ops`，檢索以 cosine 距離運算子 `<=>`。
- **Consequences**：建索引較慢、較吃記憶體；換得較佳查詢召回與延遲，且免除 IVFFlat 需先有資料才能訓練 lists 的限制。
- **Alternatives**：IVFFlat（建index快、省記憶體，但召回/延遲較差且需調 `lists`）。
- **敘事**：「讀多寫少的問答場景，查詢延遲與召回優先於建索引成本，所以選 HNSW。」

## ADR-003：串流協定用 SSE（非 WebSocket）

- **Context**：回答需逐字串流到前端。
- **Decision**：用 Server-Sent Events（單向 server→client）。
- **Consequences**：不支援雙向；但本場景只需單向推送，SSE 更輕、與 HTTP/Proxy 相容性佳、前端 `EventSource` 即可。
- **Alternatives**：WebSocket（雙向但多了連線管理與基礎設施成本）。

## ADR-004：分塊用固定大小 + overlap

- **Context**：需把文件切成可檢索的片段。
- **Decision**：固定大小分塊（預設 512、overlap 64），策略以 `Chunker` 介面化。實作以「CJK 字元=1 token、拉丁詞=1 token」近似 token 計數，不引入 BPE tokenizer（§7）。
- **Consequences**：可能切斷語意邊界；但實作簡單、確定性高、對中英文皆可測。語意分塊與 token 精確化留待 M3，可在不動上層下替換 `Chunker`。
- **Alternatives**：語意分塊 / 遞迴分塊 / 依模型 tokenizer 精確切。
- **敘事**：「先用確定性的固定分塊建立可量測基線，把分塊策略隔離成介面，之後做語意分塊時能 A/B 比較而非推倒重來。」

## ADR-005：Provider 三介面隔離（DIP）

- **Context**：embedding、向量庫、LLM 三類外部能力都可能替換（雲端↔本機、pgvector↔Qdrant）。
- **Decision**：`EmbeddingProvider` / `VectorStore` / `LlmProvider` 三介面隔離，service 層只依賴介面，禁止 `new` 具體 provider。切換以設定（bean 名稱）完成。
- **Consequences**：多一層抽象；換得可測試（外部呼叫可 mock）與可替換（切 provider 不動業務邏輯）。
- **敘事**：「依賴反轉讓『換模型供應商』從改程式變成改設定，也讓核心流程能用 mock 完整單元測試。」

## ADR-006：串流並發用 MVC + SseEmitter 起步

- **Context**：串流可選 Spring MVC（阻塞）或 WebFlux（響應式）。
- **Decision**：用 Spring MVC + `SseEmitter`，串流在獨立執行緒池驅動；領域介面 `LlmProvider.generateStream(prompt, Consumer<String>)` 不外露 Flux。
- **Consequences**：高並發長連線時執行緒成本較高；但心智負擔低、與既有 MVC 棧一致。介面中性，未來可換 WebFlux 或虛擬執行緒而不動領域層。
- **Alternatives**：WebFlux（高並發佳但全鏈路響應式、學習與除錯成本高）。
- **敘事**：「先用團隊熟悉的 MVC 把串流做穩，並用中性回呼介面把並發模型的選擇權留到有實測壓力時再定。」

## ADR-007：Spring AI 作 adapter 層，領域層保留自有介面

- **Context**：Spring AI 提供 embedding / chat 的 provider 整合，但其抽象（`EmbeddingModel`/`ChatModel`/內建 `VectorStore`）不等於本系統的領域介面。
- **Decision**：Spring AI 只出現在 `provider/` adapter 層；領域層保留自有 `EmbeddingProvider`/`LlmProvider`。向量庫自寫 `PgVectorStore`（規格 §3.1 介面與 §3.3 schema 與 Spring AI 內建表不一致）。
- **Consequences**：多一層包裝；換得守住領域邊界、不被框架抽象綁架，且能精準控制 schema 與相似度分數。
- **敘事**：「用 Spring AI 省掉 provider SDK 樣板，但把它關在 adapter 層後面——領域模型不該認得任何框架型別。」

## ADR-008：本機 LLM 策略——embedding 本機、生成開發期用雲端

- **Context**：本機硬體有限，小生成模型品質與速度不穩。
- **Decision**：embedding 可全本機（Ollama `nomic-embed-text`）；答案生成開發期建議用雲端 API（OpenAI/Claude），以設定切換。
- **Consequences**：開發期生成仰賴雲端與成本；但回答品質穩定，且 `LlmProvider` 介面讓之後改本機零成本。

## ADR-009：速率限制——Bucket4j 分層 + 全域上限 + 存取碼

- **Context**：embedding 與 LLM 呼叫昂貴，需防濫用與護成本。
- **Decision**：Bucket4j token bucket，分「每 IP」與「全域」兩層；預設記憶體後端，`redis` profile 切 Lettuce 分散式共享限額。選用 `X-Access-Code` 存取碼。超限回 429 + `Retry-After`。
- **Consequences**：存取碼降低便利性；換得成本可控與濫用防護。記憶體後端在多實例下各自為政，故水平擴展時用 Redis 後端。
- **敘事**：「用 `RateLimiter` 介面隔離後端，單機開發免 Redis、上雲多實例一鍵切 Redis 共享限額——同一套限流邏輯涵蓋兩種部署形態。」

## ADR-010：回答接地——四層 grounding

- **Context**：RAG 最大風險是模型用知識庫外資訊編造（幻覺）。
- **Decision**：四層防線——(1) 檢索門檻 `minSimilarity`、(2) `documentScope` 範圍過濾、(3) prompt 約束、(4) 後處理引用驗證。檢索全數低於門檻即回固定訊息、不呼叫 LLM。
- **Consequences**：門檻需依模型調校（過高漏答、過低引入雜訊）；換得回答鎖在知識庫內，並在無命中時省下 LLM 成本。
- **敘事**：「防線 1+2 控制模型看得到什麼、3+4 控制它怎麼用；無命中直接短路不呼叫 LLM，同時省成本又防幻覺。」
