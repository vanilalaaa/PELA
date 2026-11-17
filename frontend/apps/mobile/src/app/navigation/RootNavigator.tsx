import React from 'react';
import { ActivityIndicator, View } from 'react-native';
import { NavigationContainer, useNavigationContainerRef, type LinkingOptions } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../core/ui/theme';
import { UiKitScreen } from '../dev/UiKitScreen';
import { ActionsInputsPage } from '../dev/uikit/ActionsInputsPage';
import { DisplayPage } from '../dev/uikit/DisplayPage';
import { AuthKitPage } from '../dev/uikit/AuthKitPage';
import { DbCheckScreen } from '../dev/DbCheckScreen';
import { useSession } from '../session/sessionStore';
import { selectRootBranch } from '../session/rootBranch';
import { useSessionBootstrap } from '../session/useSessionActions';
import { AuthNavigator } from './AuthNavigator';
import { MainTabs } from './MainTabs';
import { OnboardingNavigator } from './OnboardingNavigator';
import { SettingsScreen } from './screens/SettingsScreen';
import * as P from './screens/placeholders';
import { createDevNavigationLog } from './devNavigationLog';
import type { RootStackParamList } from './types';

const Stack = createNativeStackNavigator<RootStackParamList>();

/**
 * Deep link `pela://…`. Thông báo đẩy mở theo bảng FLOW-12 (docs/ui/flow-review.md); route chỉ
 * khớp khi đang ở nhánh tương ứng (vd. đã đăng nhập). Route dev chỉ có ở bản dev.
 */
const linking: LinkingOptions<RootStackParamList> = {
  prefixes: ['pela://'],
  config: {
    screens: {
      Auth: { screens: { Welcome: 'welcome', Login: 'login', Register: 'register' } },
      Main: {
        screens: { Home: 'home', Roadmap: 'roadmap', Practice: 'practice', Leaderboard: 'leaderboard', Profile: 'profile' },
      },
      ChallengeSession: 'challenge/:type',
      FinalTestIntro: 'final-test/:topicId',
      WritingResult: 'writing/result/:submissionId',
      FriendRequests: 'friends/requests',
      Streak: 'streak',
      NotificationInbox: 'notifications',
      DevUiKit: 'dev/ui-kit',
      DevUiKitActions: 'dev/ui-kit/actions',
      DevUiKitDisplay: 'dev/ui-kit/display',
      DevUiKitAuth: 'dev/ui-kit/auth',
      DevDbCheck: 'dev/db-check',
    },
  },
};

function SplashScreen() {
  const theme = useTheme();
  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: theme.colors.bgCanvas }}>
      <ActivityIndicator color={theme.colors.bgPrimary} />
    </View>
  );
}

/**
 * Điều hướng gốc. Nhánh theo phiên: đang khởi động → Splash; chưa đăng nhập → AuthStack;
 * đăng nhập nhưng chưa xong onboarding → OnboardingStack; còn lại → MainTabs + các route toàn màn.
 */
