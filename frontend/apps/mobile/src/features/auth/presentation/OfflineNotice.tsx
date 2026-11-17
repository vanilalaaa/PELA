import React from 'react';
import { useTranslation } from 'react-i18next';
import { InlineAlert } from '../../../core/ui/components';
import { useOnlineStatus } from '../../../app/hooks/useOnlineStatus';

/** Báo mất mạng trên màn Auth (đăng nhập/đăng ký cần mạng; GAP-2, GAP-12). */
export function OfflineNotice() {
  const { t } = useTranslation();
  return useOnlineStatus() ? null : <InlineAlert tone="info" testID="offline-notice" message={t('auth.offline')} />;
}
