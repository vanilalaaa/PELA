import type { OutboxEntry, SyncResult } from './types';

export interface SyncRepository {
  /** Gửi lô bản ghi outbox; server tính lại kết quả (BR-23) và trả số bản ghi đã nhận. */
  push(deviceId: string, entries: OutboxEntry[]): Promise<SyncResult>;
  getLastSync(): Promise<SyncResult | null>;
}
