/**
 * Khảo sát định hướng 5 câu (UC-02, Onboarding.Survey.Present): bản nháp, kiểm tra từng câu, dựng
 * câu trả lời gửi server. Hàm thuần, có test.
 */
import type { CefrLevel } from '../../../domain/types';
import {
  DAILY_GOAL_MINUTES,
  INTERESTS,
  LEARNING_GOALS,
  OCCUPATIONS,
  type DailyGoalMinutes,
  type Interest,
  type LearningGoal,
  type Occupation,
  type SurveyAnswers,
} from './types';

export const SURVEY_STEP_COUNT = 5;
/** Tối đa 3 mục tiêu (UC-02 bước 1; learner_profile CHECK cardinality ≤ 3). */
export const MAX_GOALS = 3;
/** learner_profile.occupation_other varchar(100). */
export const OCCUPATION_OTHER_MAX = 100;
export const CEFR_LEVELS: readonly CefrLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];

/** 0 = vai trò · 1 = mục tiêu · 2 = chủ đề · 3 = thời gian · 4 = trình độ. */
export type SurveyStep = 0 | 1 | 2 | 3 | 4;

export interface SurveyDraft {
  step: SurveyStep;
  occupation: Occupation | null;
  occupationOther: string;
  learningGoals: LearningGoal[];
  interests: Interest[];
  dailyGoalMinutes: DailyGoalMinutes | null;
  selfAssessedLevel: CefrLevel | null;
}

export const EMPTY_SURVEY_DRAFT: SurveyDraft = {
  step: 0,
  occupation: null,
  occupationOther: '',
  learningGoals: [],
  interests: [],
  dailyGoalMinutes: null,
  selfAssessedLevel: null,
};

/** Bật/tắt một mục; đã đủ `max` thì không thêm (trả nguyên danh sách). */
export function toggleLimited<T>(list: readonly T[], item: T, max: number = Number.POSITIVE_INFINITY): T[] {
  if (list.includes(item)) return list.filter((x) => x !== item);
  if (list.length >= max) return [...list];
  return [...list, item];
}

/** Ô "Khác" hợp lệ: có chữ thật (không toàn khoảng trắng) và ≤ 100 ký tự sau khi trim. */
export function isOccupationOtherValid(text: string): boolean {
  const t = text.trim();
  return t.length > 0 && t.length <= OCCUPATION_OTHER_MAX;
}

/** Câu hiện tại đã trả lời đủ để sang câu sau chưa. */
export function isStepValid(step: SurveyStep, d: SurveyDraft): boolean {
  switch (step) {
    case 0:
      return d.occupation !== null && (d.occupation !== 'other' || isOccupationOtherValid(d.occupationOther));
    case 1:
      return d.learningGoals.length >= 1 && d.learningGoals.length <= MAX_GOALS;
    case 2:
      return d.interests.length >= 1;
    case 3:
      return d.dailyGoalMinutes !== null;
    case 4:
      return d.selfAssessedLevel !== null;
  }
}

/** Câu trả lời gửi server; null nếu bản nháp chưa đủ 5 câu. */
export function toSurveyAnswers(d: SurveyDraft): SurveyAnswers | null {
  const steps: SurveyStep[] = [0, 1, 2, 3, 4];
  if (!steps.every((s) => isStepValid(s, d))) return null;
  return {
    occupation: d.occupation as Occupation,
    occupationOther: d.occupation === 'other' ? d.occupationOther.trim() : null,
    learningGoals: [...d.learningGoals],
    interests: [...d.interests],
    dailyGoalMinutes: d.dailyGoalMinutes as DailyGoalMinutes,
    selfAssessedLevel: d.selfAssessedLevel,
  };
}

const isOneOf = <T>(values: readonly T[], v: unknown): v is T => values.includes(v as T);

/**
 * Đọc bản nháp đã lưu (dữ liệu cũ có thể sai khuôn): bỏ key lạ, giới hạn số mục, bước nằm trong 0–4.
 * Không đọc được thì null.
 */
export function parseSurveyDraft(raw: unknown): SurveyDraft | null {
  if (typeof raw !== 'object' || raw === null) return null;
  const r = raw as Record<string, unknown>;
  const step = typeof r.step === 'number' && Number.isInteger(r.step) ? Math.min(4, Math.max(0, r.step)) : 0;
  const list = <T>(values: readonly T[], v: unknown) => (Array.isArray(v) ? [...new Set(v.filter((x) => isOneOf(values, x)))] : []);
  return {
    step: step as SurveyStep,
    occupation: isOneOf(OCCUPATIONS, r.occupation) ? r.occupation : null,
    occupationOther: typeof r.occupationOther === 'string' ? r.occupationOther.slice(0, OCCUPATION_OTHER_MAX) : '',
    learningGoals: list(LEARNING_GOALS, r.learningGoals).slice(0, MAX_GOALS),
    interests: list(INTERESTS, r.interests),
    dailyGoalMinutes: isOneOf(DAILY_GOAL_MINUTES, r.dailyGoalMinutes) ? r.dailyGoalMinutes : null,
    selfAssessedLevel: isOneOf(CEFR_LEVELS, r.selfAssessedLevel) ? r.selfAssessedLevel : null,
  };
}

/**
 * Bước mở lại: không vượt quá câu đầu tiên chưa hợp lệ (nháp sửa tay/hỏng không nhảy cóc qua câu
 * chưa trả lời).
 */
export function resumeStep(d: SurveyDraft): SurveyStep {
  const steps: SurveyStep[] = [0, 1, 2, 3, 4];
  const firstInvalid = steps.find((s) => !isStepValid(s, d));
  return firstInvalid === undefined ? d.step : (Math.min(d.step, firstInvalid) as SurveyStep);
}
