import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Button, ChatBubble, InlineAlert, Mascot, Screen, ScreenHeader } from '../../../core/ui/components';
import { UIIcon } from '../../../core/ui/icons';
import { useTheme } from '../../../core/ui/theme';
import type { AuthStackParamList } from '../../../app/navigation/types';

/**
 * 04 Verify Email (Figma 613:14481). "Mở ứng dụng email" ẩn ở R1.0 (GAP-27); "Gửi lại email" vô hiệu hoá
 * vì chưa có API-2 (GAP-28); thêm "Đã xác minh, đăng nhập" (GAP-29); "Đổi địa chỉ email" về Đăng ký.
 */
export function VerifyEmailScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const nav = useNavigation<NativeStackNavigationProp<AuthStackParamList>>();
  const { params } = useRoute<RouteProp<AuthStackParamList, 'VerifyEmail'>>();

  return (
    <Screen scroll contentStyle={{ gap: 14 }}>
      <ScreenHeader backLabel={t('auth.back')} onBack={() => nav.goBack()} testID="verify-back" />
      <View style={[styles.body, { gap: 14 }]}>
        <View style={styles.bubbleRow}>
          <ChatBubble message={t('auth.verify.bubble')} />
        </View>
        <View style={styles.art}>
          <Mascot width={140} />
          <UIIcon name="MailOpen" size={76} color={theme.colors.bgPrimary} />
        </View>
        <Text style={[theme.typeScale.h1, styles.center, { color: theme.colors.textPrimary }]} accessibilityRole="header">
          {t('auth.verify.title')}
        </Text>
        <Text style={[theme.typeScale.body, styles.center, { color: theme.colors.textSecondary }]}>
          {t('auth.verify.sentTo')}
          <Text style={[theme.typeScale.bodyStrong, { color: theme.colors.textPrimary }]}>{params.email}</Text>
          {t('auth.verify.instructions')}
        </Text>
        <InlineAlert tone="info" message={t('auth.verify.spamHint')} />
      </View>
      <View style={{ gap: 10 }}>
        <Button label={t('auth.verify.verifiedLogin')} size="lg" fullWidth onPress={() => nav.replace('Login')} testID="verify-login" />
        <Button
          label={t('auth.verify.resend')}
          size="lg"
          fullWidth
          disabled
          accessibilityLabel={`${t('auth.verify.resend')}. ${t('auth.error.resendUnavailable')}`}
          testID="verify-resend"
        />
        <Text style={[theme.typeScale.caption, styles.center, { color: theme.colors.textTertiary }]}>
          {t('auth.error.resendUnavailable')}
        </Text>
        <Button
          label={t('auth.verify.changeEmail')}
          size="md"
          variant="ghost"
          fullWidth
          onPress={() => nav.replace('Register', { fullName: params.fullName })}
          testID="verify-change-email"
        />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { flex: 1, justifyContent: 'center' },
  bubbleRow: { alignItems: 'flex-end', paddingRight: 8 },
  art: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'center', gap: 6 },
  center: { textAlign: 'center' },
});
