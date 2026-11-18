import type { SurveyDraft } from './survey';

/**
 * Nơi lưu bản nháp khảo sát trên thiết bị (ROB-1): mở lại app giữa chừng vẫn ở đúng câu, đáp án còn.
 * Theo từng tài khoản; gửi thành công thì xoá.
 */
export interface SurveyDraftStore {
  load(userId: string): Promise<SurveyDraft | null>;
  save(userId: string, draft: SurveyDraft): Promise<void>;
  clear(): Promise<void>;
}
