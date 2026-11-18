import type { Database } from '@nozbe/watermelondb';
import type { SurveyDraftStore } from '../application/SurveyDraftStore';
import type { SurveyDraft } from '../application/survey';

/** Khoá trong `database.localStorage` (bảng local_storage của WatermelonDB, không đồng bộ lên server). */
export const SURVEY_DRAFT_KEY = 'onboarding.surveyDraft';

interface StoredDraft {
  userId: string;
  draft: SurveyDraft;
}

/**
 * Bản nháp khảo sát lưu bằng `database.localStorage` của WatermelonDB (ROB-1, không thêm dependency).
 * Ghi kèm userId: đăng nhập tài khoản khác không thấy nháp cũ.
 */
export function createWatermelonSurveyDraftStore(getDatabase: () => Promise<Database>): SurveyDraftStore {
  return {
    async load(userId) {
      const db = await getDatabase();
      const stored = await db.localStorage.get<StoredDraft>(SURVEY_DRAFT_KEY);
      return stored && stored.userId === userId ? stored.draft : null;
    },
    async save(userId, draft) {
      const db = await getDatabase();
      await db.localStorage.set<StoredDraft>(SURVEY_DRAFT_KEY, { userId, draft });
    },
    async clear() {
      const db = await getDatabase();
      await db.localStorage.remove(SURVEY_DRAFT_KEY);
    },
  };
}
