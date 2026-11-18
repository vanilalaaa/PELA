import { describe, expect, it, vi } from 'vitest';
import { createOnboardingUseCases, isOnboardingDone, nextStepAfterSurvey, onboardingEntry } from './onboardingUseCases';
import type { SurveyAnswers } from './types';

const ANSWERS: SurveyAnswers = {
  occupation: 'working',
  learningGoals: ['school', 'certificate', 'travel', 'work'],
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
  it('màn mở đầu theo trạng thái (UC-02 POST-1, BR-19)', () => {
    expect(onboardingEntry({ onboardingStatus: 'surveyPending', orientedLevel: null, cefrLevel: null })).toEqual({ screen: 'Survey' });
    expect(onboardingEntry({ onboardingStatus: 'placementPending', orientedLevel: 'B2', cefrLevel: null })).toEqual({
      screen: 'PlacementIntro',
      orientedLevel: 'B2',
    });
    expect(onboardingEntry({ onboardingStatus: 'done', orientedLevel: 'C1', cefrLevel: 'B1' })).toBeNull();
  });
  it('placementPending thiếu orientedLevel → làm lại khảo sát (dữ liệu hỏng)', () => {
    expect(onboardingEntry({ onboardingStatus: 'placementPending', orientedLevel: null, cefrLevel: null })).toEqual({ screen: 'Survey' });
  });
  it('isOnboardingDone chỉ đúng khi done', () => {
    expect(isOnboardingDone({ onboardingStatus: 'placementPending', orientedLevel: 'B1', cefrLevel: null })).toBe(false);
    expect(isOnboardingDone({ onboardingStatus: 'done', orientedLevel: null, cefrLevel: 'A1' })).toBe(true);
  });
  it('gửi tối đa 3 mục tiêu và trả bước kế tiếp', async () => {
    const submitSurvey = vi.fn(async (_answers: SurveyAnswers) => ({ mappedLevel: 'B1' as const }));
    const uc = createOnboardingUseCases({ getStatus: vi.fn(), submitSurvey, startFromBeginning: vi.fn() });
    expect(await uc.submitSurvey(ANSWERS)).toEqual({ mappedLevel: 'B1', next: 'placement' });
    expect(submitSurvey.mock.calls[0]?.[0]?.learningGoals).toEqual(['school', 'certificate', 'travel']);
  });
});
