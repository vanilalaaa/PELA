import { describe, expect, it } from 'vitest';
import {
  EMPTY_SURVEY_DRAFT,
  isOccupationOtherValid,
  isStepValid,
  parseSurveyDraft,
  resumeStep,
  toggleLimited,
  toSurveyAnswers,
  type SurveyDraft,
} from './survey';

const FULL: SurveyDraft = {
  step: 4,
  occupation: 'university',
  occupationOther: '',
  learningGoals: ['certificate', 'travel'],
  interests: ['family', 'movie'],
  dailyGoalMinutes: 10,
  selfAssessedLevel: 'B1',
};

describe('khảo sát định hướng (UC-02, Onboarding.Survey.Present)', () => {
  it('toggleLimited: bật/tắt, không vượt giới hạn', () => {
    expect(toggleLimited(['a', 'b'], 'c', 3)).toEqual(['a', 'b', 'c']);
    expect(toggleLimited(['a', 'b', 'c'], 'd', 3)).toEqual(['a', 'b', 'c']);
    expect(toggleLimited(['a', 'b', 'c'], 'b', 3)).toEqual(['a', 'c']);
    expect(toggleLimited([], 'x')).toEqual(['x']);
  });

  it('ô "Khác": không nhận chuỗi toàn khoảng trắng, tối đa 100 ký tự', () => {
    expect(isOccupationOtherValid('   ')).toBe(false);
    expect(isOccupationOtherValid('\n\t ')).toBe(false);
    expect(isOccupationOtherValid('  Giáo viên ')).toBe(true);
    expect(isOccupationOtherValid('a'.repeat(100))).toBe(true);
    expect(isOccupationOtherValid('a'.repeat(101))).toBe(false);
  });

  it('isStepValid theo từng câu', () => {
    const d = EMPTY_SURVEY_DRAFT;
    expect(isStepValid(0, d)).toBe(false);
    expect(isStepValid(0, { ...d, occupation: 'working' })).toBe(true);
    expect(isStepValid(0, { ...d, occupation: 'other', occupationOther: '  ' })).toBe(false);
    expect(isStepValid(0, { ...d, occupation: 'other', occupationOther: 'Giáo viên' })).toBe(true);
    expect(isStepValid(1, d)).toBe(false);
    expect(isStepValid(1, { ...d, learningGoals: ['school', 'work', 'travel'] })).toBe(true);
    expect(isStepValid(2, { ...d, interests: ['tech'] })).toBe(true);
    expect(isStepValid(3, { ...d, dailyGoalMinutes: 30 })).toBe(true);
    expect(isStepValid(4, { ...d, selfAssessedLevel: 'C2' })).toBe(true);
  });

  it('toSurveyAnswers: đủ 5 câu mới gửi; "Khác" được trim, vai trò khác thì bỏ', () => {
    expect(toSurveyAnswers(EMPTY_SURVEY_DRAFT)).toBeNull();
    expect(toSurveyAnswers({ ...FULL, occupationOther: 'rác' })).toMatchObject({ occupation: 'university', occupationOther: null });
    expect(toSurveyAnswers({ ...FULL, occupation: 'other', occupationOther: '  Giáo viên  ' })?.occupationOther).toBe('Giáo viên');
    expect(toSurveyAnswers({ ...FULL, selfAssessedLevel: null })).toBeNull();
  });

  it('parseSurveyDraft: bỏ key lạ, giới hạn 3 mục tiêu, bước trong 0–4, dữ liệu hỏng → null', () => {
    expect(parseSurveyDraft(null)).toBeNull();
    expect(parseSurveyDraft('x')).toBeNull();
    const d = parseSurveyDraft({
      step: 9,
      occupation: 'pilot',
      learningGoals: ['school', 'hack', 'work', 'travel', 'certificate', 'school'],
      interests: ['food', 42, 'food'],
      dailyGoalMinutes: 15,
      selfAssessedLevel: 'D1',
    });
    expect(d).toEqual({
      step: 4,
      occupation: null,
      occupationOther: '',
      learningGoals: ['school', 'work', 'travel'],
      interests: ['food'],
      dailyGoalMinutes: null,
      selfAssessedLevel: null,
    });
  });

  it('resumeStep: không nhảy qua câu chưa trả lời', () => {
    expect(resumeStep(FULL)).toBe(4);
    expect(resumeStep({ ...FULL, step: 3 })).toBe(3);
    expect(resumeStep({ ...FULL, interests: [] })).toBe(2);
    expect(resumeStep({ ...EMPTY_SURVEY_DRAFT, step: 3 })).toBe(0);
  });
});
