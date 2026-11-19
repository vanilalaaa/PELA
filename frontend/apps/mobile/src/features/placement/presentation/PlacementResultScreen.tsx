import React, { useCallback, useEffect, useState } from 'react';
import { BackHandler, StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useFocusEffect, useRoute, type RouteProp } from '@react-navigation/native';
import Svg, { Defs, LinearGradient, Rect, Stop } from 'react-native-svg';
import { Button, Chip, Mascot, RankBadge, Screen } from '../../../core/ui/components';
import { PelaIcon, UIIcon } from '../../../core/ui/icons';
import { useTheme } from '../../../core/ui/theme';
import { useUseCases } from '../../../app/UseCasesContext';
import { useSession } from '../../../app/session/sessionStore';
import type { OnboardingStackParamList } from '../../../app/navigation/types';
import type { CefrLevel } from '../../../domain/types';

const TIER = { A1: 1, A2: 2, B1: 3, B2: 4, C1: 5, C2: 6 } as const;

/** Chủ đề đầu tiên của lộ trình (thẻ "Bước tiếp theo"); lỗi thì null để ẩn thẻ. */
function useFirstTopic() {
  const { roadmap } = useUseCases();
  const [name, setName] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    roadmap
      .getRoadmap()
      .then((r) => {
        const first = [...r.topics].sort((a, b) => a.orderIndex - b.orderIndex).find((tp) => tp.status !== 'locked') ?? r.topics[0];
        if (active && first) setName(first.name);
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [roadmap]);
  return name;
}

/**
 * 13 Đạt (621:15433) · 14 Xếp bậc đề xuất (615:15060) · 15 Bắt đầu ở A1 (615:15222). Chặn Back (BR-19).
 * "Bắt đầu học" kết thúc onboarding.
 */
export function PlacementResultScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const { params } = useRoute<RouteProp<OnboardingStackParamList, 'PlacementResult'>>();
  const topic = useFirstTopic();
  const tierName = t(`cefr.tier.${params.placedLevel}`);

  useFocusEffect(
    useCallback(() => {
      const sub = BackHandler.addEventListener('hardwareBackPress', () => true);
      return () => sub.remove();
    }, []),
  );

  const badge = (level: CefrLevel, color: string) => (
    <View style={styles.badge}>
      <RankBadge tier={TIER[level]} width={104} />
      <Text style={[theme.typeScale.h2, styles.center, styles.stretch, { color }]}>{t(`cefr.tier.${level}`)}</Text>
    </View>
  );

  const nextCard = topic ? (
    <View style={[styles.next, theme.elevation.sm, { backgroundColor: theme.colors.bgSurface, borderRadius: theme.radii.xl + 4 }]}>
      <PelaIcon name="Lesson" size={28} />
      <View style={styles.flex}>
        <Text style={[theme.typeScale.captionBold, { color: theme.colors.textTertiary }]}>{t('placement.result.nextStep')}</Text>
        <Text style={[theme.typeScale.bodyStrong, { color: theme.colors.textPrimary }]}>{topic}</Text>
      </View>
      <UIIcon name="CaretRight" size={18} color={theme.colors.textTertiary} />
    </View>
  ) : null;

  const passed = params.kind === 'passed';
  return (
    <View style={[styles.flex, { backgroundColor: theme.colors.bgCanvas }]}>
      {passed ? (
        <Svg style={StyleSheet.absoluteFill} preserveAspectRatio="none">
          <Defs>
            <LinearGradient id="bg" x1="0" y1="0" x2="0" y2="1">
              <Stop offset="0" stopColor={theme.colors.bgPrimarySubtle} />
              <Stop offset="1" stopColor={theme.colors.bgCanvas} />
            </LinearGradient>
          </Defs>
          <Rect width="100%" height="100%" fill="url(#bg)" />
        </Svg>
      ) : null}
      <Screen
        transparent={passed}
        contentStyle={{ gap: 16 }}
        footer={
          <Button
            label={t('placement.result.start')}
            size="lg"
            fullWidth
            onPress={() => useSession.getState().completeOnboarding()}
            testID="placement-done"
          />
        }
      >
        <View style={styles.body}>
          {params.kind === 'passed' ? (
            <>
              <Mascot pose="celebrate" />
              <Text style={[theme.typeScale.h1, styles.center, { color: theme.colors.textPrimary }]} accessibilityRole="header">
                {t('placement.result.passedTitle')}
              </Text>
              {badge(params.placedLevel, theme.colors.textPrimary)}
              {params.correctCount !== undefined && params.totalQuestions ? (
                <Chip
                  tone="mint"
                  size="md"
                  label={t('placement.result.scoreChip', { correct: params.correctCount, total: params.totalQuestions, score: params.score })}
                  renderIcon={() => <PelaIcon name="Success" size={16} />}
                  style={styles.centerSelf}
                  testID="placement-score"
                />
              ) : null}
              <Text style={[theme.typeScale.body, styles.center, { color: theme.colors.textSecondary }]}>
                {t('placement.result.passedBody', { tier: tierName })}
              </Text>
            </>
          ) : (
            <>
              <Mascot pose={params.kind === 'a1' ? 'welcome' : 'cheer'} width={160} />
              <Text style={[theme.typeScale.h1, styles.center, { color: theme.colors.textPrimary }]} accessibilityRole="header">
                {t(params.kind === 'a1' ? 'placement.result.a1Title' : 'placement.result.lowerTitle')}
              </Text>
              <Text style={[theme.typeScale.body, styles.center, { color: theme.colors.textSecondary }]}>
                {params.kind === 'a1' ? t('placement.result.a1Body') : t('placement.result.lowerBody', { tier: tierName })}
              </Text>
              <View
                style={[
                  styles.levelBox,
                  { backgroundColor: params.kind === 'a1' ? theme.colors.infoBg : theme.colors.successBg, borderRadius: theme.radii.xl + 4 },
                ]}
              >
                <Text
                  style={[
                    theme.typeScale.captionBold,
                    { color: params.kind === 'a1' ? theme.colors.textBrand : theme.colors.successFg },
                  ]}
                >
                  {t(params.kind === 'a1' ? 'placement.result.startLevel' : 'placement.result.yourLevel')}
                </Text>
                {badge(params.placedLevel, params.kind === 'a1' ? theme.colors.textBrand : theme.colors.successFg)}
              </View>
            </>
          )}
          {nextCard}
        </View>
      </Screen>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  body: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 14 },
  center: { textAlign: 'center' },
  // Chữ căn giữa có giãn chữ âm: cho chiếm hết bề ngang để Android không đo hụt rồi xuống dòng.
  stretch: { alignSelf: 'stretch' },
  centerSelf: { alignSelf: 'center' },
  badge: { alignSelf: 'stretch', alignItems: 'center', gap: 6 },
  levelBox: { alignSelf: 'stretch', alignItems: 'center', gap: 6, padding: 18 },
  next: { alignSelf: 'stretch', flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 14 },
});
