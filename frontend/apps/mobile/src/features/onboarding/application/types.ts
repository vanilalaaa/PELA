import type { CefrLevel, IsoDateTime } from '../../../domain/types';

/** UC-02 khảo sát định hướng (Onboarding.Survey; SRS 4.1.26 Learner profile). */
export type Occupation = 'high_school' | 'university' | 'working' | 'other';
export type DailyGoalMinutes = 5 | 10 | 20 | 30;

export interface SurveyAnswers {
  occupation: Occupation;
  occupationOther?: string | null;
  /** Tối đa 3 mục tiêu. */
  learningGoals: string[];
  interests: string[];
  dailyGoalMinutes: DailyGoalMinutes;
  selfAssessedLevel: CefrLevel | null;
}

export interface LearnerProfile extends SurveyAnswers {
  userId: string;
  updatedAt: IsoDateTime;
}

/** Trạng thái onboarding để chặn điều hướng (OpenAPI chưa có — xem docs/api-requests.md). */
export interface OnboardingStatus {
  surveyDone: boolean;
  placementDone: boolean;
  cefrLevel: CefrLevel | null;
}

/** Kết quả ánh xạ khảo sát → trình độ (Onboarding.Survey.Map, bảng do Admin cấu hình, server tính). */
export interface SurveyResult {
  mappedLevel: CefrLevel;
}

export type OnboardingNextStep = 'placement' | 'done';
