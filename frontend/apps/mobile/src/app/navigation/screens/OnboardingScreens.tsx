import React, { useCallback, useState } from 'react';
import { BackHandler, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useFocusEffect, useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Button, Card, Screen } from '../../../core/ui/components';
import { useTheme } from '../../../core/ui/theme';
import type { SurveyAnswers } from '../../../features/onboarding/application/types';
import { useUseCases } from '../../UseCasesContext';
import { useSession } from '../../session/sessionStore';
import type { OnboardingStackParamList } from '../types';

type OnboardingNav = NativeStackNavigationProp<OnboardingStackParamList>;

/** Chặn nút Back Android khi màn đang hiển thị (bài xếp lớp chỉ làm một lần, BR-19). */
function useBlockHardwareBack() {
  useFocusEffect(
    useCallback(() => {
      const sub = BackHandler.addEventListener('hardwareBackPress', () => true);
      return () => sub.remove();
    }, []),
  );
}

/** Câu trả lời mẫu để thử luồng; form 5 bước thật (09.1–09.5) dựng ở P3. */
const SAMPLE_ANSWERS: SurveyAnswers = {
  occupation: 'university',
  learningGoals: ['communication'],
  interests: ['travel'],
  dailyGoalMinutes: 10,
  selfAssessedLevel: 'B1',
};

function Frame({ title, figma, children }: { title: string; figma: string; children: React.ReactNode }) {
  const { t } = useTranslation();
  const theme = useTheme();
  return (
    <Screen edges={['bottom']}>
      <View style={{ gap: theme.spacing.xs }}>
        <Text style={[theme.typeScale.h2, { color: theme.colors.textPrimary }]}>{t(`nav.title.${title}`)}</Text>
        <Text style={[theme.typeScale.caption, { color: theme.colors.textTertiary }]}>{t('nav.placeholder', { figma })}</Text>
      </View>
      <Card>
        <View style={{ gap: theme.spacing.sm }}>{children}</View>
      </Card>
    </Screen>
  );
}

/** 09.1–09.5: gửi khảo sát → A1 thì xong onboarding (SkipRule), ngược lại sang Placement. */
export function SurveyScreen() {
  const { t } = useTranslation();
  const nav = useNavigation<OnboardingNav>();
  const { onboarding } = useUseCases();
  const [busy, setBusy] = useState(false);
  const submit = async () => {
    setBusy(true);
    try {
      const result = await onboarding.submitSurvey(SAMPLE_ANSWERS);
      if (result.next === 'done') useSession.getState().completeOnboarding();
      // replace: khảo sát đã gửi, không quay lại được.
      else nav.replace('PlacementIntro', { orientedLevel: result.mappedLevel });
    } finally {
      setBusy(false);
    }
  };
  return (
    <Frame title="Survey" figma="09.1–09.5, 10">
      <Button label={t('nav.action.submitSurvey')} fullWidth loading={busy} onPress={() => void submit()} testID="survey-submit" />
    </Frame>
  );
}

export function SurveyAnalyzingScreen() {
  return <Frame title="SurveyAnalyzing" figma="10">{null}</Frame>;
}

/** 11 Intro: làm bài, hoặc "Bắt đầu từ đầu" xếp A1 (UC-03 A1, FLOW-4). */
export function PlacementIntroScreen() {
  const { t } = useTranslation();
  const nav = useNavigation<OnboardingNav>();
  const { params } = useRoute<RouteProp<OnboardingStackParamList, 'PlacementIntro'>>();
  return (
    <Frame title="PlacementIntro" figma="11 · FLOW-4, FLOW-10">
      <Button
        label={t('nav.action.startTest')}
        fullWidth
        onPress={() => nav.replace('PlacementTest', params)}
        testID="placement-start"
      />
      <Button
        label={t('nav.action.startFromBeginning')}
        variant="ghost"
        fullWidth
        onPress={() => useSession.getState().completeOnboarding()}
      />
    </Frame>
  );
}

/** 12: nộp đáp án (id lượt do client sinh — tạm dùng id mẫu cho tới khi có bộ sinh UUID). */
export function PlacementTestScreen() {
  const { t } = useTranslation();
  const nav = useNavigation<OnboardingNav>();
  const { placement } = useUseCases();
  useBlockHardwareBack();
  const [busy, setBusy] = useState(false);
  const submit = async () => {
    setBusy(true);
    try {
      const r = await placement.submit('placement-demo-attempt', []);
      // replace: không quay lại bài thi đã nộp.
      nav.replace('PlacementResult', { placedLevel: r.placedLevel, passed: r.passed, score: r.score });
    } finally {
      setBusy(false);
    }
  };
  return (
    <Frame title="PlacementTest" figma="12">
      <Button label={t('nav.action.submitTest')} fullWidth loading={busy} onPress={() => void submit()} testID="placement-submit" />
    </Frame>
  );
}

/** 13/14/15: kết quả xếp lớp → vào học (kết thúc onboarding). */
export function PlacementResultScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const { params } = useRoute<RouteProp<OnboardingStackParamList, 'PlacementResult'>>();
  useBlockHardwareBack();
  return (
    <Frame title="PlacementResult" figma={params.passed ? '13' : '14'}>
      <Text style={[theme.typeScale.bodyStrong, { color: theme.colors.textPrimary }]}>
        {t('nav.placedLevel', { level: params.placedLevel, score: params.score })}
      </Text>
      <Button label={t('nav.action.startLearning')} fullWidth onPress={() => useSession.getState().completeOnboarding()} testID="placement-done" />
    </Frame>
  );
}
