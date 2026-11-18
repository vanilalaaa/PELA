/**
 * Màn tạm cho mọi route chưa dựng (P3 thay bằng màn thật trong features/<f>/presentation).
 * Nút trong mỗi màn đi theo sơ đồ luồng ở docs/ui/flow-review.md để thử điều hướng.
 */
import { placeholderScreen, type RootNav } from '../PlaceholderScreen';

const TAB = ['top'] as const;
// Id mẫu để thử luồng; màn thật lấy id từ dữ liệu, id lượt do client sinh (UUID) khi có bộ sinh.
const DEMO = 'demo';
// Lối vào màn dev (chỉ bản dev).
const DEV = __DEV__
  ? [{ label: 'devUiKit', variant: 'ghost' as const, run: (n: RootNav) => n.navigate('DevUiKit') }]
  : [];

// ---- MainTabs
export const HomeScreen = placeholderScreen({
  title: 'Home',
  figma: '16 · USE-2, GAP-9',
  edges: TAB,
  actions: [
    { label: 'dailyChallenge', variant: 'star', run: (n) => n.navigate('ChallengeSession', { type: 'daily' }) },
    { label: 'continueLesson', run: (n) => n.navigate('LessonSession', { lessonId: DEMO }) },
    { label: 'inbox', run: (n) => n.navigate('NotificationInbox') },
    ...DEV,
  ],
});
export const RoadmapScreen = placeholderScreen({
  title: 'Roadmap',
  figma: '17',
  edges: TAB,
  actions: [
    { label: 'openLesson', run: (n) => n.navigate('LessonSession', { lessonId: DEMO }) },
    { label: 'finalTest', run: (n) => n.navigate('FinalTestIntro', { topicId: DEMO }) },
  ],
});
export const PracticeScreen = placeholderScreen({
  title: 'Practice',
  figma: '35 · GAP-1',
  edges: TAB,
  actions: [
    { label: 'dailyChallenge', variant: 'star', run: (n) => n.navigate('ChallengeSession', { type: 'daily' }) },
    { label: 'weeklyChallenge', run: (n) => n.navigate('ChallengeSession', { type: 'weekly' }) },
    { label: 'listening', run: (n) => n.navigate('ListeningTopics') },
    { label: 'writing', run: (n) => n.navigate('WritingHub') },
  ],
});
export const LeaderboardScreen = placeholderScreen({
  title: 'Leaderboard',
  figma: '27 · FLOW-11',
  edges: TAB,
  actions: [{ label: 'userProfile', run: (n) => n.navigate('UserProfile', { userId: DEMO }) }],
});
export const ProfileScreen = placeholderScreen({
  title: 'Profile',
  figma: '28 · FLOW-19',
  edges: TAB,
  actions: [
    { label: 'streak', run: (n) => n.navigate('Streak') },
    { label: 'badges', run: (n) => n.navigate('Badges') },
    { label: 'progressReport', run: (n) => n.navigate('ProgressReport') },
    { label: 'friends', run: (n) => n.navigate('Friends') },
    { label: 'settings', run: (n) => n.navigate('Settings') },
  ],
});

