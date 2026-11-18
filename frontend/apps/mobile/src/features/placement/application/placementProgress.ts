import type { PlacementAnswer, PlacementAttempt } from './types';

/**
 * Tiến độ lượt đang làm, lưu trên máy để thoát (✕) hoặc tắt app vẫn làm tiếp đúng câu. Không quay lại
 * câu trước: `index` chỉ tăng.
 */
export interface PlacementProgress {
  attempt: PlacementAttempt;
  /** Độ lệch giờ server − thiết bị đo lúc nhận lượt. */
  offsetMs: number;
  /** Câu đang làm (0-based). */
  index: number;
  /** questionId → chỉ số phương án đã chọn. */
  answers: Record<string, number>;
}

export function newProgress(attempt: PlacementAttempt, offsetMs: number): PlacementProgress {
  return { attempt, offsetMs, index: 0, answers: {} };
}

export function currentQuestion(p: PlacementProgress) {
  return p.attempt.questions[p.index] ?? null;
}

export function isLastQuestion(p: PlacementProgress): boolean {
  return p.index >= p.attempt.questions.length - 1;
}

/** Chọn phương án cho câu hiện tại (đổi được cho tới khi sang câu sau). */
export function answerCurrent(p: PlacementProgress, optionIndex: number): PlacementProgress {
  const q = currentQuestion(p);
  if (!q || optionIndex < 0 || optionIndex >= q.options.length) return p;
  return { ...p, answers: { ...p.answers, [q.questionId]: optionIndex } };
}

export function hasAnsweredCurrent(p: PlacementProgress): boolean {
  const q = currentQuestion(p);
  return !!q && p.answers[q.questionId] !== undefined;
}

/** Sang câu sau; chưa chọn đáp án thì không đi (không bỏ qua câu). */
export function goNext(p: PlacementProgress): PlacementProgress {
  if (!hasAnsweredCurrent(p) || isLastQuestion(p)) return p;
  return { ...p, index: p.index + 1 };
}

/** Đáp án gửi server; câu chưa làm (hết giờ) không gửi, server tính sai. */
export function toSubmitAnswers(p: PlacementProgress): PlacementAnswer[] {
  return p.attempt.questions.flatMap((q) => {
    const i = p.answers[q.questionId];
    const option = i === undefined ? undefined : q.options[i];
    return option === undefined ? [] : [{ questionId: q.questionId, answer: option }];
  });
}

/** Đọc tiến độ đã lưu; sai khuôn thì null. */
export function parsePlacementProgress(raw: unknown): PlacementProgress | null {
  if (typeof raw !== 'object' || raw === null) return null;
  const r = raw as Partial<PlacementProgress>;
  const a = r.attempt as Partial<PlacementAttempt> | undefined;
  if (!a || typeof a.attemptId !== 'string' || !Array.isArray(a.questions) || a.questions.length === 0) return null;
  if (typeof a.deadline !== 'string' || typeof a.serverTime !== 'string') return null;
  const count = a.questions.length;
  const index = typeof r.index === 'number' && Number.isInteger(r.index) ? Math.min(Math.max(0, r.index), count - 1) : 0;
  const answers: Record<string, number> = {};
  if (r.answers && typeof r.answers === 'object') {
    for (const [k, v] of Object.entries(r.answers)) if (typeof v === 'number' && Number.isInteger(v) && v >= 0) answers[k] = v;
  }
  return { attempt: a as PlacementAttempt, offsetMs: typeof r.offsetMs === 'number' ? r.offsetMs : 0, index, answers };
}
