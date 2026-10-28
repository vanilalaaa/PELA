import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../core/ui/theme';
import { UiKitScreen } from '../dev/UiKitScreen';
import type { RootStackParamList } from './types';

const Stack = createNativeStackNavigator<RootStackParamList>();

/**
 * Placeholder cho màn Home thật — các feature sẽ thay bằng screen riêng
 * (features/<module>/presentation) khi có use case đầu tiên.
 */
function HomeScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  return (
    <View style={[styles.center, { backgroundColor: theme.colors.background }]}>
      <Text style={{ color: theme.colors.textPrimary, fontSize: theme.typography.size.lg }}>PELA</Text>
      <Text style={{ color: theme.colors.textSecondary, marginTop: theme.spacing.xs }}>{t('common.loading')}</Text>
    </View>
  );
}

export function RootNavigator() {
  const theme = useTheme();

  return (
    <NavigationContainer
      theme={{
        dark: false, // Figma chưa có dark mode — xem core/ui/theme/tokens.ts
        colors: {
          primary: theme.colors.primary,
          background: theme.colors.background,
          card: theme.colors.surface,
          text: theme.colors.textPrimary,
          border: theme.colors.border,
          notification: theme.colors.danger,
        },
        fonts: {
          regular: { fontFamily: theme.typography.fontFamily.regular, fontWeight: '400' },
          medium: { fontFamily: theme.typography.fontFamily.medium, fontWeight: '500' },
          bold: { fontFamily: theme.typography.fontFamily.bold, fontWeight: '700' },
          heavy: { fontFamily: theme.typography.fontFamily.bold, fontWeight: '800' },
        },
      }}
    >
      <Stack.Navigator>
        <Stack.Screen name="Home" component={HomeScreen} options={{ title: 'PELA' }} />
        {/* Màn dev chỉ để tự so sánh UI Kit với Figma, không build vào bản release thật sự
            nên cần gỡ khỏi navigator production — tạm giữ ở __DEV__ cho tiện demo. */}
        {__DEV__ ? <Stack.Screen name="DevUiKit" component={UiKitScreen} options={{ title: 'UI Kit (dev)' }} /> : null}
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
