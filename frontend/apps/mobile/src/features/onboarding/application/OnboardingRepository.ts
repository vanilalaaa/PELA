import type { OnboardingStatus, SurveyAnswers, SurveyResult } from './types';

export interface OnboardingRepository {
  getStatus(): Promise<OnboardingStatus>;
  /** Server lưu câu trả lời, ánh xạ trình độ và chuyển trạng thái (A1 → done, còn lại → placementPending). */
  submitSurvey(answers: SurveyAnswers): Promise<SurveyResult>;
  /** UC-03 A1 (Placement.Test.StartFromBeginning): xếp A1, kết thúc onboarding. */
  startFromBeginning(): Promise<OnboardingStatus>;
}
