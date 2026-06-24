'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import type { DocumentItem } from '@/types';
import { RateLimitError } from '@/types';
import * as api from '@/lib/api';

// Patterns that indicate the knowledge base had no relevant content.
// Update these to match the exact message your backend returns.
const NO_CONTENT_PATTERNS = [
  '找不到相關資訊',
  '無相關資料',
  '找不到相關內容',
  'no relevant information',
  'no related information',
];

function isNoContent(text: string): boolean {
  const lower = text.toLowerCase().trim();
  return NO_CONTENT_PATTERNS.some((p) => lower.includes(p.toLowerCase()));
}

function friendlyError(e: unknown, fallback: string): string {
  if (e instanceof RateLimitError) return '請求過於頻繁，請稍後再試';
  if (e instanceof Error) return e.message;
  return fallback;
}

const STATUS_LABEL: Record<string, string> = {
  PENDING: '等待中',
  PROCESSING: '處理中',
  READY: '就緒',
  FAILED: '失敗',
};

const STATUS_CLASS: Record<string, string> = {
  PENDING: 'status-pending',
  PROCESSING: 'status-processing',
  READY: 'status-ready',
  FAILED: 'status-failed',
};

export default function Home() {
  // Access code — kept only in React state, never persisted to storage
  const [accessCode, setAccessCode] = useState('');

  // Document management
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [docsLoading, setDocsLoading] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');

  // Q&A
  const [question, setQuestion] = useState('');
  const [scope, setScope] = useState('all');
  const [answer, setAnswer] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isStreaming, setIsStreaming] = useState(false);
  const [noContent, setNoContent] = useState(false);
  const [queryError, setQueryError] = useState('');
  const [openInfo, setOpenInfo] = useState<Set<number>>(new Set());

  const fileRef = useRef<HTMLInputElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  const code = accessCode.trim() || undefined;

  // ── Document list ────────────────────────────────────────────────────────

  const loadDocs = useCallback(async () => {
    setDocsLoading(true);
    try {
      setDocuments(await api.getDocuments(code));
    } catch {
      // Non-critical: user can retry with the refresh button
    } finally {
      setDocsLoading(false);
    }
  }, [code]);

  useEffect(() => {
    loadDocs();
    return () => { abortRef.current?.abort(); };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setIsUploading(true);
    setUploadError('');
    try {
      await api.uploadDocument(file, code);
      await loadDocs();
    } catch (err) {
      setUploadError(friendlyError(err, '上傳失敗，請稍後再試'));
    } finally {
      setIsUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await api.deleteDocument(id, code);
      setDocuments((prev) => prev.filter((d) => d.documentId !== id));
    } catch {
      // Optimistic update already handled; reload to sync actual state
      await loadDocs();
    }
  };

  // ── Query / SSE stream ───────────────────────────────────────────────────

  const handleQuery = async () => {
    const q = question.trim();
    if (!q || isLoading || isStreaming) return;

    // Cancel any in-flight stream before starting a new one
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;

    setQueryError('');
    setAnswer('');
    setNoContent(false);
    setIsLoading(true);
    setIsStreaming(false);

    let full = '';
    let firstToken = false;

    try {
      const docScope = scope !== 'all' ? scope : undefined;

      for await (const token of api.streamQuery(q, docScope, code, ctrl.signal)) {
        if (!firstToken) {
          firstToken = true;
          setIsLoading(false);
          setIsStreaming(true);
        }
        full += token;
        setAnswer(full);
      }

      if (full && isNoContent(full)) setNoContent(true);
    } catch (err) {
      if (err instanceof Error && err.name === 'AbortError') return;
      setQueryError(friendlyError(err, '查詢失敗，請稍後再試'));
    } finally {
      setIsLoading(false);
      setIsStreaming(false);
    }
  };

  // ── Render ───────────────────────────────────────────────────────────────

  return (
    <div className="app">
      {/* Header */}
      <header className="header">
        <h1>DocRAG 文件問答</h1>
        <div className="ac-wrap">
          <label htmlFor="ac-input" className="ac-label">
            存取碼
            <span
              className="ac-tip"
              data-tooltip="後端若啟用存取碼驗證，每次請求都會帶上 X-Access-Code header。未啟用時可留空。"
            >ⓘ</span>
          </label>
          <input
            id="ac-input"
            type="password"
            autoComplete="off"
            placeholder="（選填）"
            value={accessCode}
            onChange={(e) => setAccessCode(e.target.value)}
          />
          <button className="btn-sm" onClick={loadDocs}>套用</button>
        </div>
      </header>

      <div className="main">
        {/* ── 文件管理 ── */}
        <section className="panel" aria-label="文件管理">
          <h2>文件管理</h2>

          <div className="row-inline">
            <input
              ref={fileRef}
              id="file-input"
              type="file"
              accept=".pdf,.md,.markdown"
              className="hidden"
              onChange={handleUpload}
              disabled={isUploading}
            />
            <label
              htmlFor="file-input"
              className={`btn${isUploading ? ' btn--disabled' : ''}`}
              role="button"
              aria-disabled={isUploading}
            >
              {isUploading ? '上傳中…' : '+ 上傳文件'}
            </label>
            <span className="hint">PDF / Markdown</span>
          </div>

          {uploadError && <p className="err-text">{uploadError}</p>}

          <div className="doc-list">
            {docsLoading ? (
              <p className="muted">載入中…</p>
            ) : documents.length === 0 ? (
              <p className="muted">尚無文件</p>
            ) : (
              documents.map((doc) => (
                <div key={doc.documentId} className="doc-row">
                  <span className="doc-name" title={doc.name}>{doc.name}</span>
                  <span className={`tag ${STATUS_CLASS[doc.status] ?? ''}`}>
                    {STATUS_LABEL[doc.status] ?? doc.status}
                  </span>
                  <span className="doc-chunks">{doc.chunkCount} 片段</span>
                  <button
                    className="del-btn"
                    onClick={() => handleDelete(doc.documentId)}
                    aria-label={`刪除 ${doc.name}`}
                  >
                    ×
                  </button>
                </div>
              ))
            )}
          </div>

          <button className="btn-ghost" onClick={loadDocs} disabled={docsLoading}>
            重新整理
          </button>
        </section>

        {/* ── 問答 ── */}
        <section className="panel" aria-label="問答">
          <h2>問答</h2>

          <div className="field-row">
            <label htmlFor="scope-sel" className="field-label">查詢範圍</label>
            <select
              id="scope-sel"
              className="select"
              value={scope}
              onChange={(e) => setScope(e.target.value)}
            >
              <option value="all">全部文件</option>
              {documents
                .filter((d) => d.status === 'READY')
                .map((d) => (
                  <option key={d.documentId} value={d.documentId}>
                    {d.name}
                  </option>
                ))}
            </select>
          </div>

          <div className="query-row">
            <textarea
              className="textarea"
              placeholder="輸入問題… (Enter 送出，Shift+Enter 換行)"
              rows={3}
              value={question}
              disabled={isStreaming || isLoading}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleQuery();
                }
              }}
            />
            <button
              className="btn"
              style={{ minWidth: 72, justifyContent: 'center' }}
              onClick={handleQuery}
              disabled={!question.trim() || isLoading || isStreaming}
            >
              {isLoading || isStreaming ? '查詢中…' : '送出'}
            </button>
          </div>

          {queryError && (
            <div className="banner banner--error" role="alert">
              <span>{queryError}</span>
              <button onClick={() => setQueryError('')} aria-label="關閉">×</button>
            </div>
          )}

          <div className="answer-box" aria-live="polite" aria-label="回答區域">
            {isLoading && !answer && (
              <p className="loading-text">正在查詢，請稍候…</p>
            )}

            {!isLoading && !isStreaming && !answer && !queryError && (
              <p className="placeholder-text">回答將顯示於此</p>
            )}

            {noContent && answer && (
              <div className="no-content-note" role="status">
                ℹ 知識庫中找不到相關資訊
              </div>
            )}

            {answer && (
              <p className={`answer-text${noContent ? ' answer-text--muted' : ''}`}>
                {answer}
                {isStreaming && <span className="cursor" aria-hidden="true">▊</span>}
              </p>
            )}
          </div>
        </section>
      </div>

      {/* ── 說明區塊 ── */}
      <div className="info-sections">
        {(
          [
            {
              title: '解決痛點',
              summary: '語意搜尋 × LLM，讓私有文件成為可對話的知識庫',
              body: (
                <p>傳統搜尋找關鍵字、LLM 不知道你的文件——這個系統把兩者結合，讓你直接用自然語言問私有文件裡的事。</p>
              ),
            },
            {
              title: '使用方式',
              summary: '上傳 → 等就緒 → 選範圍 → 提問，四步完成文件問答',
              body: (
                <ol>
                  <li><strong>上傳文件</strong> — 點擊「+ 上傳文件」選擇 PDF 或 Markdown 檔案，支援多次上傳、每份獨立管理。</li>
                  <li><strong>等待就緒</strong> — 狀態依序經過「等待中 → 處理中 → 就緒」。處理中代表後端正在切片並計算向量，完成後才能查詢。</li>
                  <li><strong>選擇範圍</strong>（選填）— 下拉選單可限定只搜尋某份文件；預設為全部已就緒的文件。</li>
                  <li><strong>輸入問題</strong> — 按 Enter 或「送出」後，回答以串流方式逐字顯示，不需等待整段完成。</li>
                  <li><strong>刪除文件</strong> — 點擊列表右側的 × 刪除，向量資料一併移除。</li>
                </ol>
              ),
            },
            {
              title: '背後邏輯',
              summary: '切片 → 向量化 → ANN 搜尋 → prompt 注入 → LLM 串流',
              body: (
                <>
                  <p><strong>上傳流程</strong></p>
                  <ol>
                    <li>解析文件（PDF → PDFBox；Markdown → 直接讀取）</li>
                    <li>切成 512 token 片段，相鄰片段重疊 64 token，避免語意在邊界斷裂</li>
                    <li>每個片段送進 Embedding Provider，轉成高維向量（OpenAI 1536d / Ollama 768d）</li>
                    <li>向量與原文一起寫入 PostgreSQL pgvector，建 HNSW cosine 索引</li>
                  </ol>
                  <p><strong>查詢流程</strong></p>
                  <ol>
                    <li>限流檢查（Bucket4j）— 超限直接回 429，不繼續</li>
                    <li>問題向量化（同一個 Embedding Provider）</li>
                    <li>HNSW ANN 搜尋，取相似度最高的 top-5 片段</li>
                    <li>相似度門檻判斷 — 全部低於門檻，直接回「找不到相關資訊」，不呼叫 LLM</li>
                    <li>組裝 prompt，把片段作為 context 注入，要求 LLM 只引用提供的內容</li>
                    <li>LLM 生成回答，透過 SseEmitter 逐 token 推送到前端</li>
                  </ol>
                </>
              ),
            },
            {
              title: '技術棧',
              summary: 'Java 17 · Spring Boot 4.1 · Next.js 15 · PostgreSQL 16 + pgvector',
              body: (
                <div className="tech-grid">
                  <div>
                    <p className="tech-cat">前端</p>
                    <ul>
                      <li>Next.js 15（App Router）</li>
                      <li>React 18 · TypeScript 5</li>
                    </ul>
                  </div>
                  <div>
                    <p className="tech-cat">後端</p>
                    <ul>
                      <li>Java 17 · Spring Boot 4.1.0</li>
                      <li>Spring AI 2.0.0（adapter 層）</li>
                      <li>Spring MVC + SseEmitter（SSE 串流）</li>
                    </ul>
                  </div>
                  <div>
                    <p className="tech-cat">資料層</p>
                    <ul>
                      <li>PostgreSQL 16 + pgvector</li>
                      <li>HNSW 索引（cosine 相似度）</li>
                      <li>Flyway（schema 版本管理）</li>
                    </ul>
                  </div>
                  <div>
                    <p className="tech-cat">快取 / 限流</p>
                    <ul>
                      <li>Caffeine（單機快取）</li>
                      <li>Redis + Bucket4j 8.19（分散式限流）</li>
                    </ul>
                  </div>
                  <div>
                    <p className="tech-cat">文件解析</p>
                    <ul>
                      <li>Apache PDFBox 3.0.3（PDF）</li>
                      <li>純文字讀取（Markdown）</li>
                    </ul>
                  </div>
                  <div>
                    <p className="tech-cat">LLM / Embedding（可切換）</p>
                    <ul>
                      <li>OpenAI GPT · text-embedding-3-small（1536d）</li>
                      <li>Anthropic Claude（Spring AI 原生整合）</li>
                      <li>Ollama · nomic-embed-text（768d，本機）</li>
                    </ul>
                  </div>
                </div>
              ),
            },
          ] as { title: string; summary: string; body: React.ReactNode }[]
        ).map(({ title, summary, body }, i) => {
          const isOpen = openInfo.has(i);
          const toggle = () =>
            setOpenInfo((prev) => {
              const next = new Set(prev);
              isOpen ? next.delete(i) : next.add(i);
              return next;
            });
          return (
            <section key={i} className="info-block">
              <button className="info-header" onClick={toggle} aria-expanded={isOpen}>
                <div className="info-title-row">
                  <h3>{title}</h3>
                  <span className={`info-chevron${isOpen ? ' info-chevron--open' : ''}`}>›</span>
                </div>
                {!isOpen && <p className="info-summary">{summary}</p>}
              </button>
              {isOpen && <div className="info-body">{body}</div>}
            </section>
          );
        })}
      </div>
    </div>
  );
}
