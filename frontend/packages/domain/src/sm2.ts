/**
 * Thuật toán SM-2 cho ôn tập từ vựng.
 * Server là trọng tài (BR-23); bản TS này dùng để hiển thị offline và phải khớp
 * với backend Java qua test vector `@pela/fixtures/sm2-cases.json`.
 */

export interface Sm2State {
  easinessFactor: number;
  repetitions: number;
  intervalDays: number;
  consecutiveFailures: number;
  needsReinforcement: boolean;
}

export interface Sm2Review {
  correct: boolean;
}

/** Hằng số theo BR-26. Giá trị chính thức đọc từ cấu hình; đây là mặc định để test. */
export const SM2_DEFAULTS = {
  initialEasinessFactor: 2.5,
  minEasinessFactor: 1.3,
  qualityCorrect: 5,
  qualityWrong: 2,
  failuresBeforeReinforcement: 3,
} as const;

/**
 * TODO(BR-26): đúng → q=5, sai → q=2, EF sàn 1.3,
 * sai 3 lần liên tiếp → EF = 1.3 và needsReinforcement = true.
 */
export function applyReview(_state: Sm2State, _review: Sm2Review): Sm2State {
  throw new Error('Not implemented: SM-2 (BR-26)');
}
