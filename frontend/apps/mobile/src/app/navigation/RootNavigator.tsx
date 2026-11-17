import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { NavigationContainer, useNavigation, type LinkingOptions } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../core/ui/theme';
import { Button } from '../../core/ui/components';
import { UiKitScreen } from '../dev/UiKitScreen';
import { ActionsInputsPage } from '../dev/uikit/ActionsInputsPage';
import { DisplayPage } from '../dev/uikit/DisplayPage';
import { DbCheckScreen } from '../dev/DbCheckScreen';
import type { RootStackParamList } from './types';

const Stack = createNativeStackNavigator<RootStackParamList>();

// Deep link `pela://dev/ui-kit`, `pela://dev/db-check` chỉ có tác dụng ở bản dev, vì các route
// dev chỉ đăng ký khi `__DEV__`.
const linking: LinkingOptions<RootStackParamList> = {
  prefixes: ['pela://'],
  config: {
    screens: {
      Home: '',
      DevUiKit: 'dev/ui-kit',
      DevUiKitActions: 'dev/ui-kit/actions',
      DevUiKitDisplay: 'dev/ui-kit/display',
      DevDbCheck: 'dev/db-check',
    },
  },
};

/**
 * Placeholder cho màn Home thật — các feature sẽ thay bằng screen riêng
 * (features/<module>/presentation) khi có use case đầu tiên.
 */
function HomeScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const navigation = useNavigation();
  return (
    <View style={[styles.center, { backgroundColor: theme.colors.bgCanvas }]}>
      <Text style={{ color: theme.colors.textPrimary, ...theme.typeScale.h3 }}>PELA</Text>
      <Text style={{ color: theme.colors.textSecondary, marginTop: theme.spacing.xs }}>{t('common.loading')}</Text>
      {__DEV__ ? (
        <View style={{ marginTop: theme.spacing.lg, gap: theme.spacing.sm }}>
          <Button label={t('uiKit.open')} variant="secondary" onPress={() => navigation.navigate('DevUiKit')} />
          <Button label={t('dbCheck.open')} variant="ghost" onPress={() => navigation.navigate('DevDbCheck')} />
        </View>
      ) : null}
    </View>
  );
}

export function RootNavigator() {
  const { t } = useTranslation();
  const theme = useTheme();

  return (
    <NavigationContainer
      linking={linking}
      theme={{
        dark: false, // Figma chưa có dark mode — xem core/ui/theme/tokens.ts
        colors: {
          primary: theme.colors.bgPrimary,
          background: theme.colors.bgCanvas,
          card: theme.colors.bgSurface,
          text: theme.colors.textPrimary,
          border: theme.colors.borderDefault,
          notification: theme.colors.warnFg,
        },
        fonts: {
          // Mỗi family đã là một độ đậm riêng (xem tokens.ts), nên fontWeight để '400'.
          regular: { fontFamily: theme.typography.fontFamily.nunitoSemiBold, fontWeight: '400' },
          medium: { fontFamily: theme.typography.fontFamily.nunitoBold, fontWeight: '400' },
          bold: { fontFamily: theme.typography.fontFamily.balooBold, fontWeight: '400' },
          heavy: { fontFamily: theme.typography.fontFamily.balooExtraBold, fontWeight: '400' },
        },
      }}
    >
      <Stack.Navigator>
        <Stack.Screen name="Home" component={HomeScreen} options={{ title: 'PELA' }} />
        {/* Màn dev để so UI Kit với Figma; không đăng ký ở bản release. */}
        {__DEV__ ? <Stack.Screen name="DevUiKit" component={UiKitScreen} options={{ title: t('uiKit.title') }} /> : null}
        {__DEV__ ? (
          <Stack.Screen name="DevUiKitActions" component={ActionsInputsPage} options={{ title: t('uiKit.actionsTitle') }} />
        ) : null}
        {__DEV__ ? (
          <Stack.Screen name="DevUiKitDisplay" component={DisplayPage} options={{ title: t('uiKit.displayTitle') }} />
        ) : null}
        {__DEV__ ? <Stack.Screen name="DevDbCheck" component={DbCheckScreen} options={{ title: t('dbCheck.title') }} /> : null}
      </Stack.Navigator>
    </NavigationContainer>
  );
}

const styles = StyleSheet.create({
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
