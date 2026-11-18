import React from 'react';
import { View } from 'react-native';
import { SafeAreaInsetsContext, useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';
import { InlineAlert } from '../../core/ui/components';
import { useTheme } from '../../core/ui/theme';
import { useOnlineStatus } from '../hooks/useOnlineStatus';
import { useSession } from './sessionStore';

/**
 * Bọc điều hướng gốc: khi phiên đang dùng ảnh chụp trên máy (chưa xác nhận lại với server) thì hiện banner
 * ở đầu màn và báo cho các màn bên dưới rằng safe area trên đã được chừa (tránh chừa hai lần).
 */
export function StaleSessionBanner({ children }: { children: React.ReactNode }) {
  const { t } = useTranslation();
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const online = useOnlineStatus();
  const visible = useSession((s) => s.status === 'signedIn' && s.stale);

  if (!visible) return <>{children}</>;
  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.bgCanvas }}>
      <View style={{ paddingTop: insets.top + theme.spacing.xs, paddingHorizontal: theme.spacing.screenGutter, paddingBottom: theme.spacing.xs }}>
        <InlineAlert
          tone="info"
          message={t(online ? 'session.stale.serverUnavailable' : 'session.stale.offline')}
          testID="stale-session-banner"
        />
      </View>
      <SafeAreaInsetsContext.Provider value={{ ...insets, top: 0 }}>
        <View style={{ flex: 1 }}>{children}</View>
      </SafeAreaInsetsContext.Provider>
    </View>
  );
}
