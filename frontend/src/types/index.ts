export interface DocumentItem {
  documentId: string;
  name: string;
  status: 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED';
  chunkCount: number;
}

export interface UploadResponse {
  documentId: string;
  status: string;
}

export class RateLimitError extends Error {
  constructor() {
    super('rate_limit');
    this.name = 'RateLimitError';
  }
}
