import type { SyncRepository } from './SyncRepository';

export type SyncUseCases = SyncRepository;

/** Đọc outbox thật (WatermelonDB) sẽ nối ở bước sync; hiện chỉ có cổng gửi. */
export function createSyncUseCases(repo: SyncRepository): SyncUseCases {
  return { push: (deviceId, entries) => repo.push(deviceId, entries), getLastSync: () => repo.getLastSync() };
}
