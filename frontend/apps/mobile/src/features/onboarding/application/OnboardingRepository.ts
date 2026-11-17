import type { OnboardingStatus, SurveyAnswers, SurveyResult } from './types';

export interface OnboardingRepository {
  getStatus(): Promise<OnboardingStatus>;
  submitSurvey(answers: SurveyAnswers): Promise<SurveyResult>;
}
