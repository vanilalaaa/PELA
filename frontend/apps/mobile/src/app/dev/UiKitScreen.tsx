import React, { useState } from 'react';
import { Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useNavigation } from '@react-navigation/native';
import { useTheme } from '../../core/ui/theme';
import { BottomSheet, Button, Chip, ConfirmDialog, Screen } from '../../core/ui/components';
import { UIIcon } from '../../core/ui/icons';
import { useDevMockSettings, type MockMode } from '../../core/infrastructure/mock';
import { useQuery } from '@tanstack/react-query';
import { resetMockOnboardingForDev, resolvedDataSources } from '../container';
import { useSession } from '../session/sessionStore';
import { useUseCases } from '../UseCasesContext';

const MOCK_MODES: MockMode[] = ['normal', 'error', 'empty'];

/**
 * Màn "Storybook" nội bộ, chỉ có trong bản dev. Mục lục các trang con theo đợt (đợt 1: hành động
 * và nhập liệu, đợt 2: hiển thị); các component chưa làm lại theo Figma vẫn hiển thị ở đây.
 * Trạng thái "pressed" chỉ thấy khi đang chạm.
 *
 * Chỉ được đăng ký vào navigator khi `__DEV__` (xem RootNavigator.tsx).
 */
export function UiKitScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const [sheetVisible, setSheetVisible] = useState(false);
  const [dialog, setDialog] = useState<'none' | 'default' | 'destructive'>('none');
  const navigation = useNavigation();
  const mockMode = useDevMockSettings((st) => st.mode);
  const setMockMode = useDevMockSettings((st) => st.setMode);
  const sources = resolvedDataSources();
  const { roadmap } = useUseCases();
  // Thử gọi một Mock repository để thấy 3 chế độ: số chủ đề / mảng rỗng / mã lỗi.
  const probe = useQuery({
    queryKey: ['dev', 'mockProbe', mockMode],
    queryFn: () => roadmap.getRoadmap(),
    enabled: false,
    retry: false,
  });
  const probeText = probe.isFetching
    ? t('uiKit.mockProbe.loading')
    : probe.error
      ? t('uiKit.mockProbe.error', { code: (probe.error as { code?: string }).code ?? probe.error.message })
      : probe.data
        ? t('uiKit.mockProbe.result', { count: probe.data.topics.length })
        : '';

  return (
    <Screen edges={['bottom']} contentStyle={{ gap: theme.spacing.lg }}>
      <View>
        <Text style={{ color: theme.colors.textPrimary, ...theme.typeScale.h2 }}>
          {t('uiKit.title')}
        </Text>
        <Text style={{ color: theme.colors.textSecondary, marginTop: theme.spacing.xs }}>{t('uiKit.subtitle')}</Text>
      </View>

      <View style={{ gap: theme.spacing.sm }}>
        <Button label={t('uiKit.openActions')} variant="soft" fullWidth onPress={() => navigation.navigate('DevUiKitActions')} />
        <Button label={t('uiKit.openDisplay')} variant="soft" fullWidth onPress={() => navigation.navigate('DevUiKitDisplay')} />
        <Button label={t('uiKit.openAuth')} variant="soft" fullWidth onPress={() => navigation.navigate('DevUiKitAuth')} />
        {/* Mock: về surveyPending + xoá nháp khảo sát, giữ phiên rồi chuyển sang nhánh Onboarding. */}
        <Button
          label={t('uiKit.resetOnboarding')}
          variant="secondary"
          fullWidth
          onPress={async () => {
            await resetMockOnboardingForDev();
            const me = useSession.getState().me;
            if (!me) return;
            useSession.getState().signIn(me, { screen: 'Survey' });
            // Nhánh Onboarding có thể đã mount (đang ở Placement): đặt lại stack về câu 1 sau khi nhánh đổi.
            requestAnimationFrame(() => navigation.reset({ index: 0, routes: [{ name: 'Onboarding', params: { screen: 'Survey' } }] }));
          }}
          testID="uikit-reset-onboarding"
        />
      </View>

      <Section title={t('uiKit.mockTitle')}>
        <Text style={{ color: theme.colors.textSecondary, ...theme.typeScale.caption }}>{t('uiKit.mockHint')}</Text>
        <View style={{ flexDirection: 'row', gap: theme.spacing.sm }}>
          {MOCK_MODES.map((m) => (
            <Button
              key={m}
              size="sm"
              label={t(`uiKit.mockMode.${m}`)}
              variant={mockMode === m ? 'primary' : 'secondary'}
              onPress={() => setMockMode(m)}
            />
          ))}
        </View>
        <Button size="sm" variant="soft" label={t('uiKit.mockProbe.run')} onPress={() => void probe.refetch()} />
        {probeText ? (
          <Text testID="mock-probe-result" style={{ color: theme.colors.textPrimary, ...theme.typeScale.bodyStrong }}>
            {probeText}
          </Text>
        ) : null}
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.xs }}>
          {Object.entries(sources).map(([feature, source]) => (
            <Chip key={feature} label={`${feature}: ${source}`} tone={source === 'api' ? 'sky' : 'neutral'} />
          ))}
        </View>
      </Section>

      <Section title={t('uiKit.sectionSheet')}>
        <Button label={t('uiKit.openSheet')} onPress={() => setSheetVisible(true)} />
        <BottomSheet visible={sheetVisible} onClose={() => setSheetVisible(false)}>
          <Text style={{ color: theme.colors.textPrimary, ...theme.typeScale.h3 }}>{t('uiKit.sheetTitle')}</Text>
          <Text style={{ color: theme.colors.textSecondary, marginTop: theme.spacing.sm }}>
            {t('uiKit.sheetBody')}
          </Text>
          <Button label={t('common.close')} variant="ghost" onPress={() => setSheetVisible(false)} />
        </BottomSheet>
      </Section>

      <Section title={t('uiKit.sectionDialog')}>
        <Button label={t('uiKit.openDialog')} onPress={() => setDialog('default')} />
        <Button label={t('uiKit.openDialogDestructive')} variant="warn" onPress={() => setDialog('destructive')} />
        <ConfirmDialog
          visible={dialog !== 'none'}
          destructive={dialog === 'destructive'}
          title={t('uiKit.dialogTitle')}
          message={t('uiKit.dialogBody')}
          cancelLabel={t('common.cancel')}
          confirmLabel={t('common.ok')}
          onCancel={() => setDialog('none')}
          onConfirm={() => setDialog('none')}
          renderIcon={(color, size) => <UIIcon name={dialog === 'destructive' ? 'Close' : 'Check'} size={size} color={color} />}
          testID="uikit-dialog"
        />
      </Section>
    </Screen>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  const theme = useTheme();
  return (
    <View style={{ gap: theme.spacing.sm }}>
      <Text style={{ color: theme.colors.textPrimary, ...theme.typeScale.h3 }}>
        {title}
      </Text>
      {children}
    </View>
  );
}
