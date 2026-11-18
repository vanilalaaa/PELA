import React, { useEffect, useState } from 'react';
import { Keyboard, KeyboardAvoidingView, ScrollView, TextInput, View, type StyleProp, type ViewStyle } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../theme';
import { KeyboardRevealContext } from './keyboardReveal';

export type ScreenEdge = 'top' | 'bottom';

/**
 * Khoảng đệm dưới ô đang nhập khi tránh bàn phím. TextInput chỉ là phần chữ (cao ~22) bên trong khung
 * ô 52 nên cần thêm phần đệm khung + dòng gợi ý/lỗi bên dưới.
 */
const KEYBOARD_GAP = 56;

/** Lưu vị trí cuộn trong closure (không gây render lại khi cuộn). */
function createOffsetTracker() {
  let y = 0;
  return { get: () => y, set: (next: number) => void (y = next) };
}

/** Mép trên bàn phím (null = đang đóng) và ScrollView hiện tại, giữ trong closure. */
function createKeyboardTracker() {
  let keyboardTop: number | null = null;
  let scrollView: React.ComponentRef<typeof ScrollView> | null = null;
  return {
    top: () => keyboardTop,
    setTop: (next: number | null) => void (keyboardTop = next),
    view: () => scrollView,
    setView: (next: React.ComponentRef<typeof ScrollView> | null) => void (scrollView = next),
  };
}

export interface ScreenProps {
  children: React.ReactNode;
  /** Cuộn nội dung (mặc định true). */
  scroll?: boolean;
  /**
   * Cạnh cần chừa safe area. Mặc định cả trên và dưới. Màn có header của navigator thì bỏ 'top'
   * (header đã chừa); màn trong MainTabs bỏ 'bottom' (BottomNav đã chừa).
   */
  edges?: readonly ScreenEdge[];
  /** Lề ngang theo spacing/screen-gutter (mặc định true). */
  padded?: boolean;
  contentStyle?: StyleProp<ViewStyle>;
  /**
   * Vùng nút ghim đáy (nút chính): nằm ngoài vùng cuộn, trên safe area dưới; nội dung phía trên cuộn.
   * Có footer thì safe area dưới do footer chừa.
   */
  footer?: React.ReactNode;
  /** Giữ footer khi bàn phím mở (màn chỉ có ô ngắn như OTP, không sợ che). Mặc định ẩn. */
  footerWithKeyboard?: boolean;
  /** Nền trong suốt để thấy nền vẽ phía sau (vd. gradient ở 13). Mặc định bgCanvas. */
  transparent?: boolean;
  testID?: string;
}

/**
 * Khung màn chung: nền bgCanvas, chừa safe area trên/dưới (tai thỏ, thanh điều hướng Android),
 * tuỳ chọn cuộn. Mọi màn dùng khung này thay vì tự xử lý inset.
 */
export function Screen({ children, scroll = true, edges = ['top', 'bottom'], padded = true, contentStyle, transparent = false, footer, footerWithKeyboard = false, testID }: ScreenProps) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const topInset = edges.includes('top') ? insets.top : 0;
  const [scrollView, setScrollView] = useState<React.ComponentRef<typeof ScrollView> | null>(null);
  // Vị trí cuộn hiện tại, giữ trong object ổn định (không gây render lại).
  const [scrollY] = useState(createOffsetTracker);

  const [keyboard] = useState(createKeyboardTracker);
  // Bàn phím mở thì ẩn footer để không che ô đang nhập (nút hiện lại khi đóng bàn phím).
  const [keyboardOpen, setKeyboardOpen] = useState(false);

  // Cuộn ô đang nhập lên trên bàn phím (cách KEYBOARD_GAP) nếu bị che. Gọi khi bàn phím mở và khi
  // focus chuyển sang ô khác lúc bàn phím đang mở (Input gọi qua KeyboardRevealContext).
  const [reveal] = useState(() => () => {
    requestAnimationFrame(() => {
      const top = keyboard.top();
      const view = keyboard.view();
      const input = TextInput.State.currentlyFocusedInput();
      if (top === null || !view || !input) return;
      input.measureInWindow((_x: number, y: number, _w: number, h: number) => {
        const overlap = y + h + KEYBOARD_GAP - top;
        if (overlap > 0) view.scrollTo({ y: scrollY.get() + overlap, animated: true });
      });
    });
  });

  useEffect(() => {
    if (!scroll) return undefined;
    keyboard.setView(scrollView);
    const show = Keyboard.addListener('keyboardDidShow', (e) => {
      keyboard.setTop(e.endCoordinates.screenY);
      setKeyboardOpen(true);
      reveal();
    });
    const hide = Keyboard.addListener('keyboardDidHide', () => {
      keyboard.setTop(null);
      setKeyboardOpen(false);
    });
    return () => {
      show.remove();
      hide.remove();
    };
  }, [scroll, scrollView, keyboard, reveal]);
  const inner: StyleProp<ViewStyle> = [
    {
      paddingTop: theme.spacing.md,
      paddingBottom: (edges.includes('bottom') && !(footer && (footerWithKeyboard || !keyboardOpen)) ? insets.bottom : 0) + theme.spacing.lg,
      paddingHorizontal: padded ? theme.spacing.screenGutter : 0,
      gap: theme.spacing.lg,
    },
    contentStyle,
  ];
  // Safe area trên nằm NGOÀI vùng cuộn để nội dung không trượt xuống dưới thanh trạng thái khi cuộn.
  const frame = { flex: 1, paddingTop: topInset, backgroundColor: transparent ? 'transparent' : theme.colors.bgCanvas };
  const footerView = footer && (footerWithKeyboard || !keyboardOpen) ? (
    <View
      style={{
        paddingHorizontal: padded ? theme.spacing.screenGutter : 0,
        paddingTop: theme.spacing.sm,
        paddingBottom: (edges.includes('bottom') ? insets.bottom : 0) + theme.spacing.md,
        gap: theme.spacing.sm,
      }}
    >
      {footer}
    </View>
  ) : null;

  if (!scroll) {
    return (
      <View testID={testID} style={frame}>
        <View style={[{ flex: 1 }, inner]}>{children}</View>
        {footerView}
      </View>
    );
  }
  return (
    <View testID={testID} style={frame}>
      {/* Bàn phím không che ô nhập: thu vùng cuộn lại khi bàn phím mở (app edge-to-edge nên Android cũng cần).
          Chạm vùng trống hoặc kéo để đóng bàn phím. */}
      <KeyboardAvoidingView style={{ flex: 1 }} behavior="padding">
        <KeyboardRevealContext.Provider value={reveal}>
        <ScrollView
          ref={setScrollView}
          onScroll={(e) => scrollY.set(e.nativeEvent.contentOffset.y)}
          scrollEventThrottle={16}
          style={{ flex: 1 }}
          contentContainerStyle={[{ flexGrow: 1 }, inner]}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag"
        >
          {children}
        </ScrollView>
        </KeyboardRevealContext.Provider>
        {footerView}
      </KeyboardAvoidingView>
    </View>
  );
}
