import type { OnboardingRepository } from './OnboardingRepository';
import type { OnboardingEntry, OnboardingNextStep, OnboardingStatus, SurveyAnswers, SurveyResult } from './types';

export const MAX_LEARNING_GOALS = 3;

/** Onboarding.Survey.SkipRule: ánh xạ ra A1 thì xếp thẳng A1, bỏ Placement Test (UC-03). */
export function nextStepAfterSurvey(result: SurveyResult): OnboardingNextStep {
  return result.mappedLevel === 'A1' ? 'done' : 'placement';
}

/** Đã xong onboarding (khảo sát xong và đã xếp trình độ, kể cả A1 bỏ Placement). */
export function isOnboardingDone(status: OnboardingStatus): boolean {
  return status.onboardingStatus === 'done';
}

/**
 * Màn mở đầu khi mở lại app: surveyPending → Survey; placementPending → PlacementIntro (BR-19: chưa
 * làm bài thì làm tiếp, không làm lại khảo sát); done → null (MainTabs). Thiếu orientedLevel thì coi
 * như dữ liệu hỏng và cho làm lại khảo sát.
 */
export function onboardingEntry(status: OnboardingStatus): OnboardingEntry {
  if (status.onboardingStatus === 'done') return null;
  if (status.onboardingStatus === 'placementPending' && status.orientedLevel) {
    return { screen: 'PlacementIntro', orientedLevel: status.orientedLevel };
  }
  return { screen: 'Survey' };
}

export interface OnboardingUseCases {
  getStatus(): Promise<OnboardingStatus>;
  submitSurvey(answers: SurveyAnswers): Promise<SurveyResult & { next: OnboardingNextStep }>;
  /** UC-03 A1: bỏ Placement, xếp A1 (không làm bài được nữa, BR-19). */
  startFromBeginning(): Promise<OnboardingStatus>;
}

export function createOnboardingUseCases(repo: OnboardingRepository): OnboardingUseCases {
  return {
    getStatus: () => repo.getStatus(),
    async submitSurvey(answers) {
      const result = await repo.submitSurvey({ ...answers, learningGoals: answers.learningGoals.slice(0, MAX_LEARNING_GOALS) });
      return { ...result, next: nextStepAfterSurvey(result) };
    },
    startFromBeginning: () => repo.startFromBeginning(),
  };
}
