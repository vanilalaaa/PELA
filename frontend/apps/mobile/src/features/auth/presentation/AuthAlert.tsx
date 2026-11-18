import React from 'react';
import { useTranslation } from 'react-i18next';
import { Button, InlineAlert } from '../../../core/ui/components';
import { formatDate, formatDateTime } from '../../../core/ui/logic/dateFormat';
import type { ForgotAlert, LoginAlert, OtpAlert, RegisterAlert, ResetAlert } from './authForms';

export type AnyAuthAlert = LoginAlert | RegisterAlert | ForgotAlert | OtpAlert | ResetAlert;

/**
 * Banner lỗi của form Auth (Figma 03/06; các lỗi GAP-6 dùng cùng mẫu). `onLogin`: nút "Đăng nhập" khi
 * email đã đăng ký; `onResendCode`: nút "Gửi lại mã" khi phiên đặt lại hết hạn (44d).
 */
export function AuthAlert({
  alert,
  onLogin,
  onResendCode,
}: {
  alert: AnyAuthAlert;
  onLogin?: () => void;
  onResendCode?: () => void;
}) {
  const { t, i18n } = useTranslation();

  switch (alert.key) {
    case 'emailTaken':
      return (
        <InlineAlert
          testID="auth-alert"
          message={t('auth.error.emailTaken')}
          action={onLogin ? <Button label={t('auth.login')} size="sm" variant="warn" onPress={onLogin} /> : undefined}
        />
      );
    case 'locked': {
      const time = formatDateTime(alert.lockedUntil, i18n.language);
      return (
        <InlineAlert
          testID="auth-alert"
          message={t('auth.error.locked')}
          detail={time ? t('auth.error.lockedUntil', { time }) : t('auth.error.lockedLater')}
        />
      );
    }
    case 'unverified':
      return (
        <InlineAlert
          testID="auth-alert"
          message={t('auth.error.unverified')}
          detail={t('auth.error.resendUnavailable')}
          action={<Button label={t('auth.verify.resendShort')} size="sm" variant="warn" disabled />}
        />
      );
    case 'suspended': {
      // suspendedUntil null = đình chỉ vĩnh viễn (OpenAPI AccountBlocked, UC-16).
      const until = formatDate(alert.suspendedUntil, i18n.language);
      const reason = alert.reason ? t('auth.error.suspendedReason', { reason: alert.reason }) : '';
      const end = until ? t('auth.error.suspendedUntil', { date: until }) : '';
      return (
        <InlineAlert
          testID="auth-alert"
          message={t(until ? 'auth.error.suspended' : 'auth.error.suspendedForever')}
          detail={[reason, end].filter(Boolean).join(' ') || undefined}
        />
      );
    }
    case 'pendingDeletion': {
      const day = formatDate(alert.deletionScheduledAt, i18n.language);
      return (
        <InlineAlert
          testID="auth-alert"
          message={t('auth.error.pendingDeletion')}
          detail={day ? t('auth.error.deletionDate', { date: day }) : undefined}
        />
      );
    }
    case 'tooManyRequests':
      return (
        <InlineAlert
          testID="auth-alert"
          message={
            alert.retryAfterSec
              ? t('auth.error.tooManyRequestsWait', { seconds: alert.retryAfterSec })
              : t('auth.error.tooManyRequests')
          }
        />
      );
    case 'resetExpired':
      return (
        <InlineAlert
          testID="auth-alert"
          message={t('auth.error.resetExpired')}
          action={
            onResendCode ? (
              <Button label={t('auth.reset.resendCode')} size="sm" variant="warn" onPress={onResendCode} testID="auth-alert-resend" />
            ) : undefined
          }
        />
      );
    default:
      return <InlineAlert testID="auth-alert" message={t(`auth.error.${alert.key}`)} />;
  }
}
