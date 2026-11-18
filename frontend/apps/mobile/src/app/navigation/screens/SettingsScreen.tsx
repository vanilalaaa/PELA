import React from 'react';
import { Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useNavigation } from '@react-navigation/native';
import { Button, Card, Screen } from '../../../core/ui/components';
import { useTheme } from '../../../core/ui/theme';
import { useSessionActions } from '../../session/useSessionActions';
import type { RootNav } from '../PlaceholderScreen';
import type { RootStackParamList } from '../types';

type SettingsRoute = Extract<
  keyof RootStackParamList,
  | 'EditProfile'
  | 'ChangePassword'
  | 'ChangeEmail'
  | 'ChangeLevel'
  | 'NotificationSettings'
  | 'BlockedUsers'
  | 'RateApp'
  | 'ReportProblem'
  | 'DeleteAccount'
>;

const ROWS: readonly SettingsRoute[] = [
  'EditProfile',
  'ChangePassword',
  'ChangeEmail',
  'ChangeLevel',
  'NotificationSettings',
  'BlockedUsers',
  'RateApp',
  'ReportProblem',
  'DeleteAccount',
];

/** 42 Settings tạm: lối vào các màn cài đặt + đăng xuất (50 Logout là dialog, dựng ở P3). */
export function SettingsScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const nav = useNavigation<RootNav>();
  const { logout } = useSessionActions();
  return (
    <Screen edges={['bottom']}>
      <View style={{ gap: theme.spacing.xs }}>
        <Text style={[theme.typeScale.h2, { color: theme.colors.textPrimary }]}>{t('nav.title.Settings')}</Text>
        <Text style={[theme.typeScale.caption, { color: theme.colors.textTertiary }]}>{t('nav.placeholder', { figma: '42 · 50' })}</Text>
      </View>
      <Card>
        <View style={{ gap: theme.spacing.sm }}>
          {ROWS.map((route) => (
            <Button
              key={route}
              label={t(`nav.title.${route}`)}
              variant={route === 'DeleteAccount' ? 'danger' : 'soft'}
              fullWidth
              onPress={() => nav.navigate(route)}
            />
          ))}
        </View>
      </Card>
      <Button label={t('nav.action.logout')} variant="secondary" fullWidth onPress={() => void logout()} testID="logout" />
    </Screen>
  );
}
