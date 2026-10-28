import React, { useEffect, useRef } from 'react';
import { Animated, Modal, Pressable, StyleSheet, View, type ViewStyle } from 'react-native';
import { useTheme } from '../theme';

export interface BottomSheetProps {
  visible: boolean;
  onClose: () => void;
  children: React.ReactNode;
  style?: ViewStyle;
}

/**
 * BottomSheet — dựng từ Modal + Animated có sẵn trong React Native (KHÔNG thêm lib native
 * mới), slide-up từ dưới lên, tap ra ngoài (backdrop) để đóng.
 */
export function BottomSheet({ visible, onClose, children, style }: BottomSheetProps) {
  const theme = useTheme();
  const translateY = useRef(new Animated.Value(300)).current;
  const backdropOpacity = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (visible) {
      Animated.parallel([
        Animated.timing(translateY, { toValue: 0, duration: 220, useNativeDriver: true }),
        Animated.timing(backdropOpacity, { toValue: 1, duration: 220, useNativeDriver: true }),
      ]).start();
    } else {
      translateY.setValue(300);
      backdropOpacity.setValue(0);
    }
  }, [visible, translateY, backdropOpacity]);

  return (
    <Modal visible={visible} transparent animationType="none" onRequestClose={onClose}>
      <Animated.View
        style={[styles.backdrop, { backgroundColor: theme.colors.overlay, opacity: backdropOpacity }]}
      >
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} accessibilityLabel="Đóng" />
      </Animated.View>
      <Animated.View
        style={[
          styles.sheet,
          {
            backgroundColor: theme.colors.background,
            borderTopLeftRadius: theme.radius.sheet, // radius/sheet (Figma) = 28, không phải radius.lg
            borderTopRightRadius: theme.radius.sheet,
            padding: theme.spacing.lg,
            transform: [{ translateY }],
          },
          style,
        ]}
      >
        <View
          style={{
            alignSelf: 'center',
            width: 36,
            height: 4,
            borderRadius: 2,
            backgroundColor: theme.colors.border,
            marginBottom: theme.spacing.md,
          }}
        />
        {children}
      </Animated.View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
  },
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
  },
});
