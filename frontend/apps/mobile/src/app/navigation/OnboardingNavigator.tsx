import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import {
  PlacementIntroScreen,
  PlacementResultScreen,
  PlacementTestScreen,
} from './screens/OnboardingScreens';
import { SurveyAnalyzingScreen } from '../../features/onboarding/presentation/SurveyAnalyzingScreen';
import { SurveyScreen } from '../../features/onboarding/presentation/SurveyScreen';
import { useSession } from '../session/sessionStore';
import type { OnboardingStackParamList } from './types';

const Stack = createNativeStackNavigator<OnboardingStackParamList>();

/**
 * OnboardingStack (UC-02, UC-03). Survey → 10 Đang xem kết quả (navigate, lỗi thì quay lại sửa được) → Intro → Test → Result đều chuyển bằng `replace`, nên không
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
      {/* 09.1–09.5 và 10 tự vẽ thanh trên theo Figma. 10: không vuốt lùi (Back xử lý trong màn). */}
      <Stack.Screen name="Survey" component={SurveyScreen} options={{ headerShown: false }} />
      <Stack.Screen name="SurveyAnalyzing" component={SurveyAnalyzingScreen} options={{ headerShown: false, gestureEnabled: false }} />
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
