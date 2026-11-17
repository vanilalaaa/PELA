/**
 * Khai báo route cho toàn app, theo bảng route trong docs/ui/flow-review.md. Dùng React Navigation
 * (native-stack + bottom-tabs) — quyết định đã chốt, KHÔNG đổi sang Expo Router (AGENTS.md mobile).
 * Params có kiểu; id do server hoặc client cấp. Màn kết quả của một phiên là state trong route phiên.
 */
import type { NavigatorScreenParams } from '@react-navigation/native';
import type { CefrLevel } from '../../domain/types';

/** AuthStack (UC-01): 01 Welcome, 02/03 Register, 04 Verify Email, 05/06 Login, 44b–44d quên mật khẩu. */
export type AuthStackParamList = {
  Welcome: undefined;
  Login: undefined;
  Register: { fullName?: string } | undefined;
  VerifyEmail: { email: string; fullName?: string };
  ForgotPassword: { email?: string } | undefined;
  ForgotPasswordOtp: { email: string };
  ResetPassword: { resetToken: string };
};

/** OnboardingStack (UC-02, UC-03): 09.1–09.5 Survey, 10 Analyzing, 11–15 Placement. */
export type OnboardingStackParamList = {
  Survey: undefined;
  SurveyAnalyzing: undefined;
  PlacementIntro: { orientedLevel: CefrLevel };
  PlacementTest: { orientedLevel: CefrLevel };
  PlacementResult: { placedLevel: CefrLevel; passed: boolean; score: number };
};

/** MainTabs — 5 tab theo figma-map: 16 Home, 17 Roadmap, 35 Practice Hub, 27 Leaderboard, 28 Profile. */
export type MainTabParamList = {
  Home: undefined;
  Roadmap: undefined;
  Practice: undefined;
  Leaderboard: undefined;
  Profile: undefined;
};

export type WritingComposeParams = {
  promptSource: 'bank' | 'ai_generated' | 'freeform';
  promptQuestionId?: string;
  ragCacheId?: string;
  draftId?: string;
};

export type RootStackParamList = {
  Splash: undefined;
  Auth: NavigatorScreenParams<AuthStackParamList> | undefined;
  Onboarding: NavigatorScreenParams<OnboardingStackParamList> | undefined;
  Main: NavigatorScreenParams<MainTabParamList> | undefined;
  // Phiên học toàn màn (ẩn BottomNav).
  LessonSession: { lessonId: string };
  FinalTestIntro: { topicId: string };
  FinalTestSession: { topicId: string; attemptId: string };
  ChallengeSession: { type: 'daily' | 'weekly' };
  ListeningTopics: undefined;
  ListeningSession: { clipId: string };
  WritingHub: undefined;
  WritingCompose: WritingComposeParams;
  WritingGrading: { submissionId: string };
  WritingResult: { submissionId: string };
  WritingHistory: undefined;
  // Hồ sơ, xã hội, gamification.
  Streak: undefined;
  Badges: undefined;
  ProgressReport: { from?: string; to?: string } | undefined;
  Friends: undefined;
  FriendRequests: undefined;
  UserProfile: { userId: string };
  ReportUser: { userId: string };
  // Cài đặt.
  Settings: undefined;
  NotificationSettings: undefined;
  NotificationInbox: undefined;
  BlockedUsers: undefined;
  EditProfile: undefined;
  ChangePassword: undefined;
  ChangeEmail: undefined;
  DeleteAccount: undefined;
  ChangeLevel: undefined;
  LevelUpTest: { targetLevel: CefrLevel; attemptId: string };
  RateApp: undefined;
  ReportProblem: { questionId?: string; clipId?: string } | undefined;
  // Màn dev — chỉ đăng ký khi __DEV__.
  DevUiKit: undefined;
  DevUiKitActions: undefined;
  DevUiKitDisplay: undefined;
  DevUiKitAuth: undefined;
  DevDbCheck: undefined;
};

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace ReactNavigation {
    interface RootParamList extends RootStackParamList {}
  }
}
