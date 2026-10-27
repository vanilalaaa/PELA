/**
 * Quy đổi kết quả phiên học sang Star.
 * Server là trọng tài (BR-23); bản TS chỉ để hiển thị tạm thời khi offline.
 */

export type SessionKind = 'lesson' | 'listening' | 'daily_challenge' | 'weekly_challenge';

export interface SessionResult {
  kind: SessionKind;
  correctCount: number;
  totalCount: number;
}

/** 0–5 sao/phiên, Weekly Challenge tối đa 6. */
export const MAX_STARS: Record<SessionKind, number> = {
  lesson: 5,
  listening: 5,
  daily_challenge: 5,
  weekly_challenge: 6,
};

/** TODO: quy đổi tỉ lệ đúng → số sao (ngưỡng theo SRS, đọc từ cấu hình). */
export function starsForSession(_result: SessionResult): number {
  throw new Error('Not implemented: star conversion');
}

/** TODO(BR-36): làm lại Lesson/bài nghe chỉ cộng phần tăng so với kết quả tốt nhất. */
export function starsToAward(_previousBest: number, _current: number): number {
  throw new Error('Not implemented: BR-36');
}

/** Final Test: 3 lượt, trượt hết thì khoá; cần 30 sao để mở 1 lượt (BR-20). */
export const FINAL_TEST_RULES = {
  attempts: 3,
  starsToUnlockAttempt: 30,
} as const;
