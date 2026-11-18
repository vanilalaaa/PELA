import React, { useState } from 'react';
import { Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useNavigation } from '@react-navigation/native';
import { Button, Input, PasswordField, Screen } from '../../../core/ui/components';
import { useTheme } from '../../../core/ui/theme';
import { useSessionActions } from '../../session/useSessionActions';
import type { RootNav } from '../PlaceholderScreen';

/**
 * Màn đăng nhập tạm (05 Login) để thử luồng phiên: gọi use case thật (mock hoặc API theo dataSources).
 * Form đầy đủ (react-hook-form + zod, 06 Login Error, GAP-6) dựng ở P3.
 */
export function LoginScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const nav = useNavigation<RootNav>();
  const { login } = useSessionActions();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    setLoading(true);
    setError(null);
    try {
      await login({ email: email.trim(), password });
    } catch (e) {
      const code = (e as { code?: string }).code ?? 'UNKNOWN_ERROR';
      setError(t('nav.loginError', { code }));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen edges={['bottom']}>
      <Text style={[theme.typeScale.h2, { color: theme.colors.textPrimary }]}>{t('nav.title.Login')}</Text>
      <View style={{ gap: theme.spacing.md }}>
        <Input
          label={t('nav.email')}
          value={email}
          onChangeText={setEmail}
          keyboardType="email-address"
          autoCapitalize="none"
          testID="login-email"
        />
        <PasswordField label={t('nav.password')} value={password} onChangeText={setPassword} testID="login-password" />
        {error ? <Text style={[theme.typeScale.captionBold, { color: theme.colors.warnFg }]}>{error}</Text> : null}
        <Button label={t('nav.action.login')} fullWidth loading={loading} onPress={() => void submit()} testID="login-submit" />
        <Button label={t('nav.action.forgotPassword')} variant="ghost" onPress={() => nav.navigate('Auth', { screen: 'ForgotPassword' })} />
        <Button label={t('nav.action.toRegister')} variant="ghost" onPress={() => nav.navigate('Auth', { screen: 'Register' })} />
      </View>
    </Screen>
  );
}
