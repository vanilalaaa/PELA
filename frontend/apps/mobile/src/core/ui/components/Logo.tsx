import React from 'react';
import { View } from 'react-native';
import { SvgXml } from 'react-native-svg';
import { pelaLogoStacked } from '../brand/brandSvgs';

export interface LogoProps {
  /** Chiều rộng (Figma 168); chiều cao theo tỉ lệ. Cỡ tối thiểu mark 64 (Figma). */
  width?: number;
  accessibilityLabel: string;
  testID?: string;
}

/** Logo PELA bản Stacked (Figma Logo 452:1016): mặt Thỏ trong khung tròn + chữ PELA blue-700. */
export function Logo({ width = pelaLogoStacked.width, accessibilityLabel, testID }: LogoProps) {
  const h = (width * pelaLogoStacked.height) / pelaLogoStacked.width;
  return (
    <View testID={testID} style={{ width, height: h }} accessible accessibilityRole="image" accessibilityLabel={accessibilityLabel}>
      <SvgXml xml={pelaLogoStacked.xml} width={width} height={h} />
    </View>
  );
}
