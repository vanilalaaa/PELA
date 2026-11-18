import React, { useEffect, useRef } from 'react';
import { AccessibilityInfo, Modal, Pressable, StyleSheet, Text, View, findNodeHandle, useWindowDimensions } from 'react-native';
import { useTheme } from '../theme';
import { Button } from './Button';

export interface ConfirmDialogProps {
  visible: boolean;
  title: string;
  message?: string;
  confirmLabel: string;
  cancelLabel: string;
  onConfirm: () => void;
  /** Gọi khi bấm Huỷ, chạm nền mờ hoặc nút Back Android. */
  onCancel: () => void;
  /** Hành động phá huỷ: vòng icon và nút xác nhận dùng màu cảnh báo (coral). */
  destructive?: boolean;
  /** Glyph trắng trong vòng tròn 60 (nhận màu và cỡ). */
  renderIcon?: (color: string, size: number) => React.ReactNode;
  testID?: string;
}

const ICON_BOX = 60;
/** Cỡ chữ hệ thống từ mức này thì xếp hai nút dọc để nhãn không bị cắt. */
const STACK_FONT_SCALE = 1.2;
const DIALOG_MAX_W = 342;

/**
 * Hộp xác nhận 2 nút ngang (Figma #8 ConfirmDialog 579:11971): card trắng bo radius/dialog 28 + shadow-lg,
 * vòng icon 60 (coral-500 nếu phá huỷ, blue-500 nếu thường), tiêu đề h2, nội dung body muted căn giữa,
 * hai nút LG chia đôi: Secondary (Huỷ) + Warn / Primary. Rộng tối đa 342.
 * Cỡ chữ hệ thống lớn (≥ 1.2) thì hai nút xếp dọc. Back Android đóng hộp; TalkBack/VoiceOver đọc tiêu đề khi mở.
 */
export function ConfirmDialog({
  visible,
  title,
  message,
  confirmLabel,
  cancelLabel,
  onConfirm,
  onCancel,
  destructive = false,
  renderIcon,
  testID,
}: ConfirmDialogProps) {
  const theme = useTheme();
  const { fontScale } = useWindowDimensions();
  const stacked = fontScale >= STACK_FONT_SCALE;
  const titleRef = useRef<React.ComponentRef<typeof Text>>(null);

  useEffect(() => {
    if (!visible) return undefined;
    // Đợi Modal gắn xong rồi mới chuyển tiêu điểm trợ năng vào tiêu đề.
    const timer = setTimeout(() => {
      const node = titleRef.current ? findNodeHandle(titleRef.current) : null;
      if (node) AccessibilityInfo.setAccessibilityFocus(node);
    }, 300);
    return () => clearTimeout(timer);
  }, [visible]);

  const accent = destructive ? theme.colors.warnAccent : theme.colors.bgPrimary;

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel} statusBarTranslucent>
      <View style={[styles.backdrop, { backgroundColor: theme.colors.scrim, padding: theme.spacing.screenGutter }]}>
        {/* Chạm nền mờ = Huỷ; ẩn với trình đọc màn hình (đã có nút Huỷ). */}
        <Pressable style={StyleSheet.absoluteFill} onPress={onCancel} accessible={false} importantForAccessibility="no" />
        <View
          testID={testID}
          accessibilityViewIsModal
          style={[
            styles.card,
            theme.elevation.lg,
            { backgroundColor: theme.colors.bgSurface, borderRadius: theme.radii.dialog },
          ]}
        >
          <View style={[styles.icon, { backgroundColor: accent, borderRadius: theme.radii.full }]}>
            {renderIcon ? renderIcon(theme.colors.textOnPrimary, 28) : null}
          </View>
          <Text ref={titleRef} accessibilityRole="header" style={[theme.typeScale.h2, styles.center, { color: theme.colors.textPrimary }]}>
            {title}
          </Text>
          {message ? (
            <Text style={[theme.typeScale.body, styles.center, { color: theme.colors.textSecondary }]}>{message}</Text>
          ) : null}
          <View style={[styles.actions, stacked && styles.actionsStacked]}>
            <View style={stacked ? undefined : styles.action}>
              <Button label={cancelLabel} variant="secondary" size="lg" fullWidth onPress={onCancel} testID={testID ? `${testID}-cancel` : undefined} />
            </View>
            <View style={stacked ? undefined : styles.action}>
              <Button
                label={confirmLabel}
                variant={destructive ? 'warn' : 'primary'}
                size="lg"
                fullWidth
                onPress={onConfirm}
                testID={testID ? `${testID}-confirm` : undefined}
              />
            </View>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  card: { width: '100%', maxWidth: DIALOG_MAX_W, alignItems: 'center', gap: 12, paddingHorizontal: 20, paddingTop: 24, paddingBottom: 20 },
  icon: { width: ICON_BOX, height: ICON_BOX, alignItems: 'center', justifyContent: 'center' },
  center: { textAlign: 'center' },
  actions: { flexDirection: 'row', gap: 12, alignSelf: 'stretch', marginTop: 4 },
  // Xếp dọc: nút xác nhận ở trên, Huỷ ở dưới.
  actionsStacked: { flexDirection: 'column-reverse' },
  action: { flex: 1 },
});
