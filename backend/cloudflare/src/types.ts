export interface Env {
  DB: D1Database;
  FILES?: R2Bucket;
  MEDIA?: KVNamespace;
  BOOTSTRAP_TOKEN?: string;
  STORAGE_SECRET?: string;
  ENVIRONMENT?: string;
  AI_PROVIDER?: string;
  AI_API_KEY?: string;
  AI_MODEL?: string;
  AI_BASE_URL?: string;
}

export interface Principal {
  userId: string;
  tokenId: string;
  scopes: string[];
}

export type Row = Record<string, unknown>;
