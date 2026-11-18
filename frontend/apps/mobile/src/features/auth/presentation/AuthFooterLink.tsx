import React from 'react';
import { Pressable, Text } from 'react-native';
import { useTheme } from '../../../core/ui/theme';

/** Dòng "Đã có tài khoản? Đăng nhập" (Figma 02/05): body textSecondary + link bodyStrong textLink, vùng chạm ≥ 44. */
export function AuthFooterLink({ prompt, action, onPress, testID }: { prompt: string; action: string; onPress: () => void; testID?: string }) {
  const theme = useTheme();
  return (
    <Pressable
      testID={testID}
      onPress={onPress}
      accessibilityRole="link"
      accessibilityLabel={`${prompt} ${action}`}
      style={{ minHeight: theme.spacing.tapMin, justifyContent: 'center', alignSelf: 'stretch' }}
    >
      <Text style={[theme.typeScale.body, { color: theme.colors.textSecondary, textAlign: 'center' }]}>
        {`${prompt} `}
        <Text style={[theme.typeScale.bodyStrong, { color: theme.colors.textLink }]}>{action}</Text>
      </Text>
    </Pressable>
  );
}
