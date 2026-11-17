import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import {
  PlacementIntroScreen,
  PlacementResultScreen,
  PlacementTestScreen,
  SurveyAnalyzingScreen,
  SurveyScreen,
} from './screens/OnboardingScreens';
import { useSession } from '../session/sessionStore';
import type { OnboardingStackParamList } from './types';

const Stack = createNativeStackNavigator<OnboardingStackParamList>();

/**
 * OnboardingStack (UC-02, UC-03). Survey → Intro → Test → Result đều chuyển bằng `replace`, nên không
 * quay lại khảo sát đã gửi hay bài thi đã nộp; Test và Result chặn thêm nút Back Android (BR-19).
 */
export function OnboardingNavigator() {
  const { t } = useTranslation();
  const title = (name: keyof OnboardingStackParamList) => ({ title: t(`nav.title.${name}`) });
  // Mở lại app giữa chừng: placementPending vào thẳng PlacementIntro (không làm lại khảo sát).
  const entry = useSession((s) => s.onboardingEntry);
  const placement = entry?.screen === 'PlacementIntro' ? entry : null;
  return (
    <Stack.Navigator initialRouteName={placement ? 'PlacementIntro' : 'Survey'}>
      <Stack.Screen name="Survey" component={SurveyScreen} options={title('Survey')} />
      <Stack.Screen name="SurveyAnalyzing" component={SurveyAnalyzingScreen} options={title('SurveyAnalyzing')} />
      <Stack.Screen
        name="PlacementIntro"
        component={PlacementIntroScreen}
        initialParams={placement ? { orientedLevel: placement.orientedLevel } : undefined}
        options={{ ...title('PlacementIntro'), headerBackVisible: false, gestureEnabled: false }}
      />
      <Stack.Screen
        name="PlacementTest"
        component={PlacementTestScreen}
        options={{ ...title('PlacementTest'), headerBackVisible: false, gestureEnabled: false }}
      />
      <Stack.Screen
        name="PlacementResult"
        component={PlacementResultScreen}
        options={{ ...title('PlacementResult'), headerBackVisible: false, gestureEnabled: false }}
      />
    </Stack.Navigator>
  );
}
