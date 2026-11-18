import { describe, expect, it } from 'vitest';
import { createTestDatabase } from '../../../test/createTestDatabase';
import { createOnboardingUseCases } from '../application/onboardingUseCases';
import type { OnboardingRepository } from '../application/OnboardingRepository';
import type { SurveyDraft } from '../application/survey';
import { SURVEY_DRAFT_KEY, createWatermelonSurveyDraftStore } from './WatermelonSurveyDraftStore';

const DRAFT: SurveyDraft = {
  step: 3,
  occupation: 'other',
  occupationOther: 'Giáo viên',
  learningGoals: ['work'],
  interests: ['tech', 'music'],
  dailyGoalMinutes: null,
  selfAssessedLevel: null,
};

const repo = (fail = false): OnboardingRepository => ({
  getStatus: async () => ({ onboardingStatus: 'surveyPending', orientedLevel: null, cefrLevel: null }),
  submitSurvey: async () => {
    if (fail) throw new Error('offline');
    return { mappedLevel: 'B1' };
  },
  startFromBeginning: async () => ({ onboardingStatus: 'done', orientedLevel: null, cefrLevel: 'A1' }),
});

function setup(fail = false) {
  const db = createTestDatabase();
  const store = createWatermelonSurveyDraftStore(async () => db);
  return { db, store, useCases: createOnboardingUseCases(repo(fail), store) };
}

describe('bản nháp khảo sát trên WatermelonDB localStorage (ROB-1)', () => {
  it('lưu rồi khôi phục đúng câu và đáp án', async () => {
    const { useCases } = setup();
    await useCases.saveSurveyDraft('u1', DRAFT);
    expect(await useCases.loadSurveyDraft('u1')).toEqual(DRAFT);
  });

  it('tài khoản khác không thấy nháp', async () => {
    const { useCases } = setup();
    await useCases.saveSurveyDraft('u1', DRAFT);
    expect(await useCases.loadSurveyDraft('u2')).toBeNull();
  });

  it('gửi thành công thì xoá nháp; gửi lỗi thì giữ', async () => {
    const ok = setup();
    await ok.useCases.saveSurveyDraft('u1', DRAFT);
    await ok.useCases.submitSurvey({ occupation: 'working', learningGoals: ['work'], interests: ['tech'], dailyGoalMinutes: 5, selfAssessedLevel: 'B1' });
    expect(await ok.useCases.loadSurveyDraft('u1')).toBeNull();

    const bad = setup(true);
    await bad.useCases.saveSurveyDraft('u1', DRAFT);
    await expect(
      bad.useCases.submitSurvey({ occupation: 'working', learningGoals: ['work'], interests: ['tech'], dailyGoalMinutes: 5, selfAssessedLevel: 'B1' }),
    ).rejects.toThrow('offline');
    expect(await bad.useCases.loadSurveyDraft('u1')).toEqual(DRAFT);
  });

  it('nháp hỏng: đọc được phần hợp lệ, bước lùi về câu đầu chưa trả lời', async () => {
    const { db, useCases } = setup();
    await db.localStorage.set(SURVEY_DRAFT_KEY, { userId: 'u1', draft: { step: 4, occupation: 'working', learningGoals: ['nope'] } });
    expect(await useCases.loadSurveyDraft('u1')).toMatchObject({ step: 1, occupation: 'working', learningGoals: [] });
  });
});
