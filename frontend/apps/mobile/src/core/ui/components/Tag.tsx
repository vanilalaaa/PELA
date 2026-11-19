import React from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { useTheme } from '../theme';

export type TagTone = 'blue' | 'amber' | 'mint' | 'coral';

export interface TagProps {
  label: string;
  selected?: boolean;
  tone?: TagTone;
  onPress?: () => void;
  /** Icon 16 bên trái; nhận trạng thái chọn để đổi Default/Inactive (vd. PelaIcon). */
  renderIcon?: (selected: boolean) => React.ReactNode;
  disabled?: boolean;
  testID?: string;
}

/**
 * Tag chọn chủ đề/mục tiêu (Figma 478:5509): cao 36, viền 2px, bo full, px 14, gap 6, Nunito
 * ExtraBold 14. Chọn = nền tint + viền tông 500 + chữ tông 700; chưa chọn = trắng, viền ink-200.
 */
export function Tag({ label, selected = false, tone = 'blue', onPress, renderIcon, disabled = false, testID }: TagProps) {
  const theme = useTheme();
  const c = selected ? theme.componentColors.tag[tone] : theme.componentColors.tag.unselected;
  const slop = Math.max(0, (theme.spacing.tapMin - 36) / 2);

  return (
    <Pressable
      testID={testID}
      onPress={disabled ? undefined : onPress}
      disabled={disabled || !onPress}
      hitSlop={slop}
      accessibilityRole={onPress ? 'checkbox' : 'text'}
      accessibilityLabel={label}
      accessibilityState={{ checked: selected, disabled }}
      style={[
        styles.tag,
        { borderRadius: theme.radii.full, backgroundColor: c.bg, borderColor: c.border },
        disabled && styles.disabled,
      ]}
    >
      {renderIcon ? renderIcon(selected) : null}
      <Text style={[theme.typeScale.inputLabel, { color: c.fg }]} numberOfLines={1}>
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  tag: { height: 36, borderWidth: 2, paddingHorizontal: 14, gap: 6, flexDirection: 'row', alignItems: 'center', alignSelf: 'flex-start' },
  disabled: { opacity: 0.5 },
});
