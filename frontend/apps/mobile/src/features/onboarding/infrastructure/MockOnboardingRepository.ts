import onboardingFixture from '@pela/fixtures/mock/onboarding.json';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type { OnboardingRepository } from '../application/OnboardingRepository';
import type { OnboardingStatus, SurveyResult } from '../application/types';

interface OnboardingFixture {
  status: OnboardingStatus;
  surveyResult: SurveyResult;
}

const data = fixture<OnboardingFixture>(onboardingFixture);

export function createMockOnboardingRepository(): OnboardingRepository {
  const empty: OnboardingStatus = { surveyDone: false, placementDone: false, cefrLevel: null };
  return {
    getStatus: () => mockCall(() => clone(data.status), empty),
    submitSurvey: () => mockCall(() => clone(data.surveyResult), clone(data.surveyResult)),
  };
}
