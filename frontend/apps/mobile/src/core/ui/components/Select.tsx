import React, { useRef, useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme';
import { UIIcon } from '../icons';
import { FieldShell } from './FieldShell';

export interface SelectOption<T extends string> {
  value: T;
  label: string;
  /** Phần tử đầu dòng của ô khi lựa chọn này đang được chọn (vd. RankBadge 28 ở màn 47). */
  leading?: React.ReactNode;
}

export interface SelectProps<T extends string> {
  options: readonly SelectOption<T>[];
  value: T | null;
  onChange: (value: T) => void;
  label?: string;
  placeholder?: string;
  disabled?: boolean;
  testID?: string;
}

const MENU_GAP = 8;
const OPTION_HEIGHT = 48;

interface Anchor {
  x: number;
  /** Mép trên và mép dưới của ô trong toạ độ cửa sổ. */
  top: number;
  bottom: number;
  width: number;
}

/**
 * Ô chọn (Figma Select 476:5481) và menu thả xuống khi mở (màn 47 · Select mở, 677:29413):
 * ô cao 52, bo 16, viền 2px; mở = Focused (viền blue-500 + ring-focus); menu trắng ngay dưới ô,
 * bo 16, shadow-md, đệm dọc 6, mỗi dòng cao 48; dòng đang chọn nền blue-50, chữ blue-700, dấu ✓.
 */
export function Select<T extends string>({ options, value, onChange, label, placeholder, disabled = false, testID }: SelectProps<T>) {
  const theme = useTheme();
  const { t } = useTranslation();
  const fieldRef = useRef<React.ComponentRef<typeof View>>(null);
  const [anchor, setAnchor] = useState<Anchor | null>(null);
  const window = useWindowDimensions();
  const selected = options.find((o) => o.value === value) ?? null;
  const open = anchor !== null;
  const field = theme.componentColors.field;

  const openMenu = () => {
    fieldRef.current?.measureInWindow((x: number, y: number, width: number, height: number) =>
      setAnchor({ x, top: y, bottom: y + height, width }),
    );
  };
  const close = () => setAnchor(null);

  const menuHeight = options.length * OPTION_HEIGHT + theme.spacing.sm;
  // Mặc định mở xuống dưới ô như Figma; không đủ chỗ thì mở lên trên ô.
  const fitsBelow = anchor ? anchor.bottom + MENU_GAP + menuHeight <= window.height - theme.spacing.md : true;
  const top = anchor ? (fitsBelow ? anchor.bottom + MENU_GAP : Math.max(theme.spacing.md, anchor.top - MENU_GAP - menuHeight)) : 0;

  return (
    <View>
      <Pressable
        testID={testID}
        onPress={disabled ? undefined : openMenu}
        disabled={disabled}
        accessibilityRole="combobox"
        accessibilityLabel={label}
        accessibilityValue={{ text: selected?.label ?? placeholder ?? '' }}
        accessibilityState={{ disabled, expanded: open }}
      >
        <View ref={fieldRef} collapsable={false}>
          <FieldShell label={label} labelVariant="select" state={disabled ? 'disabled' : open ? 'focused' : 'default'}>
            {selected?.leading ? <View style={disabled && styles.dim}>{selected.leading}</View> : null}
            <Text
              numberOfLines={1}
              style={[
                styles.value,
                theme.typeScale.bodyBold,
                { color: disabled ? theme.colors.textDisabled : selected ? theme.colors.textPrimary : theme.colors.textTertiary },
              ]}
            >
              {selected?.label ?? placeholder ?? ''}
            </Text>
            <UIIcon name="CaretDown" size={20} color={disabled ? theme.colors.textDisabled : field.caret} />
          </FieldShell>
        </View>
      </Pressable>

      {/* statusBarTranslucent: để toạ độ Modal trùng toạ độ measureInWindow trên Android. */}
      <Modal visible={open} transparent statusBarTranslucent animationType="fade" onRequestClose={close}>
        <Pressable style={StyleSheet.absoluteFill} onPress={close} accessibilityLabel={t('common.close')} />
        {anchor ? (
          <View
            accessibilityRole="menu"
            style={[
              styles.menu,
              theme.elevation.md,
              { top, left: anchor.x, width: anchor.width, borderRadius: theme.radii.xl, backgroundColor: theme.colors.bgSurface },
            ]}
          >
            {options.map((option) => {
              const isSelected = option.value === value;
              return (
                <Pressable
                  key={option.value}
                  accessibilityRole="menuitem"
                  accessibilityState={{ selected: isSelected }}
                  onPress={() => {
                    onChange(option.value);
                    close();
                  }}
                  style={({ pressed }) => [
                    styles.option,
                    {
                      paddingHorizontal: theme.spacing.md,
                      backgroundColor: isSelected || pressed ? field.optionSelectedBg : 'transparent',
                    },
                  ]}
                >
                  <Text
                    numberOfLines={1}
                    style={[styles.value, theme.typeScale.bodyBold, { color: isSelected ? theme.colors.textLink : theme.colors.textPrimary }]}
                  >
                    {option.label}
                  </Text>
                  {isSelected ? <UIIcon name="Check" size={20} color={theme.colors.textLink} /> : null}
                </Pressable>
              );
            })}
          </View>
        ) : null}
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  value: { flex: 1 },
  dim: { opacity: 0.5 },
  menu: { position: 'absolute', paddingVertical: 6, overflow: 'hidden' },
  option: { height: OPTION_HEIGHT, flexDirection: 'row', alignItems: 'center', gap: 8 },
});
