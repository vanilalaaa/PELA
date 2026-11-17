import React from 'react';
import { useTranslation } from 'react-i18next';
import { Button, InlineAlert } from '../../../core/ui/components';
import { formatDate, formatDateTime } from '../../../core/ui/logic/dateFormat';
import type { LoginAlert, RegisterAlert } from './authForms';

/** Banner lỗi của form Auth (Figma 03/06; các lỗi GAP-6 dùng cùng mẫu). */
export function AuthAlert({ alert, onLogin }: { alert: LoginAlert | RegisterAlert; onLogin?: () => void }) {
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
    default:
      return <InlineAlert testID="auth-alert" message={t(`auth.error.${alert.key}`)} />;
  }
}
