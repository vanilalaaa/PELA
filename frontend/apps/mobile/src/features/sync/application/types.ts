import type { IsoDateTime } from '../../../domain/types';

/** UC-14 đồng bộ offline (SRS 4.1.23 Sync log). Ghi khi offline đi qua bảng outbox (AGENTS.md mục 5). */
export type SyncStatus = 'success' | 'partial' | 'failed';

export interface SyncResult {
  syncId: string;
  recordsSynced: number;
  status: SyncStatus;
  syncStartedAt: IsoDateTime;
  syncCompletedAt: IsoDateTime | null;
}

/** Một bản ghi outbox chờ gửi (id do client sinh để gửi lại an toàn). */
export interface OutboxEntry {
  id: string;
  kind: string;
  payload: unknown;
  createdAt: IsoDateTime;
}
