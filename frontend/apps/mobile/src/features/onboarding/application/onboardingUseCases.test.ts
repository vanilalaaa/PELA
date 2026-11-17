import { describe, expect, it, vi } from 'vitest';
import { createOnboardingUseCases, isOnboardingDone, nextStepAfterSurvey } from './onboardingUseCases';
import type { SurveyAnswers } from './types';

const ANSWERS: SurveyAnswers = {
  occupation: 'working',
  learningGoals: ['a', 'b', 'c', 'd'],
  interests: [],
  dailyGoalMinutes: 20,
  selfAssessedLevel: null,
};

describe('onboarding (UC-02, Onboarding.Survey.SkipRule)', () => {
  it('ra A1 thì bỏ Placement, còn lại làm Placement', () => {
    expect(nextStepAfterSurvey({ mappedLevel: 'A1' })).toBe('done');
    expect(nextStepAfterSurvey({ mappedLevel: 'A2' })).toBe('placement');
    expect(nextStepAfterSurvey({ mappedLevel: 'C1' })).toBe('placement');
  });
  it('xong onboarding: khảo sát xong và (đã placement hoặc ở A1)', () => {
    expect(isOnboardingDone({ surveyDone: false, placementDone: false, cefrLevel: null })).toBe(false);
    expect(isOnboardingDone({ surveyDone: true, placementDone: false, cefrLevel: 'B1' })).toBe(false);
    expect(isOnboardingDone({ surveyDone: true, placementDone: false, cefrLevel: 'A1' })).toBe(true);
    expect(isOnboardingDone({ surveyDone: true, placementDone: true, cefrLevel: 'B1' })).toBe(true);
  });
  it('gửi tối đa 3 mục tiêu và trả bước kế tiếp', async () => {
    const submitSurvey = vi.fn(async (_answers: SurveyAnswers) => ({ mappedLevel: 'B1' as const }));
    const uc = createOnboardingUseCases({ getStatus: vi.fn(), submitSurvey });
    expect(await uc.submitSurvey(ANSWERS)).toEqual({ mappedLevel: 'B1', next: 'placement' });
    expect(submitSurvey.mock.calls[0]?.[0]?.learningGoals).toEqual(['a', 'b', 'c']);
  });
});
