import React, { useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useNavigation } from '@react-navigation/native';
import { useTheme } from '../../core/ui/theme';
import { BottomSheet, Button, Chip } from '../../core/ui/components';
import { useDevMockSettings, type MockMode } from '../../core/infrastructure/mock';
import { resolvedDataSources } from '../container';

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
  const navigation = useNavigation();
  const mockMode = useDevMockSettings((st) => st.mode);
  const setMockMode = useDevMockSettings((st) => st.setMode);
  const sources = resolvedDataSources();

  return (
    <ScrollView
      style={{ backgroundColor: theme.colors.bgCanvas }}
      contentContainerStyle={{ padding: theme.spacing.md, gap: theme.spacing.lg }}
    >
      <View>
        <Text style={{ color: theme.colors.textPrimary, ...theme.typeScale.h2 }}>
          {t('uiKit.title')}
        </Text>
        <Text style={{ color: theme.colors.textSecondary, marginTop: theme.spacing.xs }}>{t('uiKit.subtitle')}</Text>
      </View>

      <View style={{ gap: theme.spacing.sm }}>
        <Button label={t('uiKit.openActions')} variant="soft" fullWidth onPress={() => navigation.navigate('DevUiKitActions')} />
        <Button label={t('uiKit.openDisplay')} variant="soft" fullWidth onPress={() => navigation.navigate('DevUiKitDisplay')} />
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
    </ScrollView>
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
