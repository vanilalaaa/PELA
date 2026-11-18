import React from 'react';
import { View } from 'react-native';
import { SvgXml } from 'react-native-svg';
import { rankMedal1, rankMedal2, rankMedal3, rankMedal4, rankMedal5, rankMedal6 } from '../brand/brandSvgs';

export type RankTier = 1 | 2 | 3 | 4 | 5 | 6;

const MEDALS = { 1: rankMedal1, 2: rankMedal2, 3: rankMedal3, 4: rankMedal4, 5: rankMedal5, 6: rankMedal6 } as const;

export interface RankBadgeProps {
  /** Cấp 1–6 (Đồng → Tinh Anh), ứng A1–C2. */
  tier: RankTier;
  /** Chiều rộng; chiều cao theo tỉ lệ gốc 32×35.2. */
  width?: number;
  testID?: string;
}

/** Huy chương cấp bậc (Figma RankBadge 467:3978, bản không nhãn). Trang trí, ẩn với trình đọc màn hình. */
export function RankBadge({ tier, width, testID }: RankBadgeProps) {
  const art = MEDALS[tier];
  const w = width ?? art.width;
  const h = (w * art.height) / art.width;
  return (
    <View testID={testID} style={{ width: w, height: h }} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <SvgXml xml={art.xml} width={w} height={h} />
    </View>
  );
}
