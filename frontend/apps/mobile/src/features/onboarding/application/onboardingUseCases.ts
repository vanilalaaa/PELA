import type { OnboardingRepository } from './OnboardingRepository';
import type { OnboardingNextStep, OnboardingStatus, SurveyAnswers, SurveyResult } from './types';

export const MAX_LEARNING_GOALS = 3;

/** Onboarding.Survey.SkipRule: ánh xạ ra A1 thì xếp thẳng A1, bỏ Placement Test (UC-03). */
export function nextStepAfterSurvey(result: SurveyResult): OnboardingNextStep {
  return result.mappedLevel === 'A1' ? 'done' : 'placement';
}

/** Đã xong onboarding khi khảo sát xong và (đã làm placement hoặc được xếp A1). */
export function isOnboardingDone(status: OnboardingStatus): boolean {
  return status.surveyDone && (status.placementDone || status.cefrLevel === 'A1');
}

export interface OnboardingUseCases {
  getStatus(): Promise<OnboardingStatus>;
  submitSurvey(answers: SurveyAnswers): Promise<SurveyResult & { next: OnboardingNextStep }>;
}

export function createOnboardingUseCases(repo: OnboardingRepository): OnboardingUseCases {
  return {
    getStatus: () => repo.getStatus(),
    async submitSurvey(answers) {
      const result = await repo.submitSurvey({ ...answers, learningGoals: answers.learningGoals.slice(0, MAX_LEARNING_GOALS) });
      return { ...result, next: nextStepAfterSurvey(result) };
    },
  };
}
