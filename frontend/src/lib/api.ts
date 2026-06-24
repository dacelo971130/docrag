/**
 * Service layer — all backend calls go through here.
 *
 * Environment variable required:
 *   NEXT_PUBLIC_API_BASE_URL  e.g. http://localhost:8080
 *
 * Access code (when backend auth is enabled) is passed per-call;
 * never stored in localStorage/sessionStorage.
 */

import type { DocumentItem, UploadResponse } from '@/types';
import { RateLimitError } from '@/types';

function baseUrl(): string {
  const url = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!url) throw new Error('NEXT_PUBLIC_API_BASE_URL is not configured');
  return url.replace(/\/$/, '');
}

function accessHeaders(code?: string): Record<string, string> {
  return code ? { 'X-Access-Code': code } : {};
}

function jsonHeaders(code?: string): Record<string, string> {
  return { 'Content-Type': 'application/json', ...accessHeaders(code) };
}

// ── Documents ────────────────────────────────────────────────────────────────

export async function uploadDocument(
  file: File,
  code?: string
): Promise<UploadResponse> {
  const form = new FormData();
  form.append('file', file);

  // Do NOT set Content-Type manually — let the browser set the multipart boundary.
  const res = await fetch(`${baseUrl()}/api/documents`, {
    method: 'POST',
    headers: accessHeaders(code),
    body: form,
  });

  if (res.status === 429) throw new RateLimitError();
  if (!res.ok) throw new Error('上傳失敗，請稍後再試');
  return res.json();
}

export async function getDocuments(code?: string): Promise<DocumentItem[]> {
  const res = await fetch(`${baseUrl()}/api/documents`, {
    headers: accessHeaders(code),
  });

  if (res.status === 429) throw new RateLimitError();
  if (!res.ok) throw new Error('取得文件列表失敗');
  return res.json();
}

export async function deleteDocument(id: string, code?: string): Promise<void> {
  const res = await fetch(`${baseUrl()}/api/documents/${id}`, {
    method: 'DELETE',
    headers: accessHeaders(code),
  });

  if (res.status === 429) throw new RateLimitError();
  if (!res.ok && res.status !== 204) throw new Error('刪除失敗，請稍後再試');
}

// ── Streaming query ───────────────────────────────────────────────────────────

/**
 * Sends a query and yields SSE tokens one by one as they arrive.
 * Uses fetch + ReadableStream (not EventSource) so we can POST a body
 * and pass a cancellation signal.
 *
 * SSE format expected from Spring SseEmitter:
 *   data:<token>\n\n
 *
 * Yields each non-empty token string. Caller should concatenate them.
 */
export async function* streamQuery(
  question: string,
  documentScope?: string,
  code?: string,
  signal?: AbortSignal
): AsyncGenerator<string, void, unknown> {
  const requestBody: Record<string, string> = { question };
  if (documentScope) requestBody.documentScope = documentScope;

  const res = await fetch(`${baseUrl()}/api/query/stream`, {
    method: 'POST',
    headers: jsonHeaders(code),
    body: JSON.stringify(requestBody),
    signal,
  });

  if (res.status === 429) throw new RateLimitError();
  if (!res.ok) throw new Error('查詢失敗，請稍後再試');
  if (!res.body) throw new Error('無法讀取回應串流');

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });

      // SSE messages are separated by double newlines
      const parts = buffer.split('\n\n');
      buffer = parts.pop() ?? '';

      for (const part of parts) {
        for (const line of part.split('\n')) {
          if (line.startsWith('data:')) {
            const token = line.slice(5).trim();
            if (token === '[DONE]') return;
            if (token) yield token;
          }
        }
      }
    }

    // Flush any remaining partial message
    for (const line of buffer.split('\n')) {
      if (line.startsWith('data:')) {
        const token = line.slice(5).trim();
        if (token && token !== '[DONE]') yield token;
      }
    }
  } finally {
    reader.releaseLock();
  }
}
