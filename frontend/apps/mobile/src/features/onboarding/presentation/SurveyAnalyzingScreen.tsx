import React, { useCallback } from 'react';
import { BackHandler, StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useFocusEffect, useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Button, ChatBubble, InlineAlert, Mascot, Screen } from '../../../core/ui/components';
import { useTheme } from '../../../core/ui/theme';
import { useOnlineStatus } from '../../../app/hooks/useOnlineStatus';
import { useSession } from '../../../app/session/sessionStore';
import type { OnboardingStackParamList } from '../../../app/navigation/types';
import type { CefrLevel } from '../../../domain/types';
import type { OnboardingNextStep } from '../application/types';
import { useSurveyAnalyzingViewModel } from './useSurveyAnalyzingViewModel';

const RING = 248;

/**
 * 10 Đang xem kết quả khảo sát (Figma 614:14614 / 713:31921). Gửi khảo sát khi mở; A1 → xong onboarding
 * (Onboarding.Survey.SkipRule), còn lại → 11 PlacementIntro. Lỗi/mất mạng: Thử lại hoặc Xem lại câu trả lời
 * (Back Android cũng là Xem lại); đang gửi thì chặn Back.
 */
export function SurveyAnalyzingScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const online = useOnlineStatus();
  const nav = useNavigation<NativeStackNavigationProp<OnboardingStackParamList>>();
  const { params } = useRoute<RouteProp<OnboardingStackParamList, 'SurveyAnalyzing'>>();
  const onDone = useCallback(
    (next: OnboardingNextStep, mappedLevel: CefrLevel) => {
      if (next === 'done') {
        // Khảo sát ra A1 (SkipRule): 15 Placed A1 rồi mới vào học.
        nav.replace('PlacementResult', { kind: 'a1', placedLevel: 'A1' });
      } else {
        // Ghi bước hiện tại vào phiên (ảnh chụp trên máy) để mở lại app vào thẳng PlacementIntro.
        useSession.getState().setOnboardingEntry({ screen: 'PlacementIntro', orientedLevel: mappedLevel });
        // replace: khảo sát đã gửi, không quay lại được.
        nav.replace('PlacementIntro', { orientedLevel: mappedLevel });
      }
    },
    [nav],
  );
  const vm = useSurveyAnalyzingViewModel(params.answers, onDone);
  const failed = vm.state.kind === 'error';

  useFocusEffect(
    useCallback(() => {
      const sub = BackHandler.addEventListener('hardwareBackPress', () => {
        if (failed) nav.goBack();
        return true;
      });
      return () => sub.remove();
    }, [failed, nav]),
  );

  return (
    <Screen contentStyle={styles.content}>
      <View style={styles.body}>
        {!failed ? <ChatBubble message={t('onboarding.analyzing.bubble')} tail="bottom" /> : null}
        <View style={[styles.ring, { borderColor: theme.colors.bgPrimary, borderRadius: RING / 2 }]}>
          <Mascot pose="analyze" />
        </View>
        <Text style={[theme.typeScale.h1, styles.center, { color: theme.colors.textPrimary }]} accessibilityRole="header">
          {t(failed ? 'onboarding.analyzing.errorTitle' : 'onboarding.analyzing.title')}
        </Text>
        {!failed ? (
          <Text style={[theme.typeScale.body, styles.center, { color: theme.colors.textSecondary }]} accessibilityLiveRegion="polite">
            {t('onboarding.analyzing.subtitle')}
          </Text>
        ) : null}
      </View>
      {failed ? (
        <View style={styles.actions}>
          {/* Mất mạng thì chỉ báo ngoại tuyến (đã đủ nguyên nhân + việc cần làm), không kèm lỗi chung. */}
          {!online ? (
            <InlineAlert tone="info" message={t('onboarding.analyzing.offline')} testID="analyzing-offline" />
          ) : (
            <InlineAlert
              message={t(vm.state.kind === 'error' && vm.state.network ? 'onboarding.analyzing.networkError' : 'onboarding.analyzing.error')}
              testID="analyzing-error"
            />
          )}
          <Button label={t('common.retry')} size="lg" fullWidth onPress={vm.retry} testID="analyzing-retry" />
          <Button label={t('onboarding.analyzing.review')} size="md" variant="ghost" fullWidth onPress={() => nav.goBack()} testID="analyzing-review" />
        </View>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { gap: 14 },
  body: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 18 },
  ring: { width: RING, height: RING, borderWidth: 4, alignItems: 'center', justifyContent: 'center' },
  center: { textAlign: 'center' },
  actions: { gap: 10 },
});
