import onboardingFixture from '@pela/fixtures/mock/onboarding.json';
import { clone, fixture, mockCall, readMockOnboarding, writeMockOnboarding } from '../../../core/infrastructure/mock';
import type { KeyValueStore } from '../../../core/infrastructure/storage/keyValueStore';
import type { OnboardingRepository } from '../application/OnboardingRepository';
import type { OnboardingStatus, SurveyResult } from '../application/types';

interface OnboardingFixture {
  surveyResult: SurveyResult;
}

const data = fixture<OnboardingFixture>(onboardingFixture);

/**
 * Onboarding giả lập có lưu trạng thái (giống server): khảo sát xong → placementPending (A1 → done);
 * "Bắt đầu từ đầu" → done A1. Placement mock ghi done khi nộp bài.
 */
export function createMockOnboardingRepository(store: KeyValueStore): OnboardingRepository {
  return {
    getStatus: async () => {
      const state = await readMockOnboarding(store);
      return mockCall(() => state as OnboardingStatus, state as OnboardingStatus);
    },
    // Mock ánh xạ = trình độ tự đánh giá (server thật dùng bảng survey_level_rule): chọn A1 để thử nhánh
    // bỏ Placement, bậc khác để sang Placement. Không có thì dùng mẫu trong fixture.
    submitSurvey: async (answers) => {
      const result: SurveyResult = answers.selfAssessedLevel ? { mappedLevel: answers.selfAssessedLevel } : clone(data.surveyResult);
      const level = result.mappedLevel;
      const out = await mockCall(() => result, result);
      await writeMockOnboarding(store, {
        onboardingStatus: level === 'A1' ? 'done' : 'placementPending',
        orientedLevel: level,
        cefrLevel: level === 'A1' ? 'A1' : null,
      });
      return out;
    },
    startFromBeginning: async () => {
      const state: OnboardingStatus = { onboardingStatus: 'done', orientedLevel: null, cefrLevel: 'A1' };
      const out = await mockCall(() => state, state);
      await writeMockOnboarding(store, state);
      return out;
    },
  };
}
