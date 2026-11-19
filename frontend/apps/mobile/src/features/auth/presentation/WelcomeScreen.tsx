import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Button, DividerText, Logo, Screen, SocialButton } from '../../../core/ui/components';
import { useTheme } from '../../../core/ui/theme';
import type { AuthStackParamList } from '../../../app/navigation/types';

/**
 * 01 Welcome (Figma 612:14219): logo giữa màn, khối nút dưới (gap 12, đáy 44). Google chưa hỗ trợ
 * (API-3) nên vô hiệu hoá; Apple ẩn (GAP-8, CO-6).
 */
export function WelcomeScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const nav = useNavigation<NativeStackNavigationProp<AuthStackParamList>>();
  return (
    <Screen scroll={false} contentStyle={styles.content}>
      <View style={[styles.hero, { gap: theme.spacing['2md'] }]}>
        <Logo accessibilityLabel={t('auth.logo')} />
        <Text style={[theme.typeScale.bodyLg, { color: theme.colors.textSecondary, textAlign: 'center' }]}>
          {t('auth.welcome.tagline')}
        </Text>
      </View>
      <View style={[styles.actions, { gap: theme.spacing['2sm'] }]}>
        <Button label={t('auth.register')} size="lg" fullWidth onPress={() => nav.navigate('Register')} testID="welcome-register" />
        <Button label={t('auth.login')} size="lg" variant="secondary" fullWidth onPress={() => nav.navigate('Login')} testID="welcome-login" />
        <DividerText label={t('auth.orContinueWith')} />
        <View style={styles.socialRow}>
          <SocialButton provider="google" label="Google" disabled accessibilityLabel={t('auth.googleUnavailable')} />
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { justifyContent: 'space-between' },
  hero: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  actions: { paddingBottom: 20 },
  socialRow: { flexDirection: 'row' },
});
