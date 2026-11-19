import React from 'react';
import { Animated, Pressable, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../theme';
import { Checkbox } from './Checkbox';
import { usePressScale } from './usePressScale';

export interface ChoiceCardProps {
  title: string;
  description?: string;
  selected: boolean;
  onPress: () => void;
  /** Chọn nhiều: hiện Checkbox bên phải, role checkbox. Mặc định chọn một (role radio). */
  multiple?: boolean;
  /** Không chọn thêm được (vd. đã đủ 3 mục tiêu); mục đã chọn vẫn bỏ chọn được. */
  disabled?: boolean;
  /** Phần tử đầu thẻ (vd. huy chương ở 09.5). */
  leading?: React.ReactNode;
  /** Căn giữa chữ (ô chủ đề 09.3). */
  centered?: boolean;
  /** Thẻ cao tối thiểu 100 (Figma 09.2, 09.5). */
  tall?: boolean;
  testID?: string;
}

/**
 * Thẻ lựa chọn của khảo sát (Figma 09.2–09.5): bo radius/answer 20, viền 2px, padding 16/14.
 * Idle trắng + shadow-xs · Selected blue-50/blue-500, tiêu đề blue-700 · Disabled mờ (màu AnswerOption).
 * Tiêu đề body-strong, mô tả caption muted. Nhấn scale .98.
 */
export function ChoiceCard({
  title,
  description,
  selected,
  onPress,
  multiple = false,
  disabled = false,
  leading,
  centered = false,
  tall = false,
  testID,
}: ChoiceCardProps) {
  const theme = useTheme();
  const press = usePressScale(theme.motion.pressScale.card);
  const inactive = disabled && !selected;
  const c = theme.componentColors.answer[selected ? 'selected' : inactive ? 'disabled' : 'idle'];

  return (
    <Animated.View style={[press.scaleStyle, centered && styles.grow]}>
      <Pressable
        testID={testID}
        onPress={inactive ? undefined : onPress}
        onPressIn={inactive ? undefined : press.onPressIn}
        onPressOut={inactive ? undefined : press.onPressOut}
        disabled={inactive}
        accessibilityRole={multiple ? 'checkbox' : 'radio'}
        accessibilityLabel={description ? `${title}. ${description}` : title}
        accessibilityState={{ checked: selected, selected, disabled: inactive }}
        style={[
          styles.box,
          centered && styles.centered,
          tall && styles.tall,
          { borderRadius: theme.radii.answer, backgroundColor: c.bg, borderColor: c.border },
          !selected && !inactive ? theme.elevation.xs : null,
        ]}
      >
        {leading}
        <View style={[styles.texts, centered && styles.textsCentered]}>
          <Text style={[theme.typeScale.bodyStrong, { color: c.fg }, centered && styles.center]}>{title}</Text>
          {description ? (
            <Text style={[theme.typeScale.caption, { color: inactive ? c.fg : theme.colors.textTertiary }]}>{description}</Text>
          ) : null}
        </View>
        {multiple ? (
          <View pointerEvents="none" importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
            <Checkbox checked={selected} disabled={inactive} />
          </View>
        ) : null}
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  grow: { flex: 1 },
  box: { flexDirection: 'row', alignItems: 'center', gap: 12, borderWidth: 2, paddingHorizontal: 16, paddingVertical: 14, minHeight: 52 },
  centered: { justifyContent: 'center', flexGrow: 1 },
  tall: { minHeight: 100 },
  texts: { flex: 1, gap: 2 },
  textsCentered: { alignSelf: 'stretch', justifyContent: 'center' },
  center: { textAlign: 'center' },
});
