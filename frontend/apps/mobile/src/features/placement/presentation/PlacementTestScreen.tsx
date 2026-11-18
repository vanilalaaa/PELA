import React, { useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import {
  AnswerOption,
  Button,
  Chip,
  ConfirmDialog,
  IconButton,
  InlineAlert,
  ProgressBar,
  Screen,
} from '../../../core/ui/components';
import { PelaIcon, UIIcon } from '../../../core/ui/icons';
import { useTheme } from '../../../core/ui/theme';
import type { OnboardingStackParamList } from '../../../app/navigation/types';
import { formatMmSs } from '../application/timer';
import type { PlacementQuestion, PlacementResult } from '../application/types';
import { AlreadyTakenNotice } from './AlreadyTakenNotice';
import { usePlacementTestViewModel } from './usePlacementTestViewModel';

const KEYS = ['A', 'B', 'C', 'D', 'E', 'F'];
const BLANK = '___';

/** Câu điền từ: tách `___` thành ô trống gạch chân (Figma 12). */
function QuestionText({ q }: { q: PlacementQuestion }) {
  const theme = useTheme();
  const { t } = useTranslation();
  const style = [theme.typeScale.bodyLg, styles.italic, { color: theme.colors.textPrimary }];
  if (q.kind !== 'fill_blank' || !q.text.includes(BLANK)) {
    return <Text style={[theme.typeScale.h2, { color: theme.colors.textPrimary }]}>{q.text}</Text>;
  }
  const [before, after] = q.text.split(BLANK);
  return (
    <Text style={style} accessibilityLabel={q.text.replace(BLANK, t('placement.test.blank'))}>
      {'“'}
      {before}
      <Text style={{ color: theme.colors.bgPrimary }}>{' _______ '}</Text>
      {after}
      {'”'}
    </Text>
  );
}

/**
 * 12 Placement Question (Figma 615:14820 / 739:34546). ✕ và Back về 11, giữ tiến độ (đồng hồ vẫn chạy).
 * Đồng hồ không đọc mỗi giây cho TalkBack; chỉ thông báo còn 1 phút và hết giờ.
 */
export function PlacementTestScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const nav = useNavigation<NativeStackNavigationProp<OnboardingStackParamList>>();
  const [confirmSubmit, setConfirmSubmit] = useState(false);
  const onDone = (r: PlacementResult) =>
    nav.reset({
      index: 0,
      routes: [
        {
          name: 'PlacementResult',
          params: { kind: r.passed ? 'passed' : 'lower', placedLevel: r.placedLevel, score: r.score, correctCount: r.correctCount, totalQuestions: r.totalQuestions },
        },
      ],
    });
  const vm = usePlacementTestViewModel(onDone, () => nav.goBack());

  if (!vm.progress || !vm.question) {
    return (
      <View style={[styles.center, { backgroundColor: theme.colors.bgCanvas }]}>
        <ActivityIndicator color={theme.colors.bgPrimary} accessibilityLabel={t('common.loading')} />
      </View>
    );
  }
  const q = vm.question;
  const submitting = vm.submit.kind === 'submitting';

  return (
    <Screen
      contentStyle={{ gap: 16 }}
      footer={
        <>
          {vm.taken.visible ? (
            <AlreadyTakenNotice busy={vm.taken.busy} onContinue={() => void vm.taken.continueToApp()} />
          ) : vm.last ? (
            <Button
              label={t('placement.test.submit')}
              size="lg"
              fullWidth
              disabled={!vm.canNext}
              loading={submitting}
              onPress={() => setConfirmSubmit(true)}
              testID="placement-submit"
            />
          ) : (
            <Button label={t('placement.test.next')} size="lg" fullWidth disabled={!vm.canNext} onPress={vm.next} testID="placement-next" />
          )}
        </>
      }
    >
      <View style={styles.header}>
        <IconButton
          variant="ghost"
          size="md"
          accessibilityLabel={t('placement.test.exit')}
          onPress={() => nav.goBack()}
          disabled={submitting}
          renderIcon={(color, size) => <UIIcon name="Close" size={size} color={color} />}
          testID="placement-exit"
        />
        <View style={styles.flex}>
          <ProgressBar progress={(vm.index + 1) / vm.total} testID="placement-progress" />
        </View>
        {vm.remaining !== null ? (
          // Không là live region: TalkBack chỉ đọc khi người dùng chạm tới; mốc 1 phút/hết giờ được announce riêng.
          <View accessible accessibilityLabel={t('placement.test.timeLeftA11y', { time: formatMmSs(vm.remaining) })}>
            <Chip
              label={formatMmSs(vm.remaining)}
              tone={vm.warning || vm.locked ? 'coral' : 'sky'}
              size="md"
              renderIcon={() => <PelaIcon name="Timer" size={16} />}
              testID="placement-timer"
            />
          </View>
        ) : null}
      </View>
      <Text style={[theme.typeScale.captionBold, { color: theme.colors.textTertiary }]}>
        {t('placement.test.questionLabel', { current: vm.index + 1, total: vm.total, kind: t(`placement.test.kind.${q.kind}`) })}
      </Text>
      <Text style={[theme.typeScale.body, { color: theme.colors.textSecondary }]}>{t(`placement.test.instruction.${q.kind}`)}</Text>
      <View style={[styles.card, theme.elevation.sm, { backgroundColor: theme.colors.bgSurface, borderRadius: theme.radii.xl + 4 }]}>
        <QuestionText q={q} />
      </View>
      <View style={{ gap: 12 }} accessibilityRole="radiogroup">
        {q.options.map((option, i) => (
          <AnswerOption
            key={`${q.questionId}-${i}`}
            keyLabel={KEYS[i]}
            label={option}
            state={vm.locked ? (vm.selected === i ? 'selected' : 'disabled') : vm.selected === i ? 'selected' : 'idle'}
            onPress={() => vm.choose(i)}
            testID={`placement-option-${i}`}
          />
        ))}
      </View>
      {vm.submit.kind === 'timedOut' ? <InlineAlert tone="info" message={t('placement.test.timeUpSubmitted')} testID="placement-timeup" /> : null}
      {vm.submit.kind === 'waitingNetwork' ? <InlineAlert message={t('placement.test.timeUpOffline')} testID="placement-timeup-offline" /> : null}
      {vm.submit.kind === 'error' ? (
        <InlineAlert
          message={t(vm.submit.network ? 'placement.error.submitNetwork' : 'placement.error.submit')}
          action={<Button label={t('common.retry')} size="sm" variant="warn" onPress={vm.submitNow} testID="placement-retry" />}
          testID="placement-submit-error"
        />
      ) : null}
      <ConfirmDialog
        visible={confirmSubmit}
        title={t('placement.test.confirmTitle')}
        message={t('placement.test.confirmMessage')}
        cancelLabel={t('placement.test.confirmCancel')}
        confirmLabel={t('placement.test.submit')}
        onCancel={() => setConfirmSubmit(false)}
        onConfirm={() => {
          setConfirmSubmit(false);
          vm.submitNow();
        }}
        renderIcon={(color, size) => <UIIcon name="Check" size={size} color={color} />}
        testID="placement-confirm-submit"
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  flex: { flex: 1 },
  header: { flexDirection: 'row', alignItems: 'center', gap: 10, marginLeft: -12 },
  card: { padding: 20 },
  italic: { fontStyle: 'italic' },
});