export function RootNavigator() {
  const { t } = useTranslation();
  const theme = useTheme();
  useSessionBootstrap();
  const navigationRef = useNavigationContainerRef<RootStackParamList>();
  const [navLog] = React.useState(createDevNavigationLog);
  const branch = selectRootBranch(
    useSession((s) => s.status),
    useSession((s) => s.onboardingDone),
  );
  const title = (name: keyof RootStackParamList) => ({ title: t(`nav.title.${name}`) });

  return (
    <NavigationContainer
      ref={navigationRef}
      linking={linking}
      onReady={() => navLog.onReady(navigationRef.getRootState())}
      onStateChange={(state) => navLog.onStateChange(state)}
      theme={{
        dark: false, // Figma chưa có dark mode — xem core/ui/theme/tokens.ts
        colors: {
          primary: theme.colors.bgPrimary,
          background: theme.colors.bgCanvas,
          card: theme.colors.bgSurface,
          text: theme.colors.textPrimary,
          border: theme.colors.borderDefault,
          notification: theme.colors.warnFg,
        },
        fonts: {
          // Mỗi family đã là một độ đậm riêng (xem tokens.ts), nên fontWeight để '400'.
          regular: { fontFamily: theme.typography.fontFamily.nunitoSemiBold, fontWeight: '400' },
          medium: { fontFamily: theme.typography.fontFamily.nunitoBold, fontWeight: '400' },
          bold: { fontFamily: theme.typography.fontFamily.balooBold, fontWeight: '400' },
          heavy: { fontFamily: theme.typography.fontFamily.balooExtraBold, fontWeight: '400' },
        },
      }}
    >
      <Stack.Navigator>
        {branch === 'splash' ? (
          <Stack.Screen name="Splash" component={SplashScreen} options={{ headerShown: false }} />
        ) : branch === 'auth' ? (
          <Stack.Screen name="Auth" component={AuthNavigator} options={{ headerShown: false }} />
        ) : branch === 'onboarding' ? (
          <Stack.Screen name="Onboarding" component={OnboardingNavigator} options={{ headerShown: false }} />
        ) : (
          <Stack.Group>
            <Stack.Screen name="Main" component={MainTabs} options={{ headerShown: false }} />
            <Stack.Screen name="LessonSession" component={P.LessonSessionScreen} options={title('LessonSession')} />
            <Stack.Screen name="FinalTestIntro" component={P.FinalTestIntroScreen} options={title('FinalTestIntro')} />
            <Stack.Screen name="FinalTestSession" component={P.FinalTestSessionScreen} options={title('FinalTestSession')} />
            <Stack.Screen name="ChallengeSession" component={P.ChallengeSessionScreen} options={title('ChallengeSession')} />
            <Stack.Screen name="ListeningTopics" component={P.ListeningTopicsScreen} options={title('ListeningTopics')} />
            <Stack.Screen name="ListeningSession" component={P.ListeningSessionScreen} options={title('ListeningSession')} />
            <Stack.Screen name="WritingHub" component={P.WritingHubScreen} options={title('WritingHub')} />
            <Stack.Screen name="WritingCompose" component={P.WritingComposeScreen} options={title('WritingCompose')} />
            <Stack.Screen name="WritingGrading" component={P.WritingGradingScreen} options={title('WritingGrading')} />
            <Stack.Screen name="WritingResult" component={P.WritingResultScreen} options={title('WritingResult')} />
            <Stack.Screen name="WritingHistory" component={P.WritingHistoryScreen} options={title('WritingHistory')} />
            <Stack.Screen name="Streak" component={P.StreakScreen} options={title('Streak')} />
            <Stack.Screen name="Badges" component={P.BadgesScreen} options={title('Badges')} />
            <Stack.Screen name="ProgressReport" component={P.ProgressReportScreen} options={title('ProgressReport')} />
            <Stack.Screen name="Friends" component={P.FriendsScreen} options={title('Friends')} />
            <Stack.Screen name="FriendRequests" component={P.FriendRequestsScreen} options={title('FriendRequests')} />
            <Stack.Screen name="UserProfile" component={P.UserProfileScreen} options={title('UserProfile')} />
            <Stack.Screen name="Settings" component={SettingsScreen} options={title('Settings')} />
            <Stack.Screen name="NotificationSettings" component={P.NotificationSettingsScreen} options={title('NotificationSettings')} />
            <Stack.Screen name="NotificationInbox" component={P.NotificationInboxScreen} options={title('NotificationInbox')} />
            <Stack.Screen name="BlockedUsers" component={P.BlockedUsersScreen} options={title('BlockedUsers')} />
            <Stack.Screen name="EditProfile" component={P.EditProfileScreen} options={title('EditProfile')} />
            <Stack.Screen name="ChangePassword" component={P.ChangePasswordScreen} options={title('ChangePassword')} />
            <Stack.Screen name="ChangeEmail" component={P.ChangeEmailScreen} options={title('ChangeEmail')} />
            <Stack.Screen name="DeleteAccount" component={P.DeleteAccountScreen} options={title('DeleteAccount')} />
            <Stack.Screen name="ChangeLevel" component={P.ChangeLevelScreen} options={title('ChangeLevel')} />
            <Stack.Screen name="LevelUpTest" component={P.LevelUpTestScreen} options={title('LevelUpTest')} />
            <Stack.Group screenOptions={{ presentation: 'modal' }}>
              <Stack.Screen name="ReportUser" component={P.ReportUserScreen} options={title('ReportUser')} />
              <Stack.Screen name="RateApp" component={P.RateAppScreen} options={title('RateApp')} />
              <Stack.Screen name="ReportProblem" component={P.ReportProblemScreen} options={title('ReportProblem')} />
            </Stack.Group>
          </Stack.Group>
        )}
        {/* Màn dev để so UI Kit với Figma; vào được ở mọi nhánh, không đăng ký ở bản release. */}
        {__DEV__ ? (
          <Stack.Group>
            <Stack.Screen name="DevUiKit" component={UiKitScreen} options={{ title: t('uiKit.title') }} />
            <Stack.Screen name="DevUiKitActions" component={ActionsInputsPage} options={{ title: t('uiKit.actionsTitle') }} />
            <Stack.Screen name="DevUiKitDisplay" component={DisplayPage} options={{ title: t('uiKit.displayTitle') }} />
            <Stack.Screen name="DevUiKitAuth" component={AuthKitPage} options={{ title: t('uiKit.authTitle') }} />
            <Stack.Screen name="DevDbCheck" component={DbCheckScreen} options={{ title: t('dbCheck.title') }} />
          </Stack.Group>
        ) : null}
      </Stack.Navigator>
    </NavigationContainer>
  );
}
