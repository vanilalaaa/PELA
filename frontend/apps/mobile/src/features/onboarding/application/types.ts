import type { CefrLevel, IsoDateTime } from '../../../domain/types';

/** UC-02 khảo sát định hướng (Onboarding.Survey; SRS 4.1.26 Learner profile). */
export const OCCUPATIONS = ['high_school', 'university', 'working', 'other'] as const;
export type Occupation = (typeof OCCUPATIONS)[number];
/** Mục tiêu học (learner_profile.learning_goals); `entertainment` theo Figma 09.2 — xem API-22. */
export const LEARNING_GOALS = ['school', 'certificate', 'daily_communication', 'travel', 'work', 'entertainment'] as const;
export type LearningGoal = (typeof LEARNING_GOALS)[number];
/** Chủ đề yêu thích (learner_profile.interests), dùng cho ngữ cảnh RAG — xem API-22. */
export const INTERESTS = ['family', 'school', 'travel', 'food', 'sport', 'music', 'movie', 'tech', 'animal', 'health'] as const;
export type Interest = (typeof INTERESTS)[number];
export const DAILY_GOAL_MINUTES = [5, 10, 20, 30] as const;
export type DailyGoalMinutes = (typeof DAILY_GOAL_MINUTES)[number];

export interface SurveyAnswers {
  occupation: Occupation;
  occupationOther?: string | null;
  /** Tối đa 3 mục tiêu. */
  learningGoals: LearningGoal[];
  interests: Interest[];
  dailyGoalMinutes: DailyGoalMinutes;
  selfAssessedLevel: CefrLevel | null;
}

export interface LearnerProfile extends SurveyAnswers {
  userId: string;
  updatedAt: IsoDateTime;
}

/**
 * Trạng thái onboarding để khôi phục đúng màn khi mở lại app (UC-02 POST-1, BR-19). Server lưu sau khi
 * khảo sát xong / làm Placement; OpenAPI chưa có — xem API-1 trong docs/api-requests.md.
 */
export type OnboardingStep = 'surveyPending' | 'placementPending' | 'done';

export interface OnboardingStatus {
  onboardingStatus: OnboardingStep;
  /** Trình độ định hướng từ khảo sát; có khi placementPending (Placement thi ở 1 bậc dưới). */
  orientedLevel: CefrLevel | null;
  /** Trình độ hiện tại khi done. */
  cefrLevel: CefrLevel | null;
}

/** Màn mở đầu của OnboardingStack khi mở app; `null` = đã xong, vào MainTabs. */
export type OnboardingEntry = { screen: 'Survey' } | { screen: 'PlacementIntro'; orientedLevel: CefrLevel } | null;

/** Kết quả ánh xạ khảo sát → trình độ (Onboarding.Survey.Map, bảng do Admin cấu hình, server tính). */
export interface SurveyResult {
  mappedLevel: CefrLevel;
}

export type OnboardingNextStep = 'placement' | 'done';