// ---- Root: phiên học
export const LessonSessionScreen = placeholderScreen({ title: 'LessonSession', figma: '18–24 · FLOW-2, FLOW-18' });
export const FinalTestIntroScreen = placeholderScreen({
  title: 'FinalTestIntro',
  figma: '25 · FLOW-10',
  actions: [{ label: 'startTest', variant: 'primary', run: (n) => n.navigate('FinalTestSession', { topicId: DEMO, attemptId: DEMO }) }],
});
export const FinalTestSessionScreen = placeholderScreen({ title: 'FinalTestSession', figma: 'GAP-4, 26/26b/26c · FLOW-1' });
export const ChallengeSessionScreen = placeholderScreen({ title: 'ChallengeSession', figma: '36, 36b · FLOW-2, FLOW-14' });
export const ListeningTopicsScreen = placeholderScreen({
  title: 'ListeningTopics',
  figma: '37',
  actions: [{ label: 'openClip', run: (n) => n.navigate('ListeningSession', { clipId: DEMO }) }],
});
export const ListeningSessionScreen = placeholderScreen({ title: 'ListeningSession', figma: '38–38d, 38b' });
export const WritingHubScreen = placeholderScreen({
  title: 'WritingHub',
  figma: '39 · GAP-11, FLOW-8',
  actions: [
    { label: 'compose', variant: 'primary', run: (n) => n.navigate('WritingCompose', { promptSource: 'freeform' }) },
    { label: 'writingHistory', run: (n) => n.navigate('WritingHistory') },
  ],
});
export const WritingComposeScreen = placeholderScreen({
  title: 'WritingCompose',
  figma: '40 · FLOW-6',
  actions: [{ label: 'submit', variant: 'primary', run: (n) => n.navigate('WritingGrading', { submissionId: DEMO }) }],
});
export const WritingGradingScreen = placeholderScreen({
  title: 'WritingGrading',
  figma: '40b, 41b · GAP-10, FLOW-9',
  actions: [{ label: 'viewResult', run: (n) => n.replace('WritingResult', { submissionId: DEMO }) }],
});
export const WritingResultScreen = placeholderScreen({ title: 'WritingResult', figma: '41 · FLOW-7' });
export const WritingHistoryScreen = placeholderScreen({ title: 'WritingHistory', figma: 'FLOW-20' });

// ---- Root: hồ sơ, xã hội
export const StreakScreen = placeholderScreen({ title: 'Streak', figma: '33, 33c, 33d · FLOW-17' });
export const BadgesScreen = placeholderScreen({ title: 'Badges', figma: '32' });
export const ProgressReportScreen = placeholderScreen({ title: 'ProgressReport', figma: 'FLOW-19 · PELA-RPT-1' });
export const FriendsScreen = placeholderScreen({
  title: 'Friends',
  figma: '34',
  actions: [
    { label: 'friendRequests', run: (n) => n.navigate('FriendRequests') },
    { label: 'userProfile', run: (n) => n.navigate('UserProfile', { userId: DEMO }) },
  ],
});
export const FriendRequestsScreen = placeholderScreen({ title: 'FriendRequests', figma: '34b' });
export const UserProfileScreen = placeholderScreen({
  title: 'UserProfile',
  figma: '29, 29b, 29c',
  actions: [{ label: 'report', variant: 'danger', run: (n) => n.navigate('ReportUser', { userId: DEMO }) }],
});
export const ReportUserScreen = placeholderScreen({ title: 'ReportUser', figma: '30, 30b' });

// ---- Root: cài đặt
export const NotificationSettingsScreen = placeholderScreen({ title: 'NotificationSettings', figma: '42b' });
export const NotificationInboxScreen = placeholderScreen({ title: 'NotificationInbox', figma: 'GAP-14 · FLOW-12' });
export const BlockedUsersScreen = placeholderScreen({ title: 'BlockedUsers', figma: '42c' });
export const EditProfileScreen = placeholderScreen({ title: 'EditProfile', figma: '43' });
export const ChangePasswordScreen = placeholderScreen({ title: 'ChangePassword', figma: '44' });
export const ChangeEmailScreen = placeholderScreen({ title: 'ChangeEmail', figma: '45' });
export const DeleteAccountScreen = placeholderScreen({ title: 'DeleteAccount', figma: '46, 46b' });
export const ChangeLevelScreen = placeholderScreen({
  title: 'ChangeLevel',
  figma: '47 · FLOW-3, FLOW-5',
  actions: [{ label: 'levelUpTest', variant: 'primary', run: (n) => n.navigate('LevelUpTest', { targetLevel: 'B1', attemptId: DEMO }) }],
});
export const LevelUpTestScreen = placeholderScreen({ title: 'LevelUpTest', figma: '47b, 47c, 47d' });
export const RateAppScreen = placeholderScreen({ title: 'RateApp', figma: '49' });
export const ReportProblemScreen = placeholderScreen({ title: 'ReportProblem', figma: 'GAP-3' });
