import syncFixture from '@pela/fixtures/mock/sync.json';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type { SyncRepository } from '../application/SyncRepository';
import type { SyncResult } from '../application/types';

const data = fixture<SyncResult>(syncFixture);

export function createMockSyncRepository(): SyncRepository {
  return {
    push: (_deviceId, entries) =>
      mockCall(() => ({ ...clone(data), recordsSynced: entries.length }), { ...clone(data), recordsSynced: 0 }),
    getLastSync: () => mockCall(() => clone(data), null),
  };
}
