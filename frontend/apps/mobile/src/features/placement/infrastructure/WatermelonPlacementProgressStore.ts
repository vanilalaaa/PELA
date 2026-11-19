import type { Database } from '@nozbe/watermelondb';
import type { PlacementProgressStore } from '../application/PlacementProgressStore';

export const PLACEMENT_PROGRESS_KEY = 'placement.progress';

/** Tiến độ lượt Placement trên `database.localStorage` của WatermelonDB, kèm userId. */
export function createWatermelonPlacementProgressStore(getDatabase: () => Promise<Database>): PlacementProgressStore {
  return {
    async load(userId) {
      const db = await getDatabase();
      const stored = await db.localStorage.get<{ userId: string; progress: unknown }>(PLACEMENT_PROGRESS_KEY);
      return stored && stored.userId === userId ? stored.progress : null;
    },
    async save(userId, progress) {
      const db = await getDatabase();
      await db.localStorage.set(PLACEMENT_PROGRESS_KEY, { userId, progress });
    },
    async clear() {
      const db = await getDatabase();
      await db.localStorage.remove(PLACEMENT_PROGRESS_KEY);
    },
  };
}
