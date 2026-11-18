import React, { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Button, ChatBubble, ConfirmDialog, InlineAlert, Mascot, RankBadge, Screen } from '../../../core/ui/components';
import { PelaIcon } from '../../../core/ui/icons';
import { useTheme } from '../../../core/ui/theme';
import type { OnboardingStackParamList } from '../../../app/navigation/types';
import { durationSec } from '../application/timer';
import { AlreadyTakenNotice } from './AlreadyTakenNotice';
import { usePlacementIntroViewModel } from './usePlacementIntroViewModel';

/**
 * 11 Placement Intro (Figma 614:14683). Số câu và thời gian lấy từ lượt server trả (FLOW-10), trước khi
 * bắt đầu thì hiện khoảng chung. Không có ← (khảo sát đã gửi). "Bắt đầu từ đầu" theo FLOW-4.
 */
export function PlacementIntroScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const nav = useNavigation<NativeStackNavigationProp<OnboardingStackParamList>>();
  const [confirmA1, setConfirmA1] = useState(false);
  const vm = usePlacementIntroViewModel(
    () => nav.navigate('PlacementTest'),
    () => nav.replace('PlacementResult', { kind: 'a1', placedLevel: 'A1' }),
  );
  const minutes = vm.progress ? Math.round(durationSec(vm.progress.attempt.deadline, vm.progress.attempt.serverTime) / 60) : null;
  const count = vm.progress?.attempt.questions.length ?? null;

  const row = (icon: 'List' | 'Timer' | 'Target', text: string, last = false) => (
    <View style={[styles.row, !last && { borderBottomWidth: 1, borderBottomColor: theme.colors.borderDefault }]}>
      <PelaIcon name={icon} size={24} />
      <Text style={[theme.typeScale.bodyBold, styles.flex, { color: theme.colors.textPrimary }]}>{text}</Text>
    </View>
  );

  return (
    <Screen
      contentStyle={{ gap: 16 }}
      footer={
        <>
          {vm.taken.visible ? (
            <AlreadyTakenNotice busy={vm.taken.busy} onContinue={() => void vm.taken.continueToApp()} />
          ) : (
            <View style={{ gap: 8 }}>
              <Button
                label={vm.resuming ? t('placement.intro.resume') : t('placement.intro.start')}
                size="lg"
                fullWidth
                disabled={vm.loading || !vm.canStart}
                loading={vm.busy === 'start'}
                onPress={() => void vm.start()}
                testID="placement-start"
              />
              {!vm.resuming && !vm.loading ? (
                <Button
                  label={t('placement.intro.fromBeginning')}
                  size="md"
                  variant="ghost"
                  fullWidth
                  disabled={!vm.online}
                  loading={vm.busy === 'beginning'}
                  onPress={() => setConfirmA1(true)}
                  testID="placement-from-beginning"
                />
              ) : null}
            </View>
          )}
        </>
      }
    >
      <View style={styles.greeting}>
        <Mascot pose="reassure" />
        <View style={styles.flex}>
          <ChatBubble message={t('placement.intro.bubble')} size="sm" />
        </View>
      </View>
      <View style={styles.titleRow}>
        <PelaIcon name="Exam" size={32} />
        <Text style={[theme.typeScale.h1, styles.flex, { color: theme.colors.textPrimary }]} accessibilityRole="header">
          {t('placement.intro.title')}
        </Text>
      </View>
      <Text style={[theme.typeScale.body, { color: theme.colors.textSecondary }]}>{t('placement.intro.subtitle')}</Text>
      <View style={[styles.card, theme.elevation.sm, { backgroundColor: theme.colors.bgSurface, borderRadius: theme.radii.xl + 4 }]}>
        {row('List', count ? t('placement.intro.questionsCount', { count }) : t('placement.intro.questionsRange'))}
        {row('Timer', minutes ? t('placement.intro.duration', { count: minutes }) : t('placement.intro.durationUnknown'))}
        {row('Target', t('placement.intro.passMark'), true)}
      </View>
      <InlineAlert message={t('placement.intro.oneShot')} detail={t('placement.intro.oneShotDetail')} testID="placement-oneshot" />
      {!vm.online && !vm.resuming ? <InlineAlert tone="info" message={t('placement.intro.offline')} testID="placement-offline" /> : null}
      {vm.error ? (
        <InlineAlert message={t(vm.error === 'network' ? 'placement.error.network' : 'placement.error.unknown')} testID="placement-intro-error" />
      ) : null}
      <ConfirmDialog
        visible={confirmA1}
        title={t('placement.intro.confirmA1Title')}
        message={t('placement.intro.confirmA1Message')}
        cancelLabel={t('common.cancel')}
        confirmLabel={t('placement.intro.confirmA1')}
        onCancel={() => setConfirmA1(false)}
        onConfirm={() => {
          setConfirmA1(false);
          void vm.startFromBeginning();
        }}
        renderIcon={() => <RankBadge tier={1} width={30} />}
        testID="placement-confirm-a1"
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  greeting: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  card: { paddingHorizontal: 16, paddingVertical: 4 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: 48, paddingVertical: 8 },
});
