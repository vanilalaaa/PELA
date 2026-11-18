import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, BackHandler, Keyboard, KeyboardAvoidingView, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  AnswerOption,
  Button,
  ChatBubble,
  ChoiceCard,
  IconButton,
  Input,
  Mascot,
  ProgressBar,
  RankBadge,
} from '../../../core/ui/components';
import { UIIcon } from '../../../core/ui/icons';
import { useTheme } from '../../../core/ui/theme';
import type { OnboardingStackParamList } from '../../../app/navigation/types';
import { CEFR_LEVELS, OCCUPATION_OTHER_MAX, SURVEY_STEP_COUNT, isOccupationOtherValid } from '../application/survey';
import { DAILY_GOAL_MINUTES, INTERESTS, LEARNING_GOALS, OCCUPATIONS } from '../application/types';
import { OPTION_KEYS, TIER_OF_LEVEL } from './surveyOptions';
import { useSurveyViewModel } from './useSurveyViewModel';

/**
 * 09.1–09.5 Khảo sát định hướng (Figma 703:29912, 700:36609, 703:30438, 703:30690, 703:30807).
 * Thanh trên (← · tiến độ · n/5) và nút dưới cố định, phần giữa cuộn. Câu 1 không có ←.
 */
export function SurveyScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const nav = useNavigation<NativeStackNavigationProp<OnboardingStackParamList>>();
  const vm = useSurveyViewModel((answers) => nav.navigate('SurveyAnalyzing', { answers }));
  const { draft, step } = vm;
  const scrollRef = useRef<React.ComponentRef<typeof ScrollView>>(null);
  // Ô "Khác" nằm cuối danh sách: khi bàn phím mở (vùng cuộn co lại), cuộn xuống để ô không bị nút che.
  const revealOther = () => requestAnimationFrame(() => scrollRef.current?.scrollToEnd({ animated: true }));
  // Bàn phím mở (chỉ có ở ô "Khác"): ẩn nút dưới để vùng cuộn đủ chỗ cho ô nhập, kể cả cỡ chữ lớn.
  // Đóng bàn phím (Xong/Back) thì nút hiện lại.
  const [keyboardOpen, setKeyboardOpen] = useState(false);
  useEffect(() => {
    const show = Keyboard.addListener('keyboardDidShow', () => {
      setKeyboardOpen(true);
      revealOther();
    });
    const hide = Keyboard.addListener('keyboardDidHide', () => setKeyboardOpen(false));
    return () => {
      show.remove();
      hide.remove();
    };
  }, []);
  // Sang câu khác thì về đầu vùng cuộn.
  useEffect(() => {
    scrollRef.current?.scrollTo({ y: 0, animated: false });
  }, [step]);

  // Back Android lùi một câu; ở câu 1 để hệ thống xử lý (thoát app).
  useFocusEffect(
    useCallback(() => {
      const sub = BackHandler.addEventListener('hardwareBackPress', () => vm.back());
      return () => sub.remove();
    }, [vm]),
  );

  if (vm.restoring) {
    return (
      <View style={[styles.center, { backgroundColor: theme.colors.bgCanvas }]}>
        <ActivityIndicator color={theme.colors.bgPrimary} accessibilityLabel={t('common.loading')} />
      </View>
    );
  }

  const otherInvalid = draft.occupation === 'other' && draft.occupationOther.length > 0 && !isOccupationOtherValid(draft.occupationOther);

  return (
    <KeyboardAvoidingView
      behavior="padding"
      style={{ flex: 1, backgroundColor: theme.colors.bgCanvas, paddingTop: insets.top }}
    >
      <View style={[styles.topBar, { paddingHorizontal: theme.spacing.screenGutter }]}>
        {step > 0 ? (
          <IconButton
            variant="ghost"
            size="md"
            accessibilityLabel={t('onboarding.survey.previous')}
            onPress={() => vm.back()}
            renderIcon={(color, size) => <UIIcon name="ArrowLeft" size={size} color={color} />}
            testID="survey-back"
          />
        ) : null}
        <View style={styles.progress}>
          <ProgressBar progress={(step + 1) / SURVEY_STEP_COUNT} testID="survey-progress" />
        </View>
        <Text style={[theme.typeScale.bodyStrong, { color: theme.colors.textTertiary }]} accessibilityLabel={t('onboarding.survey.progressA11y', { current: step + 1, total: SURVEY_STEP_COUNT })}>
          {t('onboarding.survey.progress', { current: step + 1, total: SURVEY_STEP_COUNT })}
        </Text>
      </View>
      <ScrollView
        ref={scrollRef}
        style={{ flex: 1 }}
        contentContainerStyle={[styles.content, { paddingHorizontal: theme.spacing.screenGutter }]}
        keyboardShouldPersistTaps="handled"
      >
        {step === 0 ? (
          <View style={styles.greeting}>
            <View style={styles.bubble}>
              <ChatBubble message={t('onboarding.survey.greeting')} tail="bottom-right" size="sm" />
            </View>
            <Mascot pose="wave" />
          </View>
        ) : null}
        <Text style={[theme.typeScale.captionBold, { color: theme.colors.textTertiary }]}>
          {t('onboarding.survey.questionLabel', { current: step + 1, total: SURVEY_STEP_COUNT })}
        </Text>
        <Text style={[theme.typeScale.h2, { color: theme.colors.textPrimary }]} accessibilityRole="header">
          {t(`onboarding.survey.q${step + 1}.title`)}
        </Text>
        {step === 1 || step === 2 ? (
          <Text style={[theme.typeScale.caption, { color: theme.colors.textTertiary }]} accessibilityLiveRegion="polite">
            {step === 1 && vm.goalsFull ? t('onboarding.survey.q2.full') : t(`onboarding.survey.q${step + 1}.hint`)}
          </Text>
        ) : null}

        {step === 0 ? (
          <View style={styles.list} accessibilityRole="radiogroup">
            {OCCUPATIONS.map((o, i) => (
              <AnswerOption
                key={o}
                keyLabel={OPTION_KEYS[i]}
                label={t(`onboarding.survey.q1.${o}`)}
                state={draft.occupation === o ? 'selected' : 'idle'}
                onPress={() => vm.setOccupation(o)}
                testID={`survey-occupation-${o}`}
              />
            ))}
            {draft.occupation === 'other' ? (
              <Input
                label={t('onboarding.survey.q1.otherLabel')}
                placeholder={t('onboarding.survey.q1.otherPlaceholder')}
                value={draft.occupationOther}
                onChangeText={vm.setOccupationOther}
                maxLength={OCCUPATION_OTHER_MAX}
                autoFocus
                returnKeyType="done"
                onSubmitEditing={() => Keyboard.dismiss()}
                errorMessage={otherInvalid ? t('onboarding.survey.q1.otherBlank') : undefined}
                testID="survey-occupation-other"
              />
            ) : null}
          </View>
        ) : null}

        {step === 1 ? (
          <View style={styles.list}>
            {LEARNING_GOALS.map((g) => (
              <ChoiceCard
                key={g}
                multiple
                tall
                title={t(`onboarding.survey.q2.${g}`)}
                selected={draft.learningGoals.includes(g)}
                disabled={vm.goalsFull}
                onPress={() => vm.toggleGoal(g)}
                testID={`survey-goal-${g}`}
              />
            ))}
          </View>
        ) : null}

        {step === 2 ? (
          <View style={styles.grid}>
            {INTERESTS.map((i) => (
              <View key={i} style={styles.cell}>
                <ChoiceCard
                  multiple={false}
                  centered
                  title={t(`onboarding.survey.q3.${i}`)}
                  selected={draft.interests.includes(i)}
                  onPress={() => vm.toggleInterest(i)}
                  testID={`survey-interest-${i}`}
                />
              </View>
            ))}
          </View>
        ) : null}

        {step === 3 ? (
          <View style={styles.list} accessibilityRole="radiogroup">
            {DAILY_GOAL_MINUTES.map((m) => (
              <ChoiceCard
                key={m}
                title={t('onboarding.survey.q4.minutes', { count: m })}
                description={t(`onboarding.survey.q4.desc${m}`)}
                selected={draft.dailyGoalMinutes === m}
                onPress={() => vm.setDailyGoal(m)}
                testID={`survey-minutes-${m}`}
              />
            ))}
          </View>
        ) : null}

        {step === 4 ? (
          <View style={styles.list} accessibilityRole="radiogroup">
            {CEFR_LEVELS.map((l) => (
              <ChoiceCard
                key={l}
                tall
                leading={<RankBadge tier={TIER_OF_LEVEL[l]} />}
                title={t(`cefr.tier.${l}`)}
                description={t(`onboarding.survey.q5.${l}`)}
                selected={draft.selfAssessedLevel === l}
                onPress={() => vm.setLevel(l)}
                testID={`survey-level-${l}`}
              />
            ))}
            <Text style={[theme.typeScale.caption, { color: theme.colors.textTertiary }]}>{t('onboarding.survey.q5.hint')}</Text>
          </View>
        ) : null}
      </ScrollView>
      {keyboardOpen ? null : (
      <View style={[styles.footer, { paddingHorizontal: theme.spacing.screenGutter, paddingBottom: insets.bottom + theme.spacing.md }]}>
        <Button
          label={step === 4 ? t('onboarding.survey.finish') : t('onboarding.survey.next')}
          size="lg"
          fullWidth
          disabled={!vm.canNext}
          onPress={vm.next}
          testID="survey-next"
        />
      </View>
      )}
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  topBar: { flexDirection: 'row', alignItems: 'center', gap: 10, minHeight: 52, paddingTop: 4, paddingBottom: 8 },
  progress: { flex: 1 },
  content: { gap: 16, paddingTop: 4, paddingBottom: 24 },
  greeting: { flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end', gap: 6 },
  bubble: { flexShrink: 1 },
  list: { gap: 10 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', rowGap: 12 },
  // Hai cột bằng nhau (Figma 169 + 12 + 169); chữ dài thì xuống dòng trong ô.
  cell: { width: '48%', flexDirection: 'row' },
  footer: { paddingTop: 8 },
});
