import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../theme';

export type IconCircleTone = 'blue' | 'sky' | 'amber' | 'coral' | 'mint' | 'lilac' | 'streak' | 'neutral';
export type IconCircleSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl';

export interface IconCircleProps {
  /** Vẽ glyph với màu tông 700 và cỡ ≈ 55% vòng. PelaIcon giữ màu gốc nên có thể bỏ qua `color`. */
  renderIcon: (color: string, size: number) => React.ReactNode;
  tone?: IconCircleTone;
  size?: IconCircleSize;
  testID?: string;
}

// IconCircle 475:5626: XS 28 · SM 36 · MD 44 · LG 56 · XL 72; glyph ≈ 55% (MD 44 → 24).
const BOX: Record<IconCircleSize, number> = { xs: 28, sm: 36, md: 44, lg: 56, xl: 72 };

/** Icon trong vòng tròn tint (trang trí, không bấm). */
export function IconCircle({ renderIcon, tone = 'blue', size = 'md', testID }: IconCircleProps) {
  const theme = useTheme();
  const box = BOX[size];
  return (
    <View
      testID={testID}
      style={{
        width: box,
        height: box,
        borderRadius: theme.radii.full,
        backgroundColor: theme.componentColors.tint[tone],
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      {renderIcon(theme.tones[tone].fg, Math.round(box * 0.55))}
    </View>
  );
}
