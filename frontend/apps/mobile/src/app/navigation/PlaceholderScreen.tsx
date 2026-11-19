import React from 'react';
import { Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Button, Card, Screen, type ButtonVariant, type ScreenEdge } from '../../core/ui/components';
import { useTheme } from '../../core/ui/theme';
import type { RootStackParamList } from './types';

export type RootNav = NativeStackNavigationProp<RootStackParamList>;

export interface PlaceholderAction {
  /** Key i18n dưới `nav.action`. */
  label: string;
  run: (nav: RootNav) => void | Promise<void>;
  variant?: ButtonVariant;
}

interface PlaceholderOptions {
  /** Key i18n dưới `nav.title`. */
  title: string;
  /** Mã màn Figma / FLOW / GAP để đối chiếu khi dựng thật (P3). */
  figma: string;
  actions?: PlaceholderAction[];
  /** Màn trong MainTabs: chừa top, BottomNav lo bottom. Màn có header: chỉ chừa bottom. */
  edges?: readonly ScreenEdge[];
}

/**
 * Màn tạm cho khung điều hướng (bước P2). Mỗi route một màn: tiêu đề, mã Figma, các nút dẫn sang
 * route kế tiếp để thử luồng. Giao diện thật dựng ở P3 trong features/<f>/presentation.
 */
export function placeholderScreen({ title, figma, actions = [], edges = ['bottom'] }: PlaceholderOptions) {
  function PlaceholderRouteScreen() {
    const { t } = useTranslation();
    const theme = useTheme();
    const nav = useNavigation<RootNav>();
    return (
      <Screen edges={edges}>
        <View style={{ gap: theme.spacing.xs }}>
          <Text style={[theme.typeScale.h2, { color: theme.colors.textPrimary }]}>{t(`nav.title.${title}`)}</Text>
          <Text style={[theme.typeScale.caption, { color: theme.colors.textTertiary }]}>
            {t('nav.placeholder', { figma })}
          </Text>
        </View>
        {actions.length > 0 ? (
          <Card>
            <View style={{ gap: theme.spacing.sm }}>
              {actions.map((a) => (
                <Button
                  key={a.label}
                  label={t(`nav.action.${a.label}`)}
                  variant={a.variant ?? 'soft'}
                  fullWidth
                  onPress={() => void a.run(nav)}
                />
              ))}
            </View>
          </Card>
        ) : null}
      </Screen>
    );
  }
  PlaceholderRouteScreen.displayName = `Placeholder(${title})`;
  return PlaceholderRouteScreen;
}
